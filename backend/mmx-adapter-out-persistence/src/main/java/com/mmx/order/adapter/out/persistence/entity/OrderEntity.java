package com.mmx.order.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "money_market_order")
public class OrderEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "external_order_reference", nullable = false, length = 100)
    private String externalOrderReference;

    @Column(name = "legal_entity_code", nullable = false, length = 3)
    private String legalEntityCode;

    @Column(name = "order_type", nullable = false, length = 20)
    private String orderType;

    @Column(name = "order_operation", nullable = false, length = 20)
    private String orderOperation;

    @Column(name = "portfolio_number", nullable = false, length = 50)
    private String portfolioNumber;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "minimum_rate", nullable = true, precision = 12, scale = 8)
    private BigDecimal minimumRate;

    @Column(name = "tenor", length = 10)
    private String tenor;

    @Column(name = "notice_period", length = 10)
    private String noticePeriod;

    @Column(name = "source_contract_number", length = 50)
    private String sourceContractNumber;

    @Column(name = "desired_counterparty_comment", columnDefinition = "TEXT")
    private String desiredCounterpartyComment;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "assigned_trader_id", length = 100)
    private String assignedTraderId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "executed_rate", precision = 12, scale = 8)
    private BigDecimal executedRate;

    @Column(name = "counterparty", length = 200)
    private String counterparty;

    @Column(name = "institution_code", length = 32)
    private String institutionCode;

    @Column(name = "execution_time")
    private Instant executionTime;

    @Column(name = "dealing_reference", length = 50)
    private String dealingReference;

    @Column(name = "generated_contract_number", length = 50)
    private String generatedContractNumber;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "handoff_status", length = 20)
    private String handoffStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OrderEntity() {}

    // ── Getters and setters ──────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getExternalOrderReference() { return externalOrderReference; }
    public void setExternalOrderReference(String v) { this.externalOrderReference = v; }

    public String getLegalEntityCode() { return legalEntityCode; }
    public void setLegalEntityCode(String v) { this.legalEntityCode = v; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String v) { this.orderType = v; }

    public String getOrderOperation() { return orderOperation; }
    public void setOrderOperation(String v) { this.orderOperation = v; }

    public String getPortfolioNumber() { return portfolioNumber; }
    public void setPortfolioNumber(String v) { this.portfolioNumber = v; }

    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }

    public LocalDate getValueDate() { return valueDate; }
    public void setValueDate(LocalDate v) { this.valueDate = v; }

    public BigDecimal getMinimumRate() { return minimumRate; }
    public void setMinimumRate(BigDecimal v) { this.minimumRate = v; }

    public String getTenor() { return tenor; }
    public void setTenor(String v) { this.tenor = v; }

    public String getNoticePeriod() { return noticePeriod; }
    public void setNoticePeriod(String v) { this.noticePeriod = v; }

    public String getSourceContractNumber() { return sourceContractNumber; }
    public void setSourceContractNumber(String v) { this.sourceContractNumber = v; }

    public String getDesiredCounterpartyComment() { return desiredCounterpartyComment; }
    public void setDesiredCounterpartyComment(String v) { this.desiredCounterpartyComment = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public String getAssignedTraderId() { return assignedTraderId; }
    public void setAssignedTraderId(String v) { this.assignedTraderId = v; }

    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant v) { this.assignedAt = v; }

    public BigDecimal getExecutedRate() { return executedRate; }
    public void setExecutedRate(BigDecimal v) { this.executedRate = v; }

    public String getCounterparty() { return counterparty; }
    public void setCounterparty(String v) { this.counterparty = v; }

    public String getInstitutionCode() { return institutionCode; }
    public void setInstitutionCode(String v) { this.institutionCode = v; }

    public Instant getExecutionTime() { return executionTime; }
    public void setExecutionTime(Instant v) { this.executionTime = v; }

    public String getDealingReference() { return dealingReference; }
    public void setDealingReference(String v) { this.dealingReference = v; }

    public String getGeneratedContractNumber() { return generatedContractNumber; }
    public void setGeneratedContractNumber(String v) { this.generatedContractNumber = v; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String v) { this.rejectionReason = v; }

    public String getHandoffStatus() { return handoffStatus; }
    public void setHandoffStatus(String handoffStatus) { this.handoffStatus = handoffStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
