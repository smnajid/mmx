package com.mmx.order.application.service;

import com.mmx.order.application.command.ExecuteOrderCommand;
import com.mmx.order.application.port.in.ExecuteOrderUseCase;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.ExecutionHandoffRoutingContext;
import com.mmx.order.application.port.out.ExecutionHandoffOutbox;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.ReferenceGenerator;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.policy.OrderAgainstInstitutionPolicy;

public final class ExecuteOrderService implements ExecuteOrderUseCase {

    static final String EVENT_ORDER_EXECUTED = "ORDER_EXECUTED";

    private final OrderRepository orderRepository;
    private final InstitutionRepository institutionRepository;
    private final OrderAgainstInstitutionPolicy institutionPolicy;
    private final ReferenceGenerator referenceGenerator;
    private final AuditLogger auditLogger;
    private final Clock clock;
    private final ExecutionHandoffOutbox executionHandoffOutbox;
    private final RoutedOrderOutcomePropagation routedOrderOutcomePropagation;

    public ExecuteOrderService(
            OrderRepository orderRepository,
            InstitutionRepository institutionRepository,
            OrderAgainstInstitutionPolicy institutionPolicy,
            ReferenceGenerator referenceGenerator,
            AuditLogger auditLogger,
            Clock clock,
            ExecutionHandoffOutbox executionHandoffOutbox,
            RoutedOrderOutcomePropagation routedOrderOutcomePropagation) {
        this.orderRepository = orderRepository;
        this.institutionRepository = institutionRepository;
        this.institutionPolicy = institutionPolicy;
        this.referenceGenerator = referenceGenerator;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.executionHandoffOutbox = executionHandoffOutbox;
        this.routedOrderOutcomePropagation = routedOrderOutcomePropagation;
    }

    @Override
    public MoneyMarketOrder execute(ExecuteOrderCommand command) {
        validate(command);
        institutionPolicy.validateCatalogNotEmpty(institutionRepository.existsAny());

        MoneyMarketOrder order =
                orderRepository.findById(command.orderId()).orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        String institutionCode = order.getInstitutionCode();
        var institutionOpt = institutionRepository.findByInstitutionCode(institutionCode);
        institutionPolicy.validateExecute(institutionCode, institutionOpt);
        Institution institution = institutionOpt.orElseThrow();

        var now = clock.now();
        ContractNumber contractNumber = resolveExecutionContractNumber(order);
        var dealingReference = referenceGenerator.generateDealingReference();
        String counterparty = institution.getDisplayName();

        order.execute(
                command.executedRate(),
                counterparty,
                institution.getInstitutionCode(),
                dealingReference,
                contractNumber,
                command.traderId(),
                now);

        order.markHandoffPending();
        MoneyMarketOrder saved = orderRepository.save(order);

        ExecutionHandoffRoutingContext handoffContext = ExecutionHandoffRoutingContext.none();
        if (saved.isHubSideRoutedLink()) {
            handoffContext = routedOrderOutcomePropagation.propagateExecution(saved).handoffContext();
        }

        if (!saved.suppressesExecutionHandoff()) {
            executionHandoffOutbox.schedule(saved, handoffContext);
        }
        auditLogger.log(saved.getId(), EVENT_ORDER_EXECUTED, command.traderId().value(), now);
        return saved;
    }

    private static void validate(ExecuteOrderCommand command) {
        if (command.executedRate() == null) {
            throw new InvalidOrderException("executedRate is required");
        }
    }

    private ContractNumber resolveExecutionContractNumber(MoneyMarketOrder order) {
        if (order.getOrderOperation() == OrderOperation.SUBSCRIPTION) {
            return referenceGenerator.generateContractNumber();
        }
        ContractNumber source = order.getSourceContractNumber();
        if (source == null) {
            throw new InvalidOrderException(
                    "sourceContractNumber is required for " + order.getOrderOperation() + " operations");
        }
        return source;
    }
}
