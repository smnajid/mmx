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
import com.mmx.order.application.support.InMemoryClientEnablementRepository;
import com.mmx.order.application.support.InMemoryInstitutionRepository;
import com.mmx.order.domain.model.ClientEnablement;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.OrganisationCode;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * Client-deployment (CGEG) leg-A outbound orchestration: {@code RemoteRoutedOrderIntake} resolves the
 * hub-side account via {@code ExternalIdentityGateway} <em>before</em> send, builds a
 * {@link RemoteRoutingRequest} carrying the resolved account + hub-native institution code, calls
 * {@link RemoteRoutingGateway#route(RemoteRoutingRequest)}, and transitions the client-side order
 * according to the response (accept → {@code ROUTED}, reject → {@code REJECTED}, silence → stays
 * {@code RECEIVED}). Spec: {@code order-routing} — Remote account resolution via
 * ExternalIdentityGateway; Silence is never terminal; Routing-failure reject is HTTP-only.
 *
 * <p>Spec: {@code order-routing} — silence is never terminal; routing-failure reject is HTTP-only.
 */
@Tag("fast")
@ExtendWith(MockitoExtension.class)
class RemoteRoutedOrderIntakeTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final LegalEntityCode CLIENT_LE = new LegalEntityCode("CGD");
    private static final LegalEntityCode HUB_LE = new LegalEntityCode("LOC");
    private static final OrganisationCode CLIENT_ORG = new OrganisationCode("CGEG");
    private static final OrganisationCode HUB_ORG = new OrganisationCode("LODH");
    private static final String HUB_INSTITUTION_CODE = "HSBC";
    private static final String ONBOARDED_CODE = "HVL-01";
    private static final String CURRENCY = "EUR";
    private static final PortfolioNumber CLIENT_PORTFOLIO = new PortfolioNumber("CGD-PM-77");
    private static final PortfolioNumber RESOLVED_HUB_PORTFOLIO = new PortfolioNumber("LOC-EUR-001");

    @Mock ExternalIdentityGateway externalIdentityGateway;
    @Mock RemoteRoutingGateway remoteRoutingGateway;
    @Mock OrderRepository orderRepository;
    @Mock Clock clock;

    InMemoryInstitutionRepository institutionRepository;
    InMemoryClientEnablementRepository clientEnablementRepository;
    Institution onboarded;
    RemoteRoutedOrderIntake subject;

    @BeforeEach
    void setUp() {
        institutionRepository = new InMemoryInstitutionRepository();
        onboarded =
                Institution.onboardFromGrant(
                        ONBOARDED_CODE,
                        "HSBC",
                        new HubInstitutionLink(HUB_LE, HUB_INSTITUTION_CODE),
                        CLIENT_LE,
                        CounterpartyAccounts.of("CGD-HSBC-T", "CGD-HSBC-OC"));
        institutionRepository.put(onboarded);
        clientEnablementRepository = new InMemoryClientEnablementRepository();
        clientEnablementRepository.save(
                CLIENT_LE, new ClientEnablement(ONBOARDED_CODE, CURRENCY, Set.of(Tenor._3M), Set.of()));
        subject =
                new RemoteRoutedOrderIntake(
                        externalIdentityGateway,
                        remoteRoutingGateway,
                        institutionRepository,
                        clientEnablementRepository,
                        orderRepository,
                        clock);
        lenient().when(clock.now()).thenReturn(FIXED_NOW);
        lenient().when(clock.today()).thenReturn(TODAY);
        lenient().when(orderRepository.save(any())).then(returnsFirstArg());
    }

    @Test
    void resolvedAccount_andGatewayAccept_transitionsClientOrderToRouted_andSendsResolvedAccount() {
        when(externalIdentityGateway.resolveHubSidePortfolioNumber(CLIENT_LE, CLIENT_PORTFOLIO, HUB_LE))
                .thenReturn(Optional.of(RESOLVED_HUB_PORTFOLIO));
        when(remoteRoutingGateway.route(any()))
                .thenReturn(new RemoteRoutingResponse.Accept(FIXED_NOW));

        IntakeUseCase.Result result = subject.completeIntake(termCommand(), cgdClient());

        assertThat(result.status()).isEqualTo(OrderStatus.ROUTED);
        assertThat(result.newlyCreated()).isTrue();

        ArgumentCaptor<RemoteRoutingRequest> requestCaptor =
                ArgumentCaptor.forClass(RemoteRoutingRequest.class);
        verify(remoteRoutingGateway).route(requestCaptor.capture());
        RemoteRoutingRequest sent = requestCaptor.getValue();
        assertThat(sent.portfolioNumber()).isEqualTo(RESOLVED_HUB_PORTFOLIO);
        assertThat(sent.institutionCode()).isEqualTo(HUB_INSTITUTION_CODE);
        assertThat(sent.clientCounterpartyAccount()).isEqualTo("CGD-HSBC-T");
        assertThat(sent.originatingLegalEntityCode()).isEqualTo(CLIENT_LE);
        assertThat(sent.routingId()).isNotNull();
    }

    @Test
    void unresolvedAccount_rejectsClientSideDirectly_andNeverCallsGateway() {
        when(externalIdentityGateway.resolveHubSidePortfolioNumber(CLIENT_LE, CLIENT_PORTFOLIO, HUB_LE))
                .thenReturn(Optional.empty());

        IntakeUseCase.Result result = subject.completeIntake(termCommand(), cgdClient());

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.newlyCreated()).isTrue();
        verify(remoteRoutingGateway, never()).route(any());
    }

    @Test
    void gatewayRoutingFailureReject_transitionsClientOrderToRejected() {
        when(externalIdentityGateway.resolveHubSidePortfolioNumber(CLIENT_LE, CLIENT_PORTFOLIO, HUB_LE))
                .thenReturn(Optional.of(RESOLVED_HUB_PORTFOLIO));
        when(remoteRoutingGateway.route(any()))
                .thenReturn(new RemoteRoutingResponse.Reject("Delegated grant validation failed"));

        IntakeUseCase.Result result = subject.completeIntake(termCommand(), cgdClient());

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.newlyCreated()).isTrue();
    }

    @Test
    void gatewayCircuitOpenException_leavesOrderReceived_silenceIsNeverTerminal() {
        when(externalIdentityGateway.resolveHubSidePortfolioNumber(CLIENT_LE, CLIENT_PORTFOLIO, HUB_LE))
                .thenReturn(Optional.of(RESOLVED_HUB_PORTFOLIO));
        when(remoteRoutingGateway.route(any()))
                .thenThrow(new RemoteRoutingCircuitOpenException("circuit open"));

        IntakeUseCase.Result result = subject.completeIntake(termCommand(), cgdClient());

        assertThat(result.status()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(result.newlyCreated()).isTrue();
    }

    @Test
    void gatewayTransientFailureException_leavesOrderReceived_silenceIsNeverTerminal() {
        when(externalIdentityGateway.resolveHubSidePortfolioNumber(CLIENT_LE, CLIENT_PORTFOLIO, HUB_LE))
                .thenReturn(Optional.of(RESOLVED_HUB_PORTFOLIO));
        when(remoteRoutingGateway.route(any()))
                .thenThrow(new RemoteRoutingTransientFailureException("transient"));

        IntakeUseCase.Result result = subject.completeIntake(termCommand(), cgdClient());

        assertThat(result.status()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(result.newlyCreated()).isTrue();
    }

    @Test
    void clientOrder_carriesTheOnboardedInstitutionAndItsDisplayName() {
        when(externalIdentityGateway.resolveHubSidePortfolioNumber(CLIENT_LE, CLIENT_PORTFOLIO, HUB_LE))
                .thenReturn(Optional.of(RESOLVED_HUB_PORTFOLIO));
        when(remoteRoutingGateway.route(any())).thenReturn(new RemoteRoutingResponse.Accept(FIXED_NOW));

        subject.completeIntake(termCommand(), cgdClient());

        MoneyMarketOrder saved = savedOrder();
        assertThat(saved.getInstitutionCode()).isEqualTo(ONBOARDED_CODE);
        assertThat(saved.getCounterparty()).isEqualTo("HSBC via LOC");
    }

    @Test
    void notOnboardedInstitution_isARoutingFailure_andNoLegAIsSent() {
        IntakeUseCase.Result result = subject.completeIntake(termCommand("SG"), cgdClient());

        assertRejectedWithoutLegA(result, "not onboarded");
    }

    @Test
    void subscriptionOnAnOffboardedInstitution_isRefused_andNoLegAIsSent() {
        onboarded.offboard();

        assertRejectedWithoutLegA(subject.completeIntake(termCommand(), cgdClient()), "closed to new business");
    }

    @Test
    void subscriptionOnATenorOutsideTheClientEnablement_isRefused_andNoLegAIsSent() {
        clientEnablementRepository.save(
                CLIENT_LE, new ClientEnablement(ONBOARDED_CODE, CURRENCY, Set.of(Tenor._1M), Set.of()));

        assertRejectedWithoutLegA(subject.completeIntake(termCommand(), cgdClient()), "not enabled");
    }

    @Test
    void missingClientCounterpartyAccount_isRefused_andNoLegAIsSent() {
        onboarded.changeAccounts(CounterpartyAccounts.of(null, "CGD-HSBC-OC"));

        assertRejectedWithoutLegA(subject.completeIntake(termCommand(), cgdClient()), "Term counterparty account");
    }

    @Test
    void redemptionOnAnOffboardedInstitutionWithASwitchedOffNotice_isSent() {
        onboarded.offboard();
        when(externalIdentityGateway.resolveHubSidePortfolioNumber(CLIENT_LE, CLIENT_PORTFOLIO, HUB_LE))
                .thenReturn(Optional.of(RESOLVED_HUB_PORTFOLIO));
        when(remoteRoutingGateway.route(any())).thenReturn(new RemoteRoutingResponse.Accept(FIXED_NOW));

        IntakeUseCase.Result result = subject.completeIntake(onCallRedemptionCommand(), cgdClient());

        assertThat(result.status()).isEqualTo(OrderStatus.ROUTED);
        ArgumentCaptor<RemoteRoutingRequest> requestCaptor = ArgumentCaptor.forClass(RemoteRoutingRequest.class);
        verify(remoteRoutingGateway).route(requestCaptor.capture());
        assertThat(requestCaptor.getValue().clientCounterpartyAccount()).isEqualTo("CGD-HSBC-OC");
    }

    private void assertRejectedWithoutLegA(IntakeUseCase.Result result, String reason) {
        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(savedOrder().getRejectionReason()).startsWith("Routing failure").contains(reason);
        verify(remoteRoutingGateway, never()).route(any());
        verify(externalIdentityGateway, never()).resolveHubSidePortfolioNumber(any(), any(), any());
    }

    private MoneyMarketOrder savedOrder() {
        ArgumentCaptor<MoneyMarketOrder> captor = ArgumentCaptor.forClass(MoneyMarketOrder.class);
        verify(orderRepository).save(captor.capture());
        return captor.getValue();
    }

    private ReceiveOrderCommand onCallRedemptionCommand() {
        return new ReceiveOrderCommand(
                new ExternalOrderReference("CGD-PM-2"),
                CLIENT_LE,
                OrderType.ON_CALL,
                OrderOperation.REDEMPTION,
                CLIENT_PORTFOLIO,
                CURRENCY,
                new BigDecimal("100000.00"),
                TODAY.plusDays(5),
                null,
                null,
                NoticePeriod._48H,
                new ContractNumber("CT-00042"),
                ONBOARDED_CODE);
    }

    private ReceiveOrderCommand termCommand() {
        return termCommand(ONBOARDED_CODE);
    }

    private ReceiveOrderCommand termCommand(String institutionCode) {
        return new ReceiveOrderCommand(
                new ExternalOrderReference("CGD-PM-1"),
                CLIENT_LE,
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                CLIENT_PORTFOLIO,
                CURRENCY,
                new BigDecimal("1000000.00"),
                TODAY.plusDays(5),
                new BigDecimal("3.25"),
                Tenor._3M,
                null,
                null,
                institutionCode);
    }

    private static LegalEntity cgdClient() {
        LegalEntity loc = LegalEntity.tradingHub(HUB_LE, HUB_ORG);
        return LegalEntity.tradingClient(CLIENT_LE, CLIENT_ORG, loc);
    }
}
