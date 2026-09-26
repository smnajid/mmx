package com.mmx.order.application.service;

import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.CrossOrgMembershipPort;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.RemoteRoutingRequest;
import com.mmx.order.application.port.out.RemoteRoutingResponse;
import com.mmx.order.application.port.out.RoutingOutcomeOutbox;
import com.mmx.order.domain.exception.DuplicateRoutedHubOrderException;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.DelegatedGrantKey;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cross-boundary idempotency for leg-A: a retry that races past the cheap pre-read collides with the
 * partial unique index {@code (originatingLegalEntityCode, routingId)}. The use case catches the
 * violation, resolves to the already-persisted hub-side order, and returns the same accept — the
 * {@code ACCEPTED} outbox row was committed in the original transaction, so it is not rescheduled.
 *
 * <p>Spec: {@code order-routing} — cross-boundary correlation and idempotency.
 */
@Tag("fast")
@ExtendWith(MockitoExtension.class)
class AcceptRoutedHubOrderIdempotencyTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final LegalEntityCode HUB_LE = new LegalEntityCode("LOC");
    private static final LegalEntityCode CLIENT_LE = new LegalEntityCode("CGD");
    private static final String INSTITUTION_CODE = "HSBC-01";
    private static final String CURRENCY = "EUR";
    private static final RoutingId ROUTING_ID = RoutingId.fromClientOrderId(UUID.randomUUID());

    @Mock
    CrossOrgMembershipPort membershipPort;
    @Mock
    DelegatedGrantRepository delegatedGrantRepository;
    @Mock
    InstitutionRepository institutionRepository;
    @Mock
    OrderRepository orderRepository;
    @Mock
    RoutingOutcomeOutbox routingOutcomeOutbox;
    @Mock
    Clock clock;

    AcceptRoutedHubOrderService subject;

    @BeforeEach
    void setUp() {
        subject =
                new AcceptRoutedHubOrderService(
                        HUB_LE,
                        membershipPort,
                        delegatedGrantRepository,
                        institutionRepository,
                        orderRepository,
                        routingOutcomeOutbox,
                        clock);
        lenient().when(membershipPort.isRemoteTradingClientOfThisHub(CLIENT_LE)).thenReturn(true);
    }

    @Test
    void legARetry_uniqueViolationCollision_resolvesToExistingHubOrder_returnsSameAccept() {
        when(institutionRepository.findByInstitutionCode(INSTITUTION_CODE))
                .thenReturn(Optional.of(new Institution(INSTITUTION_CODE, "BankCo International", true)));
        when(delegatedGrantRepository.findByKey(
                        new DelegatedGrantKey(INSTITUTION_CODE, CLIENT_LE, CURRENCY)))
                .thenReturn(Optional.of(
                        new DelegatedInstitutionGrant(
                                INSTITUTION_CODE, CLIENT_LE, CURRENCY, Set.of(Tenor._3M), Set.of(), true)));
        when(clock.today()).thenReturn(TODAY);
        when(clock.now()).thenReturn(FIXED_NOW);

        MoneyMarketOrder existing =
                MoneyMarketOrder.createHubSideFromRouting(
                        new com.mmx.order.domain.model.RoutedHubOrderDraft(
                                HUB_LE,
                                new PortfolioNumber("LOC-EUR-001"),
                                INSTITUTION_CODE,
                                "BankCo International",
                                CURRENCY,
                                new BigDecimal("1000000.00"),
                                TODAY.plusDays(5),
                                OrderType.TERM,
                                OrderOperation.SUBSCRIPTION,
                                Tenor._3M,
                                null,
                                new BigDecimal("3.25"),
                                null,
                                ROUTING_ID,
                                CLIENT_LE,
                                new ExternalOrderReference("CGD-PM-1")),
                        TODAY);
        // The persistence adapter translates the DB unique-violation into this domain exception.
        when(orderRepository.save(any())).thenThrow(new DuplicateRoutedHubOrderException("collision"));
        when(orderRepository.findHubOrderByOriginatingAndRoutingId(CLIENT_LE, ROUTING_ID))
                .thenReturn(Optional.of(existing));

        RemoteRoutingResponse response = subject.accept(termRequest(), CLIENT_LE);

        assertThat(response.isAccepted()).isTrue();
        // The ACCEPTED outbox row was committed in the original (winning) transaction — not rescheduled.
        verify(routingOutcomeOutbox, never()).scheduleAccepted(any(), any());
        // Trust-boundary containment (D5): collision resolves via the composite key
        // (originatingLegalEntityCode, routingId) — never routingId alone, so one client cannot
        // accidentally resolve to another client's order.
        verify(orderRepository).findHubOrderByOriginatingAndRoutingId(CLIENT_LE, ROUTING_ID);
        verify(orderRepository, never()).findHubOrderByRoutingId(any());
    }

    private static RemoteRoutingRequest termRequest() {
        return new RemoteRoutingRequest(
                CLIENT_LE,
                ROUTING_ID,
                new PortfolioNumber("LOC-EUR-001"),
                INSTITUTION_CODE,
                new ExternalOrderReference("CGD-PM-1"),
                CURRENCY,
                new BigDecimal("1000000.00"),
                TODAY.plusDays(5),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                Tenor._3M,
                null,
                new BigDecimal("3.25"),
                null);
    }
}
