package com.mmx.order.application.service;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.port.in.IntakeUseCase;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.ExternalIdentityGateway;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.RemoteRoutingCircuitOpenException;
import com.mmx.order.application.port.out.RemoteRoutingGateway;
import com.mmx.order.application.port.out.RemoteRoutingRequest;
import com.mmx.order.application.port.out.RemoteRoutingResponse;
import com.mmx.order.application.port.out.RemoteRoutingTransientFailureException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.TradingClientRole;

import java.util.Objects;
import java.util.Optional;

/**
 * Client-deployment (e.g. CGED) leg-A outbound orchestration for a remote routed order. Resolves
 * the hub-side {@code portfolioNumber} via {@link ExternalIdentityGateway} <em>before</em> send,
 * builds a {@link RemoteRoutingRequest} carrying the resolved account + hub-native institution code,
 * calls {@link RemoteRoutingGateway#route(RemoteRoutingRequest)}, and transitions the client-side
 * order from {@code RECEIVED} per the response: {@link RemoteRoutingResponse.Accept} → {@code ROUTED},
 * {@link RemoteRoutingResponse.Reject} → {@code REJECTED}, gateway exception → stays {@code RECEIVED}
 * (silence is never terminal — the gateway owns the ops signal).
 *
 * <p>Spec: {@code order-routing} — Remote account resolution via ExternalIdentityGateway; Silence is
 * never terminal for a remote client-side order; Routing-failure reject is HTTP-only. Design D3/D7.
 */
public final class RemoteRoutedOrderIntake {

    private final ExternalIdentityGateway externalIdentityGateway;
    private final RemoteRoutingGateway remoteRoutingGateway;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public RemoteRoutedOrderIntake(
            ExternalIdentityGateway externalIdentityGateway,
            RemoteRoutingGateway remoteRoutingGateway,
            OrderRepository orderRepository,
            Clock clock) {
        this.externalIdentityGateway = Objects.requireNonNull(externalIdentityGateway);
        this.remoteRoutingGateway = Objects.requireNonNull(remoteRoutingGateway);
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public IntakeUseCase.Result completeIntake(ReceiveOrderCommand command, LegalEntity clientEntity) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(clientEntity, "clientEntity must not be null");
        LegalEntityCode hubCode = ((TradingClientRole) clientEntity.getRole()).connectedHubCode();

        MoneyMarketOrder clientOrder = createClientOrder(command);
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
                        command.institutionCode(),
                        command.externalOrderReference(),
                        command.currency(),
                        command.amount(),
                        command.valueDate(),
                        command.orderType(),
                        command.orderOperation(),
                        command.tenor(),
                        command.noticePeriod(),
                        command.minimumRate(),
                        command.sourceContractNumber());

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
                    new com.mmx.order.domain.model.TraderId(IntakeService.AUDIT_ACTOR_SYSTEM),
                    ((RemoteRoutingResponse.Reject) response).reason(),
                    clock.now());
        }

        MoneyMarketOrder saved = orderRepository.save(clientOrder);
        return new IntakeUseCase.Result(saved.getId(), saved.getStatus(), true);
    }

    private MoneyMarketOrder createClientOrder(ReceiveOrderCommand command) {
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
                command.institutionCode(),
                command.institutionCode(),
                clock.today());
    }

    private IntakeUseCase.Result rejectAtIntake(MoneyMarketOrder clientOrder, String reason) {
        clientOrder.reject(
                new com.mmx.order.domain.model.TraderId(IntakeService.AUDIT_ACTOR_SYSTEM),
                reason,
                clock.now());
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
