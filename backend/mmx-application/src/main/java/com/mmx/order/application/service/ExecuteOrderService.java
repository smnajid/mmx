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
import com.mmx.order.application.port.out.RoutedPairLocalityResolver;
import com.mmx.order.application.port.out.RoutingOutcomeOutbox;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.HubLocality;
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
    private final RoutingOutcomeOutbox routingOutcomeOutbox;
    private final RoutedPairLocalityResolver routedPairLocalityResolver;

    public ExecuteOrderService(
            OrderRepository orderRepository,
            InstitutionRepository institutionRepository,
            OrderAgainstInstitutionPolicy institutionPolicy,
            ReferenceGenerator referenceGenerator,
            AuditLogger auditLogger,
            Clock clock,
            ExecutionHandoffOutbox executionHandoffOutbox,
            RoutedOrderOutcomePropagation routedOrderOutcomePropagation,
            RoutingOutcomeOutbox routingOutcomeOutbox,
            RoutedPairLocalityResolver routedPairLocalityResolver) {
        this.orderRepository = orderRepository;
        this.institutionRepository = institutionRepository;
        this.institutionPolicy = institutionPolicy;
        this.referenceGenerator = referenceGenerator;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.executionHandoffOutbox = executionHandoffOutbox;
        this.routedOrderOutcomePropagation = routedOrderOutcomePropagation;
        this.routingOutcomeOutbox = routingOutcomeOutbox;
        this.routedPairLocalityResolver = routedPairLocalityResolver;
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
            if (isRemotePair(saved)) {
                // Cross-deployment pair (e.g. CGD@CGEG → LOC@LODH): there is no in-process client-side
                // order to propagate to. The leg-B EXECUTED outcome is committed in this transaction
                // and the client deployment applies it (silence is never terminal). The back-office
                // event below still carries the cross-boundary correlation (routingId + originating
                // LegalEntityCode); client-side fields are unknowable at the hub and omitted.
                routingOutcomeOutbox.scheduleExecuted(saved, now);
                handoffContext =
                        new ExecutionHandoffRoutingContext(
                                saved.getRoutingId(), saved.getOriginatingLegalEntityCode(), null, null, null);
            } else {
                handoffContext = routedOrderOutcomePropagation.propagateExecution(saved).handoffContext();
            }
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

    private boolean isRemotePair(MoneyMarketOrder hubOrder) {
        return routedPairLocalityResolver.resolve(hubOrder.getOriginatingLegalEntityCode())
                == HubLocality.REMOTE;
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
