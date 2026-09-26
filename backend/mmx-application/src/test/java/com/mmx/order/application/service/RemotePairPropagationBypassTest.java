package com.mmx.order.application.service;

import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.RoutingOutcomeOutbox;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrganisationCode;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutedHubOrderDraft;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.TraderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hub-side propagation bypass for remote routed pairs (D6). When {@code originatingLegalEntityCode}'s
 * organisation ≠ the hub deployment's organisation, the hub execute/cancel/reject transaction
 * persists only the hub-side terminal transition + a leg-B outbox row; it never looks up or
 * transitions the client-side order. Local pairs (same org) delegate to the existing synchronous
 * in-process propagation unchanged.
 *
 * <p>Spec: {@code order-routing} — hub-side propagation bypassed for remote pairs.
 */
@Tag("fast")
@ExtendWith(MockitoExtension.class)
class RemotePairPropagationBypassTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final TraderId TRADER = new TraderId("trader-a");
    private static final OrganisationCode HUB_ORG = new OrganisationCode("LODH");
    private static final LegalEntityCode HUB_LE = new LegalEntityCode("LOC");
    private static final LegalEntityCode REMOTE_CLIENT_LE = new LegalEntityCode("CGD");
    private static final LegalEntityCode LOCAL_CLIENT_LE = new LegalEntityCode("PAR");
    private static final ContractNumber HUB_CONTRACT = new ContractNumber("CN-hub");

    @Mock
    LegalEntityRepository legalEntityRepository;
    @Mock
    RoutedOrderOutcomePropagation localPropagation;
    @Mock
    RoutingOutcomeOutbox routingOutcomeOutbox;

    LocalityAwareRoutedOrderOutcomePropagation subject;

    @BeforeEach
    void setUp() {
        subject =
                new LocalityAwareRoutedOrderOutcomePropagation(
                        HUB_ORG, legalEntityRepository, localPropagation, routingOutcomeOutbox);
    }

    // ── REMOTE pairs: bypass local propagation, schedule leg-B outbox ───────────

    @Test
    void propagateExecution_remotePair_schedulesLegBExecuted_doesNotLookupOrTransitionClient() {
        MoneyMarketOrder hub = executedHub(REMOTE_CLIENT_LE);
        when(legalEntityRepository.belongsToOrganisation(REMOTE_CLIENT_LE, HUB_ORG)).thenReturn(false);

        ExecutionPropagationResult result = subject.propagateExecution(hub);

        verify(routingOutcomeOutbox).scheduleExecuted(eq(hub), eq(FIXED_NOW));
        verify(localPropagation, never()).propagateExecution(any());
        // No internal order UUID crosses the boundary; only the cross-boundary key is carried.
        assertThat(result.clientOrder()).isNull();
        assertThat(result.handoffContext().routingId()).isEqualTo(hub.getRoutingId());
        assertThat(result.handoffContext().originatingLegalEntityCode()).isEqualTo(REMOTE_CLIENT_LE);
        assertThat(result.handoffContext().clientOrderId()).isNull();
        assertThat(result.handoffContext().clientPortfolioNumber()).isNull();
        assertThat(result.handoffContext().clientCounterparty()).isNull();
    }

    @Test
    void propagateCancel_remotePair_schedulesLegBCancelled_doesNotLookupClient() {
        MoneyMarketOrder hub = cancelledHub(REMOTE_CLIENT_LE);
        when(legalEntityRepository.belongsToOrganisation(REMOTE_CLIENT_LE, HUB_ORG)).thenReturn(false);

        subject.propagateCancel(hub, FIXED_NOW);

        verify(routingOutcomeOutbox).scheduleCancelled(eq(hub), eq(FIXED_NOW));
        verify(localPropagation, never()).propagateCancel(any(), any());
    }

    @Test
    void propagateReject_remotePair_schedulesLegBRejected_doesNotLookupClient() {
        MoneyMarketOrder hub = rejectedHub(REMOTE_CLIENT_LE);
        when(legalEntityRepository.belongsToOrganisation(REMOTE_CLIENT_LE, HUB_ORG)).thenReturn(false);

        subject.propagateReject(hub, "Trader declined", FIXED_NOW);

        verify(routingOutcomeOutbox).scheduleRejected(eq(hub), eq("Trader declined"), eq(FIXED_NOW));
        verify(localPropagation, never()).propagateReject(any(), any(), any());
    }

    // ── LOCAL pairs: delegate to synchronous in-process propagation unchanged ──

    @Test
    void propagateExecution_localPair_delegatesToLocalPropagation_noLegBSchedule() {
        MoneyMarketOrder hub = executedHub(LOCAL_CLIENT_LE);
        ExecutionPropagationResult localResult =
                new ExecutionPropagationResult(null, com.mmx.order.application.port.out.ExecutionHandoffRoutingContext.none());
        when(legalEntityRepository.belongsToOrganisation(LOCAL_CLIENT_LE, HUB_ORG)).thenReturn(true);
        when(localPropagation.propagateExecution(hub)).thenReturn(localResult);

        ExecutionPropagationResult result = subject.propagateExecution(hub);

        assertThat(result).isSameAs(localResult);
        verify(routingOutcomeOutbox, never()).scheduleExecuted(any(), any());
    }

    @Test
    void propagateCancel_localPair_delegatesToLocalPropagation_noLegBSchedule() {
        MoneyMarketOrder hub = cancelledHub(LOCAL_CLIENT_LE);
        when(legalEntityRepository.belongsToOrganisation(LOCAL_CLIENT_LE, HUB_ORG)).thenReturn(true);

        subject.propagateCancel(hub, FIXED_NOW);

        verify(localPropagation).propagateCancel(hub, FIXED_NOW);
        verify(routingOutcomeOutbox, never()).scheduleCancelled(any(), any());
    }

    @Test
    void propagateReject_localPair_delegatesToLocalPropagation_noLegBSchedule() {
        MoneyMarketOrder hub = rejectedHub(LOCAL_CLIENT_LE);
        when(legalEntityRepository.belongsToOrganisation(LOCAL_CLIENT_LE, HUB_ORG)).thenReturn(true);

        subject.propagateReject(hub, "No capacity", FIXED_NOW);

        verify(localPropagation).propagateReject(hub, "No capacity", FIXED_NOW);
        verify(routingOutcomeOutbox, never()).scheduleRejected(any(), any(), any());
    }

    // ── Hub-order builders ──────────────────────────────────────────────────────

    private static MoneyMarketOrder hubOrder(LegalEntityCode originatingLe) {
        return MoneyMarketOrder.createHubSideFromRouting(
                new RoutedHubOrderDraft(
                        HUB_LE,
                        new PortfolioNumber("LOC-EUR-001"),
                        "HSBC-01",
                        "BankCo International",
                        "EUR",
                        new BigDecimal("1000000.00"),
                        TODAY.plusDays(5),
                        OrderType.TERM,
                        OrderOperation.SUBSCRIPTION,
                        Tenor._3M,
                        null,
                        new BigDecimal("3.25"),
                        null,
                        RoutingId.fromClientOrderId(UUID.randomUUID()),
                        originatingLe,
                        new ExternalOrderReference("CLIENT-EXT-1")),
                TODAY);
    }

    private static MoneyMarketOrder executedHub(LegalEntityCode originatingLe) {
        MoneyMarketOrder hub = hubOrder(originatingLe);
        hub.assign(TRADER, FIXED_NOW);
        hub.execute(
                new BigDecimal("3.55"),
                "BankCo International",
                "HSBC-01",
                new DealingReference("DL-hub"),
                HUB_CONTRACT,
                TRADER,
                FIXED_NOW);
        return hub;
    }

    private static MoneyMarketOrder cancelledHub(LegalEntityCode originatingLe) {
        MoneyMarketOrder hub = hubOrder(originatingLe);
        hub.cancel(FIXED_NOW);
        return hub;
    }

    private static MoneyMarketOrder rejectedHub(LegalEntityCode originatingLe) {
        MoneyMarketOrder hub = hubOrder(originatingLe);
        hub.reject(TRADER, "Trader declined", FIXED_NOW);
        return hub;
    }
}
