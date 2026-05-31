package com.mmx.order.config;

import com.mmx.order.adapter.out.persistence.JpaTermRateRepository;
import com.mmx.order.adapter.out.persistence.mapper.TermRatePersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataTermRateRepository;
import com.mmx.order.application.port.in.ListTermRateTradingDaysUseCase;
import com.mmx.order.application.port.in.ListTermRatesForDayUseCase;
import com.mmx.order.application.port.in.UploadTermRatesUseCase;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.application.service.ListTermRateTradingDaysService;
import com.mmx.order.application.service.ListTermRatesForDayService;
import com.mmx.order.application.service.UploadTermRatesService;
import com.mmx.order.application.termrate.SampleTermRateCsvGenerator;
import com.mmx.order.application.termrate.TermRateCsvParser;
import com.mmx.order.domain.policy.TermRateIngestPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mmx.order.application.port.out.Clock;

import java.time.ZoneId;

@Configuration
public class TermRateSettingsModuleConfiguration {

    private static final ZoneId DESK_ZONE = ZoneId.of("Europe/Paris");

    @Bean
    public TermRateRepository termRateRepository(
            SpringDataTermRateRepository springDataTermRateRepository,
            TermRatePersistenceMapper mapper,
            PlatformTransactionManager transactionManager) {
        return new JpaTermRateRepository(
                springDataTermRateRepository, mapper, new TransactionTemplate(transactionManager));
    }

    @Bean
    public TermRatePersistenceMapper termRatePersistenceMapper() {
        return new TermRatePersistenceMapper();
    }

    @Bean
    public TermRateCsvParser termRateCsvParser() {
        return new TermRateCsvParser();
    }

    @Bean
    public TermRateIngestPolicy termRateIngestPolicy() {
        return new TermRateIngestPolicy();
    }

    @Bean
    public SampleTermRateCsvGenerator sampleTermRateCsvGenerator(
            com.mmx.order.application.port.out.InstitutionRepository institutionRepository,
            com.mmx.order.application.port.out.ManagedCurrencyRepository managedCurrencyRepository,
            Clock clock) {
        return new SampleTermRateCsvGenerator(
                institutionRepository, managedCurrencyRepository, clock, DESK_ZONE);
    }

    @Bean
    public UploadTermRatesUseCase uploadTermRatesUseCase(
            TermRateCsvParser termRateCsvParser,
            TermRateIngestPolicy termRateIngestPolicy,
            com.mmx.order.application.port.out.InstitutionRepository institutionRepository,
            com.mmx.order.application.port.out.ManagedCurrencyRepository managedCurrencyRepository,
            TermRateRepository termRateRepository,
            Clock clock) {
        return new UploadTermRatesService(
                termRateCsvParser,
                termRateIngestPolicy,
                institutionRepository,
                managedCurrencyRepository,
                termRateRepository,
                clock);
    }

    @Bean
    public ListTermRatesForDayUseCase listTermRatesForDayUseCase(TermRateRepository termRateRepository) {
        return new ListTermRatesForDayService(termRateRepository);
    }

    @Bean
    public ListTermRateTradingDaysUseCase listTermRateTradingDaysUseCase(TermRateRepository termRateRepository) {
        return new ListTermRateTradingDaysService(termRateRepository);
    }
}
