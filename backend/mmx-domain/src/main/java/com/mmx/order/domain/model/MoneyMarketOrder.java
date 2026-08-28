package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import com.mmx.order.domain.exception.UnauthorizedTraderException;
import com.mmx.order.domain.model.RoutedHubOrderDraft;

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
    private final LegalEntityCode legalEntityCode;
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
    private final String institutionCode;
    private final String counterparty;
    private OrderStatus status;
    private Assignment assignment;
    private ExecutionDetails executionDetails;
    private String rejectionReason;
    private RejectionOrigin rejectionOrigin;
    /** Integration handoff toward back-office; meaningful when {@link #status} is {@link OrderStatus#EXECUTED}. */
    private HandoffStatus handoffStatus;
    private RoutingId routingId;
    private LegalEntityCode originatingLegalEntityCode;
    private ExternalOrderReference originatingExternalOrderReference;
    private final Instant createdAt;
    private Instant updatedAt;

    private MoneyMarketOrder(
            UUID id,
            ExternalOrderReference externalOrderReference,
            LegalEntityCode legalEntityCode,
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
            String institutionCode,
            String counterparty,
            OrderStatus status,
            HandoffStatus handoffStatus,
            Instant createdAt
    ) {
        this.id = id;
        this.externalOrderReference = externalOrderReference;
        this.legalEntityCode = Objects.requireNonNull(legalEntityCode, "legalEntityCode must not be null");
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
        this.institutionCode = institutionCode;
        this.counterparty = counterparty;
        this.status = status;
        this.handoffStatus = handoffStatus;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static MoneyMarketOrder create(
            ExternalOrderReference externalOrderReference,
            LegalEntityCode legalEntityCode,
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
            String institutionCode,
            String counterparty,
            LocalDate today
    ) {
        validateInstitutionAtIntake(institutionCode, counterparty);
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
                legalEntityCode,
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
                institutionCode,
                counterparty,
                OrderStatus.RECEIVED,
                null,
                now
        );
    }

    public static MoneyMarketOrder createHubSideFromRouting(RoutedHubOrderDraft draft, LocalDate today) {
        Instant now = Instant.now();
        BigDecimal minimumRateScaled =
                draft.minimumRate() == null
                        ? null
                        : draft.minimumRate().setScale(8, java.math.RoundingMode.UNNECESSARY);
        MoneyMarketOrder order =
                new MoneyMarketOrder(
                        UUID.randomUUID(),
                        new ExternalOrderReference(draft.routingId().value().toString()),
                        draft.hubLegalEntityCode(),
                        draft.orderType(),
                        draft.orderOperation(),
                        draft.portfolioNumber(),
                        draft.currency(),
                        draft.amount().setScale(2, java.math.RoundingMode.UNNECESSARY),
                        draft.valueDate(),
                        minimumRateScaled,
                        draft.tenor(),
                        draft.noticePeriod(),
                        draft.sourceContractNumber(),
                        draft.institutionCode(),
                        draft.counterparty(),
                        OrderStatus.RECEIVED,
                        null,
                        now);
        order.routingId = draft.routingId();
        order.originatingLegalEntityCode = draft.originatingLegalEntityCode();
        order.originatingExternalOrderReference = draft.originatingExternalOrderReference();
        return order;
    }

    // ── Reconstitution (persistence layer only) ───────────────────────────────

    public static MoneyMarketOrder reconstitute(
            java.util.UUID id,
            ExternalOrderReference externalOrderReference,
            LegalEntityCode legalEntityCode,
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
            String institutionCode,
            String counterparty,
            OrderStatus status,
            Assignment assignment,
            ExecutionDetails executionDetails,
            String rejectionReason,
            RejectionOrigin rejectionOrigin,
            HandoffStatus handoffStatus,
            RoutingId routingId,
            LegalEntityCode originatingLegalEntityCode,
            ExternalOrderReference originatingExternalOrderReference,
            Instant createdAt,
            Instant updatedAt
    ) {
        MoneyMarketOrder order = new MoneyMarketOrder(
                id, externalOrderReference, legalEntityCode, orderType, orderOperation,
                portfolioNumber, currency, amount, valueDate, minimumRate,
                tenor, noticePeriod, sourceContractNumber, institutionCode, counterparty,
                status, handoffStatus, createdAt
        );
        order.assignment = assignment;
        order.executionDetails = executionDetails;
        order.rejectionReason = rejectionReason;
        order.rejectionOrigin = rejectionOrigin;
        order.routingId = routingId;
        order.originatingLegalEntityCode = originatingLegalEntityCode;
        order.originatingExternalOrderReference = originatingExternalOrderReference;
        order.updatedAt = updatedAt;
        return order;
    }

    /** @deprecated use overload with routing fields */
    public static MoneyMarketOrder reconstitute(
            java.util.UUID id,
            ExternalOrderReference externalOrderReference,
            LegalEntityCode legalEntityCode,
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
            String institutionCode,
            String counterparty,
            OrderStatus status,
            Assignment assignment,
            ExecutionDetails executionDetails,
            String rejectionReason,
            HandoffStatus handoffStatus,
            Instant createdAt,
            Instant updatedAt
    ) {
        return reconstitute(
                id, externalOrderReference, legalEntityCode, orderType, orderOperation,
                portfolioNumber, currency, amount, valueDate, minimumRate,
                tenor, noticePeriod, sourceContractNumber, institutionCode, counterparty,
                status, assignment, executionDetails, rejectionReason, null, handoffStatus,
                null, null, null, createdAt, updatedAt);
    }

    // ── Routing lifecycle (client-side) ─────────────────────────────────────

    public void markRouted(RoutingId linkRoutingId, Instant now) {
        Objects.requireNonNull(linkRoutingId, "routingId must not be null");
        this.status = this.status.transitionTo(OrderStatus.ROUTED, OrderLifecycleKind.ROUTED_CLIENT);
        this.routingId = linkRoutingId;
        this.updatedAt = now;
    }

    /**
     * Apply a leg-B {@code ACCEPTED} outcome to a remote client-side order. Spec: {@code
     * money-market-order-lifecycle} / silence-is-never-terminal — leg B is the authoritative
     * lifecycle mirror; it closes {@code Received→Routed} using the same domain transition as leg
     * A, with no new {@link OrderStatus} value. The apply is idempotent: if the order is already
     * {@code ROUTED} (leg A delivered first), this is a no-op ack (benign redundancy); any other
     * state is an error (mismatched terminal under at-least-once Kafka) — silence never
     * auto-terminalizes a remote {@code Received} order.
     */
    public void applyAcceptedFromLegB(RoutingId linkRoutingId, Instant now) {
        Objects.requireNonNull(linkRoutingId, "routingId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (this.status == OrderStatus.ROUTED) {
            // Idempotent ack only when the routingId matches the one already routed. A mismatched
            // routingId means the leg-B event targets the wrong order (poisoned / mis-routed under
            // at-least-once Kafka) — surface it, never silently swallow. (RoutingId is deterministic
            // from the order id, so this only fires on genuine corruption / mis-sequencing.)
            if (!linkRoutingId.equals(this.routingId)) {
                throw new InvalidStatusTransitionException(
                        "leg-B ACCEPTED carries routingId " + linkRoutingId
                                + " but order is already ROUTED under routingId " + this.routingId
                                + "; possible poisoned or mis-routed event");
            }
            return;
        }
        if (this.status != OrderStatus.RECEIVED) {
            throw new InvalidStatusTransitionException(this.status, OrderStatus.ROUTED);
        }
        this.status = this.status.transitionTo(OrderStatus.ROUTED, OrderLifecycleKind.ROUTED_CLIENT);
        this.routingId = linkRoutingId;
        this.updatedAt = now;
    }

    public void propagateExecutionFromHub(
            ExecutionDetails hubExecution,
            String clientViaCounterparty,
            ContractNumber clientContractNumber,
            Instant now) {
        Objects.requireNonNull(hubExecution, "hubExecution must not be null");
        this.status = this.status.transitionTo(OrderStatus.EXECUTED, OrderLifecycleKind.ROUTED_CLIENT);
        this.executionDetails =
                new ExecutionDetails(
                        hubExecution.executedRate(),
                        clientViaCounterparty,
                        hubExecution.institutionCode(),
                        hubExecution.executionTime(),
                        hubExecution.dealingReference(),
                        clientContractNumber != null ? clientContractNumber : hubExecution.generatedContractNumber());
        this.updatedAt = now;
    }

    public void propagateRejectFromHub(String reason, Instant now) {
        Objects.requireNonNull(reason, "reason must not be null");
        this.status = this.status.transitionTo(OrderStatus.REJECTED, OrderLifecycleKind.ROUTED_CLIENT);
        this.rejectionReason = reason.trim();
        this.rejectionOrigin = RejectionOrigin.TRADER;
        this.updatedAt = now;
    }

    public void propagateCancelFromHub(Instant now) {
        this.status = this.status.transitionTo(OrderStatus.CANCELLED, OrderLifecycleKind.ROUTED_CLIENT);
        this.updatedAt = now;
    }

    public boolean isRoutedClientSide() {
        return routingId != null && originatingLegalEntityCode == null;
    }

    public boolean isHubSideRoutedLink() {
        return routingId != null && originatingLegalEntityCode != null;
    }

    /** Propagated client-side EXECUTED must not schedule a back-office outbox row. */
    public boolean suppressesExecutionHandoff() {
        return isRoutedClientSide() && status == OrderStatus.EXECUTED;
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
            String institutionCode,
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
                executedRate, counterparty, institutionCode, now, dealingReference, generatedContractNumber);
        this.updatedAt = now;
    }

    /**
     * Marks durable handoff as pending immediately after execute, before outbox relay publishes to Kafka.
     */
    public void markHandoffPending() {
        if (this.status != OrderStatus.EXECUTED) {
            throw new InvalidOrderException("handoff can only be pending for EXECUTED orders");
        }
        this.handoffStatus = HandoffStatus.PENDING;
    }

    public void transitionHandoffToPublished() {
        if (this.status != OrderStatus.EXECUTED || this.handoffStatus != HandoffStatus.PENDING) {
            throw new InvalidOrderException("handoff must be PENDING on an EXECUTED order to publish");
        }
        this.handoffStatus = HandoffStatus.PUBLISHED;
    }

    public void transitionHandoffToFailed() {
        if (this.status != OrderStatus.EXECUTED || this.handoffStatus != HandoffStatus.PENDING) {
            throw new InvalidOrderException("handoff must be PENDING on an EXECUTED order to fail");
        }
        this.handoffStatus = HandoffStatus.FAILED;
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
     * Reject from RECEIVED (any Trader) or ASSIGNED (assigned Trader only). A desk decision —
     * records {@link RejectionOrigin#TRADER}.
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
        this.rejectionOrigin = RejectionOrigin.TRADER;
        this.updatedAt = now;
    }

    /**
     * Reject because the route itself failed — no hub-side order lifecycle exists or the route
     * never completed (unresolved global account, delegated-grant/enabled-set violation at local
     * intake, unresolved external identity account, or a leg-A HTTP reject closing the client-side
     * order). Records {@link RejectionOrigin#ROUTING_FAILURE}. System-originated: no Trader
     * authorization applies.
     */
    public void rejectAsRoutingFailure(String reason, Instant now) {
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(now, "now must not be null");
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidOrderException("reason is required");
        }
        this.status = this.status.transitionTo(OrderStatus.REJECTED);
        this.rejectionReason = trimmed;
        this.rejectionOrigin = RejectionOrigin.ROUTING_FAILURE;
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

    private static void validateInstitutionAtIntake(String institutionCode, String counterparty) {
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new InvalidOrderException("institutionCode is required");
        }
        if (institutionCode.length() > 32) {
            throw new InvalidOrderException("institutionCode must not exceed 32 characters");
        }
        if (counterparty == null || counterparty.isBlank()) {
            throw new InvalidOrderException("counterparty is required");
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public ExternalOrderReference getExternalOrderReference() { return externalOrderReference; }
    public LegalEntityCode getLegalEntityCode() { return legalEntityCode; }
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
    public String getInstitutionCode() {
        return executionDetails != null ? executionDetails.institutionCode() : institutionCode;
    }

    public String getCounterparty() {
        return executionDetails != null ? executionDetails.counterparty() : counterparty;
    }
    public OrderStatus getStatus() { return status; }
    public Assignment getAssignment() { return assignment; }
    public ExecutionDetails getExecutionDetails() { return executionDetails; }
    public String getRejectionReason() { return rejectionReason; }
    public RejectionOrigin getRejectionOrigin() { return rejectionOrigin; }
    public HandoffStatus getHandoffStatus() { return handoffStatus; }
    public RoutingId getRoutingId() { return routingId; }
    public LegalEntityCode getOriginatingLegalEntityCode() { return originatingLegalEntityCode; }
    public ExternalOrderReference getOriginatingExternalOrderReference() {
        return originatingExternalOrderReference;
    }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
