package com.mmx.order.config;

import com.mmx.order.adapter.out.persistence.JpaDelegatedGrantDirectory;
import com.mmx.order.adapter.out.persistence.JpaDelegatedGrantRepository;
import com.mmx.order.adapter.out.persistence.mapper.DelegatedGrantPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataDelegatedGrantRepository;
import com.mmx.order.application.port.in.ListInstitutionsUseCase;
import com.mmx.order.application.port.in.ManageDelegatedGrantsUseCase;
import com.mmx.order.application.port.out.DelegatedGrantDirectory;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.HubInstitutionCatalog;
import com.mmx.order.application.port.out.InstitutionExportOutbox;
import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase;
import com.mmx.order.application.port.out.HubScopeResolver;
import com.mmx.order.application.port.out.ReferenceDataMutationGuard;
import com.mmx.order.application.service.ListInstitutionsService;
import com.mmx.order.application.service.ManageDelegatedGrantsService;
import com.mmx.order.application.service.OnboardInstitutionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DelegatedGrantsModuleConfiguration {

    @Bean
    public DelegatedGrantRepository delegatedGrantRepository(
            SpringDataDelegatedGrantRepository springDataDelegatedGrantRepository,
            DelegatedGrantPersistenceMapper mapper) {
        return new JpaDelegatedGrantRepository(springDataDelegatedGrantRepository, mapper);
    }

    @Bean
    public DelegatedGrantDirectory delegatedGrantDirectory(
            SpringDataDelegatedGrantRepository springDataDelegatedGrantRepository,
            DelegatedGrantPersistenceMapper mapper) {
        return new JpaDelegatedGrantDirectory(springDataDelegatedGrantRepository, mapper);
    }

    @Bean
    public ManageDelegatedGrantsUseCase manageDelegatedGrantsUseCase(
            DelegatedGrantRepository grantRepository,
            InstitutionRepository institutionRepository,
            ManagedCurrencyRepository managedCurrencyRepository) {
        return new ManageDelegatedGrantsService(grantRepository, institutionRepository, managedCurrencyRepository);
    }

    /** Wrapped by {@link TransactionalOnboardInstitutionUseCase} so the export row commits with the change. */
    @Bean
    public OnboardInstitutionService onboardInstitutionService(
            InstitutionRepository institutionRepository,
            DelegatedGrantRepository grantRepository,
            LegalEntityRepository legalEntityRepository,
            HubInstitutionCatalog hubInstitutionCatalog,
            InstitutionExportOutbox institutionExportOutbox,
            ManageInstitutionSettingsUseCase nativeOnboard) {
        return new OnboardInstitutionService(
                institutionRepository,
                grantRepository,
                legalEntityRepository,
                hubInstitutionCatalog,
                institutionExportOutbox,
                nativeOnboard);
    }

    @Bean
    public ListInstitutionsUseCase listInstitutionsUseCase(InstitutionRepository institutionRepository) {
        return new ListInstitutionsService(institutionRepository);
    }

    @Bean
    public ReferenceDataMutationGuard referenceDataMutationGuard() {
        return new ReferenceDataMutationGuard();
    }

    @Bean
    public HubScopeResolver hubScopeResolver(LegalEntityRepository legalEntityRepository) {
        return new HubScopeResolver(legalEntityRepository);
    }
}
