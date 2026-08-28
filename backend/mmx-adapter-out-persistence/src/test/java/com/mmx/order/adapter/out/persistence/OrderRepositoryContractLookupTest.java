package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.mapper.OrderPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOrderRepository;
import com.mmx.order.application.port.out.ExecutedSubscriptionContractInfo;
import com.mmx.order.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
@Tag("integration")

@SpringBootTest
@ActiveProfiles("test")
class OrderRepositoryContractLookupTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final LocalDate VALUE_DATE = TODAY.plusDays(2);
    private static final Instant EXECUTION_TIME = Instant.parse("2026-06-03T10:00:00Z");

    @Autowired
    SpringDataOrderRepository springDataRepository;

    @Autowired
    OrderPersistenceMapper mapper;

    JpaOrderRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaOrderRepository(springDataRepository, mapper);
        springDataRepository.deleteAll();
    }

    @Test
    void findExecutedSubscriptionByContractNumber_returnsCurrencyAndNoticePeriod() {
        MoneyMarketOrder order = createOnCallSubscription("LOOKUP-001");
        order.assign(new TraderId("trader-a"), EXECUTION_TIME);
        order.execute(
                new BigDecimal("2.85"),
                "BankCo",
                "BNKCO",
                new DealingReference("DL-1"),
                new ContractNumber("CT-00042"),
                new TraderId("trader-a"),
                EXECUTION_TIME);
        repository.save(order);

        Optional<ExecutedSubscriptionContractInfo> info =
                repository.findExecutedSubscriptionByContractNumber("CT-00042");

        assertThat(info).isPresent();
        assertThat(info.get().currency()).isEqualTo("EUR");
        assertThat(info.get().noticePeriod()).isEqualTo(NoticePeriod._24H);
        assertThat(info.get().institutionCode()).isEqualTo("BNKCO");
        assertThat(info.get().counterparty()).isEqualTo("BankCo");
    }

    @Test
    void findExecutedSubscriptionByContractNumber_returnsEmptyForNonSubscription() {
        MoneyMarketOrder order =
                MoneyMarketOrder.create(
                        new ExternalOrderReference("LOOKUP-002"),
                        new LegalEntityCode("LOC"),
                        OrderType.ON_CALL,
                        OrderOperation.INCREASE,
                        new PortfolioNumber("PF-001"),
                        "EUR",
                        new BigDecimal("1000000.00"),
                        VALUE_DATE,
                        null,
                        null,
                        NoticePeriod._24H,
                        new ContractNumber("CT-SRC"), "BNKCO", "BankCo",
                        TODAY);
        order.assign(new TraderId("trader-a"), EXECUTION_TIME);
        order.execute(
                new BigDecimal("2.85"),
                "BankCo",
                "BNKCO",
                new DealingReference("DL-2"),
                new ContractNumber("CT-00043"),
                new TraderId("trader-a"),
                EXECUTION_TIME);
        repository.save(order);

        assertThat(repository.findExecutedSubscriptionByContractNumber("CT-00043")).isEmpty();
    }

    @Test
    void findExecutedSubscriptionByContractNumber_returnsEmptyForNonExecutedOrder() {
        MoneyMarketOrder order = createOnCallSubscription("LOOKUP-003");
        repository.save(order);

        assertThat(repository.findExecutedSubscriptionByContractNumber("CT-MISSING")).isEmpty();
    }

    private MoneyMarketOrder createOnCallSubscription(String externalRef) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference(externalRef),
                new LegalEntityCode("LOC"),
                OrderType.ON_CALL,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-001"),
                "EUR",
                new BigDecimal("2000000.00"),
                VALUE_DATE,
                null,
                null,
                NoticePeriod._24H, null, "BNKCO", "BankCo",
                TODAY);
    }
}
