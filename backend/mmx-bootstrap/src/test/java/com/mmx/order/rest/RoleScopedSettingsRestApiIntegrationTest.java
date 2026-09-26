package com.mmx.order.rest;

import com.mmx.order.MmxApplication;
import com.mmx.order.support.SharedPostgresTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.support.RestTestInstitutions;
import com.mmx.order.support.RoutedClientFixture;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thin REST contract smoke for role-scoped settings authorisation wiring (spec
 * {@code test-feedback-loop}, Phase B). The underlying grant/onboarding business rules are asserted
 * in fast {@code mmx-application} tests ({@code ManageDelegatedGrantsServiceTest},
 * {@code OnboardInstitutionServiceTest}); this class pins only the HTTP authorisation wiring
 * (ClientRepresentative is barred from mutating desk/settings-vs-scope surfaces) against the full
 * Spring context + PostgreSQL, plus the client institution onboarding flow end to end: each exported-state
 * mutation commits exactly one {@code institution_export_outbox} row, client enablement commits none.
 */
@Tag("integration")
@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class RoleScopedSettingsRestApiIntegrationTest extends SharedPostgresTestBase {

    private static final String DEMO_TRADER = "demo-trader";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final ObjectMapper json = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void clientRepresentative_currencyOnboard_returns403() throws Exception {
        reScopeToParClient();
        HttpResponse<String> res =
                postJson(
                        "/api/v1/settings/currencies",
                        """
                        {
                          "code": "CHF",
                          "minSubscriptionAmount": 1000000.00,
                          "minIncreaseDecreaseAmount": 250000.00,
                          "enabledTenors": ["3M"],
                          "enabledNoticePeriods": ["24H"]
                        }
                        """);
        assertThat(res.statusCode()).isEqualTo(403);
    }

    @Test
    void clientRepresentative_nativeOnboardByDisplayName_returns403() throws Exception {
        reScopeToParClient();
        HttpResponse<String> res =
                postJson(
                        "/api/v1/settings/institutions",
                        """
                        {"displayName":"Free-form Native"}
                        """);
        assertThat(res.statusCode()).isEqualTo(403);
    }

    @Test
    void clientRepresentative_grantedList_onboard_accounts_enable_offboard_reonboard() throws Exception {
        RoutedClientFixture fx = new RoutedClientFixture(port);
        grantCpocToParInUsd(fx);

        fx.asParClient();
        HttpResponse<String> granted = fx.get("/api/v1/settings/institutions/granted");
        assertThat(granted.statusCode()).isEqualTo(200);
        JsonNode cpoc = entry(json.readTree(granted.body()), "hubInstitutionCode", RestTestInstitutions.CP_OC_CODE);
        assertThat(cpoc.path("displayName").asText()).isEqualTo("CP-OC via LOC");
        assertThat(cpoc.path("currencies").toString()).contains("USD");

        String code = openOnboarded(fx);
        JsonNode detail = json.readTree(fx.get("/api/v1/settings/institutions/" + code).body());
        assertThat(detail.path("closedToNewBusiness").asBoolean()).isFalse();
        assertThat(entry(detail.path("enablements"), "currency", "USD").path("grantedTenors").toString()).contains("1M");

        int rows = exportRows("PAR", code);
        String termAccount = "PAR-CPOC-T" + System.nanoTime() % 100000;
        assertThat(fx.setAccounts(code, termAccount, "PAR-CPOC-OC").statusCode()).isEqualTo(200);
        assertThat(exportRows("PAR", code)).isEqualTo(rows + 1);
        assertThat(fx.setAccounts(code, termAccount, "PAR-CPOC-OC").statusCode()).isEqualTo(200);
        assertThat(exportRows("PAR", code)).as("unchanged accounts record nothing").isEqualTo(rows + 1);

        HttpResponse<String> enabled = fx.enable(code, "USD", List.of("1M"), List.of());
        assertThat(enabled.statusCode()).as(enabled.body()).isEqualTo(200);
        assertThat(entry(json.readTree(enabled.body()).path("enablements"), "currency", "USD").path("enabledTenors").toString())
                .contains("1M");
        assertThat(exportRows("PAR", code)).as("client enablement records no export").isEqualTo(rows + 1);

        assertThat(fx.post("/api/v1/settings/institutions/" + code + "/deactivate", "").statusCode()).isEqualTo(200);
        assertThat(exportRows("PAR", code)).isEqualTo(rows + 2);
        assertThat(fx.post("/api/v1/settings/institutions/" + code + "/deactivate", "").statusCode())
                .as("offboarding is idempotent")
                .isEqualTo(200);
        assertThat(exportRows("PAR", code)).isEqualTo(rows + 2);

        HttpResponse<String> reonboarded =
                fx.post("/api/v1/settings/institutions", "{\"hubInstitutionCode\":\"%s\"}".formatted(RestTestInstitutions.CP_OC_CODE));
        assertThat(reonboarded.statusCode()).as(reonboarded.body()).isEqualTo(200);
        JsonNode reopened = json.readTree(reonboarded.body());
        assertThat(reopened.path("institutionCode").asText()).isEqualTo(code);
        assertThat(reopened.path("closedToNewBusiness").asBoolean()).isFalse();
        assertThat(reopened.path("termCounterpartyAccount").asText()).isEqualTo(termAccount);
        assertThat(exportRows("PAR", code)).isEqualTo(rows + 3);
    }

    @Test
    void clientEnablement_withoutTheAccount_orOutsideTheGrant_isRejected() throws Exception {
        RoutedClientFixture fx = new RoutedClientFixture(port);
        grantCpocToParInUsd(fx);
        String code = openOnboarded(fx);
        fx.enable(code, "USD", List.of(), List.of());
        assertThat(fx.setAccounts(code, null, "PAR-CPOC-OC").statusCode()).isEqualTo(200);

        assertThat(fx.enable(code, "USD", List.of("1M"), List.of()).statusCode()).as("no Term account").isEqualTo(400);

        assertThat(fx.setAccounts(code, "PAR-CPOC-T", "PAR-CPOC-OC").statusCode()).isEqualTo(200);
        assertThat(fx.enable(code, "USD", List.of("6M"), List.of()).statusCode()).as("6M is not granted").isEqualTo(400);
    }

    @Test
    void trader_setsHubAccounts_oneExportRowPerChange() throws Exception {
        RoutedClientFixture fx = new RoutedClientFixture(port);
        fx.asLocTrader();
        String cpoc = RestTestInstitutions.CP_OC_CODE;
        int rows = exportRows("LOC", cpoc);
        String suffix = Long.toString(System.nanoTime() % 100000);

        HttpResponse<String> res = fx.setAccounts(cpoc, "LOC-CPOC-T" + suffix, "LOC-CPOC-OC");

        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        assertThat(json.readTree(res.body()).path("termCounterpartyAccount").asText()).isEqualTo("LOC-CPOC-T" + suffix);
        assertThat(exportRows("LOC", cpoc)).isEqualTo(rows + 1);
    }

    @Test
    void crossScope_accountChange_isRejected() throws Exception {
        RoutedClientFixture fx = new RoutedClientFixture(port);
        fx.asParClient();

        HttpResponse<String> res = fx.setAccounts(RestTestInstitutions.CP_OC_CODE, "HIJACK", null);

        assertThat(res.statusCode()).isEqualTo(404);
    }

    @Test
    void trader_cannotListGrantedInstitutions() throws Exception {
        RoutedClientFixture fx = new RoutedClientFixture(port);
        fx.asLocTrader();

        assertThat(fx.get("/api/v1/settings/institutions/granted").statusCode()).isEqualTo(403);
    }

    private void grantCpocToParInUsd(RoutedClientFixture fx) throws Exception {
        fx.asLocTrader();
        String key = "/api/v1/settings/delegated-grants/" + RestTestInstitutions.CP_OC_CODE + "/PAR/USD";
        HttpResponse<String> created =
                fx.post(
                        "/api/v1/settings/delegated-grants",
                        """
                        {"hubInstitutionCode":"%s","clientLegalEntityCode":"PAR","currency":"USD",
                         "enabledTenors":["1M"],"enabledNoticePeriods":["24H"]}
                        """
                                .formatted(RestTestInstitutions.CP_OC_CODE));
        assertThat(created.statusCode()).isIn(201, 409);
        assertThat(fx.post(key + "/reactivate", "").statusCode()).isIn(200, 409);
    }

    /** PAR's open onboarded institution for CPOC-01 (onboarded, or re-onboarded if a previous test offboarded it). */
    private String openOnboarded(RoutedClientFixture fx) throws Exception {
        fx.asParClient();
        HttpResponse<String> res =
                fx.post("/api/v1/settings/institutions", "{\"hubInstitutionCode\":\"%s\"}".formatted(RestTestInstitutions.CP_OC_CODE));
        if (res.statusCode() == 201 || res.statusCode() == 200) {
            return json.readTree(res.body()).path("institutionCode").asText();
        }
        assertThat(res.statusCode()).as(res.body()).isEqualTo(409);
        return entry(json.readTree(fx.get("/api/v1/settings/institutions").body()), "hubInstitutionCode", RestTestInstitutions.CP_OC_CODE)
                .path("institutionCode")
                .asText();
    }

    private int exportRows(String legalEntityCode, String institutionCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM institution_export_outbox WHERE legal_entity_code = ? AND institution_code = ?",
                Integer.class,
                legalEntityCode,
                institutionCode);
    }

    private static JsonNode entry(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.path(field).asText())) {
                return node;
            }
        }
        throw new AssertionError("No entry with " + field + "=" + value + " in " + array);
    }

    private void reScopeToParClient() throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri("/api/v1/session/scope"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("X-User-Id", DEMO_TRADER)
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        """
                                        {"legalEntityCode":"PAR","role":"CLIENT_REPRESENTATIVE"}
                                        """,
                                        StandardCharsets.UTF_8))
                        .build();
        HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(res.statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> postJson(String path, String json) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri(path))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("X-User-Id", DEMO_TRADER)
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI baseUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}