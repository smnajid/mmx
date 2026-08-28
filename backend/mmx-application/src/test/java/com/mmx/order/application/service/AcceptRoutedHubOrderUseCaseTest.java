package com.mmx.order.application.service;

import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.CrossOrgMembershipPort;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.RemoteRoutingRequest;
import com.mmx.order.application.port.out.RemoteRoutingResponse;
import com.mmx.order.application.port.out.RoutingOutcomeOutbox;
import com.mmx.order.domain.exception.CrossOrgMembershipException;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.DelegatedGrantKey;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hub-deployment (LODH) leg-A inbound: {@code AcceptRoutedHubOrderUseCase} validates the originating
 * client's grant against the hub's own reference data, creates the hub-side order on success, and
 * emits a leg-B {@code ACCEPTED} outbox row in the same transaction; a grant/currency/tenor
 * violation rejects, creating no order and emitting no event.
 *
 * <p>Spec: {@code order-routing} — remote routed order intake at the hub.
 */
@Tag("fast")
@ExtendWith(MockitoExtension.class)
class AcceptRoutedHubOrderUseCaseTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final LegalEntityCode HUB_LE = new LegalEntityCode("LOC");
    private static final LegalEntityCode CLIENT_LE = new LegalEntityCode("CGD");
    private static final String INSTITUTION_CODE = "HSBC-01";
    private static final String INSTITUTION_DISPLAY = "BankCo International";
    private static final String CURRENCY = "EUR";

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
    void validTermOrder_withActiveGrantAndEnabledTenor_isAccepted_createsHubOrderAndSchedulesAccepted() {
        stubInstitution();
        stubGrant(
                new DelegatedInstitutionGrant(
                        INSTITUTION_CODE, CLIENT_LE, CURRENCY, Set.of(Tenor._3M), Set.of(), true));
        when(orderRepository.save(any())).then(returnsFirstArg());
        when(clock.today()).thenReturn(TODAY);
        when(clock.now()).thenReturn(FIXED_NOW);

        RemoteRoutingRequest request = termRequest(Tenor._3M);

        RemoteRoutingResponse response = subject.accept(request, CLIENT_LE);

        assertThat(response.isAccepted()).isTrue();
        assertThat(((RemoteRoutingResponse.Accept) response).acceptedAt()).isEqualTo(FIXED_NOW);
        verify(orderRepository).save(any());
        verify(routingOutcomeOutbox).scheduleAccepted(any(), eq(FIXED_NOW));
    }

    @Test
    void validOnCallOrder_withEnabledNotice_isAccepted() {
        stubInstitution();
        stubGrant(
                new DelegatedInstitutionGrant(
                        INSTITUTION_CODE, CLIENT_LE, CURRENCY, Set.of(), Set.of(NoticePeriod._24H), true));
        when(orderRepository.save(any())).then(returnsFirstArg());
        when(clock.today()).thenReturn(TODAY);
        when(clock.now()).thenReturn(FIXED_NOW);

        RemoteRoutingResponse response = subject.accept(onCallRequest(NoticePeriod._24H), CLIENT_LE);

        assertThat(response.isAccepted()).isTrue();
        verify(orderRepository).save(any());
        verify(routingOutcomeOutbox).scheduleAccepted(any(), eq(FIXED_NOW));
    }

    @Test
    void grantMissing_isRejected_noOrderNoEvent() {
        stubInstitution();
        when(delegatedGrantRepository.findByKey(grantKey())).thenReturn(Optional.empty());

        RemoteRoutingResponse response = subject.accept(termRequest(Tenor._3M), CLIENT_LE);

        assertThat(response.isRejected()).isTrue();
        verify(orderRepository, never()).save(any());
        verify(routingOutcomeOutbox, never()).scheduleAccepted(any(), any());
    }

    @Test
    void grantInactive_isRejected() {
        stubInstitution();
        stubGrant(
                new DelegatedInstitutionGrant(
                        INSTITUTION_CODE, CLIENT_LE, CURRENCY, Set.of(Tenor._3M), Set.of(), false));

        RemoteRoutingResponse response = subject.accept(termRequest(Tenor._3M), CLIENT_LE);

        assertThat(response.isRejected()).isTrue();
        verify(orderRepository, never()).save(any());
        verify(routingOutcomeOutbox, never()).scheduleAccepted(any(), any());
    }

    @Test
    void termTenorNotInEnabledSet_isRejected() {
        stubInstitution();
        stubGrant(
                new DelegatedInstitutionGrant(
                        INSTITUTION_CODE, CLIENT_LE, CURRENCY, Set.of(Tenor._1M), Set.of(), true));

        RemoteRoutingResponse response = subject.accept(termRequest(Tenor._3M), CLIENT_LE);

        assertThat(response.isRejected()).isTrue();
        verify(orderRepository, never()).save(any());
        verify(routingOutcomeOutbox, never()).scheduleAccepted(any(), any());
    }

    @Test
    void onCallNoticeNotInEnabledSet_isRejected() {
        stubInstitution();
        stubGrant(
                new DelegatedInstitutionGrant(
                        INSTITUTION_CODE, CLIENT_LE, CURRENCY, Set.of(), Set.of(NoticePeriod._24H), true));

        RemoteRoutingResponse response = subject.accept(onCallRequest(NoticePeriod._48H), CLIENT_LE);

        assertThat(response.isRejected()).isTrue();
        verify(orderRepository, never()).save(any());
        verify(routingOutcomeOutbox, never()).scheduleAccepted(any(), any());
    }

    @Test
    void provenNonMember_throwsMembershipException_defenseInDepth() {
        when(membershipPort.isRemoteTradingClientOfThisHub(CLIENT_LE)).thenReturn(false);

        assertThatThrownBy(() -> subject.accept(termRequest(Tenor._3M), CLIENT_LE))
                .isInstanceOf(CrossOrgMembershipException.class);

        verify(orderRepository, never()).save(any());
        verify(routingOutcomeOutbox, never()).scheduleAccepted(any(), any());
    }

    private void stubInstitution() {
        when(institutionRepository.findByInstitutionCode(INSTITUTION_CODE))
                .thenReturn(Optional.of(new Institution(INSTITUTION_CODE, INSTITUTION_DISPLAY, true)));
    }

    private void stubGrant(DelegatedInstitutionGrant grant) {
        when(delegatedGrantRepository.findByKey(grantKey())).thenReturn(Optional.of(grant));
    }

    private DelegatedGrantKey grantKey() {
        return new DelegatedGrantKey(INSTITUTION_CODE, CLIENT_LE, CURRENCY);
    }

    private static RemoteRoutingRequest termRequest(Tenor tenor) {
        return new RemoteRoutingRequest(
                CLIENT_LE,
                RoutingId.fromClientOrderId(UUID.randomUUID()),
                new PortfolioNumber("LOC-EUR-001"),
                INSTITUTION_CODE,
                new ExternalOrderReference("CGD-PM-1"),
                CURRENCY,
                new BigDecimal("1000000.00"),
                TODAY.plusDays(5),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                tenor,
                null,
                new BigDecimal("3.25"),
                null);
    }

    private static RemoteRoutingRequest onCallRequest(NoticePeriod noticePeriod) {
        return new RemoteRoutingRequest(
                CLIENT_LE,
                RoutingId.fromClientOrderId(UUID.randomUUID()),
                new PortfolioNumber("LOC-EUR-001"),
                INSTITUTION_CODE,
                new ExternalOrderReference("CGD-PM-2"),
                CURRENCY,
                new BigDecimal("1000000.00"),
                TODAY.plusDays(1),
                OrderType.ON_CALL,
                OrderOperation.SUBSCRIPTION,
                null,
                noticePeriod,
                null,
                null);
    }
}
