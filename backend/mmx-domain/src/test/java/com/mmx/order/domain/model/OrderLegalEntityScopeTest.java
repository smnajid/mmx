package com.mmx.order.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MoneyMarketOrder LegalEntity scoping")
class OrderLegalEntityScopeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");
    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");

    @Test
    void order_carriesNonNullOwningLegalEntityCode() {
        MoneyMarketOrder order = createOrder(PAR);

        assertThat(order.getLegalEntityCode()).isEqualTo(PAR);
    }

    @Test
    void create_rejectsNullLegalEntityCode() {
        assertThatThrownBy(() -> MoneyMarketOrder.create(
                        new ExternalOrderReference("PM-001"),
                        null,
                        OrderType.TERM,
                        OrderOperation.SUBSCRIPTION,
                        new PortfolioNumber("PF-001"),
                        "EUR",
                        new BigDecimal("1000000.00"),
                        TODAY.plusDays(2),
                        new BigDecimal("3.00000000"),
                        Tenor._1M,
                        null,
                        null,
                        "BNKCO",
                        "BankCo",
                        TODAY))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void reconstitutedOrders_preserveLegalEntityCode() {
        UUID id = UUID.randomUUID();
        MoneyMarketOrder order = MoneyMarketOrder.reconstitute(
                id,
                new ExternalOrderReference("PM-001"),
                PAR,
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-001"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(2),
                new BigDecimal("3.00000000"),
                Tenor._1M,
                null,
                null,
                "BNKCO",
                "BankCo",
                OrderStatus.RECEIVED,
                null,
                null,
                null,
                null,
                java.time.Instant.parse("2026-05-01T10:00:00Z"),
                java.time.Instant.parse("2026-05-01T10:00:00Z"));

        assertThat(order.getLegalEntityCode()).isEqualTo(PAR);
    }

    @Test
    void ordersWithDifferentLegalEntityCodes_areDistinct() {
        MoneyMarketOrder parOrder = createOrder(PAR);
        MoneyMarketOrder locOrder = createOrder(LOC);

        assertThat(parOrder.getLegalEntityCode()).isNotEqualTo(locOrder.getLegalEntityCode());
    }

    private static MoneyMarketOrder createOrder(LegalEntityCode legalEntityCode) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("PM-001"),
                legalEntityCode,
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-001"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(2),
                new BigDecimal("3.00000000"),
                Tenor._1M,
                null,
                null,
                "BNKCO",
                "BankCo",
                TODAY);
    }
}
