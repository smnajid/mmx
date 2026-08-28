package com.mmx.order.config;

import com.mmx.order.adapter.out.integration.SystemClock;
import com.mmx.order.adapter.out.integration.UuidReferenceGenerator;
import com.mmx.order.adapter.out.persistence.JpaAuditLogger;
import com.mmx.order.adapter.out.persistence.JpaOrderRepository;
import com.mmx.order.adapter.out.persistence.mapper.OrderPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataAuditLogRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOrderRepository;
import com.mmx.order.application.port.in.DeskOrderQueries;
import com.mmx.order.application.port.in.MarkOrderAccountedUseCase;
import com.mmx.order.application.port.in.UpdateAssignedOrderUseCase;
import com.mmx.order.application.port.out.*;
import com.mmx.order.application.service.AssignmentService;
import com.mmx.order.application.service.ExecuteOrderService;
import com.mmx.order.application.service.IntakeService;
import com.mmx.order.application.service.MarkOrderAccountedService;
import com.mmx.order.application.service.OrderLifecycleService;
import com.mmx.order.application.service.DeskOrderQueryService;
import com.mmx.order.application.service.RemoteRoutedOrderIntake;
import com.mmx.order.application.service.RoutedOrderIntake;
import com.mmx.order.application.service.RoutedOrderOutcomePropagation;
import com.mmx.order.application.service.RoutedOrderOutcomePropagationService;
import com.mmx.order.application.service.UpdateOrderService;
import com.mmx.order.domain.model.OrganisationCode;
import com.mmx.order.domain.policy.OrderAgainstInstitutionPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderModuleConfiguration {

    @Bean
    public OrderRepository orderRepository(SpringDataOrderRepository springDataOrderRepository,
                                            OrderPersistenceMapper mapper) {
        return new JpaOrderRepository(springDataOrderRepository, mapper);
    }

    @Bean
    public AuditLogger auditLogger(SpringDataAuditLogRepository springDataAuditLogRepository) {
        return new JpaAuditLogger(springDataAuditLogRepository);
    }

    @Bean
    public ReferenceGenerator referenceGenerator() {
        return new UuidReferenceGenerator();
    }

    @Bean
    public RoutedOrderOutcomePropagation routedOrderOutcomePropagation(
            OrderRepository orderRepository, ReferenceGenerator referenceGenerator) {
        return new RoutedOrderOutcomePropagationService(orderRepository, referenceGenerator);
    }

    @Bean
    public ExecuteOrderService executeOrderService(
            OrderRepository orderRepository,
            InstitutionRepository institutionRepository,
            OrderAgainstInstitutionPolicy orderAgainstInstitutionPolicy,
            ReferenceGenerator referenceGenerator,
            AuditLogger auditLogger,
            Clock clock,
            ExecutionHandoffOutbox executionHandoffOutbox,
            RoutedOrderOutcomePropagation routedOrderOutcomePropagation) {
        return new ExecuteOrderService(
                orderRepository,
                institutionRepository,
                orderAgainstInstitutionPolicy,
                referenceGenerator,
                auditLogger,
                clock,
                executionHandoffOutbox,
                routedOrderOutcomePropagation);
    }

    /**
     * Application entry uses {@link TransactionalExecuteOrderUseCase} (component-scanned) so execute + outbox share one transaction.
     */

    @Bean
    public MarkOrderAccountedUseCase markOrderAccountedUseCase(
            OrderRepository orderRepository, AuditLogger auditLogger, Clock clock) {
        return new MarkOrderAccountedService(orderRepository, auditLogger, clock);
    }

    @Bean
    public Clock clock() {
        return new SystemClock();
    }

    @Bean
    public RoutedOrderIntake routedOrderIntake(
            ProxyInstitutionRepository proxyInstitutionRepository,
            DelegatedGrantDirectory delegatedGrantDirectory,
            GlobalAccountDirectory globalAccountDirectory,
            InstitutionRepository institutionRepository,
            OrderRepository orderRepository,
            Clock clock) {
        return new RoutedOrderIntake(
                proxyInstitutionRepository,
                delegatedGrantDirectory,
                globalAccountDirectory,
                institutionRepository,
                orderRepository,
                clock);
    }

    @Bean
    public IntakeService intakeService(
            OrderRepository orderRepository,
            ManagedCurrencyRepository managedCurrencyRepository,
            InstitutionRepository institutionRepository,
            OpenPositionPort openPositionPort,
            OrganisationRepository organisationRepository,
            LegalEntityRepository legalEntityRepository,
            OrganisationCode portfolioManagementOrganisation,
            RoutedOrderIntake routedOrderIntake,
            AuditLogger auditLogger,
            Clock clock,
            ObjectProvider<HubLocalityResolver> hubLocalityResolverProvider,
            ObjectProvider<RemoteRoutedOrderIntake> remoteRoutedOrderIntakeProvider) {
        return new IntakeService(
                orderRepository,
                managedCurrencyRepository,
                institutionRepository,
                openPositionPort,
                organisationRepository,
                legalEntityRepository,
                portfolioManagementOrganisation,
                routedOrderIntake,
                auditLogger,
                clock,
                hubLocalityResolverProvider.getIfAvailable(),
                remoteRoutedOrderIntakeProvider.getIfAvailable());
    }

    @Bean
    public DeskOrderQueries deskOrderQueries(OrderRepository orderRepository, Clock clock) {
        return new DeskOrderQueryService(orderRepository, clock);
    }

    @Bean
    public AssignmentService assignmentService(OrderRepository orderRepository, AuditLogger auditLogger, Clock clock) {
        return new AssignmentService(orderRepository, auditLogger, clock);
    }

    @Bean
    public UpdateAssignedOrderUseCase updateAssignedOrderUseCase(
            OrderRepository orderRepository,
            ManagedCurrencyRepository managedCurrencyRepository,
            OpenPositionPort openPositionPort,
            AuditLogger auditLogger,
            Clock clock) {
        return new UpdateOrderService(
                orderRepository, managedCurrencyRepository, openPositionPort, auditLogger, clock);
    }

    @Bean
    public OrderLifecycleService orderLifecycleService(
            OrderRepository orderRepository,
            AuditLogger auditLogger,
            Clock clock,
            RoutedOrderOutcomePropagation routedOrderOutcomePropagation) {
        return new OrderLifecycleService(orderRepository, auditLogger, clock, routedOrderOutcomePropagation);
    }

    /**
     * Application entry uses {@link TransactionalOrderLifecycleUseCase} (component-scanned) so cancel/reject +
     * client propagation share one transaction.
     */
}
