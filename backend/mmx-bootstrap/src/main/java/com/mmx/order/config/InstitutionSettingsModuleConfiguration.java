package com.mmx.order.config;

import com.mmx.order.adapter.out.messaging.InstitutionExportOutboxAdapter;
import com.mmx.order.adapter.out.messaging.InstitutionUpdatedV1PayloadMapper;
import com.mmx.order.adapter.out.messaging.repository.SpringDataInstitutionExportOutboxRepository;
import com.mmx.order.adapter.out.persistence.JpaClientEnablementRepository;
import com.mmx.order.adapter.out.persistence.JpaInstitutionRepository;
import com.mmx.order.adapter.out.persistence.mapper.InstitutionPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataClientEnablementRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataInstitutionRepository;
import com.mmx.order.application.port.in.ListGrantedInstitutionsUseCase;
import com.mmx.order.application.port.out.ClientEnablementRepository;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.HubInstitutionCatalog;
import com.mmx.order.application.port.out.InstitutionExportOutbox;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ScopeContextProvider;
import com.mmx.order.application.service.ListGrantedInstitutionsService;
import com.mmx.order.application.service.ManageClientEnablementService;
import com.mmx.order.application.service.ManageInstitutionSettingsService;
import com.mmx.order.application.service.UpdateCounterpartyAccountsService;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.policy.OrderAgainstInstitutionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

/**
 * Institution settings: the institution and client-enablement repositories (local JPA in every deployment,
 * including a remote client, which stores its own onboarded institutions), the institution export outbox,
 * and the settings services. Mutating services are wrapped in a transaction by the {@code Transactional*}
 * use cases so each change commits with its {@code institution_export_outbox} row.
 */
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
    public ClientEnablementRepository clientEnablementRepository(
            SpringDataClientEnablementRepository springDataClientEnablementRepository) {
        return new JpaClientEnablementRepository(springDataClientEnablementRepository);
    }

    @Bean
    public InstitutionExportOutbox institutionExportOutbox(
            SpringDataInstitutionExportOutboxRepository repository, InstitutionUpdatedV1PayloadMapper mapper) {
        return new InstitutionExportOutboxAdapter(repository, mapper);
    }

    /**
     * In-process hub catalog for a same-Organisation deployment: the hub's native institutions stored here.
     * A remote client overrides it with the live remote catalog ({@code CrossOrgRoutingModuleConfiguration}).
     */
    @Bean
    public HubInstitutionCatalog hubInstitutionCatalog(InstitutionRepository repository) {
        return new HubInstitutionCatalog() {
            @Override
            public List<Institution> findAll() {
                return repository.findAll().stream().filter(i -> !i.isOnboarded()).toList();
            }

            @Override
            public Optional<Institution> findByInstitutionCode(String hubInstitutionCode) {
                return repository.findByInstitutionCode(hubInstitutionCode).filter(i -> !i.isOnboarded());
            }
        };
    }

    @Bean
    public ManageInstitutionSettingsService manageInstitutionSettingsService(
            InstitutionRepository repository,
            DelegatedGrantRepository grantRepository,
            InstitutionExportOutbox institutionExportOutbox) {
        return new ManageInstitutionSettingsService(repository, grantRepository, institutionExportOutbox);
    }

    @Bean
    public UpdateCounterpartyAccountsService updateCounterpartyAccountsService(
            InstitutionRepository repository,
            ClientEnablementRepository clientEnablementRepository,
            InstitutionExportOutbox institutionExportOutbox) {
        return new UpdateCounterpartyAccountsService(repository, clientEnablementRepository, institutionExportOutbox);
    }

    @Bean
    public ManageClientEnablementService manageClientEnablementService(
            InstitutionRepository repository,
            DelegatedGrantRepository grantRepository,
            ClientEnablementRepository clientEnablementRepository) {
        return new ManageClientEnablementService(repository, grantRepository, clientEnablementRepository);
    }

    @Bean
    public ListGrantedInstitutionsUseCase listGrantedInstitutionsUseCase(
            DelegatedGrantRepository grantRepository,
            HubInstitutionCatalog hubInstitutionCatalog,
            InstitutionRepository institutionRepository,
            LegalEntityRepository legalEntityRepository) {
        return new ListGrantedInstitutionsService(
                grantRepository, hubInstitutionCatalog, institutionRepository, legalEntityRepository);
    }

    @Bean
    public OrderAgainstInstitutionPolicy orderAgainstInstitutionPolicy() {
        return new OrderAgainstInstitutionPolicy();
    }
}
