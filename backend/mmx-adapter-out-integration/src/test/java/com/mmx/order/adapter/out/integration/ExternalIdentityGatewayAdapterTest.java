package com.mmx.order.adapter.out.integration;

import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.PortfolioNumber;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolves {@code (client LE, client portfolioNumber, hub LE) → hub-side portfolioNumber} from the
 * external identity system. Unresolved → empty (the caller transitions the client-side order to
 * {@code Rejected} directly, no hub round-trip).
 *
 * <p>Spec: {@code order-routing} — remote account resolution via ExternalIdentityGateway.
 */
@Tag("fast")
class ExternalIdentityGatewayAdapterTest {

    private static final LegalEntityCode CLIENT_LE = new LegalEntityCode("CGD");
    private static final LegalEntityCode HUB_LE = new LegalEntityCode("LOC");

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void resolve_on200_returnsHubPortfolioNumber() {
        server.createContext("/api/identity/resolve", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            respond(exchange, 200, """
                    {"hubPortfolioNumber":"LOC-EUR-001"}
                    """);
        });

        var adapter = new ExternalIdentityGatewayAdapter("http://localhost:" + port);

        Optional<PortfolioNumber> result = adapter.resolveHubSidePortfolioNumber(
                CLIENT_LE, new PortfolioNumber("CGD-EUR-001"), HUB_LE);

        assertThat(result).contains(new PortfolioNumber("LOC-EUR-001"));
    }

    @Test
    void resolve_on404_returnsEmpty() {
        server.createContext("/api/identity/resolve", exchange ->
                respond(exchange, 404, ""));

        var adapter = new ExternalIdentityGatewayAdapter("http://localhost:" + port);

        Optional<PortfolioNumber> result = adapter.resolveHubSidePortfolioNumber(
                CLIENT_LE, new PortfolioNumber("UNKNOWN"), HUB_LE);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_onConnectionError_returnsEmpty() {
        var adapter = new ExternalIdentityGatewayAdapter("http://localhost:1");

        Optional<PortfolioNumber> result = adapter.resolveHubSidePortfolioNumber(
                CLIENT_LE, new PortfolioNumber("CGD-EUR-001"), HUB_LE);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_sendsClientAndHubParamsAsQuery() throws Exception {
        server.createContext("/api/identity/resolve", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String capturedQuery = query;
            respond(exchange, 200, """
                    {"hubPortfolioNumber":"LOC-EUR-001"}
                    """);
            // Store for assertion — write to a static field for test access
            ExternalIdentityGatewayAdapterTest.capturedQuery = capturedQuery;
        });

        var adapter = new ExternalIdentityGatewayAdapter("http://localhost:" + port);
        adapter.resolveHubSidePortfolioNumber(
                CLIENT_LE, new PortfolioNumber("CGD-EUR-001"), HUB_LE);

        assertThat(capturedQuery)
                .contains("clientLegalEntityCode=CGD")
                .contains("clientPortfolioNumber=CGD-EUR-001")
                .contains("hubLegalEntityCode=LOC");
    }

    static String capturedQuery;

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
