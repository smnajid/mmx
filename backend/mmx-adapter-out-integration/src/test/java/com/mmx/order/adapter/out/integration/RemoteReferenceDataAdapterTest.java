package com.mmx.order.adapter.out.integration;

import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.application.termrate.TermRateAuditRow;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Remote-backed reference-data adapters: CGED reads currencies / institutions / term-rates / grants
 * live from LODH via REST. The adapters send the {@code X-MMX-CrossOrg-Key} credential; LODH
 * auto-scopes grants to the proven client. CGED stores zero hub reference data locally.
 *
 * <p>Spec: {@code order-routing} — thin-client reference-data reads; proxy indirection collapses
 * (hub-native codes cross the boundary).
 */
@Tag("fast")
class RemoteReferenceDataAdapterTest {

    private static final String CREDENTIAL = "key-cgd";
    private static final LegalEntityCode CLIENT_LE = new LegalEntityCode("CGD");

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
    void remoteManagedCurrencyRepository_parsesCurrenciesFromLodh() {
        server.createContext("/api/v1/cross-org/reference/currencies", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-MMX-CrossOrg-Key")).isEqualTo(CREDENTIAL);
            respond(exchange, 200, """
                    [
                      {"code":"EUR","active":true,"minSubscriptionAmount":10000.0,"minIncreaseDecreaseAmount":1000.0,"enabledTenors":["3M","6M"],"enabledNoticePeriods":["24H"]},
                      {"code":"USD","active":true,"minSubscriptionAmount":50000.0,"minIncreaseDecreaseAmount":5000.0,"enabledTenors":["1M"],"enabledNoticePeriods":[]}
                    ]
                    """);
        });

        var ctx = new RemoteReferenceDataContext("http://localhost:" + port, CREDENTIAL);
        ManagedCurrencyRepository repo = new RemoteManagedCurrencyRepository(ctx);

        List<ManagedCurrency> result = repo.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getCode()).isEqualTo("EUR");
        assertThat(result.getFirst().isActive()).isTrue();
        assertThat(result.getFirst().getMinSubscriptionAmount()).isEqualByComparingTo("10000.00");
        assertThat(result.getFirst().getEnabledTenors())
                .containsExactlyInAnyOrder(com.mmx.order.domain.model.Tenor._3M, com.mmx.order.domain.model.Tenor._6M);
        assertThat(result.getFirst().getEnabledNoticePeriods())
                .containsExactly(com.mmx.order.domain.model.NoticePeriod._24H);
        assertThat(result.get(1).getCode()).isEqualTo("USD");
    }

    @Test
    void remoteInstitutionRepository_parsesInstitutionsFromLodh() {
        server.createContext("/api/v1/cross-org/reference/institutions", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            respond(exchange, 200, """
                    [
                      {"institutionCode":"HSBC-01","displayName":"BankCo International","active":true},
                      {"institutionCode":"BARC-02","displayName":"Barclays Capital","active":false}
                    ]
                    """);
        });

        var ctx = new RemoteReferenceDataContext("http://localhost:" + port, CREDENTIAL);
        InstitutionRepository repo = new RemoteInstitutionRepository(ctx);

        List<Institution> result = repo.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getInstitutionCode()).isEqualTo("HSBC-01");
        assertThat(result.getFirst().getDisplayName()).isEqualTo("BankCo International");
        assertThat(result.getFirst().isActive()).isTrue();
        assertThat(result.get(1).isActive()).isFalse();
    }

    @Test
    void remoteInstitutionRepository_findActive_appendsActiveOnlyParam() {
        server.createContext("/api/v1/cross-org/reference/institutions", exchange -> {
            assertThat(exchange.getRequestURI().getQuery()).contains("activeOnly=true");
            respond(exchange, 200, """
                    [{"institutionCode":"HSBC-01","displayName":"BankCo International","active":true}]
                    """);
        });

        var ctx = new RemoteReferenceDataContext("http://localhost:" + port, CREDENTIAL);
        InstitutionRepository repo = new RemoteInstitutionRepository(ctx);

        List<Institution> result = repo.findActive();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getInstitutionCode()).isEqualTo("HSBC-01");
    }

    @Test
    void remoteTermRateRepository_parsesRatesForDate() {
        server.createContext("/api/v1/cross-org/reference/term-rates", exchange -> {
            assertThat(exchange.getRequestURI().getQuery()).contains("tradingDate=2026-08-01");
            respond(exchange, 200, """
                    [
                      {"tradingDate":"2026-08-01","institutionCode":"HSBC-01","currency":"EUR","tenor":"3M","rate":3.25}
                    ]
                    """);
        });

        var ctx = new RemoteReferenceDataContext("http://localhost:" + port, CREDENTIAL);
        TermRateRepository repo = new RemoteTermRateRepository(ctx);

        List<TermRateAuditRow> result = repo.findByTradingDate(LocalDate.of(2026, 8, 1));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().tradingDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(result.getFirst().institutionCode()).isEqualTo("HSBC-01");
        assertThat(result.getFirst().currency()).isEqualTo("EUR");
        assertThat(result.getFirst().tenor()).isEqualTo(com.mmx.order.domain.model.Tenor._3M);
        assertThat(result.getFirst().rate()).isEqualByComparingTo("3.25");
    }

    @Test
    void remoteDelegatedGrantRepository_parsesGrantsScopedToProvenClient() {
        server.createContext("/api/v1/cross-org/reference/grants", exchange -> {
            respond(exchange, 200, """
                    [
                      {"hubInstitutionCode":"HSBC-01","clientLegalEntityCode":"CGD","currency":"EUR","enabledTenors":["3M"],"enabledNoticePeriods":["24H"],"active":true}
                    ]
                    """);
        });

        var ctx = new RemoteReferenceDataContext("http://localhost:" + port, CREDENTIAL);
        DelegatedGrantRepository repo = new RemoteDelegatedGrantRepository(ctx);

        List<DelegatedInstitutionGrant> result = repo.findByClientLegalEntityCode(CLIENT_LE);

        assertThat(result).hasSize(1);
        DelegatedInstitutionGrant grant = result.getFirst();
        assertThat(grant.getHubInstitutionCode()).isEqualTo("HSBC-01");
        assertThat(grant.getClientLegalEntityCode()).isEqualTo(CLIENT_LE);
        assertThat(grant.getCurrency()).isEqualTo("EUR");
        assertThat(grant.getEnabledTenors()).containsExactly(com.mmx.order.domain.model.Tenor._3M);
        assertThat(grant.isActive()).isTrue();
    }

    @Test
    void adapters_returnEmptyList_onServerError() {
        server.createContext("/", exchange -> respond(exchange, 503, ""));

        var ctx = new RemoteReferenceDataContext("http://localhost:" + port, CREDENTIAL);
        assertThat(new RemoteManagedCurrencyRepository(ctx).findAll()).isEmpty();
        assertThat(new RemoteInstitutionRepository(ctx).findAll()).isEmpty();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
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
