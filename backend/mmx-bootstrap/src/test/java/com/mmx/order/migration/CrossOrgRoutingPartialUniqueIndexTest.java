package com.mmx.order.migration;

import com.mmx.order.MmxApplication;
import com.mmx.order.support.SharedPostgresTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V24 spec-mandated composite partial unique index on
 * {@code (originating_legal_entity_code, routing_id)} for routed hub-side orders: a hub deployment
 * may host hub-side orders from several originating client entities, so {@code routing_id} alone is
 * not globally unique — uniqueness is scoped to the pair (originating client entity, routing id).
 * Native desk orders ({@code NULL} originating) are unconstrained.
 *
 * <p>Lives in {@code mmx-bootstrap} against PostgreSQL (shared singleton container) so the V24
 * Java migration's partial unique index is exercised with a real Postgres planner.
 */
@Tag("integration")
@SpringBootTest(classes = MmxApplication.class)
@ActiveProfiles("test")
class CrossOrgRoutingPartialUniqueIndexTest extends SharedPostgresTestBase {

    private static final String ORIGIN_A = "CGA";
    private static final String ORIGIN_B = "CGB";

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void composite_partial_unique_index_exists_on_routed_hub_side_orders() {
        Integer indexCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM pg_indexes
                        WHERE tablename = 'money_market_order'
                          AND indexname = 'uq_money_market_order_routing_hub_pair'
                        """,
                        Integer.class);
        assertThat(indexCount).isEqualTo(1);
    }

    @Test
    void duplicate_originating_and_routing_id_is_rejected() {
        UUID routingId = UUID.randomUUID();
        insertHubSideOrder(UUID.randomUUID(), ORIGIN_A, routingId);
        assertThatThrownBy(() -> insertHubSideOrder(UUID.randomUUID(), ORIGIN_A, routingId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void same_routing_id_for_different_originating_is_allowed() {
        UUID routingId = UUID.randomUUID();
        insertHubSideOrder(UUID.randomUUID(), ORIGIN_A, routingId);
        insertHubSideOrder(UUID.randomUUID(), ORIGIN_B, routingId);
        assertThat(countRowsByRoutingId(routingId)).isEqualTo(2);
    }

    @Test
    void local_desk_orders_with_null_originating_are_unconstrained() {
        insertLocalDeskOrder(UUID.randomUUID(), "LOC");
        insertLocalDeskOrder(UUID.randomUUID(), "PAR");
        assertThat(countRowsWithNullOriginating()).isEqualTo(2);
    }

    private void insertHubSideOrder(UUID id, String originatingLe, UUID routingId) {
        insertOrder(id, "LOC", "HUB-" + id, routingId, originatingLe);
    }

    private void insertLocalDeskOrder(UUID id, String leCode) {
        insertOrder(id, leCode, "LOCAL-" + id, null, null);
    }

    private void insertOrder(
            UUID id, String leCode, String externalRef, UUID routingId, String originatingLe) {
        Instant now = Instant.parse("2026-05-01T12:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO money_market_order (
                    id, legal_entity_code, external_order_reference, order_type, order_operation,
                    portfolio_number, currency, amount, value_date, minimum_rate, status,
                    created_at, updated_at, routing_id, originating_legal_entity_code
                ) VALUES (?, ?, ?, 'TERM', 'SUBSCRIPTION', 'PF-ROUTE', 'EUR', ?, ?, ?, 'ROUTED', ?, ?, ?, ?)
                """,
                id,
                leCode,
                externalRef,
                new BigDecimal("1000000.00"),
                LocalDate.of(2026, 5, 10),
                new BigDecimal("3.25000000"),
                Timestamp.from(now),
                Timestamp.from(now),
                routingId,
                originatingLe);
    }

    private Integer countRowsByRoutingId(UUID routingId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM money_market_order WHERE routing_id = ?", Integer.class, routingId);
    }

    private Integer countRowsWithNullOriginating() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM money_market_order WHERE originating_legal_entity_code IS NULL",
                Integer.class);
    }
}