package com.mmx.order.config;

import com.mmx.order.adapter.out.integration.NoOpDepositsGateway;
import com.mmx.order.adapter.out.integration.SystemClock;
import com.mmx.order.adapter.out.integration.UuidReferenceGenerator;
import com.mmx.order.adapter.out.persistence.JpaAuditLogger;
import com.mmx.order.adapter.out.persistence.JpaOrderRepository;
import com.mmx.order.adapter.out.persistence.mapper.OrderPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataAuditLogRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOrderRepository;
import com.mmx.order.application.port.in.ReceiveOrderUseCase;
import com.mmx.order.application.port.out.*;
import com.mmx.order.application.service.ReceiveOrderService;
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
    public DepositsGateway depositsGateway() {
        return new NoOpDepositsGateway();
    }

    @Bean
    public Clock clock() {
        return new SystemClock();
    }

    @Bean
    public ReceiveOrderUseCase receiveOrderUseCase(OrderRepository orderRepository, AuditLogger auditLogger, Clock clock) {
        return new ReceiveOrderService(orderRepository, auditLogger, clock);
    }
}
