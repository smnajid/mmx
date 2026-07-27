package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Port contract for {@link RemoteRoutingGateway} — CGED's leg-A outbound port. Verifies the
 * request carries the resolved hub-side portfolio number, hub-native institution code, order
 * fields, deterministic routing id, and originating client LegalEntityCode; the response is a
 * discriminated accept/reject (no internal order UUID crosses the boundary).
 *
 * <p>Spec: {@code order-routing} — cross-org routing transport backbone (leg A = synchronous
 * REST); cross-boundary correlation = {@code (originatingLegalEntityCode, routingId)}.
 */
class RemoteRoutingGatewayContractTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final UUID CLIENT_ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final RoutingId ROUTING_ID = RoutingId.fromClientOrderId(CLIENT_ORDER_ID);

    @Test
    void route_acceptResponse_carriesAcceptSignal() {
        FakeGateway gateway = new FakeGateway();
        gateway.willAcceptOnNext(FIXED_NOW);

        RemoteRoutingResponse response = gateway.route(termRequest());

        assertThat(response.isAccepted()).isTrue();
        assertThat(response.isRejected()).isFalse();
        assertThat(response.asAccept().acceptedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void route_rejectResponse_carriesRejectionReason() {
        FakeGateway gateway = new FakeGateway();
        gateway.willRejectOnNext("Grant violation: BNP/EUR/6M not enabled");

        RemoteRoutingResponse response = gateway.route(termRequest());

        assertThat(response.isRejected()).isTrue();
        assertThat(response.isAccepted()).isFalse();
        assertThat(response.asReject().reason()).isEqualTo("Grant violation: BNP/EUR/6M not enabled");
    }

    @Test
    void route_unresolvedRoutingFailure_returnsRejectWithoutThrowing() {
        // A routing-failure reject (grant/currency/tenor invalid at LODH accept) is returned
        // as a reject response — the application decides to flip the client-side order to
        // Rejected; the port does not throw on routing failure.
        FakeGateway gateway = new FakeGateway();
        gateway.willRejectOnNext("Currency USD not managed at hub");

        RemoteRoutingResponse response = gateway.route(termRequest());

        assertThat(response.isRejected()).isTrue();
    }

    @Test
    void request_carriesResolvedHubSidePortfolioNumber_notLookedUpAtHub() {
        RemoteRoutingRequest request = termRequest();

        // Spec: the resolved account travels in the leg-A payload; the hub deployment does not
        // revalidate it.
        assertThat(request.portfolioNumber()).isEqualTo(new PortfolioNumber("CGD-LOC-001"));
    }

    @Test
    void request_carriesHubNativeInstitutionCode_proxyIndirectionCollapses() {
        RemoteRoutingRequest request = termRequest();

        // Spec: leg A carries the hub-native code "BNP", not the client's "BNP via LOC" proxy.
        // CGD renders the display name client-side; LODH performs no proxy resolution.
        assertThat(request.institutionCode()).isEqualTo("BNP");
    }

    @Test
    void request_carriesOriginatingLegalEntityCode_andRoutingId_correlationKey() {
        RemoteRoutingRequest request = termRequest();

        // Spec: cross-boundary correlation key is (originatingLegalEntityCode, routingId) only;
        // no internal order UUID crosses the boundary.
        assertThat(request.originatingLegalEntityCode()).isEqualTo(new LegalEntityCode("CGD"));
        assertThat(request.routingId()).isEqualTo(ROUTING_ID);
    }

    @Test
    void request_carriesOriginatingExternalOrderReference_forTraceability() {
        RemoteRoutingRequest request = termRequest();

        assertThat(request.originatingExternalOrderReference())
                .isEqualTo(new ExternalOrderReference("PM-CLIENT-77"));
    }

    @Test
    void request_carriesOrderFields_currencyAmountValueDateTypeOperationTenorMinimumRate() {
        RemoteRoutingRequest request = termRequest();

        assertThat(request.currency()).isEqualTo("EUR");
        assertThat(request.amount()).isEqualByComparingTo(new BigDecimal("1000000.00"));
        assertThat(request.valueDate()).isEqualTo(LocalDate.of(2026, 5, 6));
        assertThat(request.orderType()).isEqualTo(OrderType.TERM);
        assertThat(request.orderOperation()).isEqualTo(OrderOperation.SUBSCRIPTION);
        assertThat(request.tenor()).isEqualTo(Tenor._3M);
        assertThat(request.noticePeriod()).isNull();
        assertThat(request.minimumRate()).isEqualByComparingTo(new BigDecimal("3.25"));
        assertThat(request.sourceContractNumber()).isNull();
    }

    @Test
    void request_supportsOnCallVariant_withNoticePeriodAndSourceContractNumber() {
        RemoteRoutingRequest request = onCallLifecycleRequest();

        assertThat(request.orderType()).isEqualTo(OrderType.ON_CALL);
        assertThat(request.noticePeriod()).isEqualTo(NoticePeriod._24H);
        assertThat(request.tenor()).isNull();
        assertThat(request.sourceContractNumber()).isEqualTo(new ContractNumber("CN-oncall-src"));
    }

    private static RemoteRoutingRequest termRequest() {
        return new RemoteRoutingRequest(
                new LegalEntityCode("CGD"),
                ROUTING_ID,
                new PortfolioNumber("CGD-LOC-001"),
                "BNP",
                new ExternalOrderReference("PM-CLIENT-77"),
                "EUR",
                new BigDecimal("1000000.00"),
                LocalDate.of(2026, 5, 6),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                Tenor._3M,
                null,
                new BigDecimal("3.25"),
                null);
    }

    private static RemoteRoutingRequest onCallLifecycleRequest() {
        return new RemoteRoutingRequest(
                new LegalEntityCode("CGD"),
                ROUTING_ID,
                new PortfolioNumber("CGD-LOC-001"),
                "BNP",
                new ExternalOrderReference("PM-CLIENT-ONCALL-91"),
                "EUR",
                new BigDecimal("500000.00"),
                LocalDate.of(2026, 5, 6),
                OrderType.ON_CALL,
                OrderOperation.INCREASE,
                null,
                NoticePeriod._24H,
                null,
                new ContractNumber("CN-oncall-src"));
    }

    /** Minimal fake honouring the port contract; mirrors how the test asserts the contract. */
    private static final class FakeGateway implements RemoteRoutingGateway {

        private RemoteRoutingResponse next;

        void willAcceptOnNext(Instant acceptedAt) {
            this.next = new RemoteRoutingResponse.Accept(acceptedAt);
        }

        void willRejectOnNext(String reason) {
            this.next = new RemoteRoutingResponse.Reject(reason);
        }

        @Override
        public RemoteRoutingResponse route(RemoteRoutingRequest request) {
            if (next == null) {
                throw new IllegalStateException("FakeGateway not configured");
            }
            return next;
        }
    }
}
