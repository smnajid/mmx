package com.mmx.order.application.service;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.port.in.IntakeUseCase;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.DelegatedGrantDirectory;
import com.mmx.order.application.port.out.GlobalAccountDirectory;
import com.mmx.order.application.port.out.GrantResolution;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.ProxyInstitutionRepository;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.RoutedHubOrderDraft;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.ThinProxyInstitution;
import com.mmx.order.domain.model.TradingClientRole;

import java.util.Optional;

public final class RoutedOrderIntake {

    private final ProxyInstitutionRepository proxyInstitutionRepository;
    private final DelegatedGrantDirectory delegatedGrantDirectory;
    private final GlobalAccountDirectory globalAccountDirectory;
    private final InstitutionRepository institutionRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public RoutedOrderIntake(
            ProxyInstitutionRepository proxyInstitutionRepository,
            DelegatedGrantDirectory delegatedGrantDirectory,
            GlobalAccountDirectory globalAccountDirectory,
            InstitutionRepository institutionRepository,
            OrderRepository orderRepository,
            Clock clock) {
        this.proxyInstitutionRepository = proxyInstitutionRepository;
        this.delegatedGrantDirectory = delegatedGrantDirectory;
        this.globalAccountDirectory = globalAccountDirectory;
        this.institutionRepository = institutionRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    public ThinProxyInstitution resolveProxy(String institutionCode) {
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new InvalidOrderException("institutionCode is required");
        }
        return proxyInstitutionRepository
                .findByInstitutionCode(institutionCode)
                .orElseThrow(() -> new InvalidOrderException("Unknown proxy institution: " + institutionCode));
    }

    public GrantResolution resolveGrant(ReceiveOrderCommand command, ThinProxyInstitution proxy) {
        return switch (command.orderType()) {
            case TERM ->
                    delegatedGrantDirectory.resolveTenor(
                            command.legalEntityCode(),
                            proxy.getInstitutionCode(),
                            command.currency(),
                            command.tenor());
            case ON_CALL ->
                    delegatedGrantDirectory.resolveNotice(
                            command.legalEntityCode(),
                            proxy.getInstitutionCode(),
                            command.currency(),
                            command.noticePeriod());
        };
    }

    public GlobalAccount resolveGlobalAccount(
            LegalEntityCode clientCode, LegalEntityCode hubCode, String currency) {
        return globalAccountDirectory.resolve(clientCode, hubCode, currency).orElse(null);
    }

    public IntakeUseCase.Result completeIntake(
            ReceiveOrderCommand command, ThinProxyInstitution proxy, LegalEntity clientEntity) {
        GrantResolution grantResolution = resolveGrant(command, proxy);
        if (!grantResolution.isGranted()) {
            return rejectAtIntake(command, proxy, "Delegated grant validation failed: " + grantResolution);
        }

        LegalEntityCode hubCode = ((TradingClientRole) clientEntity.getRole()).connectedHubCode();
        GlobalAccount globalAccount = resolveGlobalAccount(command.legalEntityCode(), hubCode, command.currency());
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
                        () ->
                                MoneyMarketOrder.createHubSideFromRouting(
                                        toHubSideDraft(
                                                clientOrder,
                                                globalAccount,
                                                routingId,
                                                hubInstitution.getInstitutionCode(),
                                                hubInstitution.getDisplayName()),
                                        clock.today()));

        markRouted(clientOrder, routingId);
        MoneyMarketOrder savedClient = orderRepository.save(clientOrder);
        if (existingHub.isEmpty()) {
            orderRepository.save(hubOrder);
        }

        return new IntakeUseCase.Result(savedClient.getId(), savedClient.getStatus(), true);
    }

    public IntakeUseCase.Result rejectAtIntake(
            ReceiveOrderCommand command, ThinProxyInstitution proxy, String reason) {
        MoneyMarketOrder clientOrder = createClientOrder(command, proxy);
        clientOrder.reject(
                new com.mmx.order.domain.model.TraderId(IntakeService.AUDIT_ACTOR_SYSTEM), reason, clock.now());
        MoneyMarketOrder saved = orderRepository.save(clientOrder);
        return new IntakeUseCase.Result(saved.getId(), saved.getStatus(), true);
    }

    void markRouted(MoneyMarketOrder clientOrder, RoutingId routingId) {
        clientOrder.markRouted(routingId, clock.now());
    }

    MoneyMarketOrder createClientOrder(ReceiveOrderCommand command, ThinProxyInstitution proxy) {
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

    private RoutedHubOrderDraft toHubSideDraft(
            MoneyMarketOrder clientOrder,
            GlobalAccount globalAccount,
            RoutingId routingId,
            String hubNativeInstitutionCode,
            String hubNativeInstitutionDisplayName) {
        return new RoutedHubOrderDraft(
                globalAccount.hubLegalEntityCode(),
                globalAccount.asPortfolioNumber(),
                hubNativeInstitutionCode,
                hubNativeInstitutionDisplayName,
                clientOrder.getCurrency(),
                clientOrder.getAmount(),
                clientOrder.getValueDate(),
                clientOrder.getOrderType(),
                clientOrder.getOrderOperation(),
                clientOrder.getTenor(),
                clientOrder.getNoticePeriod(),
                clientOrder.getMinimumRate(),
                clientOrder.getSourceContractNumber(),
                routingId,
                clientOrder.getLegalEntityCode(),
                clientOrder.getExternalOrderReference());
    }

    private static ContractNumber intakeSourceContractNumber(ReceiveOrderCommand command) {
        if (command.orderOperation() == OrderOperation.SUBSCRIPTION) {
            return null;
        }
        return command.sourceContractNumber();
    }
}
