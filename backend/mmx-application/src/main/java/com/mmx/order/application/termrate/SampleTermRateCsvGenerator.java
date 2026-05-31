package com.mmx.order.application.termrate;

import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.Tenor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import com.mmx.order.application.port.out.Clock;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class SampleTermRateCsvGenerator {

    private static final BigDecimal PLACEHOLDER_RATE = new BigDecimal("1.00000000");

    private final InstitutionRepository institutionRepository;
    private final ManagedCurrencyRepository currencyRepository;
    private final Clock clock;
    private final ZoneId zoneId;

    public SampleTermRateCsvGenerator(
            InstitutionRepository institutionRepository,
            ManagedCurrencyRepository currencyRepository,
            Clock clock,
            ZoneId zoneId) {
        this.institutionRepository = institutionRepository;
        this.currencyRepository = currencyRepository;
        this.clock = clock;
        this.zoneId = zoneId;
    }

    public byte[] generate() {
        LocalDate tradingDate = clock.now().atZone(zoneId).toLocalDate();
        StringBuilder csv = new StringBuilder();
        csv.append("tradingDate,institutionCode,currency,tenor,rate").append('\n');
        for (Institution institution : institutionRepository.findActive()) {
            for (ManagedCurrency currency : currencyRepository.findAll()) {
                if (!currency.isActive()) {
                    continue;
                }
                for (Tenor tenor : currency.getEnabledTenors()) {
                    csv.append(tradingDate)
                            .append(',')
                            .append(institution.getInstitutionCode())
                            .append(',')
                            .append(currency.getCode())
                            .append(',')
                            .append(tenor.getCode())
                            .append(',')
                            .append(PLACEHOLDER_RATE)
                            .append('\n');
                }
            }
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public List<String> headerColumns() {
        return List.of("tradingDate", "institutionCode", "currency", "tenor", "rate");
    }
}
