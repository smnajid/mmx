package com.mmx.order.adapter.in.rest.mapper;

import com.mmx.order.adapter.in.rest.generated.model.ExecuteOrderRequest;
import com.mmx.order.adapter.in.rest.generated.model.ReceiveOrderRequest;
import com.mmx.order.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRestMapperTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");

    OrderRestMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OrderRestMapper();
    }

    @Test
    void toSummary_exposesTenorCodeNotEnumName() {
        MoneyMarketOrder order = receivedTerm();

        var summary = mapper.toSummary(order);

        assertThat(summary.getTenor()).isEqualTo("3M");
        assertThat(summary.getHandoffStatus()).isNull();
    }

    @Test
    void toSummary_includesHandoffForExecutedOnly() {
        MoneyMarketOrder order = executedWithHandoff();

        var summary = mapper.toSummary(order);

        assertThat(summary.getHandoffStatus()).isNotNull();
        assertThat(summary.getHandoffStatus().name()).isEqualTo("PENDING");
        assertThat(summary.getCounterparty()).isEqualTo("BankCo");
    }

    @Test
    void toDetails_mapsExecutionBlockWithoutHandoffField() {
        MoneyMarketOrder order = executedWithHandoff();

        var details = mapper.toDetails(order);

        assertThat(details.getExecutedRate()).isEqualTo(3.55);
        assertThat(details.getDealingReference()).isEqualTo("DL-1");
        assertThat(details.getAssignedTraderId()).isEqualTo("trader-a");
    }

    @Test
    void toDetails_mapsAssignedFields() {
        MoneyMarketOrder order = receivedTerm();
        order.assign(new TraderId("trader-a"), NOW);

        var details = mapper.toDetails(order);

        assertThat(details.getStatus().name()).isEqualTo("ASSIGNED");
        assertThat(details.getAssignedTraderId()).isEqualTo("trader-a");
        assertThat(details.getAssignedAt()).isNotNull();
    }

    @Test
    void toCommand_mapsReceiveRequestNoticePeriod() {
        var request = new ReceiveOrderRequest();
        request.setExternalOrderReference("PM-IN-1");
        request.setOrderType(com.mmx.order.adapter.in.rest.generated.model.OrderType.TERM);
        request.setOrderOperation(com.mmx.order.adapter.in.rest.generated.model.OrderOperation.SUBSCRIPTION);
        request.setPortfolioNumber("PF-1");
        request.setCurrency("EUR");
        request.setAmount(1_000_000.0);
        request.setValueDate(TODAY.plusDays(2));
        request.setNoticePeriod(com.mmx.order.adapter.in.rest.generated.model.NoticePeriod._24_H);

        var command = mapper.toCommand(request);

        assertThat(command.noticePeriod()).isEqualTo(NoticePeriod._24H);
    }

    @Test
    void toExecuteCommand_mapsFields() {
        var request = new ExecuteOrderRequest();
        request.setExecutedRate(3.5);
        UUID id = UUID.randomUUID();

        var command = mapper.toExecuteCommand(request, id, "trader-a");

        assertThat(command.orderId()).isEqualTo(id);
        assertThat(command.traderId()).isEqualTo(new TraderId("trader-a"));
        assertThat(command.executedRate()).isEqualByComparingTo(new BigDecimal("3.5"));
    }

    private static MoneyMarketOrder receivedTerm() {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("PM-T-1"),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-1"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(3),
                new BigDecimal("3.25000000"),
                Tenor._3M, null, null, "BNKCO", "BankCo",
                TODAY);
    }

    private static MoneyMarketOrder executedWithHandoff() {
        MoneyMarketOrder order = receivedTerm();
        order.assign(new TraderId("trader-a"), NOW);
        order.execute(
                new BigDecimal("3.55000000"),
                "BankCo",
                "HSBC-01",
                new DealingReference("DL-1"),
                new ContractNumber("CN-1"),
                new TraderId("trader-a"),
                NOW);
        order.markHandoffPending();
        return order;
    }
}
