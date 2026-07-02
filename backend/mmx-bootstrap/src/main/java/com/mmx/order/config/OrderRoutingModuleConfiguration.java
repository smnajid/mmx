package com.mmx.order.config;

import com.mmx.order.adapter.out.persistence.JpaGlobalAccountDirectory;
import com.mmx.order.adapter.out.persistence.JpaGlobalAccountRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataGlobalAccountRepository;
import com.mmx.order.application.port.in.RouteOrderUseCase;
import com.mmx.order.application.port.out.GlobalAccountDirectory;
import com.mmx.order.application.service.RouteOrderService;
import com.mmx.order.domain.model.OrganisationCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderRoutingModuleConfiguration {

    @Bean
    public GlobalAccountDirectory globalAccountDirectory(
            SpringDataGlobalAccountRepository springDataGlobalAccountRepository) {
        return new JpaGlobalAccountDirectory(springDataGlobalAccountRepository);
    }

    @Bean
    public com.mmx.order.application.port.out.GlobalAccountRepository globalAccountRepository(
            SpringDataGlobalAccountRepository springDataGlobalAccountRepository) {
        return new JpaGlobalAccountRepository(springDataGlobalAccountRepository);
    }

    @Bean
    public com.mmx.order.application.port.in.ManageGlobalAccountsUseCase manageGlobalAccountsUseCase(
            com.mmx.order.application.port.out.GlobalAccountRepository globalAccountRepository,
            com.mmx.order.application.port.out.LegalEntityRepository legalEntityRepository,
            com.mmx.order.application.port.out.HubScopeResolver hubScopeResolver,
            com.mmx.order.application.port.out.ReferenceDataMutationGuard mutationGuard) {
        return new com.mmx.order.application.service.ManageGlobalAccountsService(
                globalAccountRepository, legalEntityRepository, hubScopeResolver, mutationGuard);
    }

    @Bean
    public RouteOrderService routeOrderService(
            com.mmx.order.application.port.out.OrderRepository orderRepository,
            com.mmx.order.application.port.out.ManagedCurrencyRepository managedCurrencyRepository,
            com.mmx.order.application.port.out.InstitutionRepository institutionRepository,
            com.mmx.order.application.port.out.ProxyInstitutionRepository proxyInstitutionRepository,
            com.mmx.order.application.port.out.OpenPositionPort openPositionPort,
            com.mmx.order.application.port.out.OrganisationRepository organisationRepository,
            com.mmx.order.application.port.out.LegalEntityRepository legalEntityRepository,
            com.mmx.order.application.port.out.DelegatedGrantDirectory delegatedGrantDirectory,
            GlobalAccountDirectory globalAccountDirectory,
            OrganisationCode portfolioManagementOrganisation,
            com.mmx.order.application.port.out.AuditLogger auditLogger,
            com.mmx.order.application.port.out.Clock clock) {
        return new RouteOrderService(
                orderRepository,
                managedCurrencyRepository,
                institutionRepository,
                proxyInstitutionRepository,
                openPositionPort,
                organisationRepository,
                legalEntityRepository,
                delegatedGrantDirectory,
                globalAccountDirectory,
                portfolioManagementOrganisation,
                auditLogger,
                clock);
    }
}
