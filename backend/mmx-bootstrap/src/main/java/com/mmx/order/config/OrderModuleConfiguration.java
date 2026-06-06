package com.mmx.order.config;

import com.mmx.order.adapter.out.integration.SystemClock;
import com.mmx.order.adapter.out.integration.UuidReferenceGenerator;
import com.mmx.order.adapter.out.persistence.JpaAuditLogger;
import com.mmx.order.adapter.out.persistence.JpaOrderRepository;
import com.mmx.order.adapter.out.persistence.mapper.OrderPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataAuditLogRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOrderRepository;
import com.mmx.order.application.port.in.CancelOrderUseCase;
import com.mmx.order.application.port.in.DeskOrderQueries;
import com.mmx.order.application.port.in.MarkOrderAccountedUseCase;
import com.mmx.order.application.port.in.ReceiveOrderUseCase;
import com.mmx.order.application.port.in.RejectOrderUseCase;
import com.mmx.order.application.port.in.UpdateAssignedOrderUseCase;
import com.mmx.order.application.port.out.*;
import com.mmx.order.application.service.AssignmentService;
import com.mmx.order.application.service.ExecuteOrderService;
import com.mmx.order.application.service.MarkOrderAccountedService;
import com.mmx.order.application.service.OrderLifecycleService;
import com.mmx.order.application.service.DeskOrderQueryService;
import com.mmx.order.application.service.ReceiveOrderService;
import com.mmx.order.application.service.UpdateOrderService;
import com.mmx.order.domain.policy.OrderAgainstInstitutionPolicy;
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
    public ExecuteOrderService executeOrderService(
            OrderRepository orderRepository,
            InstitutionRepository institutionRepository,
            OrderAgainstInstitutionPolicy orderAgainstInstitutionPolicy,
            ReferenceGenerator referenceGenerator,
            AuditLogger auditLogger,
            Clock clock,
            ExecutionHandoffOutbox executionHandoffOutbox) {
        return new ExecuteOrderService(
                orderRepository,
                institutionRepository,
                orderAgainstInstitutionPolicy,
                referenceGenerator,
                auditLogger,
                clock,
                executionHandoffOutbox);
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
    public ReceiveOrderUseCase receiveOrderUseCase(
            OrderRepository orderRepository,
            ManagedCurrencyRepository managedCurrencyRepository,
            InstitutionRepository institutionRepository,
            OpenPositionPort openPositionPort,
            AuditLogger auditLogger,
            Clock clock) {
        return new ReceiveOrderService(
                orderRepository,
                managedCurrencyRepository,
                institutionRepository,
                openPositionPort,
                auditLogger,
                clock);
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
            OrderRepository orderRepository, AuditLogger auditLogger, Clock clock) {
        return new OrderLifecycleService(orderRepository, auditLogger, clock);
    }

    @Bean
    public CancelOrderUseCase cancelOrderUseCase(OrderLifecycleService orderLifecycleService) {
        return orderLifecycleService;
    }

    @Bean
    public RejectOrderUseCase rejectOrderUseCase(OrderLifecycleService orderLifecycleService) {
        return orderLifecycleService;
    }
}
