package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import com.mmx.order.domain.exception.UnauthorizedTraderException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class MoneyMarketOrder {

    private static final Set<OrderOperation> TERM_ALLOWED_OPERATIONS =
            Set.of(OrderOperation.SUBSCRIPTION);

    private static final Set<OrderOperation> ON_CALL_ALLOWED_OPERATIONS =
            Set.of(OrderOperation.SUBSCRIPTION, OrderOperation.INCREASE,
                   OrderOperation.DECREASE, OrderOperation.REDEMPTION);

    private static final Set<OrderOperation> LIFECYCLE_OPERATIONS =
            Set.of(OrderOperation.INCREASE, OrderOperation.DECREASE, OrderOperation.REDEMPTION);

    private final UUID id;
    private final ExternalOrderReference externalOrderReference;
    private final OrderType orderType;
    private final OrderOperation orderOperation;
    private final PortfolioNumber portfolioNumber;
    private final String currency;
    private BigDecimal amount;
    private LocalDate valueDate;
    private BigDecimal minimumRate;
    private final Tenor tenor;
    private final NoticePeriod noticePeriod;
    private final ContractNumber sourceContractNumber;
    private String desiredCounterpartyComment;
    private OrderStatus status;
    private Assignment assignment;
    private ExecutionDetails executionDetails;
    private String rejectionReason;
    private final Instant createdAt;
    private Instant updatedAt;

    private MoneyMarketOrder(
            UUID id,
            ExternalOrderReference externalOrderReference,
            OrderType orderType,
            OrderOperation orderOperation,
            PortfolioNumber portfolioNumber,
            String currency,
            BigDecimal amount,
            LocalDate valueDate,
            BigDecimal minimumRate,
            Tenor tenor,
            NoticePeriod noticePeriod,
            ContractNumber sourceContractNumber,
            String desiredCounterpartyComment,
            OrderStatus status,
            Instant createdAt
    ) {
        this.id = id;
        this.externalOrderReference = externalOrderReference;
        this.orderType = orderType;
        this.orderOperation = orderOperation;
        this.portfolioNumber = portfolioNumber;
        this.currency = currency;
        this.amount = amount;
        this.valueDate = valueDate;
        this.minimumRate = minimumRate;
        this.tenor = tenor;
        this.noticePeriod = noticePeriod;
        this.sourceContractNumber = sourceContractNumber;
        this.desiredCounterpartyComment = desiredCounterpartyComment;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static MoneyMarketOrder create(
            ExternalOrderReference externalOrderReference,
            OrderType orderType,
            OrderOperation orderOperation,
            PortfolioNumber portfolioNumber,
            String currency,
            BigDecimal amount,
            LocalDate valueDate,
            BigDecimal minimumRate,
            Tenor tenor,
            NoticePeriod noticePeriod,
            ContractNumber sourceContractNumber,
            String desiredCounterpartyComment,
            LocalDate today
    ) {
        validateOrderTypeOperation(orderType, orderOperation);
        validateTenorNoticePeriod(orderType, tenor, noticePeriod);
        validateSourceContractNumber(orderOperation, sourceContractNumber);
        validateAmount(amount);
        if (minimumRate != null) {
            validateMinimumRate(minimumRate);
        }
        validateValueDate(valueDate, today);

        Instant now = Instant.now();
        BigDecimal minimumRateScaled =
                minimumRate == null ? null : minimumRate.setScale(8, java.math.RoundingMode.UNNECESSARY);
        return new MoneyMarketOrder(
                UUID.randomUUID(),
                Objects.requireNonNull(externalOrderReference),
                orderType,
                orderOperation,
                Objects.requireNonNull(portfolioNumber),
                Objects.requireNonNull(currency),
                amount.setScale(2, java.math.RoundingMode.UNNECESSARY),
                valueDate,
                minimumRateScaled,
                tenor,
                noticePeriod,
                sourceContractNumber,
                desiredCounterpartyComment,
                OrderStatus.RECEIVED,
                now
        );
    }

    // ── Reconstitution (persistence layer only) ───────────────────────────────

    public static MoneyMarketOrder reconstitute(
            java.util.UUID id,
            ExternalOrderReference externalOrderReference,
            OrderType orderType,
            OrderOperation orderOperation,
            PortfolioNumber portfolioNumber,
            String currency,
            java.math.BigDecimal amount,
            java.time.LocalDate valueDate,
            java.math.BigDecimal minimumRate,
            Tenor tenor,
            NoticePeriod noticePeriod,
            ContractNumber sourceContractNumber,
            String desiredCounterpartyComment,
            OrderStatus status,
            Assignment assignment,
            ExecutionDetails executionDetails,
            String rejectionReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        MoneyMarketOrder order = new MoneyMarketOrder(
                id, externalOrderReference, orderType, orderOperation,
                portfolioNumber, currency, amount, valueDate, minimumRate,
                tenor, noticePeriod, sourceContractNumber, desiredCounterpartyComment,
                status, createdAt
        );
        order.assignment = assignment;
        order.executionDetails = executionDetails;
        order.rejectionReason = rejectionReason;
        order.updatedAt = updatedAt;
        return order;
    }

    // ── Lifecycle methods ─────────────────────────────────────────────────────

    public void assign(TraderId traderId, Instant now) {
        Objects.requireNonNull(traderId, "traderId must not be null");
        this.status = this.status.transitionTo(OrderStatus.ASSIGNED);
        this.assignment = new Assignment(traderId, now);
        this.updatedAt = now;
    }

    public void unassign(TraderId requestingTraderId, Instant now) {
        Objects.requireNonNull(requestingTraderId);
        if (this.status != OrderStatus.ASSIGNED) {
            throw new InvalidStatusTransitionException(this.status, OrderStatus.RECEIVED);
        }
        if (assignment == null || !assignment.traderId().equals(requestingTraderId)) {
            throw new UnauthorizedTraderException("Only the assigned Trader may unassign the order");
        }
        this.status = this.status.transitionTo(OrderStatus.RECEIVED);
        this.assignment = null;
        this.updatedAt = now;
    }

    public void update(
            BigDecimal amount,
            LocalDate valueDate,
            TraderId requestingTraderId,
            LocalDate today,
            Instant now
    ) {
        if (this.status != OrderStatus.ASSIGNED) {
            throw new InvalidStatusTransitionException("Order must be in ASSIGNED status to update");
        }
        if (assignment == null || !assignment.traderId().equals(requestingTraderId)) {
            throw new UnauthorizedTraderException("Only the assigned Trader may update the order");
        }
        if (amount != null) {
            validateAmount(amount);
            this.amount = amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
        }
        if (valueDate != null) {
            validateValueDate(valueDate, today);
            this.valueDate = valueDate;
        }
        this.updatedAt = now;
    }

    public void execute(
            BigDecimal executedRate,
            String counterparty,
            DealingReference dealingReference,
            ContractNumber generatedContractNumber,
            TraderId requestingTraderId,
            Instant now
    ) {
        Objects.requireNonNull(requestingTraderId);
        if (this.status != OrderStatus.ASSIGNED) {
            throw new InvalidStatusTransitionException(this.status, OrderStatus.EXECUTED);
        }
        if (assignment == null || !assignment.traderId().equals(requestingTraderId)) {
            throw new UnauthorizedTraderException("Only the assigned Trader may execute the order");
        }
        if (minimumRate != null && executedRate.compareTo(minimumRate) < 0) {
            throw new InvalidOrderException(
                    "executedRate must be greater than or equal to MinimumRate (" + minimumRate + ")");
        }
        if (LIFECYCLE_OPERATIONS.contains(this.orderOperation) && generatedContractNumber == null) {
            throw new InvalidOrderException(
                    "sourceContractNumber is required for " + this.orderOperation + " operations");
        }
        this.status = this.status.transitionTo(OrderStatus.EXECUTED);
        this.executionDetails = new ExecutionDetails(
                executedRate, counterparty, now, dealingReference, generatedContractNumber
        );
        this.updatedAt = now;
    }

    /**
     * Back-office confirmation that portfolio position reflects this execution (EXECUTED → ACCOUNTED).
     */
    public void markAccounted(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.status != OrderStatus.EXECUTED) {
            throw new InvalidStatusTransitionException(this.status, OrderStatus.ACCOUNTED);
        }
        this.status = this.status.transitionTo(OrderStatus.ACCOUNTED);
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        this.status = this.status.transitionTo(OrderStatus.CANCELLED);
        this.updatedAt = now;
    }

    /**
     * Reject from RECEIVED (any Trader) or ASSIGNED (assigned Trader only).
     */
    public void reject(TraderId requestingTraderId, String reason, Instant now) {
        Objects.requireNonNull(requestingTraderId);
        Objects.requireNonNull(reason, "reason must not be null");
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidOrderException("reason is required");
        }
        if (this.status == OrderStatus.RECEIVED) {
            this.status = this.status.transitionTo(OrderStatus.REJECTED);
        } else if (this.status == OrderStatus.ASSIGNED) {
            if (assignment == null || !assignment.traderId().equals(requestingTraderId)) {
                throw new UnauthorizedTraderException(
                        "Only the assigned Trader may reject an Assigned order");
            }
            this.status = this.status.transitionTo(OrderStatus.REJECTED);
        } else {
            throw new InvalidStatusTransitionException(this.status, OrderStatus.REJECTED);
        }
        this.rejectionReason = trimmed;
        this.updatedAt = now;
    }

    // ── Private validation helpers ────────────────────────────────────────────

    private static void validateOrderTypeOperation(OrderType orderType, OrderOperation orderOperation) {
        Set<OrderOperation> allowed = orderType == OrderType.TERM
                ? TERM_ALLOWED_OPERATIONS
                : ON_CALL_ALLOWED_OPERATIONS;
        if (!allowed.contains(orderOperation)) {
            throw new InvalidOrderException(
                    "OrderOperation " + orderOperation + " is not allowed for OrderType " + orderType);
        }
    }

    private static void validateTenorNoticePeriod(OrderType orderType, Tenor tenor, NoticePeriod noticePeriod) {
        if (orderType == OrderType.TERM) {
            if (tenor == null) {
                throw new InvalidOrderException("Tenor is required for TERM orders");
            }
            if (noticePeriod != null) {
                throw new InvalidOrderException("NoticePeriod must be null for TERM orders");
            }
        } else {
            if (noticePeriod == null) {
                throw new InvalidOrderException("NoticePeriod is required for ON_CALL orders");
            }
            if (tenor != null) {
                throw new InvalidOrderException("Tenor must be null for ON_CALL orders");
            }
        }
    }

    private static void validateSourceContractNumber(OrderOperation orderOperation, ContractNumber sourceContractNumber) {
        if (LIFECYCLE_OPERATIONS.contains(orderOperation) && sourceContractNumber == null) {
            throw new InvalidOrderException(
                    "sourceContractNumber is required for " + orderOperation + " operations");
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderException("Amount must be greater than zero");
        }
    }

    private static void validateMinimumRate(BigDecimal minimumRate) {
        if (minimumRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidOrderException("MinimumRate must be >= 0");
        }
    }

    private static void validateValueDate(LocalDate valueDate, LocalDate today) {
        if (valueDate == null || valueDate.isBefore(today.plusDays(2))) {
            throw new InvalidOrderException("ValueDate must be at least today + 2 calendar days");
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public ExternalOrderReference getExternalOrderReference() { return externalOrderReference; }
    public OrderType getOrderType() { return orderType; }
    public OrderOperation getOrderOperation() { return orderOperation; }
    public PortfolioNumber getPortfolioNumber() { return portfolioNumber; }
    public String getCurrency() { return currency; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getValueDate() { return valueDate; }
    public BigDecimal getMinimumRate() { return minimumRate; }
    public Tenor getTenor() { return tenor; }
    public NoticePeriod getNoticePeriod() { return noticePeriod; }
    public ContractNumber getSourceContractNumber() { return sourceContractNumber; }
    public String getDesiredCounterpartyComment() { return desiredCounterpartyComment; }
    public OrderStatus getStatus() { return status; }
    public Assignment getAssignment() { return assignment; }
    public ExecutionDetails getExecutionDetails() { return executionDetails; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
