package com.mmx.order.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.MmxApplication;
import com.mmx.order.support.RestTestInstitutions;
import com.mmx.order.support.RoutedClientFixture;
import com.mmx.order.support.SharedPostgresTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REST contract wiring for local routed intake (spec {@code test-feedback-loop}, Phase B) against the full
 * Spring context + PostgreSQL. The routing rules themselves are asserted in fast {@code mmx-application}
 * tests ({@code IntakeServiceTest}); this class pins the HTTP contract and the correlated rows, including the
 * closed-to-new-business, client-enablement and counterparty-account outcomes.
 */
@Tag("integration")
@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rest-test")
class OrderRoutingIntakeIntegrationTest extends SharedPostgresTestBase {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    RoutedClientFixture fixture;
    String onboardedCode;

    @BeforeEach
    void seedRoutingPrerequisites() throws Exception {
        fixture = new RoutedClientFixture(port);
        onboardedCode = fixture.routedBaseline();
    }

    /** The shared database outlives this class: restore the seeded hub accounts a test may have cleared. */
    @AfterEach
    void restoreHubAccounts() throws Exception {
        fixture.hubAccounts("LOC-" + RestTestInstitutions.BANKCO_CODE + "-T", "LOC-" + RestTestInstitutions.BANKCO_CODE + "-OC");
    }

    @Test
    void clientRoutedIntake_linksHubSideOrder_withSharedRoutingId_andTheClientAccountSnapshot() throws Exception {
        JsonNode body = intake(termJson("SUBSCRIPTION", "3M"));
        assertThat(body.path("status").asText()).isEqualTo("ROUTED");
        UUID clientOrderId = UUID.fromString(body.path("orderId").asText());

        String routingId =
                jdbcTemplate.queryForObject(
                        "SELECT routing_id::text FROM money_market_order WHERE id = ?", String.class, clientOrderId);
        assertThat(routingId).isNotBlank();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM money_market_order WHERE routing_id = ?::uuid "
                                + "AND originating_legal_entity_code IS NOT NULL",
                        Integer.class,
                        routingId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT status, client_counterparty_account FROM money_market_order WHERE routing_id = ?::uuid "
                                + "AND originating_legal_entity_code IS NOT NULL",
                        routingId))
                .containsEntry("status", "RECEIVED")
                .containsEntry("client_counterparty_account", RoutedClientFixture.PAR_TERM_ACCOUNT);
    }

    @Test
    void redemption_onADeactivatedGrant_isRouted() throws Exception {
        String contractNumber = executedOnCallSubscriptionContract();
        fixture.deactivateGrant("EUR");

        JsonNode body = intake(onCallJson("REDEMPTION", contractNumber));

        assertThat(body.path("status").asText()).as(body.toString()).isEqualTo("ROUTED");
    }

    @Test
    void subscription_onAnOffboardedInstitution_isRejected() throws Exception {
        fixture.asParClient();
        assertThat(fixture.post("/api/v1/settings/institutions/" + onboardedCode + "/deactivate", "").statusCode())
                .isEqualTo(200);

        JsonNode body = intake(termJson("SUBSCRIPTION", "3M"));

        assertThat(body.path("status").asText()).isEqualTo("REJECTED");
        assertThat(rejectionReason(body)).contains("closed to new business");
    }

    @Test
    void subscription_onAGrantedTenorTheClientHasNotEnabled_isRejected() throws Exception {
        JsonNode body = intake(termJson("SUBSCRIPTION", "6M"));

        assertThat(body.path("status").asText()).isEqualTo("REJECTED");
        assertThat(rejectionReason(body)).contains("not enabled");
    }

    @Test
    void missingHubCounterpartyAccount_isARoutingFailure_withNoHubSideOrder() throws Exception {
        fixture.hubAccounts(null, "LOC-" + RestTestInstitutions.BANKCO_CODE + "-OC");
        int hubOrdersBefore = hubSideOrderCount();

        JsonNode body = intake(termJson("SUBSCRIPTION", "3M"));

        assertThat(body.path("status").asText()).isEqualTo("REJECTED");
        assertThat(rejectionReason(body)).startsWith("Routing failure").contains("Term counterparty account");
        assertThat(hubSideOrderCount()).isEqualTo(hubOrdersBefore);
    }

    /** Routes and executes a PAR OnCall 24H Subscription; returns the client-side contract number. */
    private String executedOnCallSubscriptionContract() throws Exception {
        JsonNode subscribed = intake(onCallJson("SUBSCRIPTION", null));
        assertThat(subscribed.path("status").asText()).as(subscribed.toString()).isEqualTo("ROUTED");
        UUID clientOrderId = UUID.fromString(subscribed.path("orderId").asText());
        String hubOrderId =
                jdbcTemplate.queryForObject(
                        "SELECT h.id::text FROM money_market_order h JOIN money_market_order c ON c.routing_id = h.routing_id "
                                + "WHERE c.id = ? AND h.originating_legal_entity_code IS NOT NULL",
                        String.class,
                        clientOrderId);
        fixture.asLocTrader();
        assertThat(fixture.post("/api/v1/orders/" + hubOrderId + "/assign", "").statusCode()).isEqualTo(200);
        HttpResponse<String> executed =
                fixture.post("/api/v1/orders/" + hubOrderId + "/execute", RestTestInstitutions.bankCoExecuteJson(2.9));
        assertThat(executed.statusCode()).as(executed.body()).isEqualTo(200);
        return jdbcTemplate.queryForObject(
                "SELECT generated_contract_number FROM money_market_order WHERE id = ?", String.class, clientOrderId);
    }

    private JsonNode intake(String json) throws Exception {
        HttpResponse<String> res = fixture.post("/api/v1/orders", json);
        assertThat(res.statusCode()).as(res.body()).isEqualTo(201);
        return objectMapper.readTree(res.body());
    }

    private String rejectionReason(JsonNode intakeBody) {
        return jdbcTemplate.queryForObject(
                "SELECT rejection_reason FROM money_market_order WHERE id = ?::uuid",
                String.class,
                intakeBody.path("orderId").asText());
    }

    private int hubSideOrderCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM money_market_order WHERE originating_legal_entity_code IS NOT NULL", Integer.class);
    }

    private String termJson(String operation, String tenor) {
        return """
                {
                  "externalOrderReference": "IT-ROUTE-%s",
                  "legalEntityCode": "PAR",
                  "orderType": "TERM",
                  "orderOperation": "%s",
                  "portfolioNumber": "PAR-PM-77",
                  "currency": "EUR",
                  "amount": 1000000.00,
                  "valueDate": "%s",
                  "minimumRate": 2.5,
                  "tenor": "%s",
                  "institutionCode": "%s"
                }
                """
                .formatted(System.nanoTime(), operation, LocalDate.now().plusDays(10), tenor, onboardedCode);
    }

    private String onCallJson(String operation, String sourceContractNumber) {
        String source = sourceContractNumber == null ? "" : "\"sourceContractNumber\": \"%s\",".formatted(sourceContractNumber);
        return """
                {
                  "externalOrderReference": "IT-ROUTE-OC-%s",
                  "legalEntityCode": "PAR",
                  "orderType": "ON_CALL",
                  "orderOperation": "%s",
                  "portfolioNumber": "PAR-PM-77",
                  "currency": "EUR",
                  "amount": %s,
                  "valueDate": "%s",
                  "noticePeriod": "24H",
                  %s
                  "institutionCode": "%s"
                }
                """
                .formatted(
                        System.nanoTime(),
                        operation,
                        "SUBSCRIPTION".equals(operation) ? "1000000.00" : "100000.00",
                        LocalDate.now().plusDays(10),
                        source,
                        onboardedCode);
    }
}
