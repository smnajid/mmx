package com.mmx.order.adapter.out.persistence.mapper;

import com.mmx.order.adapter.out.persistence.entity.OrderEntity;
import com.mmx.order.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPersistenceMapperTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");

    OrderPersistenceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OrderPersistenceMapper();
    }

    @Test
    void receivedTerm_roundTrip_preservesTenorAsEnumName() {
        MoneyMarketOrder source = receivedTerm("PM-REC-1");

        OrderEntity entity = mapper.toEntity(source);
        assertThat(entity.getTenor()).isEqualTo(Tenor._3M.name());

        MoneyMarketOrder restored = mapper.toDomain(entity);
        assertThat(restored.getTenor()).isEqualTo(Tenor._3M);
        assertThat(restored.getStatus()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(restored.getAmount()).isEqualByComparingTo(source.getAmount());
    }

    @Test
    void assigned_roundTrip_preservesAssignment() {
        MoneyMarketOrder source = receivedTerm("PM-ASGN-1");
        source.assign(new TraderId("trader-a"), NOW);

        MoneyMarketOrder restored = mapper.toDomain(mapper.toEntity(source));

        assertThat(restored.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(restored.getAssignment().traderId()).isEqualTo(new TraderId("trader-a"));
        assertThat(restored.getAssignment().assignedAt()).isEqualTo(NOW);
    }

    @Test
    void executedWithHandoff_roundTrip_preservesExecutionAndHandoff() {
        MoneyMarketOrder source = receivedTerm("PM-EX-1");
        source.assign(new TraderId("trader-a"), NOW);
        source.execute(
                new BigDecimal("3.55000000"),
                "BankCo",
                new DealingReference("DL-1"),
                new ContractNumber("CN-1"),
                new TraderId("trader-a"),
                NOW);
        source.markHandoffPending();

        OrderEntity entity = mapper.toEntity(source);
        assertThat(entity.getHandoffStatus()).isEqualTo(HandoffStatus.PENDING.name());
        assertThat(entity.getDealingReference()).isEqualTo("DL-1");

        MoneyMarketOrder restored = mapper.toDomain(entity);
        assertThat(restored.getStatus()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(restored.getHandoffStatus()).isEqualTo(HandoffStatus.PENDING);
        assertThat(restored.getExecutionDetails().counterparty()).isEqualTo("BankCo");
    }

    @Test
    void onCallReceived_roundTrip_preservesNoticePeriodAsEnumName() {
        MoneyMarketOrder source = receivedOnCall("PM-OC-1");

        OrderEntity entity = mapper.toEntity(source);
        assertThat(entity.getNoticePeriod()).isEqualTo(NoticePeriod._24H.name());

        assertThat(mapper.toDomain(entity).getNoticePeriod()).isEqualTo(NoticePeriod._24H);
    }

    private static MoneyMarketOrder receivedTerm(String extRef) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference(extRef),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-001"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(3),
                new BigDecimal("3.25000000"),
                Tenor._3M,
                null,
                null,
                null,
                TODAY);
    }

    private static MoneyMarketOrder receivedOnCall(String extRef) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference(extRef),
                OrderType.ON_CALL,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-002"),
                "CHF",
                new BigDecimal("500000.00"),
                TODAY.plusDays(3),
                new BigDecimal("2.50000000"),
                null,
                NoticePeriod._24H,
                null,
                null,
                TODAY);
    }
}
