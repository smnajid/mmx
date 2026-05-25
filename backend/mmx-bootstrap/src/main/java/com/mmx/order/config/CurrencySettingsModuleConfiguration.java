package com.mmx.order.config;

import com.mmx.order.adapter.out.integration.InMemoryOpenPositionPort;
import com.mmx.order.adapter.out.integration.PositionApiOpenPositionAdapter;
import com.mmx.order.adapter.out.persistence.JpaManagedCurrencyRepository;
import com.mmx.order.adapter.out.persistence.mapper.ManagedCurrencyPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataManagedCurrencyRepository;
import com.mmx.order.application.port.in.ManageCurrencySettingsUseCase;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OpenPositionPort;
import com.mmx.order.application.service.ManageCurrencySettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CurrencySettingsModuleConfiguration {

    @Bean
    public ManagedCurrencyRepository managedCurrencyRepository(
            SpringDataManagedCurrencyRepository springDataManagedCurrencyRepository,
            ManagedCurrencyPersistenceMapper mapper) {
        return new JpaManagedCurrencyRepository(springDataManagedCurrencyRepository, mapper);
    }

    @Bean
    public InMemoryOpenPositionPort inMemoryOpenPositionPort() {
        return new InMemoryOpenPositionPort();
    }

    @Bean
    @ConditionalOnProperty(name = "mmx.position-api.enabled", havingValue = "true")
    public OpenPositionPort positionApiOpenPositionAdapter(
            @Value("${mmx.position-api.base-url:http://localhost:8090}") String baseUrl) {
        return new PositionApiOpenPositionAdapter(baseUrl);
    }

    @Bean
    @ConditionalOnMissingBean(OpenPositionPort.class)
    public OpenPositionPort inMemoryOpenPositionPortAdapter(InMemoryOpenPositionPort inMemoryOpenPositionPort) {
        return inMemoryOpenPositionPort;
    }

    @Bean
    public ManageCurrencySettingsUseCase manageCurrencySettingsUseCase(ManagedCurrencyRepository repository) {
        return new ManageCurrencySettingsService(repository);
    }
}
