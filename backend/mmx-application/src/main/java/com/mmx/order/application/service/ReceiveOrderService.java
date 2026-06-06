package com.mmx.order.application.service;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.port.in.ReceiveOrderUseCase;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OpenPositionPort;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OpenContractPosition;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.policy.OrderAgainstCurrencyPolicy;
import com.mmx.order.domain.policy.OrderAgainstInstitutionPolicy;

import java.util.Optional;

public final class ReceiveOrderService implements ReceiveOrderUseCase {

    static final String AUDIT_ACTOR_SYSTEM = "PORTFOLIO_MANAGEMENT";
    static final String EVENT_ORDER_RECEIVED = "ORDER_RECEIVED";
    static final String EVENT_DUPLICATE_RECEIVE_IGNORED = "DUPLICATE_RECEIVE_IGNORED";

    private final OrderRepository orderRepository;
    private final ManagedCurrencyRepository managedCurrencyRepository;
    private final InstitutionRepository institutionRepository;
    private final OpenPositionPort openPositionPort;
    private final OrderAgainstCurrencyPolicy currencyPolicy;
    private final OrderAgainstInstitutionPolicy institutionPolicy;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public ReceiveOrderService(
            OrderRepository orderRepository,
            ManagedCurrencyRepository managedCurrencyRepository,
            InstitutionRepository institutionRepository,
            OpenPositionPort openPositionPort,
            AuditLogger auditLogger,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.managedCurrencyRepository = managedCurrencyRepository;
        this.institutionRepository = institutionRepository;
        this.openPositionPort = openPositionPort;
        this.currencyPolicy = new OrderAgainstCurrencyPolicy();
        this.institutionPolicy = new OrderAgainstInstitutionPolicy();
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Override
    public Result receive(ReceiveOrderCommand command) {
        var existingOpt = orderRepository.findByExternalOrderReference(command.externalOrderReference());
        if (existingOpt.isPresent()) {
            MoneyMarketOrder existing = existingOpt.get();
            auditLogger.log(existing.getId(), EVENT_DUPLICATE_RECEIVE_IGNORED, AUDIT_ACTOR_SYSTEM, clock.now());
            return new Result(existing.getId(), existing.getStatus(), false);
        }

        Institution institution = resolveActiveInstitution(command.institutionCode());

        var currencyOpt = managedCurrencyRepository.findByCode(command.currency());
        Optional<OpenContractPosition> openPosition =
                command.orderOperation() == OrderOperation.DECREASE && command.sourceContractNumber() != null
                        ? openPositionPort.findOpenByContractNumber(command.sourceContractNumber())
                        : Optional.empty();

        currencyPolicy.validateReceive(
                currencyOpt,
                command.currency(),
                command.orderType(),
                command.orderOperation(),
                command.amount(),
                command.tenor(),
                command.noticePeriod(),
                openPosition);

        MoneyMarketOrder created =
                MoneyMarketOrder.create(
                        command.externalOrderReference(),
                        command.orderType(),
                        command.orderOperation(),
                        command.portfolioNumber(),
                        command.currency(),
                        command.amount(),
                        command.valueDate(),
                        command.minimumRate(),
                        command.tenor(),
                        command.noticePeriod(),
                        intakeSourceContractNumber(command),
                        institution.getInstitutionCode(),
                        institution.getDisplayName(),
                        clock.today());

        MoneyMarketOrder saved = orderRepository.save(created);
        auditLogger.log(saved.getId(), EVENT_ORDER_RECEIVED, AUDIT_ACTOR_SYSTEM, clock.now());
        return new Result(saved.getId(), saved.getStatus(), true);
    }

    private Institution resolveActiveInstitution(String institutionCode) {
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new InvalidOrderException("institutionCode is required");
        }
        var institutionOpt = institutionRepository.findByInstitutionCode(institutionCode);
        institutionPolicy.validateExecute(institutionCode, institutionOpt);
        return institutionOpt.orElseThrow();
    }

    /** Subscription: ignore PM {@code sourceContractNumber} — not persisted. */
    private static ContractNumber intakeSourceContractNumber(ReceiveOrderCommand command) {
        if (command.orderOperation() == OrderOperation.SUBSCRIPTION) {
            return null;
        }
        return command.sourceContractNumber();
    }
}
