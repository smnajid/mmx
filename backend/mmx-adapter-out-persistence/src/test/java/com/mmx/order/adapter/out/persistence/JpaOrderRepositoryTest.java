package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.mapper.OrderPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOrderRepository;
import com.mmx.order.application.port.in.OrderPage;
import com.mmx.order.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("JpaOrderRepository integration")
class JpaOrderRepositoryTest {

    @Autowired
    SpringDataOrderRepository springDataRepository;

    @Autowired
    OrderPersistenceMapper mapper;

    JpaOrderRepository repository;

    @BeforeEach
    void initRepo() {
        repository = new JpaOrderRepository(springDataRepository, mapper);
        springDataRepository.deleteAll();
    }

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final LocalDate VALUE_DATE = TODAY.plusDays(2);

    private MoneyMarketOrder createTermOrder(String extRef) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference(extRef),
                new LegalEntityCode("LOC"),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-001"),
                "EUR",
                new BigDecimal("5000000.00"),
                VALUE_DATE,
                new BigDecimal("3.25000000"),
                Tenor._3M,
                null, null, "BNKCO", "BankCo",
                TODAY
        );
    }

    private MoneyMarketOrder createOnCallOrder(String extRef) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference(extRef),
                new LegalEntityCode("LOC"),
                OrderType.ON_CALL,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-002"),
                "EUR",
                new BigDecimal("2000000.00"),
                VALUE_DATE,
                new BigDecimal("2.50000000"),
                null,
                NoticePeriod._24H, null, "BNKCO", "BankCo",
                TODAY
        );
    }

    // ── Save and retrieve ────────────────────────────────────────────────────

    @Nested
    @DisplayName("save and findById")
    class SaveAndFind {

        @Test
        void save_and_retrieve_roundtrip_for_term_order() {
            MoneyMarketOrder saved = repository.save(createTermOrder("SAVE-001"));

            Optional<MoneyMarketOrder> found = repository.findById(saved.getId());

            assertThat(found).isPresent();
            MoneyMarketOrder order = found.get();
            assertThat(order.getExternalOrderReference()).isEqualTo(new ExternalOrderReference("SAVE-001"));
            assertThat(order.getOrderType()).isEqualTo(OrderType.TERM);
            assertThat(order.getOrderOperation()).isEqualTo(OrderOperation.SUBSCRIPTION);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.RECEIVED);
            assertThat(order.getTenor()).isEqualTo(Tenor._3M);
            assertThat(order.getAmount()).isEqualByComparingTo(new BigDecimal("5000000.00"));
            assertThat(order.getMinimumRate()).isEqualByComparingTo(new BigDecimal("3.25000000"));
        }

        @Test
        void save_and_retrieve_preserves_accounted_status() {
            Instant t0 = Instant.parse("2026-05-01T10:00:00Z");
            Instant t1 = Instant.parse("2026-05-01T11:00:00Z");
            MoneyMarketOrder order = createTermOrder("ACC-ROUNDTRIP");
            order.assign(new TraderId("trader-a"), t0);
            order.execute(
                    new BigDecimal("3.5"),
                    "BankCo",
                    "HSBC-01",
                    new DealingReference("DL-acc-1"),
                    new ContractNumber("CN-acc-1"),
                    new TraderId("trader-a"),
                    t0);
            order.markAccounted(t1);

            MoneyMarketOrder saved = repository.save(order);
            Optional<MoneyMarketOrder> found = repository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getStatus()).isEqualTo(OrderStatus.ACCOUNTED);
            assertThat(found.get().getUpdatedAt()).isEqualTo(t1);
        }

        @Test
        void findById_returns_empty_for_unknown_id() {
            Optional<MoneyMarketOrder> found = repository.findById(java.util.UUID.randomUUID());
            assertThat(found).isEmpty();
        }
    }

    // ── Unique constraint ────────────────────────────────────────────────────

    @Nested
    @DisplayName("unique constraint on external_order_reference")
    class UniqueConstraint {

        @Test
        void duplicate_external_order_reference_throws() {
            repository.save(createTermOrder("UNIQUE-001"));

            assertThatThrownBy(() -> repository.save(createTermOrder("UNIQUE-001")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ── Query methods ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByStatusAndOrderType")
    class FindByStatusAndType {

        @Test
        void returns_orders_matching_status_and_type() {
            repository.save(createTermOrder("QUERY-TERM-001"));
            repository.save(createTermOrder("QUERY-TERM-002"));
            repository.save(createOnCallOrder("QUERY-ONCALL-001"));

            List<MoneyMarketOrder> termReceived = repository.findByStatusAndOrderType(
                    LOC, OrderStatus.RECEIVED, OrderType.TERM);

            assertThat(termReceived).hasSize(2);
            assertThat(termReceived).allMatch(o -> o.getOrderType() == OrderType.TERM);
        }
    }

    @Nested
    @DisplayName("findReceivedPageByOrderType")
    class FindReceivedPage {

        @Test
        void valueDateRange_excludes_outside_bounds() {
            repository.save(createTermOrder("PAGE-IN"));
            MoneyMarketOrder far = MoneyMarketOrder.create(
                    new ExternalOrderReference("PAGE-OUT"),
                    new LegalEntityCode("LOC"),
                    OrderType.TERM,
                    OrderOperation.SUBSCRIPTION,
                    new PortfolioNumber("PF-R"),
                    "EUR",
                    new BigDecimal("100000.00"),
                    TODAY.plusDays(20),
                    new BigDecimal("3.00000000"),
                    Tenor._1M, null, null, "BNKCO", "BankCo",
                    TODAY);
            repository.save(far);

            OrderPage page =
                    repository.findReceivedPageByOrderType(
                            LOC,
                            OrderType.TERM,
                            java.util.Optional.of(TODAY),
                            java.util.Optional.of(TODAY.plusDays(2)),
                            0,
                            20);

            assertThat(page.content()).hasSize(1);
            assertThat(page.content().getFirst().getExternalOrderReference().value()).isEqualTo("PAGE-IN");
        }
    }

    @Nested
    @DisplayName("findByAssignedTraderIdAndStatus")
    class FindByTrader {

        @Test
        void returns_orders_assigned_to_trader_in_given_status() {
            MoneyMarketOrder order1 = repository.save(createTermOrder("TRADER-001"));
            MoneyMarketOrder order2 = repository.save(createTermOrder("TRADER-002"));
            repository.save(createTermOrder("TRADER-UNASSIGNED-001"));

            TraderId traderId = new TraderId("trader-alice");
            Instant now = Instant.now();
            order1.assign(traderId, now);
            order2.assign(traderId, now);
            repository.save(order1);
            repository.save(order2);

            List<MoneyMarketOrder> assigned = repository.findByAssignedTraderIdAndStatus(
                    LOC, traderId, OrderStatus.ASSIGNED);

            assertThat(assigned).hasSize(2);
            assertThat(assigned).allMatch(o ->
                    o.getAssignment() != null &&
                    o.getAssignment().traderId().equals(traderId)
            );
        }
    }

    // ── Mapping roundtrip ────────────────────────────────────────────────────

    @Nested
    @DisplayName("entity mapping roundtrip")
    class MappingRoundtrip {

        @Test
        void assigned_order_persists_and_restores_assignment() {
            TraderId traderId = new TraderId("trader-bob");
            Instant now = Instant.parse("2026-05-01T12:00:00Z");

            MoneyMarketOrder order = repository.save(createTermOrder("ROUNDTRIP-001"));
            order.assign(traderId, now);
            MoneyMarketOrder updated = repository.save(order);

            MoneyMarketOrder reloaded = repository.findById(updated.getId()).orElseThrow();

            assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(reloaded.getAssignment()).isNotNull();
            assertThat(reloaded.getAssignment().traderId()).isEqualTo(traderId);
        }

        @Test
        void findByExternalOrderReference_returns_order() {
            repository.save(createTermOrder("EXTREF-001"));

            Optional<MoneyMarketOrder> found =
                    repository.findByExternalOrderReference(new ExternalOrderReference("EXTREF-001"));

            assertThat(found).isPresent();
            assertThat(found.get().getExternalOrderReference())
                    .isEqualTo(new ExternalOrderReference("EXTREF-001"));
        }
    }
}
