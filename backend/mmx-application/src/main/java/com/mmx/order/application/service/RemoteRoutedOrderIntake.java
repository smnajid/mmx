package com.mmx.order.application.service;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.port.in.IntakeUseCase;
import com.mmx.order.application.port.out.ClientEnablementRepository;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.ExternalIdentityGateway;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.RemoteRoutingCircuitOpenException;
import com.mmx.order.application.port.out.RemoteRoutingGateway;
import com.mmx.order.application.port.out.RemoteRoutingRequest;
import com.mmx.order.application.port.out.RemoteRoutingResponse;
import com.mmx.order.application.port.out.RemoteRoutingTransientFailureException;
import com.mmx.order.domain.model.ClientEnablement;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.TraderId;
import com.mmx.order.domain.model.TradingClientRole;
import com.mmx.order.domain.policy.NewBusinessPolicy;
import com.mmx.order.domain.policy.OrderAgainstInstitutionPolicy;

import java.util.Objects;
import java.util.Optional;

/**
 * Client-deployment (e.g. CGEG) leg-A outbound orchestration for a remote routed order.
 *
 * <p>The client first validates what it owns, rejecting synchronously as a routing failure with no leg-A
 * send: the institution must be one of its own onboarded institutions, it must hold the counterparty
 * account for the OrderType, and for a Subscription or Increase the institution must be open to new
 * business and the tenor/notice period within the client enablement. (The hub's leg-A grant check is the
 * one true validation of the grant, which completes the effective enablement, grant ∩ client enablement.)
 *
 * <p>It then resolves the hub-side {@code portfolioNumber} via {@link ExternalIdentityGateway}, builds a
 * {@link RemoteRoutingRequest} carrying the resolved account, the hub-native institution code linked to the
 * onboarded institution and the client counterparty account snapshot, calls
 * {@link RemoteRoutingGateway#route(RemoteRoutingRequest)}, and transitions the client-side order from
 * {@code RECEIVED} per the response: {@link RemoteRoutingResponse.Accept} → {@code ROUTED},
 * {@link RemoteRoutingResponse.Reject} → {@code REJECTED}, gateway exception → stays {@code RECEIVED}
 * (silence is never terminal — the gateway owns the ops signal).
 */
public final class RemoteRoutedOrderIntake {

    private final ExternalIdentityGateway externalIdentityGateway;
    private final RemoteRoutingGateway remoteRoutingGateway;
    private final InstitutionRepository institutionRepository;
    private final ClientEnablementRepository clientEnablementRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public RemoteRoutedOrderIntake(
            ExternalIdentityGateway externalIdentityGateway,
            RemoteRoutingGateway remoteRoutingGateway,
            InstitutionRepository institutionRepository,
            ClientEnablementRepository clientEnablementRepository,
            OrderRepository orderRepository,
            Clock clock) {
        this.externalIdentityGateway = Objects.requireNonNull(externalIdentityGateway);
        this.remoteRoutingGateway = Objects.requireNonNull(remoteRoutingGateway);
        this.institutionRepository = Objects.requireNonNull(institutionRepository);
        this.clientEnablementRepository = Objects.requireNonNull(clientEnablementRepository);
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public IntakeUseCase.Result completeIntake(ReceiveOrderCommand command, LegalEntity clientEntity) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(clientEntity, "clientEntity must not be null");
        LegalEntityCode hubCode = ((TradingClientRole) clientEntity.getRole()).connectedHubCode();

        Optional<Institution> onboardedOpt =
                Optional.ofNullable(command.institutionCode())
                        .flatMap(institutionRepository::findByInstitutionCode)
                        .filter(Institution::isOnboarded)
                        .filter(i -> clientEntity.getCode().equals(i.getOwningLegalEntityCode()));
        if (onboardedOpt.isEmpty()) {
            return rejectAtIntake(
                    createClientOrder(command, command.institutionCode(), command.institutionCode()),
                    RoutedOrderIntake.ROUTING_FAILURE + "institution " + command.institutionCode()
                            + " is not onboarded by " + clientEntity.getCode());
        }
        Institution onboarded = onboardedOpt.get();
        MoneyMarketOrder clientOrder =
                createClientOrder(command, onboarded.getInstitutionCode(), onboarded.getDisplayName());
        Optional<String> refusal = refuseOnClientSide(command, onboarded);
        if (refusal.isPresent()) {
            return rejectAtIntake(clientOrder, RoutedOrderIntake.ROUTING_FAILURE + refusal.get());
        }
        String clientCounterpartyAccount =
                onboarded.getCounterpartyAccounts().accountFor(command.orderType()).orElseThrow();

        RoutingId routingId = RoutingId.fromClientOrderId(clientOrder.getId());
        Optional<PortfolioNumber> resolvedAccount =
                externalIdentityGateway.resolveHubSidePortfolioNumber(
                        command.legalEntityCode(), command.portfolioNumber(), hubCode);
        if (resolvedAccount.isEmpty()) {
            return rejectAtIntake(
                    clientOrder,
                    "External identity gateway could not resolve hub-side account for ("
                            + command.legalEntityCode() + ", "
                            + command.portfolioNumber() + ", " + hubCode + ")");
        }

        RemoteRoutingRequest request =
                new RemoteRoutingRequest(
                        command.legalEntityCode(),
                        routingId,
                        resolvedAccount.get(),
                        onboarded.getHubLink().orElseThrow().hubInstitutionCode(),
                        command.externalOrderReference(),
                        command.currency(),
                        command.amount(),
                        command.valueDate(),
                        command.orderType(),
                        command.orderOperation(),
                        command.tenor(),
                        command.noticePeriod(),
                        command.minimumRate(),
                        command.sourceContractNumber(),
                        clientCounterpartyAccount);

        RemoteRoutingResponse response;
        try {
            response = remoteRoutingGateway.route(request);
        } catch (RemoteRoutingCircuitOpenException | RemoteRoutingTransientFailureException silence) {
            // Silence is never terminal — the gateway owns the ops signal; the order stays RECEIVED.
            MoneyMarketOrder saved = orderRepository.save(clientOrder);
            return new IntakeUseCase.Result(saved.getId(), saved.getStatus(), true);
        }

        if (response.isAccepted()) {
            clientOrder.markRouted(routingId, clock.now());
        } else {
            clientOrder.reject(
                    new TraderId(IntakeService.AUDIT_ACTOR_SYSTEM),
                    ((RemoteRoutingResponse.Reject) response).reason(),
                    clock.now());
        }

        MoneyMarketOrder saved = orderRepository.save(clientOrder);
        return new IntakeUseCase.Result(saved.getId(), saved.getStatus(), true);
    }

    /** The client-owned facts: counterparty account, and for new business, open state and client enablement. */
    private Optional<String> refuseOnClientSide(ReceiveOrderCommand command, Institution onboarded) {
        Optional<String> refusal =
                OrderAgainstInstitutionPolicy.refusal(onboarded, command.orderOperation(), command.orderType());
        if (refusal.isPresent()) {
            return refusal;
        }
        if (NewBusinessPolicy.addsExposure(command.orderOperation()) && !isClientEnabled(command, onboarded)) {
            return Optional.of("client enablement does not include this " + command.orderType() + " term for "
                    + command.currency() + "; it is not enabled on " + onboarded.getInstitutionCode());
        }
        return Optional.empty();
    }

    private boolean isClientEnabled(ReceiveOrderCommand command, Institution onboarded) {
        ClientEnablement enablement = clientEnablementRepository.find(onboarded.getInstitutionCode(), command.currency());
        return switch (command.orderType()) {
            case TERM -> command.tenor() != null && enablement.tenors().contains(command.tenor());
            case ON_CALL -> command.noticePeriod() != null && enablement.noticePeriods().contains(command.noticePeriod());
        };
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

    private IntakeUseCase.Result rejectAtIntake(MoneyMarketOrder clientOrder, String reason) {
        clientOrder.reject(new TraderId(IntakeService.AUDIT_ACTOR_SYSTEM), reason, clock.now());
        MoneyMarketOrder saved = orderRepository.save(clientOrder);
        return new IntakeUseCase.Result(saved.getId(), saved.getStatus(), true);
    }

    private static ContractNumber intakeSourceContractNumber(ReceiveOrderCommand command) {
        if (command.orderOperation() == OrderOperation.SUBSCRIPTION) {
            return null;
        }
        return command.sourceContractNumber();
    }
}
