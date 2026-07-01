package com.mmx.order.config;

import com.mmx.order.adapter.out.persistence.JpaDelegatedGrantDirectory;
import com.mmx.order.adapter.out.persistence.JpaDelegatedGrantRepository;
import com.mmx.order.adapter.out.persistence.JpaProxyInstitutionRepository;
import com.mmx.order.adapter.out.persistence.mapper.DelegatedGrantPersistenceMapper;
import com.mmx.order.adapter.out.persistence.mapper.ProxyInstitutionPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataDelegatedGrantRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataInstitutionRepository;
import com.mmx.order.application.port.in.ListInstitutionsUseCase;
import com.mmx.order.application.port.in.ManageDelegatedGrantsUseCase;
import com.mmx.order.application.port.in.OnboardInstitutionUseCase;
import com.mmx.order.application.port.out.DelegatedGrantDirectory;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.ProxyInstitutionRepository;
import com.mmx.order.application.port.out.ScopeContextProvider;
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
    public ProxyInstitutionRepository proxyInstitutionRepository(
            SpringDataInstitutionRepository springDataInstitutionRepository,
            ProxyInstitutionPersistenceMapper mapper,
            ScopeContextProvider scopeContextProvider) {
        return new JpaProxyInstitutionRepository(springDataInstitutionRepository, mapper, scopeContextProvider);
    }

    @Bean
    public ManageDelegatedGrantsUseCase manageDelegatedGrantsUseCase(
            DelegatedGrantRepository grantRepository,
            InstitutionRepository institutionRepository,
            ManagedCurrencyRepository managedCurrencyRepository) {
        return new ManageDelegatedGrantsService(grantRepository, institutionRepository, managedCurrencyRepository);
    }

    @Bean
    public OnboardInstitutionUseCase onboardInstitutionUseCase(
            InstitutionRepository institutionRepository,
            ProxyInstitutionRepository proxyRepository,
            DelegatedGrantRepository grantRepository,
            LegalEntityRepository legalEntityRepository,
            ManageInstitutionSettingsUseCase nativeOnboard) {
        return new OnboardInstitutionService(
                institutionRepository, proxyRepository, grantRepository, legalEntityRepository, nativeOnboard);
    }

    @Bean
    public ListInstitutionsUseCase listInstitutionsUseCase(
            InstitutionRepository institutionRepository, ProxyInstitutionRepository proxyRepository) {
        return new ListInstitutionsService(institutionRepository, proxyRepository);
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
