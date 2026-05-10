package com.mmx.order.config;

import com.mmx.order.adapter.out.integration.NoOpBackOfficeGateway;
import com.mmx.order.adapter.out.integration.SystemClock;
import com.mmx.order.adapter.out.integration.UuidReferenceGenerator;
import com.mmx.order.adapter.out.persistence.JpaAuditLogger;
import com.mmx.order.adapter.out.persistence.JpaOrderRepository;
import com.mmx.order.adapter.out.persistence.mapper.OrderPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataAuditLogRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOrderRepository;
import com.mmx.order.application.port.in.CancelOrderUseCase;
import com.mmx.order.application.port.in.ExecuteOrderUseCase;
import com.mmx.order.application.port.in.MarkOrderAccountedUseCase;
import com.mmx.order.application.port.in.ReceiveOrderUseCase;
import com.mmx.order.application.port.in.RejectOrderUseCase;
import com.mmx.order.application.port.in.UpdateAssignedOrderUseCase;
import com.mmx.order.application.port.out.*;
import com.mmx.order.application.service.AssignmentService;
import com.mmx.order.application.service.ExecuteOrderService;
import com.mmx.order.application.service.MarkOrderAccountedService;
import com.mmx.order.application.service.OrderLifecycleService;
import com.mmx.order.application.service.OrderQueryService;
import com.mmx.order.application.service.ReceiveOrderService;
import com.mmx.order.application.service.UpdateOrderService;
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
    public BackOfficeGateway backOfficeGateway() {
        return new NoOpBackOfficeGateway();
    }

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
    public ReceiveOrderUseCase receiveOrderUseCase(OrderRepository orderRepository, AuditLogger auditLogger, Clock clock) {
        return new ReceiveOrderService(orderRepository, auditLogger, clock);
    }

    @Bean
    public OrderQueryService orderQueryService(OrderRepository orderRepository, Clock clock) {
        return new OrderQueryService(orderRepository, clock);
    }

    @Bean
    public AssignmentService assignmentService(OrderRepository orderRepository, AuditLogger auditLogger, Clock clock) {
        return new AssignmentService(orderRepository, auditLogger, clock);
    }

    @Bean
    public ExecuteOrderUseCase executeOrderUseCase(
            OrderRepository orderRepository,
            ReferenceGenerator referenceGenerator,
            AuditLogger auditLogger,
            Clock clock) {
        return new ExecuteOrderService(orderRepository, referenceGenerator, auditLogger, clock);
    }

    @Bean
    public UpdateAssignedOrderUseCase updateAssignedOrderUseCase(
            OrderRepository orderRepository, AuditLogger auditLogger, Clock clock) {
        return new UpdateOrderService(orderRepository, auditLogger, clock);
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
