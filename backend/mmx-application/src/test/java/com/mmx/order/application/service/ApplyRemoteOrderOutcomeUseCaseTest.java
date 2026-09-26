package com.mmx.order.application.service;

import com.mmx.order.application.port.in.RemoteOrderOutcome;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.ReferenceGenerator;
import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import com.mmx.order.domain.exception.RoutedOrderPairIntegrityException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;
import com.mmx.order.domain.model.ExecutionDetails;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Client-deployment (CGED) leg-B inbound: {@code ApplyRemoteOrderOutcomeUseCase} applies a leg-B
 * {@link RemoteOrderOutcome} to the linked client-side order. It is idempotent under at-least-once
 * Kafka (re-delivery is a no-op ack; mismatched terminal surfaces as an error), and surfaces a
 * broken routed pair as {@link RoutedOrderPairIntegrityException}.
 *
 * <p>Spec: {@code order-routing} — silence is never terminal; leg B is the authoritative lifecycle
 * mirror.
 */
@Tag("fast")
@ExtendWith(MockitoExtension.class)
class ApplyRemoteOrderOutcomeUseCaseTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final LegalEntityCode CLIENT_LE = new LegalEntityCode("CGD");
    private static final LegalEntityCode FOREIGN_LE = new LegalEntityCode("ZZZ");
    private static final ContractNumber CLIENT_CONTRACT = new ContractNumber("CN-client");
    private static final ContractNumber SOURCE_CONTRACT = new ContractNumber("CN-source");
    private static final RoutingId ROUTING_ID = RoutingId.fromClientOrderId(UUID.randomUUID());

    @Mock
    OrderRepository orderRepository;
    @Mock
    ReferenceGenerator referenceGenerator;

    ApplyRemoteOrderOutcomeService subject;

    @BeforeEach
    void setUp() {
        subject = new ApplyRemoteOrderOutcomeService(orderRepository, referenceGenerator);
    }

    // ── ACCEPTED ───────────────────────────────────────────────────────────────

    @Test
    void accepted_onReceived_closesToRouted_andSaves() {
        MoneyMarketOrder client = receivedSubscriptionClient();
        stubClient(ROUTING_ID, client);
        when(orderRepository.save(any())).then(returnsFirstArg());

        subject.apply(new RemoteOrderOutcome.Accepted(CLIENT_LE, ROUTING_ID, FIXED_NOW));

        assertThat(client.getStatus()).isEqualTo(OrderStatus.ROUTED);
        assertThat(client.getRoutingId()).isEqualTo(ROUTING_ID);
        verify(orderRepository).save(client);
    }

    @Test
    void accepted_onAlreadyRouted_isNoOpAck_doesNotSave() {
        // Leg A delivered the accept first; leg-B ACCEPTED is benign redundancy (offset advances).
        MoneyMarketOrder client = routedSubscriptionClient();
        stubClient(client.getRoutingId(), client);

        subject.apply(new RemoteOrderOutcome.Accepted(CLIENT_LE, client.getRoutingId(), FIXED_NOW));

        assertThat(client.getStatus()).isEqualTo(OrderStatus.ROUTED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void accepted_onTerminal_isNoOpAck_doesNotSave() {
        // Silence already resolved terminally; a stale ACCEPTED cannot overwrite it.
        MoneyMarketOrder client = routedSubscriptionClient();
        client.propagateCancelFromHub(FIXED_NOW);
        stubClient(client.getRoutingId(), client);

        subject.apply(new RemoteOrderOutcome.Accepted(CLIENT_LE, client.getRoutingId(), FIXED_NOW));

        assertThat(client.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository, never()).save(any());
    }

    // ── EXECUTED ───────────────────────────────────────────────────────────────

    @Test
    void executed_subscription_onRouted_generatesClientContract_appliesAndSaves() {
        MoneyMarketOrder client = routedSubscriptionClient();
        stubClient(client.getRoutingId(), client);
        when(orderRepository.save(any())).then(returnsFirstArg());
        when(referenceGenerator.generateContractNumber()).thenReturn(CLIENT_CONTRACT);

        subject.apply(executed(ROUTING_ID));

        assertThat(client.getStatus()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(client.getExecutionDetails().generatedContractNumber()).isEqualTo(CLIENT_CONTRACT);
        verify(orderRepository).save(client);
    }

    @Test
    void executed_lifecycle_onRouted_reusesSourceContractNumber_doesNotGenerate() {
        MoneyMarketOrder client = routedRedemptionClient();
        stubClient(client.getRoutingId(), client);
        when(orderRepository.save(any())).then(returnsFirstArg());

        subject.apply(executed(client.getRoutingId()));

        assertThat(client.getStatus()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(client.getExecutionDetails().generatedContractNumber()).isEqualTo(SOURCE_CONTRACT);
        verify(referenceGenerator, never()).generateContractNumber();
    }

    @Test
    void executed_onAlreadyExecuted_isIdempotentNoOp_doesNotSave() {
        // At-least-once re-delivery of EXECUTED after EXECUTED — no-op ack, offset advances.
        MoneyMarketOrder client = routedSubscriptionClient();
        client.propagateExecutionFromHub(hubExecution(), client.getCounterparty(), CLIENT_CONTRACT, FIXED_NOW);
        stubClient(client.getRoutingId(), client);

        subject.apply(executed(client.getRoutingId()));

        assertThat(client.getStatus()).isEqualTo(OrderStatus.EXECUTED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void executed_onCancelled_throwsMismatchedTerminal_doesNotSave() {
        // Poison-message guard: a mismatched terminal under at-least-once Kafka surfaces as an error.
        MoneyMarketOrder client = routedSubscriptionClient();
        client.propagateCancelFromHub(FIXED_NOW);
        stubClient(client.getRoutingId(), client);

        assertThatThrownBy(() -> subject.apply(executed(client.getRoutingId())))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verify(orderRepository, never()).save(any());
    }

    // ── CANCELLED ───────────────────────────────────────────────────────────────

    @Test
    void cancelled_onRouted_appliesAndSaves() {
        MoneyMarketOrder client = routedSubscriptionClient();
        stubClient(client.getRoutingId(), client);
        when(orderRepository.save(any())).then(returnsFirstArg());

        subject.apply(new RemoteOrderOutcome.Cancelled(CLIENT_LE, client.getRoutingId(), FIXED_NOW));

        assertThat(client.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(client);
    }

    @Test
    void cancelled_onAlreadyCancelled_isIdempotentNoOp_doesNotSave() {
        MoneyMarketOrder client = routedSubscriptionClient();
        client.propagateCancelFromHub(FIXED_NOW);
        stubClient(client.getRoutingId(), client);

        subject.apply(new RemoteOrderOutcome.Cancelled(CLIENT_LE, client.getRoutingId(), FIXED_NOW));

        assertThat(client.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository, never()).save(any());
    }

    // ── REJECTED ───────────────────────────────────────────────────────────────

    @Test
    void rejected_onRouted_appliesAndSaves() {
        MoneyMarketOrder client = routedSubscriptionClient();
        stubClient(client.getRoutingId(), client);
        when(orderRepository.save(any())).then(returnsFirstArg());

        subject.apply(new RemoteOrderOutcome.Rejected(CLIENT_LE, client.getRoutingId(), "Trader declined", FIXED_NOW));

        assertThat(client.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(client.getRejectionReason()).isEqualTo("Trader declined");
        verify(orderRepository).save(client);
    }

    @Test
    void rejected_onAlreadyRejected_isIdempotentNoOp_doesNotSave() {
        MoneyMarketOrder client = routedSubscriptionClient();
        client.propagateRejectFromHub("Trader declined", FIXED_NOW);
        stubClient(client.getRoutingId(), client);

        subject.apply(new RemoteOrderOutcome.Rejected(CLIENT_LE, client.getRoutingId(), "Trader declined", FIXED_NOW));

        assertThat(client.getStatus()).isEqualTo(OrderStatus.REJECTED);
        verify(orderRepository, never()).save(any());
    }

    // ── Broken pair ─────────────────────────────────────────────────────────────

    @Test
    void missingClientOrder_throwsPairIntegrity_doesNotSave() {
        when(orderRepository.findRoutedClientOrderByRoutingId(ROUTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.apply(executed(ROUTING_ID)))
                .isInstanceOf(RoutedOrderPairIntegrityException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void foreignOriginatingLegalEntity_throwsPairIntegrity_doesNotSave() {
        MoneyMarketOrder client = routedSubscriptionClient();
        stubClient(client.getRoutingId(), client);

        RemoteOrderOutcome.Executed foreignOutcome =
                new RemoteOrderOutcome.Executed(FOREIGN_LE, client.getRoutingId(), hubExecution(), FIXED_NOW);

        assertThatThrownBy(() -> subject.apply(foreignOutcome))
                .isInstanceOf(RoutedOrderPairIntegrityException.class);
        verify(orderRepository, never()).save(any());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private void stubClient(RoutingId routingId, MoneyMarketOrder client) {
        when(orderRepository.findRoutedClientOrderByRoutingId(routingId))
                .thenReturn(Optional.of(client));
    }

    private static RemoteOrderOutcome.Executed executed(RoutingId routingId) {
        return new RemoteOrderOutcome.Executed(CLIENT_LE, routingId, hubExecution(), FIXED_NOW);
    }

    private static ExecutionDetails hubExecution() {
        return new ExecutionDetails(
                new BigDecimal("3.55"),
                "BNP via LOC",
                "BNP",
                FIXED_NOW,
                new DealingReference("DL-hub"),
                new ContractNumber("CN-hub"));
    }

    private static MoneyMarketOrder receivedSubscriptionClient() {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("CGD-PM-" + UUID.randomUUID()),
                CLIENT_LE,
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("CGD-EUR-77"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(5),
                new BigDecimal("3.25"),
                Tenor._3M,
                null,
                null,
                "BNP",
                "BNP via LOC",
                TODAY);
    }

    private static MoneyMarketOrder routedSubscriptionClient() {
        MoneyMarketOrder client = receivedSubscriptionClient();
        client.markRouted(ROUTING_ID, FIXED_NOW);
        return client;
    }

    private static MoneyMarketOrder routedRedemptionClient() {
        MoneyMarketOrder client = MoneyMarketOrder.create(
                new ExternalOrderReference("CGD-PM-" + UUID.randomUUID()),
                CLIENT_LE,
                OrderType.ON_CALL,
                OrderOperation.REDEMPTION,
                new PortfolioNumber("CGD-EUR-78"),
                "EUR",
                new BigDecimal("500000.00"),
                TODAY.plusDays(2),
                null,
                null,
                NoticePeriod._24H,
                SOURCE_CONTRACT,
                "BNP",
                "BNP via LOC",
                TODAY);
        client.markRouted(RoutingId.fromClientOrderId(client.getId()), FIXED_NOW);
        return client;
    }
}
