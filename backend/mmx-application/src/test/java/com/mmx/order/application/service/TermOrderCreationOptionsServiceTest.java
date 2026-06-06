package com.mmx.order.application.service;

import com.mmx.order.application.ordercreation.CounterpartiesResult;
import com.mmx.order.application.ordercreation.OperationsResult;
import com.mmx.order.application.ordercreation.OrderCreationCounterparty;
import com.mmx.order.application.ordercreation.OrderCreationOperation;
import com.mmx.order.application.ordercreation.TermCurrenciesResult;
import com.mmx.order.application.ordercreation.TenorsResult;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermOrderCreationOptionsServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 6);
    private static final Instant NOW = Instant.parse("2026-06-06T10:00:00Z");

    @Mock
    ManagedCurrencyRepository managedCurrencyRepository;

    @Mock
    TermRateRepository termRateRepository;

    @Mock
    InstitutionRepository institutionRepository;

    TermOrderCreationOptionsService subject;

    @BeforeEach
    void setUp() {
        Clock clock = () -> NOW;
        subject =
                new TermOrderCreationOptionsService(
                        managedCurrencyRepository, termRateRepository, institutionRepository, clock);
    }

    @Test
    void listCurrencies_filtersByActiveManagedCurrencyEnabledTenorsAndRateExistence() {
        ManagedCurrency eur =
                new ManagedCurrency(
                        "EUR",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.of(Tenor._1M, Tenor._3M),
                        EnumSet.noneOf(NoticePeriod.class));
        ManagedCurrency chf =
                new ManagedCurrency(
                        "CHF",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.of(Tenor._1M),
                        EnumSet.noneOf(NoticePeriod.class));
        ManagedCurrency jpy =
                new ManagedCurrency(
                        "JPY",
                        false,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.of(Tenor._1M),
                        EnumSet.noneOf(NoticePeriod.class));
        ManagedCurrency gbp =
                new ManagedCurrency(
                        "GBP",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.noneOf(Tenor.class),
                        EnumSet.of(NoticePeriod._24H));

        when(managedCurrencyRepository.findAll()).thenReturn(List.of(eur, chf, jpy, gbp));
        when(termRateRepository.findDistinctCurrenciesWithTermRates()).thenReturn(List.of("EUR"));

        TermCurrenciesResult result = subject.listCurrencies();

        assertThat(result.tradingDate()).isEqualTo(TODAY);
        assertThat(result.currencies()).containsExactly("EUR");
    }

    @Test
    void listTenors_filtersByEnabledTenorsAndRateExistence() {
        ManagedCurrency eur =
                new ManagedCurrency(
                        "EUR",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.of(Tenor._1M, Tenor._3M, Tenor._6M),
                        EnumSet.noneOf(NoticePeriod.class));
        when(managedCurrencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._1M))
                .thenReturn(List.of(rateRow("BNKCO", Tenor._1M, TODAY, "3.10")));
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._3M))
                .thenReturn(List.of(rateRow("BNKCO", Tenor._3M, TODAY, "3.20")));
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._6M)).thenReturn(List.of());

        TenorsResult result = subject.listTenors("EUR");

        assertThat(result.tenors()).containsExactly(Tenor._1M, Tenor._3M);
    }

    @Test
    void listCounterparties_returnsLatestRatesWithIndicativeFlagSortedByRateDesc() {
        LocalDate yesterday = TODAY.minusDays(1);
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._3M))
                .thenReturn(
                        List.of(
                                rateRow("BNKCO", Tenor._3M, TODAY, "3.45"),
                                rateRow("CDNRD", Tenor._3M, yesterday, "3.40")));
        when(institutionRepository.findByInstitutionCode("BNKCO"))
                .thenReturn(Optional.of(new Institution("BNKCO", "BankCo", true)));
        when(institutionRepository.findByInstitutionCode("CDNRD"))
                .thenReturn(Optional.of(new Institution("CDNRD", "Canada Rd", true)));

        CounterpartiesResult result = subject.listCounterparties("EUR", Tenor._3M);

        assertThat(result.counterparties())
                .extracting(OrderCreationCounterparty::institutionCode)
                .containsExactly("BNKCO", "CDNRD");
        assertThat(result.counterparties().get(0))
                .satisfies(
                        cp -> {
                            assertThat(cp.displayName()).isEqualTo("BankCo");
                            assertThat(cp.rate()).isEqualByComparingTo("3.45");
                            assertThat(cp.rateDate()).isEqualTo(TODAY);
                            assertThat(cp.indicative()).isFalse();
                        });
        assertThat(result.counterparties().get(1))
                .satisfies(
                        cp -> {
                            assertThat(cp.displayName()).isEqualTo("Canada Rd");
                            assertThat(cp.rate()).isEqualByComparingTo("3.40");
                            assertThat(cp.rateDate()).isEqualTo(yesterday);
                            assertThat(cp.indicative()).isTrue();
                        });
    }

    @Test
    void listOperations_returnsSubscriptionWithMinSubscriptionAmount() {
        ManagedCurrency eur =
                new ManagedCurrency(
                        "EUR",
                        true,
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        EnumSet.of(Tenor._3M),
                        EnumSet.noneOf(NoticePeriod.class));
        when(managedCurrencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));

        OperationsResult result = subject.listOperations("EUR");

        assertThat(result.operations())
                .containsExactly(new OrderCreationOperation(OrderOperation.SUBSCRIPTION, new BigDecimal("500000")));
    }

    private static TermRateAuditRow rateRow(
            String institutionCode, Tenor tenor, LocalDate tradingDate, String rate) {
        return new TermRateAuditRow(
                tradingDate,
                institutionCode,
                "EUR",
                tenor,
                new BigDecimal(rate),
                NOW,
                "trader-1");
    }
}
