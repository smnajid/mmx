package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.mapper.OrderPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOrderRepository;
import com.mmx.order.application.port.out.ExecutedSubscriptionContract;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
@Tag("integration")

@SpringBootTest
@ActiveProfiles("test")
class OrderRepositoryLiveContractsTest {

    private static final String PORTFOLIO = "PF-001";
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final LocalDate VALUE_DATE = TODAY.plusDays(2);
    private static final Instant EXECUTION_TIME = Instant.parse("2026-06-03T10:00:00Z");
    private static final TraderId TRADER = new TraderId("trader-a");

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
    void findExecutedSubscriptionsByPortfolioAndOrderType_returnsExecutedOnCallSubscriptions() {
        MoneyMarketOrder subscription = createOnCallSubscription("SUB-001");
        executeSubscription(subscription, new ContractNumber("CT-00042"));
        repository.save(subscription);

        List<ExecutedSubscriptionContract> results =
                repository.findExecutedSubscriptionsByPortfolioAndOrderType(PORTFOLIO, OrderType.ON_CALL);

        assertThat(results)
                .containsExactly(
                        new ExecutedSubscriptionContract(
                                "CT-00042",
                                OrderType.ON_CALL,
                                "EUR",
                                NoticePeriod._24H,
                                null,
                                VALUE_DATE,
                                new BigDecimal("2000000.00")));
    }

    @Test
    void findExecutedSubscriptionsByPortfolioAndOrderType_returnsExecutedTermSubscriptions() {
        MoneyMarketOrder subscription = createTermSubscription("SUB-TERM-001");
        executeSubscription(subscription, new ContractNumber("CT-00100"));
        repository.save(subscription);

        List<ExecutedSubscriptionContract> results =
                repository.findExecutedSubscriptionsByPortfolioAndOrderType(PORTFOLIO, OrderType.TERM);

        assertThat(results)
                .containsExactly(
                        new ExecutedSubscriptionContract(
                                "CT-00100",
                                OrderType.TERM,
                                "EUR",
                                null,
                                Tenor._3M,
                                VALUE_DATE,
                                new BigDecimal("5000000.00")));
    }

    @Test
    void findExecutedSubscriptionsByPortfolioAndOrderType_excludesNonExecutedSubscriptions() {
        repository.save(createOnCallSubscription("SUB-RECEIVED"));

        assertThat(repository.findExecutedSubscriptionsByPortfolioAndOrderType(PORTFOLIO, OrderType.ON_CALL))
                .isEmpty();
    }

    @Test
    void findExecutedSubscriptionsByPortfolioAndOrderType_excludesOtherPortfoliosAndOrderTypes() {
        MoneyMarketOrder subscription = createOnCallSubscription("SUB-OTHER-PF");
        executeSubscription(subscription, new ContractNumber("CT-OTHER"));
        repository.save(subscription);

        assertThat(repository.findExecutedSubscriptionsByPortfolioAndOrderType("PF-OTHER", OrderType.ON_CALL))
                .isEmpty();

        MoneyMarketOrder increase =
                MoneyMarketOrder.create(
                        new ExternalOrderReference("INC-001"),
                        new LegalEntityCode("LOC"),
                        OrderType.ON_CALL,
                        OrderOperation.INCREASE,
                        new PortfolioNumber(PORTFOLIO),
                        "EUR",
                        new BigDecimal("100000.00"),
                        VALUE_DATE,
                        null,
                        null,
                        NoticePeriod._24H,
                        new ContractNumber("CT-00042"),
                        "BNKCO",
                        "BankCo",
                        TODAY);
        increase.assign(TRADER, EXECUTION_TIME);
        increase.execute(
                new BigDecimal("2.85"),
                "BankCo",
                "BNKCO",
                new DealingReference("DL-INC"),
                new ContractNumber("CT-INC"),
                TRADER,
                EXECUTION_TIME);
        repository.save(increase);

        assertThat(repository.findExecutedSubscriptionsByPortfolioAndOrderType(PORTFOLIO, OrderType.ON_CALL))
                .containsExactly(
                        new ExecutedSubscriptionContract(
                                "CT-OTHER",
                                OrderType.ON_CALL,
                                "EUR",
                                NoticePeriod._24H,
                                null,
                                VALUE_DATE,
                                new BigDecimal("2000000.00")));
    }

    @Test
    void findContractNumbersWithNonCancelledRedemption_includesReceivedRedemption() {
        persistExecutedOnCallSubscription("SUB-LIVE", "CT-LIVE");
        repository.save(createOnCallRedemption("RED-RECEIVED", "CT-LIVE", OrderStatus.RECEIVED));

        Set<String> redeemed =
                repository.findContractNumbersWithNonCancelledRedemption(List.of("CT-LIVE", "CT-OTHER"));

        assertThat(redeemed).containsExactly("CT-LIVE");
    }

    @Test
    void findContractNumbersWithNonCancelledRedemption_includesExecutedRedemption() {
        persistExecutedOnCallSubscription("SUB-LIVE-2", "CT-LIVE-2");
        MoneyMarketOrder redemption = createOnCallRedemption("RED-EXEC", "CT-LIVE-2", OrderStatus.RECEIVED);
        redemption.assign(TRADER, EXECUTION_TIME);
        redemption.execute(
                new BigDecimal("2.85"),
                "BankCo",
                "BNKCO",
                new DealingReference("DL-RED"),
                new ContractNumber("CT-RED-GEN"),
                TRADER,
                EXECUTION_TIME);
        repository.save(redemption);

        Set<String> redeemed = repository.findContractNumbersWithNonCancelledRedemption(List.of("CT-LIVE-2"));

        assertThat(redeemed).containsExactly("CT-LIVE-2");
    }

    @Test
    void findContractNumbersWithNonCancelledRedemption_ignoresCancelledRedemption() {
        persistExecutedOnCallSubscription("SUB-STILL-LIVE", "CT-STILL-LIVE");
        MoneyMarketOrder redemption = createOnCallRedemption("RED-CANCEL", "CT-STILL-LIVE", OrderStatus.RECEIVED);
        redemption.cancel(EXECUTION_TIME);
        repository.save(redemption);

        Set<String> redeemed = repository.findContractNumbersWithNonCancelledRedemption(List.of("CT-STILL-LIVE"));

        assertThat(redeemed).isEmpty();
    }

    @Test
    void findContractNumbersWithNonCancelledRedemption_returnsEmptyForEmptyInput() {
        assertThat(repository.findContractNumbersWithNonCancelledRedemption(List.of())).isEmpty();
    }

    private void persistExecutedOnCallSubscription(String externalRef, String contractNumber) {
        MoneyMarketOrder subscription = createOnCallSubscription(externalRef);
        executeSubscription(subscription, new ContractNumber(contractNumber));
        repository.save(subscription);
    }

    private MoneyMarketOrder createOnCallSubscription(String externalRef) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference(externalRef),
                new LegalEntityCode("LOC"),
                OrderType.ON_CALL,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber(PORTFOLIO),
                "EUR",
                new BigDecimal("2000000.00"),
                VALUE_DATE,
                null,
                null,
                NoticePeriod._24H,
                null,
                "BNKCO",
                "BankCo",
                TODAY);
    }

    private MoneyMarketOrder createTermSubscription(String externalRef) {
        return MoneyMarketOrder.create(
                new ExternalOrderReference(externalRef),
                new LegalEntityCode("LOC"),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber(PORTFOLIO),
                "EUR",
                new BigDecimal("5000000.00"),
                VALUE_DATE,
                new BigDecimal("3.25000000"),
                Tenor._3M,
                null,
                null,
                "BNKCO",
                "BankCo",
                TODAY);
    }

    private MoneyMarketOrder createOnCallRedemption(String externalRef, String sourceContract, OrderStatus status) {
        MoneyMarketOrder redemption =
                MoneyMarketOrder.create(
                        new ExternalOrderReference(externalRef),
                        new LegalEntityCode("LOC"),
                        OrderType.ON_CALL,
                        OrderOperation.REDEMPTION,
                        new PortfolioNumber(PORTFOLIO),
                        "EUR",
                        new BigDecimal("500000.00"),
                        VALUE_DATE,
                        null,
                        null,
                        NoticePeriod._24H,
                        new ContractNumber(sourceContract),
                        "BNKCO",
                        "BankCo",
                        TODAY);
        if (status == OrderStatus.ASSIGNED) {
            redemption.assign(TRADER, EXECUTION_TIME);
        }
        return redemption;
    }

    private void executeSubscription(MoneyMarketOrder subscription, ContractNumber contractNumber) {
        BigDecimal executedRate =
                subscription.getMinimumRate() != null ? subscription.getMinimumRate() : new BigDecimal("2.85");
        subscription.assign(TRADER, EXECUTION_TIME);
        subscription.execute(
                executedRate,
                "BankCo",
                "BNKCO",
                new DealingReference("DL-" + subscription.getExternalOrderReference().value()),
                contractNumber,
                TRADER,
                EXECUTION_TIME);
    }
}
