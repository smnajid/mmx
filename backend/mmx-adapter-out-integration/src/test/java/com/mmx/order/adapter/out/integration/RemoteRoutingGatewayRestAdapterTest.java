package com.mmx.order.adapter.out.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.application.port.out.RemoteRoutingRequest;
import com.mmx.order.application.port.out.RemoteRoutingResponse;
import com.mmx.order.application.port.out.RemoteRoutingTransientFailureException;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.Tenor;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CGED REST client for leg-A: calls LODH {@code POST /api/v1/cross-org/routed-orders} with the
 * resolved hub-side portfolio + hub-native institution code, the {@code X-MMX-CrossOrg-Key} transport
 * credential, and returns the accept/reject response.
 *
 * <p>Spec: {@code order-routing} — cross-org routing transport backbone.
 */
@Tag("fast")
class RemoteRoutingGatewayRestAdapterTest {

    private static final String CREDENTIAL = "key-cgd";
    private static final LegalEntityCode CLIENT_LE = new LegalEntityCode("CGD");

    private HttpServer server;
    private int port;
    private CapturingHandler handler;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        handler = new CapturingHandler();
        server.createContext("/", handler);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void route_on200_returnsAccept_andSendsCredentialHeaderAndBody() throws Exception {
        handler.respondWith(200, """
                {"outcome":"ACCEPTED","originatingLegalEntityCode":"CGD","routingId":"%s","acceptedAt":"2026-08-02T12:00:00Z"}
                """.formatted(routingUuid()));

        var adapter = new RemoteRoutingGatewayRestAdapter("http://localhost:" + port, CREDENTIAL);

        RemoteRoutingResponse response = adapter.route(termRequest());

        assertThat(response.isAccepted()).isTrue();
        assertThat(((RemoteRoutingResponse.Accept) response).acceptedAt().toString())
                .isEqualTo("2026-08-02T12:00:00Z");

        assertThat(handler.capturedCredentialHeader).isEqualTo(CREDENTIAL);
        JsonNode body = handler.capturedBody();
        assertThat(body.get("portfolioNumber").asText()).isEqualTo("LOC-EUR-001");
        assertThat(body.get("institutionCode").asText()).isEqualTo("HSBC-01");
        assertThat(body.get("currency").asText()).isEqualTo("EUR");
        assertThat(body.get("amount").asDouble()).isEqualTo(1000000.00);
        assertThat(body.get("orderType").asText()).isEqualTo("TERM");
        assertThat(body.get("orderOperation").asText()).isEqualTo("SUBSCRIPTION");
        assertThat(body.get("tenor").asText()).isEqualTo("3M");
    }

    @Test
    void route_on422_returnsReject() {
        handler.respondWith(422, """
                {"outcome":"REJECTED","originatingLegalEntityCode":"CGD","routingId":"%s","reason":"Grant validation failed"}
                """.formatted(routingUuid()));

        var adapter = new RemoteRoutingGatewayRestAdapter("http://localhost:" + port, CREDENTIAL);

        RemoteRoutingResponse response = adapter.route(termRequest());

        assertThat(response.isRejected()).isTrue();
        assertThat(((RemoteRoutingResponse.Reject) response).reason()).isEqualTo("Grant validation failed");
    }

    @Test
    void route_on503_throwsTransientFailure() {
        handler.respondWith(503, "Service Unavailable");

        var adapter = new RemoteRoutingGatewayRestAdapter("http://localhost:" + port, CREDENTIAL);

        assertThatThrownBy(() -> adapter.route(termRequest()))
                .isInstanceOf(RemoteRoutingTransientFailureException.class)
                .hasMessageContaining("503");
    }

    @Test
    void route_onConnectionRefused_throwsTransientFailure() {
        var adapter = new RemoteRoutingGatewayRestAdapter("http://localhost:1", CREDENTIAL);

        assertThatThrownBy(() -> adapter.route(termRequest()))
                .isInstanceOf(RemoteRoutingTransientFailureException.class);
    }

    private static UUID routingUuid() {
        return UUID.fromString("11111111-1111-1111-1111-111111111111");
    }

    private static RemoteRoutingRequest termRequest() {
        return new RemoteRoutingRequest(
                CLIENT_LE,
                new RoutingId(routingUuid()),
                new PortfolioNumber("LOC-EUR-001"),
                "HSBC-01",
                new ExternalOrderReference("CGD-PM-1"),
                "EUR",
                new BigDecimal("1000000.00"),
                LocalDate.of(2026, 8, 7),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                Tenor._3M,
                null,
                new BigDecimal("3.25"),
                null);
    }

    private static class CapturingHandler implements com.sun.net.httpserver.HttpHandler {

        private int status;
        private String body;
        private String capturedCredentialHeader;
        private byte[] capturedRawBody;

        void respondWith(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            capturedCredentialHeader = exchange.getRequestHeaders().getFirst("X-MMX-CrossOrg-Key");
            capturedRawBody = exchange.getRequestBody().readAllBytes();
            byte[] resp = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        }

        JsonNode capturedBody() throws Exception {
            return new ObjectMapper().readTree(capturedRawBody);
        }
    }
}
