package com.mmx.order.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.MmxApplication;
import com.mmx.order.application.port.out.ExternalIdentityGateway;
import com.mmx.order.application.port.out.HubLocalityResolver;
import com.mmx.order.domain.model.HubLocality;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.application.port.out.RemoteRoutingGateway;
import com.mmx.order.application.service.ResilientRemoteRoutingGateway;
import com.mmx.order.support.SharedPostgresTestBase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the FULL deployment context with {@code mmx.cross-org.role=client} — the CGEG deployment
 * shape used by {@code application-cgeg.yml} — against the shared Testcontainer Postgres, with an
 * in-process stub standing in for the LODH hub (reference data, leg-A accept) and the external identity
 * system.
 *
 * <p>Guards the client-side cross-org wiring: Leg-A outbound {@link RemoteRoutingGateway} wrapped
 * in the retry/circuit-breaker resilience decorator, the external-identity resolution hook
 * ({@link ExternalIdentityGateway}),
 * {@code ApplyRemoteOrderOutcomeUseCase}, and a {@link HubLocalityResolver} that always resolves
 * REMOTE (V1 deployment-role-based locality). The property block mirrors
 * {@code application-cgeg.yml}'s {@code mmx.cross-org.*} shape so a binding typo (e.g. a renamed
 * retry key silently binding to null) fails here instead of at deployment start-up.
 * The Leg-B Kafka listener is left disabled ({@code consumer-enabled=false}) — no broker exists in
 * the {@code fast|integration} loop; its wiring is covered by the e2e suite.
 *
 * <p>It also pins ADR 0008 on the client deployment: the remote client stores its own onboarded
 * institutions and client enablement locally (no hub institution row, no hub write) and sends its
 * counterparty account snapshot on leg A.
 *
 * <p>The {@code rest-test} profile is deliberately NOT activated: its {@code ApplicationRunner}
 * seeders onboard hub reference data, which a thin client does not master. The datasource comes from
 * the shared-container {@link SharedPostgresTestBase} dynamic properties alone.
 */
@Tag("integration")
@TestPropertySource(properties = {
        "mmx.organisation.code=CGEG",
        "mmx.cross-org.role=client",
        "mmx.cross-org.own-legal-entity-codes=CGD",
        "mmx.cross-org.reference-data-remote=true",
        "mmx.cross-org.remote-routing-gateway.credential-key=test-client-key",
        "mmx.cross-org.retry.max-attempts=3",
        "mmx.cross-org.retry.initial-backoff-ms=500",
        "mmx.cross-org.retry.failure-threshold=5",
        "mmx.cross-org.retry.recovery-duration-ms=30000",
        "mmx.cross-org.consumer-enabled=false",
        "mmx.backoffice.outbox.relay-enabled=false",
        "mmx.oncall.outbox.relay-enabled=false",
        "mmx.institution.outbox.relay-enabled=false",
})
@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CrossOrgClientDeploymentIntegrationTest extends SharedPostgresTestBase {

    /** A hub-native institution that exists only at the stub hub, never in this deployment's database. */
    private static final String HUB_ONLY_INSTITUTION = "SGX-01";

    private static final StubHub HUB = StubHub.start();

    @DynamicPropertySource
    static void stubHubEndpoints(DynamicPropertyRegistry registry) {
        registry.add("mmx.cross-org.remote-routing-gateway.base-url", HUB::baseUrl);
        registry.add("mmx.cross-org.external-identity.base-url", HUB::baseUrl);
    }

    @AfterAll
    static void stopHub() {
        HUB.stop();
    }

    @Autowired
    @Qualifier("remoteRoutingGateway")
    RemoteRoutingGateway remoteRoutingGateway;

    @Autowired
    HubLocalityResolver hubLocalityResolver;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @LocalServerPort
    int port;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void clientRoleContextBoots_withResilientGatewayAndRemoteLocality() {
        assertThat(remoteRoutingGateway).isInstanceOf(ResilientRemoteRoutingGateway.class);
        assertThat(hubLocalityResolver.resolveForClient(new LegalEntityCode("CGD")))
                .isEqualTo(HubLocality.REMOTE);
    }

    @Test
    void remoteClient_storesItsOnboardedInstitutionAndEnablementLocally_andLegACarriesItsAccount() throws Exception {
        asCgdClientRepresentative();

        JsonNode granted = json.readTree(call("GET", "/api/v1/settings/institutions/granted", null).body());
        assertThat(granted.toString()).contains(HUB_ONLY_INSTITUTION).contains("Stub Bank via LOC");

        String code = onboard();
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT legal_entity_code, hub_legal_entity_code, hub_institution_code FROM institution "
                                + "WHERE institution_code = ?",
                        code))
                .containsEntry("legal_entity_code", "CGD")
                .containsEntry("hub_legal_entity_code", "LOC")
                .containsEntry("hub_institution_code", HUB_ONLY_INSTITUTION);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM institution WHERE institution_code = ?", Integer.class, HUB_ONLY_INSTITUTION))
                .as("the hub-native institution is not stored in the client deployment")
                .isZero();

        JsonNode listed = json.readTree(call("GET", "/api/v1/settings/institutions", null).body());
        assertThat(listed.toString()).contains(code);

        HttpResponse<String> enabled =
                call("PUT", "/api/v1/settings/institutions/" + code + "/enablement/EUR",
                        "{\"enabledTenors\":[\"3M\"],\"enabledNoticePeriods\":[]}");
        assertThat(enabled.statusCode()).as(enabled.body()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM client_institution_enablement WHERE institution_code = ? AND currency = 'EUR'",
                        Integer.class,
                        code))
                .isEqualTo(1);
        assertThat(HUB.writes()).as("settings never write to the hub").isEmpty();

        HttpResponse<String> intake = call("POST", "/api/v1/orders", termSubscription(code));
        assertThat(intake.statusCode()).as(intake.body()).isEqualTo(201);
        assertThat(json.readTree(intake.body()).path("status").asText()).isEqualTo("ROUTED");
        assertThat(HUB.legABodies()).hasSize(1);
        JsonNode legA = json.readTree(HUB.legABodies().getFirst());
        assertThat(legA.path("institutionCode").asText()).isEqualTo(HUB_ONLY_INSTITUTION);
        assertThat(legA.path("clientCounterpartyAccount").asText()).isEqualTo("CGD-SGX-T");
    }

    private String onboard() throws Exception {
        HttpResponse<String> res =
                call("POST", "/api/v1/settings/institutions",
                        """
                        {"hubInstitutionCode":"%s","termCounterpartyAccount":"CGD-SGX-T","onCallCounterpartyAccount":"CGD-SGX-OC"}
                        """
                                .formatted(HUB_ONLY_INSTITUTION));
        assertThat(res.statusCode()).as(res.body()).isIn(200, 201);
        return json.readTree(res.body()).path("institutionCode").asText();
    }

    private void asCgdClientRepresentative() throws Exception {
        HttpResponse<String> res =
                call("POST", "/api/v1/session/scope", "{\"legalEntityCode\":\"CGD\",\"role\":\"CLIENT_REPRESENTATIVE\"}");
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
    }

    private HttpResponse<String> call(String method, String path, String body) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .timeout(Duration.ofSeconds(30))
                        .header("X-User-Id", "demo-trader")
                        .header("Content-Type", "application/json");
        request.method(
                method,
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String termSubscription(String institutionCode) {
        return """
                {
                  "externalOrderReference": "CGD-IT-%s",
                  "legalEntityCode": "CGD",
                  "orderType": "TERM",
                  "orderOperation": "SUBSCRIPTION",
                  "portfolioNumber": "CGD-PM-1",
                  "currency": "EUR",
                  "amount": 1000000.00,
                  "valueDate": "%s",
                  "tenor": "3M",
                  "institutionCode": "%s"
                }
                """
                .formatted(System.nanoTime(), LocalDate.now().plusDays(10), institutionCode);
    }

    /** The LODH hub and the external identity system, as seen from the CGEG client deployment. */
    private static final class StubHub {

        private final HttpServer server;
        private final List<String> writes = new CopyOnWriteArrayList<>();
        private final List<String> legABodies = new CopyOnWriteArrayList<>();

        private StubHub(HttpServer server) {
            this.server = server;
        }

        static StubHub start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                StubHub hub = new StubHub(server);
                hub.routes();
                server.start();
                return hub;
            } catch (IOException e) {
                throw new IllegalStateException("Cannot start stub hub", e);
            }
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        List<String> writes() {
            return writes;
        }

        List<String> legABodies() {
            return legABodies;
        }

        void stop() {
            server.stop(0);
        }

        private void routes() {
            server.createContext("/api/v1/cross-org/reference/institutions", ex -> reply(ex, 200, """
                    [{"institutionCode":"%s","displayName":"Stub Bank","active":true}]
                    """.formatted(HUB_ONLY_INSTITUTION)));
            server.createContext("/api/v1/cross-org/reference/grants", ex -> reply(ex, 200, """
                    [{"hubInstitutionCode":"%s","clientLegalEntityCode":"CGD","currency":"EUR",
                      "enabledTenors":["3M"],"enabledNoticePeriods":[],"active":true}]
                    """.formatted(HUB_ONLY_INSTITUTION)));
            server.createContext("/api/v1/cross-org/reference/currencies", ex -> reply(ex, 200, """
                    [{"code":"EUR","active":true,"minSubscriptionAmount":1.00,"minIncreaseDecreaseAmount":1.00,
                      "enabledTenors":["1W","2W","1M","3M","6M","1Y"],"enabledNoticePeriods":["24H","48H"]}]
                    """));
            server.createContext("/api/identity/resolve", ex -> reply(ex, 200, "{\"hubPortfolioNumber\":\"LOC-EUR-001\"}"));
            server.createContext("/api/v1/cross-org/routed-orders", ex -> {
                legABodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                reply(ex, 200, "{\"outcome\":\"ACCEPTED\",\"acceptedAt\":\"%s\"}".formatted(Instant.now()));
            });
            server.createContext("/", ex -> reply(ex, 404, ""));
        }

        private void reply(HttpExchange exchange, int status, String body) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())
                    && !exchange.getRequestURI().getPath().endsWith("/routed-orders")) {
                writes.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        }
    }
}
