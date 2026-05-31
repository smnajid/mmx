package com.mmx.order.application.termrate;

import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import com.mmx.order.application.port.out.Clock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleTermRateCsvGeneratorTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final Clock CLOCK = () -> Instant.parse("2026-05-30T08:00:00Z");

    @Mock
    InstitutionRepository institutionRepository;

    @Mock
    ManagedCurrencyRepository currencyRepository;

    SampleTermRateCsvGenerator subject;

    @BeforeEach
    void setUp() {
        subject = new SampleTermRateCsvGenerator(institutionRepository, currencyRepository, CLOCK, ZONE);
    }

    @Test
    void generate_includesHeaderAndCatalogRows() {
        when(institutionRepository.findActive())
                .thenReturn(List.of(new Institution("HSBC-01", "HSBC", true)));
        when(currencyRepository.findAll())
                .thenReturn(
                        List.of(
                                new ManagedCurrency(
                                        "EUR",
                                        true,
                                        new BigDecimal("1000"),
                                        new BigDecimal("500"),
                                        EnumSet.of(Tenor._1M),
                                        EnumSet.noneOf(
                                                com.mmx.order.domain.model.NoticePeriod.class))));

        String csv = new String(subject.generate(), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("tradingDate,institutionCode,currency,tenor,rate");
        assertThat(csv).contains("2026-05-30,HSBC-01,EUR,1M,1.00000000");
    }

    @Test
    void generate_omitsInactiveInstitution() {
        when(institutionRepository.findActive()).thenReturn(List.of());

        String csv = new String(subject.generate(), StandardCharsets.UTF_8);

        assertThat(csv.lines().count()).isEqualTo(1);
    }
}
