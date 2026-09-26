package com.mmx.order.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idempotent REST setup for TradingClient {@code PAR} routing to hub {@code LOC} in the rest-test context: the
 * delegated grant, PAR's onboarded institution with counterparty accounts and client enablement, and the global
 * account. The Spring context and database are shared across test classes, so every step converges to the
 * requested state instead of assuming a cold start.
 */
public final class RoutedClientFixture {

    public static final String USER = "demo-trader";
    public static final String PAR_TERM_ACCOUNT = "PAR-BI-T";
    public static final String PAR_ONCALL_ACCOUNT = "PAR-BI-OC";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int port;

    public RoutedClientFixture(int port) {
        this.port = port;
    }

    /** Active grant {@code (BI-01, PAR, currency)} with exactly the given sets. */
    public void grant(String currency, List<String> tenors, List<String> notices) throws Exception {
        asLocTrader();
        String hub = RestTestInstitutions.BANKCO_CODE;
        HttpResponse<String> created =
                post(
                        "/api/v1/settings/delegated-grants",
                        """
                        {"hubInstitutionCode":"%s","clientLegalEntityCode":"PAR","currency":"%s",
                         "enabledTenors":%s,"enabledNoticePeriods":%s}
                        """
                                .formatted(hub, currency, json(tenors), json(notices)));
        assertThat(created.statusCode()).isIn(201, 409);
        String key = "/api/v1/settings/delegated-grants/" + hub + "/PAR/" + currency;
        assertThat(send("PATCH", key, "{\"enabledTenors\":%s,\"enabledNoticePeriods\":%s}".formatted(json(tenors), json(notices))).statusCode())
                .isEqualTo(200);
        assertThat(post(key + "/reactivate", "").statusCode()).isIn(200, 409);
    }

    public void deactivateGrant(String currency) throws Exception {
        asLocTrader();
        assertThat(post("/api/v1/settings/delegated-grants/" + RestTestInstitutions.BANKCO_CODE + "/PAR/" + currency + "/deactivate", "")
                        .statusCode())
                .isIn(200, 409);
    }

    /** PAR's open onboarded institution for BI-01, with both counterparty accounts; returns its code. */
    public String onboardedWithAccounts() throws Exception {
        asParClient();
        HttpResponse<String> onboarded =
                post("/api/v1/settings/institutions", "{\"hubInstitutionCode\":\"%s\"}".formatted(RestTestInstitutions.BANKCO_CODE));
        String code;
        if (onboarded.statusCode() == 201 || onboarded.statusCode() == 200) {
            code = objectMapper.readTree(onboarded.body()).path("institutionCode").asText();
        } else {
            assertThat(onboarded.statusCode()).as(onboarded.body()).isEqualTo(409);
            code = parOnboardedCode();
        }
        setAccounts(code, PAR_TERM_ACCOUNT, PAR_ONCALL_ACCOUNT);
        return code;
    }

    public String parOnboardedCode() throws Exception {
        asParClient();
        HttpResponse<String> listed = get("/api/v1/settings/institutions");
        assertThat(listed.statusCode()).isEqualTo(200);
        for (JsonNode node : objectMapper.readTree(listed.body())) {
            if (RestTestInstitutions.BANKCO_CODE.equals(node.path("hubInstitutionCode").asText())) {
                return node.path("institutionCode").asText();
            }
        }
        throw new IllegalStateException("PAR has no onboarded institution for " + RestTestInstitutions.BANKCO_CODE);
    }

    /** Full replacement of the accounts of an institution owned by the active scope; returns the response. */
    public HttpResponse<String> setAccounts(String institutionCode, String term, String onCall) throws Exception {
        HttpResponse<String> res =
                send(
                        "PUT",
                        "/api/v1/settings/institutions/" + institutionCode + "/counterparty-accounts",
                        "{\"termCounterpartyAccount\":%s,\"onCallCounterpartyAccount\":%s}".formatted(quoted(term), quoted(onCall)));
        return res;
    }

    public HttpResponse<String> enable(String institutionCode, String currency, List<String> tenors, List<String> notices)
            throws Exception {
        asParClient();
        return send(
                "PUT",
                "/api/v1/settings/institutions/" + institutionCode + "/enablement/" + currency,
                "{\"enabledTenors\":%s,\"enabledNoticePeriods\":%s}".formatted(json(tenors), json(notices)));
    }

    /** BI-01's hub-native accounts at LOC, set by the Trader. */
    public void hubAccounts(String term, String onCall) throws Exception {
        asLocTrader();
        assertThat(setAccounts(RestTestInstitutions.BANKCO_CODE, term, onCall).statusCode()).isEqualTo(200);
    }

    public void globalAccount(String currency, String accountRef) throws Exception {
        asLocTrader();
        HttpResponse<String> res =
                send(
                        "PUT",
                        "/api/v1/settings/global-accounts",
                        "{\"clientLegalEntityCode\":\"PAR\",\"currency\":\"%s\",\"accountRef\":\"%s\"}".formatted(currency, accountRef));
        assertThat(res.statusCode()).isEqualTo(200);
    }

    /** The routed baseline: EUR grant (3M, 6M / 24H), PAR onboarded with accounts, client-enabled 3M / 24H. */
    public String routedBaseline() throws Exception {
        grant("EUR", List.of("3M", "6M"), List.of("24H"));
        hubAccounts("LOC-" + RestTestInstitutions.BANKCO_CODE + "-T", "LOC-" + RestTestInstitutions.BANKCO_CODE + "-OC");
        String code = onboardedWithAccounts();
        assertThat(enable(code, "EUR", List.of("3M"), List.of("24H")).statusCode()).isEqualTo(200);
        globalAccount("EUR", "PAR-EUR-001");
        return code;
    }

    public void asLocTrader() throws Exception {
        scope("LOC", "TRADER");
    }

    public void asParClient() throws Exception {
        scope("PAR", "CLIENT_REPRESENTATIVE");
    }

    private void scope(String legalEntityCode, String role) throws Exception {
        HttpResponse<String> res =
                post("/api/v1/session/scope", "{\"legalEntityCode\":\"%s\",\"role\":\"%s\"}".formatted(legalEntityCode, role));
        assertThat(res.statusCode()).isEqualTo(200);
    }

    public HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(
                request(path).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public HttpResponse<String> post(String path, String json) throws Exception {
        return send("POST", path, json);
    }

    public HttpResponse<String> send(String method, String path, String json) throws Exception {
        return httpClient.send(
                request(path)
                        .header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("X-User-Id", USER);
    }

    private static String json(List<String> values) {
        return values.stream().map(v -> "\"" + v + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
