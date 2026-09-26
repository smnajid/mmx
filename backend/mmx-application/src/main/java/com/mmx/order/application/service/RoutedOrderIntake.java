package com.mmx.order.application.service;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.port.in.IntakeUseCase;
import com.mmx.order.application.port.out.ClientEnablementRepository;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.DelegatedGrantDirectory;
import com.mmx.order.application.port.out.GlobalAccountDirectory;
import com.mmx.order.application.port.out.GrantResolution;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.model.ClientEnablement;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.RoutedHubOrderDraft;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.TraderId;
import com.mmx.order.domain.model.TradingClientRole;
import com.mmx.order.domain.policy.CounterpartyAccountPolicy;
import com.mmx.order.domain.policy.NewBusinessPolicy;

import java.util.Optional;

/**
 * Local (same-deployment) routing of a TradingClient order to its TradingHub, inside the intake transaction.
 * Every validation failure rejects the client-side order ({@code REJECTED}) and creates no hub-side order:
 * an institution the client has not onboarded; for a Subscription or Increase, an institution closed to new
 * business or a tenor/notice period outside the effective enablement (grant ∩ client enablement); a missing
 * counterparty account for the OrderType at either LegalEntity; an unresolved global account.
 */
public final class RoutedOrderIntake {

    static final String ROUTING_FAILURE = "Routing failure: ";

    private final DelegatedGrantDirectory delegatedGrantDirectory;
    private final GlobalAccountDirectory globalAccountDirectory;
    private final InstitutionRepository institutionRepository;
    private final ClientEnablementRepository clientEnablementRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public RoutedOrderIntake(
            DelegatedGrantDirectory delegatedGrantDirectory,
            GlobalAccountDirectory globalAccountDirectory,
            InstitutionRepository institutionRepository,
            ClientEnablementRepository clientEnablementRepository,
            OrderRepository orderRepository,
            Clock clock) {
        this.delegatedGrantDirectory = delegatedGrantDirectory;
        this.globalAccountDirectory = globalAccountDirectory;
        this.institutionRepository = institutionRepository;
        this.clientEnablementRepository = clientEnablementRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    /** The client's own onboarded institution for {@code institutionCode}, if any. */
    public Optional<Institution> findOnboarded(String institutionCode, LegalEntityCode clientCode) {
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new InvalidOrderException("institutionCode is required");
        }
        return institutionRepository
                .findByInstitutionCode(institutionCode)
                .filter(Institution::isOnboarded)
                .filter(i -> clientCode.equals(i.getOwningLegalEntityCode()));
    }

    public GrantResolution resolveGrant(ReceiveOrderCommand command, Institution onboarded) {
        return switch (command.orderType()) {
            case TERM ->
                    delegatedGrantDirectory.resolveTenor(
                            command.legalEntityCode(),
                            onboarded.getInstitutionCode(),
                            command.currency(),
                            command.tenor(),
                            command.orderOperation());
            case ON_CALL ->
                    delegatedGrantDirectory.resolveNotice(
                            command.legalEntityCode(),
                            onboarded.getInstitutionCode(),
                            command.currency(),
                            command.noticePeriod(),
                            command.orderOperation());
        };
    }

    public GlobalAccount resolveGlobalAccount(
            LegalEntityCode clientCode, LegalEntityCode hubCode, String currency) {
        return globalAccountDirectory.resolve(clientCode, hubCode, currency).orElse(null);
    }

    public IntakeUseCase.Result completeIntake(
            ReceiveOrderCommand command, Optional<Institution> onboardedOpt, LegalEntity clientEntity) {
        if (onboardedOpt.isEmpty()) {
            return rejectAtIntake(
                    command,
                    command.institutionCode(),
                    command.institutionCode(),
                    "Institution " + command.institutionCode() + " is not onboarded by " + command.legalEntityCode());
        }
        Institution onboarded = onboardedOpt.get();
        boolean addsExposure = NewBusinessPolicy.addsExposure(command.orderOperation());
        if (addsExposure && onboarded.isClosedToNewBusiness()) {
            return rejectAtIntake(
                    command, onboarded, "Institution " + onboarded.getInstitutionCode() + " is closed to new business");
        }
        Optional<String> clientAccount = onboarded.getCounterpartyAccounts().accountFor(command.orderType());
        if (clientAccount.isEmpty()) {
            return rejectAtIntake(command, onboarded, missingAccount(onboarded, command));
        }

        GrantResolution grantResolution = resolveGrant(command, onboarded);
        if (!grantResolution.isPermitted()) {
            return rejectAtIntake(command, onboarded, "Delegated grant validation failed: " + grantResolution);
        }
        if (addsExposure && !isClientEnabled(command, onboarded)) {
            return rejectAtIntake(
                    command,
                    onboarded,
                    "Client enablement: " + termCode(command) + " is not enabled for " + command.currency()
                            + " on " + onboarded.getInstitutionCode());
        }

        LegalEntityCode hubCode = ((TradingClientRole) clientEntity.getRole()).connectedHubCode();
        GlobalAccount globalAccount = resolveGlobalAccount(command.legalEntityCode(), hubCode, command.currency());
        if (globalAccount == null) {
            return rejectAtIntake(command, onboarded, "No global account configured for routing");
        }

        String hubInstitutionCode = onboarded.getHubLink().orElseThrow().hubInstitutionCode();
        Optional<Institution> hubInstitutionOpt = institutionRepository.findByInstitutionCode(hubInstitutionCode);
        if (hubInstitutionOpt.isEmpty()) {
            return rejectAtIntake(
                    command, onboarded, ROUTING_FAILURE + "hub native institution " + hubInstitutionCode + " not found");
        }
        Institution hubInstitution = hubInstitutionOpt.get();
        if (addsExposure && hubInstitution.isClosedToNewBusiness()) {
            return rejectAtIntake(
                    command,
                    onboarded,
                    ROUTING_FAILURE + "hub institution " + hubInstitutionCode + " is closed to new business");
        }
        if (hubInstitution.getCounterpartyAccounts().accountFor(command.orderType()).isEmpty()) {
            return rejectAtIntake(command, onboarded, ROUTING_FAILURE + missingAccount(hubInstitution, command));
        }

        MoneyMarketOrder clientOrder = createClientOrder(command, onboarded);
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
                                                hubInstitution.getDisplayName(),
                                                clientAccount.get()),
                                        clock.today()));

        markRouted(clientOrder, routingId);
        MoneyMarketOrder savedClient = orderRepository.save(clientOrder);
        if (existingHub.isEmpty()) {
            orderRepository.save(hubOrder);
        }

        return new IntakeUseCase.Result(savedClient.getId(), savedClient.getStatus(), true);
    }

    /** Effective enablement check: the grant already admitted the term; the client must have switched it on. */
    private boolean isClientEnabled(ReceiveOrderCommand command, Institution onboarded) {
        ClientEnablement enablement = clientEnablementRepository.find(onboarded.getInstitutionCode(), command.currency());
        return switch (command.orderType()) {
            case TERM -> command.tenor() != null && enablement.tenors().contains(command.tenor());
            case ON_CALL -> command.noticePeriod() != null && enablement.noticePeriods().contains(command.noticePeriod());
        };
    }

    private static String termCode(ReceiveOrderCommand command) {
        return switch (command.orderType()) {
            case TERM -> command.tenor() == null ? "tenor" : command.tenor().getCode();
            case ON_CALL -> command.noticePeriod() == null ? "notice period" : command.noticePeriod().getCode();
        };
    }

    private static String missingAccount(Institution institution, ReceiveOrderCommand command) {
        return "Institution " + institution.getInstitutionCode() + " has no "
                + CounterpartyAccountPolicy.label(command.orderType()) + " counterparty account";
    }

    public IntakeUseCase.Result rejectAtIntake(
            ReceiveOrderCommand command, Institution onboarded, String reason) {
        return rejectAtIntake(command, onboarded.getInstitutionCode(), onboarded.getDisplayName(), reason);
    }

    private IntakeUseCase.Result rejectAtIntake(
            ReceiveOrderCommand command, String institutionCode, String counterparty, String reason) {
        MoneyMarketOrder clientOrder = createClientOrder(command, institutionCode, counterparty);
        clientOrder.reject(new TraderId(IntakeService.AUDIT_ACTOR_SYSTEM), reason, clock.now());
        MoneyMarketOrder saved = orderRepository.save(clientOrder);
        return new IntakeUseCase.Result(saved.getId(), saved.getStatus(), true);
    }

    void markRouted(MoneyMarketOrder clientOrder, RoutingId routingId) {
        clientOrder.markRouted(routingId, clock.now());
    }

    MoneyMarketOrder createClientOrder(ReceiveOrderCommand command, Institution onboarded) {
        return createClientOrder(command, onboarded.getInstitutionCode(), onboarded.getDisplayName());
    }

    private MoneyMarketOrder createClientOrder(ReceiveOrderCommand command, String institutionCode, String counterparty) {
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
                institutionCode,
                counterparty,
                clock.today());
    }

    private RoutedHubOrderDraft toHubSideDraft(
            MoneyMarketOrder clientOrder,
            GlobalAccount globalAccount,
            RoutingId routingId,
            String hubNativeInstitutionCode,
            String hubNativeInstitutionDisplayName,
            String clientCounterpartyAccount) {
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
                clientOrder.getExternalOrderReference(),
                clientCounterpartyAccount);
    }

    private static ContractNumber intakeSourceContractNumber(ReceiveOrderCommand command) {
        if (command.orderOperation() == OrderOperation.SUBSCRIPTION) {
            return null;
        }
        return command.sourceContractNumber();
    }
}
