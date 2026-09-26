package com.mmx.order.config;

import com.mmx.order.adapter.out.persistence.JpaInstitutionRepository;
import com.mmx.order.adapter.out.persistence.mapper.InstitutionPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataInstitutionRepository;
import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.HubInstitutionCatalog;
import com.mmx.order.application.port.out.InstitutionExportOutbox;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ScopeContextProvider;
import com.mmx.order.application.service.ManageInstitutionSettingsService;
import com.mmx.order.domain.policy.OrderAgainstInstitutionPolicy;
import com.mmx.order.domain.model.Institution;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InstitutionSettingsModuleConfiguration {

    @Bean
    public InstitutionRepository institutionRepository(
            SpringDataInstitutionRepository springDataInstitutionRepository,
            InstitutionPersistenceMapper mapper,
            ScopeContextProvider scopeContextProvider) {
        return new JpaInstitutionRepository(springDataInstitutionRepository, mapper, scopeContextProvider);
    }

    @Bean
    public ManageInstitutionSettingsUseCase manageInstitutionSettingsUseCase(
            InstitutionRepository repository,
            DelegatedGrantRepository grantRepository,
            InstitutionExportOutbox institutionExportOutbox) {
        return new ManageInstitutionSettingsService(repository, grantRepository, institutionExportOutbox);
    }

    // TODO(client-institution-onboarding 6.2/8.1): replace with InstitutionExportOutboxAdapter
    // (institution_export_outbox rows + relay). Until then no export row is recorded.
    @Bean
    public InstitutionExportOutbox institutionExportOutbox() {
        return (institution, reason) -> {};
    }

    // TODO(client-institution-onboarding 8.1): remote-backed on CGEG over /cross-org/reference/institutions,
    // with the client's InstitutionRepository switched to local JPA (design D7).
    @Bean
    public HubInstitutionCatalog hubInstitutionCatalog(InstitutionRepository repository) {
        return new HubInstitutionCatalog() {
            @Override
            public java.util.List<Institution> findAll() {
                return repository.findAll().stream().filter(i -> !i.isOnboarded()).toList();
            }

            @Override
            public java.util.Optional<Institution> findByInstitutionCode(String hubInstitutionCode) {
                return repository.findByInstitutionCode(hubInstitutionCode).filter(i -> !i.isOnboarded());
            }
        };
    }

    @Bean
    public OrderAgainstInstitutionPolicy orderAgainstInstitutionPolicy() {
        return new OrderAgainstInstitutionPolicy();
    }
}
