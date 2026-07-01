package com.mmx.order.config;

import com.mmx.order.adapter.out.integration.InMemoryActiveScopeStore;
import com.mmx.order.adapter.out.persistence.JpaLegalEntityRepository;
import com.mmx.order.adapter.out.persistence.JpaMmxUserRepository;
import com.mmx.order.adapter.out.persistence.JpaOrganisationRepository;
import com.mmx.order.adapter.out.persistence.mapper.MmxUserPersistenceMapper;
import com.mmx.order.adapter.out.persistence.mapper.TenancyPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataLegalEntityRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataMmxUserRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOrganisationRepository;
import com.mmx.order.application.port.in.ReScopeUseCase;
import com.mmx.order.application.port.in.ResolveUserScopeUseCase;
import com.mmx.order.application.port.out.ActiveScopeStore;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.MmxUserRepository;
import com.mmx.order.application.port.out.OrganisationRepository;
import com.mmx.order.application.service.ReScopeService;
import com.mmx.order.application.service.ResolveUserScopeService;
import com.mmx.order.domain.model.OrganisationCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OrganisationProperties.class)
public class TenancyModuleConfiguration {

    @Bean
    public OrganisationCode portfolioManagementOrganisation(OrganisationProperties properties) {
        return new OrganisationCode(properties.code());
    }

    @Bean
    public OrganisationRepository organisationRepository(
            SpringDataOrganisationRepository springDataOrganisationRepository,
            TenancyPersistenceMapper tenancyPersistenceMapper) {
        return new JpaOrganisationRepository(springDataOrganisationRepository, tenancyPersistenceMapper);
    }

    @Bean
    public LegalEntityRepository legalEntityRepository(
            SpringDataLegalEntityRepository springDataLegalEntityRepository,
            TenancyPersistenceMapper tenancyPersistenceMapper) {
        return new JpaLegalEntityRepository(springDataLegalEntityRepository, tenancyPersistenceMapper);
    }

    @Bean
    public MmxUserRepository mmxUserRepository(
            SpringDataMmxUserRepository springDataMmxUserRepository, MmxUserPersistenceMapper mmxUserPersistenceMapper) {
        return new JpaMmxUserRepository(springDataMmxUserRepository, mmxUserPersistenceMapper);
    }

    @Bean
    public ActiveScopeStore activeScopeStore() {
        return new InMemoryActiveScopeStore();
    }

    @Bean
    public ResolveUserScopeUseCase resolveUserScopeUseCase(
            MmxUserRepository mmxUserRepository, ActiveScopeStore activeScopeStore) {
        return new ResolveUserScopeService(mmxUserRepository, activeScopeStore);
    }

    @Bean
    public ReScopeUseCase reScopeUseCase(MmxUserRepository mmxUserRepository, ActiveScopeStore activeScopeStore) {
        return new ReScopeService(mmxUserRepository, activeScopeStore);
    }
}
