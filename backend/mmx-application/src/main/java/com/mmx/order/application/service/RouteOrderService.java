package com.mmx.order.application.service;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.port.in.RouteOrderUseCase;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.DelegatedGrantDirectory;
import com.mmx.order.application.port.out.GlobalAccountDirectory;
import com.mmx.order.application.port.out.GrantResolution;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OpenPositionPort;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.OrganisationRepository;
import com.mmx.order.application.port.out.ProxyInstitutionRepository;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OpenContractPosition;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.OrganisationCode;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.ThinProxyInstitution;
import com.mmx.order.domain.model.TradingClientRole;
import com.mmx.order.domain.policy.OrderAgainstCurrencyPolicy;
import com.mmx.order.domain.policy.OrderRoutingFieldMappingPolicy;
import com.mmx.order.domain.policy.OrderRoutingFieldMappingPolicy.RoutedHubOrderDraft;

import java.util.Optional;
import java.util.UUID;

public final class RouteOrderService implements RouteOrderUseCase {

    static final String AUDIT_ACTOR_SYSTEM = ReceiveOrderService.AUDIT_ACTOR_SYSTEM;
    static final String EVENT_ORDER_ROUTED = "ORDER_ROUTED";
    static final String EVENT_ORDER_ROUTING_REJECTED = "ORDER_ROUTING_REJECTED";
    static final String EVENT_DUPLICATE_RECEIVE_IGNORED = ReceiveOrderService.EVENT_DUPLICATE_RECEIVE_IGNORED;

    private final OrderRepository orderRepository;
    private final ManagedCurrencyRepository managedCurrencyRepository;
    private final InstitutionRepository institutionRepository;
    private final ProxyInstitutionRepository proxyInstitutionRepository;
    private final OpenPositionPort openPositionPort;
    private final OrganisationRepository organisationRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final DelegatedGrantDirectory delegatedGrantDirectory;
    private final GlobalAccountDirectory globalAccountDirectory;
    private final OrganisationCode portfolioManagementOrganisation;
    private final OrderAgainstCurrencyPolicy currencyPolicy;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public RouteOrderService(
            OrderRepository orderRepository,
            ManagedCurrencyRepository managedCurrencyRepository,
            InstitutionRepository institutionRepository,
            ProxyInstitutionRepository proxyInstitutionRepository,
            OpenPositionPort openPositionPort,
            OrganisationRepository organisationRepository,
            LegalEntityRepository legalEntityRepository,
            DelegatedGrantDirectory delegatedGrantDirectory,
            GlobalAccountDirectory globalAccountDirectory,
            OrganisationCode portfolioManagementOrganisation,
            AuditLogger auditLogger,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.managedCurrencyRepository = managedCurrencyRepository;
        this.institutionRepository = institutionRepository;
        this.proxyInstitutionRepository = proxyInstitutionRepository;
        this.openPositionPort = openPositionPort;
        this.organisationRepository = organisationRepository;
        this.legalEntityRepository = legalEntityRepository;
        this.delegatedGrantDirectory = delegatedGrantDirectory;
        this.globalAccountDirectory = globalAccountDirectory;
        this.portfolioManagementOrganisation = portfolioManagementOrganisation;
        this.currencyPolicy = new OrderAgainstCurrencyPolicy();
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Override
    public Result route(ReceiveOrderCommand command) {
        LegalEntity clientEntity = resolveTradingClient(command.legalEntityCode());

        var existingClient =
                orderRepository.findByLegalEntityAndExternalReference(
                        command.legalEntityCode(), command.externalOrderReference());
        if (existingClient.isPresent()) {
            MoneyMarketOrder existing = existingClient.get();
            auditLogger.log(existing.getId(), EVENT_DUPLICATE_RECEIVE_IGNORED, AUDIT_ACTOR_SYSTEM, clock.now());
            UUID hubId =
                    existing.getRoutingId() != null
                            ? orderRepository
                                    .findHubOrderByRoutingId(existing.getRoutingId())
                                    .map(MoneyMarketOrder::getId)
                                    .orElse(null)
                            : null;
            return new Result(existing.getId(), hubId, existing.getStatus(), false);
        }

        ThinProxyInstitution proxy = resolveProxy(command.institutionCode());
        GrantResolution grantResolution = resolveGrant(command, proxy);
        if (!grantResolution.isGranted()) {
            return rejectAtIntake(command, proxy, "Delegated grant validation failed: " + grantResolution);
        }
        validateCurrency(command);

        LegalEntityCode hubCode = ((TradingClientRole) clientEntity.getRole()).connectedHubCode();
        GlobalAccount globalAccount =
                globalAccountDirectory
                        .resolve(command.legalEntityCode(), hubCode, command.currency())
                        .orElse(null);
        if (globalAccount == null) {
            return rejectAtIntake(command, proxy, "No global account configured for routing");
        }

        Institution hubInstitution =
                institutionRepository
                        .findByInstitutionCode(proxy.getHubInstitutionCode())
                        .orElseThrow(
                                () ->
                                        new InvalidOrderException(
                                                "Hub native institution not found: "
                                                        + proxy.getHubInstitutionCode()));

        MoneyMarketOrder clientOrder = createClientOrder(command, proxy);
        RoutingId routingId = RoutingId.fromClientOrderId(clientOrder.getId());

        Optional<MoneyMarketOrder> existingHub = orderRepository.findHubOrderByRoutingId(routingId);
        MoneyMarketOrder hubOrder =
                existingHub.orElseGet(
                        () -> {
                            RoutedHubOrderDraft draft =
                                    OrderRoutingFieldMappingPolicy.mapToHubSide(
                                            clientOrder,
                                            globalAccount,
                                            routingId,
                                            hubInstitution.getInstitutionCode(),
                                            hubInstitution.getDisplayName());
                            return MoneyMarketOrder.createHubSideFromRouting(draft, clock.today());
                        });

        clientOrder.markRouted(routingId, clock.now());
        MoneyMarketOrder savedClient = orderRepository.save(clientOrder);
        MoneyMarketOrder savedHub =
                existingHub.isPresent() ? hubOrder : orderRepository.save(hubOrder);

        auditLogger.log(savedClient.getId(), EVENT_ORDER_ROUTED, AUDIT_ACTOR_SYSTEM, clock.now());
        return new Result(
                savedClient.getId(), savedHub.getId(), savedClient.getStatus(), true);
    }

    private Result rejectAtIntake(ReceiveOrderCommand command, ThinProxyInstitution proxy, String reason) {
        MoneyMarketOrder clientOrder = createClientOrder(command, proxy);
        clientOrder.reject(
                new com.mmx.order.domain.model.TraderId(AUDIT_ACTOR_SYSTEM), reason, clock.now());
        MoneyMarketOrder saved = orderRepository.save(clientOrder);
        auditLogger.log(saved.getId(), EVENT_ORDER_ROUTING_REJECTED, AUDIT_ACTOR_SYSTEM, clock.now());
        return new Result(saved.getId(), null, saved.getStatus(), true);
    }

    private GrantResolution resolveGrant(ReceiveOrderCommand command, ThinProxyInstitution proxy) {
        return command.orderType() == OrderType.TERM
                ? delegatedGrantDirectory.resolveTenor(
                        command.legalEntityCode(),
                        proxy.getInstitutionCode(),
                        command.currency(),
                        command.tenor())
                : delegatedGrantDirectory.resolveNotice(
                        command.legalEntityCode(),
                        proxy.getInstitutionCode(),
                        command.currency(),
                        command.noticePeriod());
    }

    private void validateCurrency(ReceiveOrderCommand command) {
        var currencyOpt = managedCurrencyRepository.findByCode(command.currency());
        Optional<OpenContractPosition> openPosition =
                command.orderOperation() == OrderOperation.DECREASE && command.sourceContractNumber() != null
                        ? openPositionPort.findOpenByContractNumber(command.sourceContractNumber())
                        : Optional.empty();
        currencyPolicy.validateReceive(
                currencyOpt,
                command.currency(),
                command.orderType(),
                command.orderOperation(),
                command.amount(),
                command.tenor(),
                command.noticePeriod(),
                openPosition);
    }

    private MoneyMarketOrder createClientOrder(ReceiveOrderCommand command, ThinProxyInstitution proxy) {
        return MoneyMarketOrder.create(
                command.externalOrderReference(),
                command.legalEntityCode(),
                command.orderType(),
                command.orderOperation(),
                command.portfolioNumber(),
                command.currency(),
                command.amount(),
                command.valueDate(),
                command.minimumRate(),
                command.tenor(),
                command.noticePeriod(),
                intakeSourceContractNumber(command),
                proxy.getInstitutionCode(),
                proxy.getDisplayName(),
                clock.today());
    }

    private LegalEntity resolveTradingClient(LegalEntityCode legalEntityCode) {
        if (legalEntityCode == null) {
            throw new InvalidOrderException("legalEntityCode is required");
        }
        organisationRepository
                .findByCode(portfolioManagementOrganisation)
                .orElseThrow(
                        () ->
                                new InvalidOrderException(
                                        "Portfolio Management Organisation is not configured"));
        LegalEntity entity =
                legalEntityRepository
                        .findByCode(legalEntityCode)
                        .orElseThrow(
                                () -> new InvalidOrderException("Unknown legalEntityCode: " + legalEntityCode));
        if (!entity.isTradingClient()) {
            throw new InvalidOrderException("Routing intake requires a TradingClient legal entity");
        }
        if (!legalEntityRepository.belongsToOrganisation(legalEntityCode, portfolioManagementOrganisation)) {
            throw new InvalidOrderException(
                    "legalEntityCode is not a LegalEntity of the Portfolio Management Organisation");
        }
        return entity;
    }

    private ThinProxyInstitution resolveProxy(String institutionCode) {
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new InvalidOrderException("institutionCode is required");
        }
        return proxyInstitutionRepository
                .findByInstitutionCode(institutionCode)
                .orElseThrow(() -> new InvalidOrderException("Unknown proxy institution: " + institutionCode));
    }

    private static ContractNumber intakeSourceContractNumber(ReceiveOrderCommand command) {
        if (command.orderOperation() == OrderOperation.SUBSCRIPTION) {
            return null;
        }
        return command.sourceContractNumber();
    }
}
