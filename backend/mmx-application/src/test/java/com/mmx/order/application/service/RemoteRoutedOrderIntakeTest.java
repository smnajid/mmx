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
import com.mmx.order.domain.model.ExternalOrderReference;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * Client-deployment (CGED) leg-A outbound orchestration: {@code RemoteRoutedOrderIntake} resolves the
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
    private static final OrganisationCode CLIENT_ORG = new OrganisationCode("CGED");
    private static final OrganisationCode HUB_ORG = new OrganisationCode("LODH");
    private static final String INSTITUTION_CODE = "HSBC";
    private static final String CURRENCY = "EUR";
    private static final PortfolioNumber CLIENT_PORTFOLIO = new PortfolioNumber("CGD-PM-77");
    private static final PortfolioNumber RESOLVED_HUB_PORTFOLIO = new PortfolioNumber("LOC-EUR-001");

    @Mock ExternalIdentityGateway externalIdentityGateway;
    @Mock RemoteRoutingGateway remoteRoutingGateway;
    @Mock OrderRepository orderRepository;
    @Mock Clock clock;

    RemoteRoutedOrderIntake subject;

    @BeforeEach
    void setUp() {
        subject =
                new RemoteRoutedOrderIntake(
                        externalIdentityGateway, remoteRoutingGateway, orderRepository, clock);
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
        assertThat(sent.institutionCode()).isEqualTo(INSTITUTION_CODE);
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

    private ReceiveOrderCommand termCommand() {
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
                INSTITUTION_CODE);
    }

    private static LegalEntity cgdClient() {
        LegalEntity loc = LegalEntity.tradingHub(HUB_LE, HUB_ORG);
        return LegalEntity.tradingClient(CLIENT_LE, CLIENT_ORG, loc);
    }
}
