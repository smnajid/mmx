package com.mmx.order.config;

import com.mmx.order.adapter.out.persistence.JpaInstitutionRepository;
import com.mmx.order.adapter.out.persistence.mapper.InstitutionPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataInstitutionRepository;
import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.service.ManageInstitutionSettingsService;
import com.mmx.order.domain.policy.OrderAgainstInstitutionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InstitutionSettingsModuleConfiguration {

    @Bean
    public InstitutionRepository institutionRepository(
            SpringDataInstitutionRepository springDataInstitutionRepository,
            InstitutionPersistenceMapper mapper) {
        return new JpaInstitutionRepository(springDataInstitutionRepository, mapper);
    }

    @Bean
    public ManageInstitutionSettingsUseCase manageInstitutionSettingsUseCase(InstitutionRepository repository) {
        return new ManageInstitutionSettingsService(repository);
    }

    @Bean
    public OrderAgainstInstitutionPolicy orderAgainstInstitutionPolicy() {
        return new OrderAgainstInstitutionPolicy();
    }
}
