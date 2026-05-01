package com.mmx.order.adapter.out.persistence.mapper;

import com.mmx.order.adapter.out.persistence.entity.OrderEntity;
import com.mmx.order.domain.model.*;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OrderPersistenceMapper {

    public OrderEntity toEntity(MoneyMarketOrder order) {
        OrderEntity e = new OrderEntity();
        e.setId(order.getId());
        e.setExternalOrderReference(order.getExternalOrderReference().value());
        e.setOrderType(order.getOrderType().name());
        e.setOrderOperation(order.getOrderOperation().name());
        e.setPortfolioNumber(order.getPortfolioNumber().value());
        e.setCurrency(order.getCurrency());
        e.setAmount(order.getAmount());
        e.setValueDate(order.getValueDate());
        e.setMinimumRate(order.getMinimumRate());
        e.setTenor(order.getTenor() != null ? order.getTenor().name() : null);
        e.setNoticePeriod(order.getNoticePeriod() != null ? order.getNoticePeriod().name() : null);
        e.setSourceContractNumber(
                order.getSourceContractNumber() != null ? order.getSourceContractNumber().value() : null);
        e.setDesiredCounterpartyComment(order.getDesiredCounterpartyComment());
        e.setStatus(order.getStatus().name());
        e.setCreatedAt(order.getCreatedAt());
        e.setUpdatedAt(order.getUpdatedAt());
        e.setRejectionReason(order.getRejectionReason());

        if (order.getAssignment() != null) {
            e.setAssignedTraderId(order.getAssignment().traderId().value());
            e.setAssignedAt(order.getAssignment().assignedAt());
        }

        if (order.getExecutionDetails() != null) {
            ExecutionDetails ex = order.getExecutionDetails();
            e.setExecutedRate(ex.executedRate());
            e.setCounterparty(ex.counterparty());
            e.setExecutionTime(ex.executionTime());
            e.setDealingReference(ex.dealingReference().value());
            e.setGeneratedContractNumber(ex.generatedContractNumber().value());
        }

        return e;
    }

    public MoneyMarketOrder toDomain(OrderEntity e) {
        Tenor tenor = e.getTenor() != null ? Tenor.valueOf(e.getTenor()) : null;
        NoticePeriod noticePeriod = e.getNoticePeriod() != null ? NoticePeriod.valueOf(e.getNoticePeriod()) : null;
        ContractNumber sourceContract = e.getSourceContractNumber() != null
                ? new ContractNumber(e.getSourceContractNumber()) : null;

        MoneyMarketOrder order = MoneyMarketOrder.reconstitute(
                e.getId(),
                new ExternalOrderReference(e.getExternalOrderReference()),
                OrderType.valueOf(e.getOrderType()),
                OrderOperation.valueOf(e.getOrderOperation()),
                new PortfolioNumber(e.getPortfolioNumber()),
                e.getCurrency(),
                e.getAmount(),
                e.getValueDate(),
                e.getMinimumRate(),
                tenor,
                noticePeriod,
                sourceContract,
                e.getDesiredCounterpartyComment(),
                OrderStatus.valueOf(e.getStatus()),
                buildAssignment(e),
                buildExecutionDetails(e),
                e.getRejectionReason(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
        return order;
    }

    private Assignment buildAssignment(OrderEntity e) {
        if (e.getAssignedTraderId() == null) return null;
        return new Assignment(new TraderId(e.getAssignedTraderId()), e.getAssignedAt());
    }

    private ExecutionDetails buildExecutionDetails(OrderEntity e) {
        if (e.getExecutedRate() == null) return null;
        DealingReference dealRef = e.getDealingReference() != null
                ? new DealingReference(e.getDealingReference()) : null;
        ContractNumber contractNum = e.getGeneratedContractNumber() != null
                ? new ContractNumber(e.getGeneratedContractNumber()) : null;
        return new ExecutionDetails(
                e.getExecutedRate(), e.getCounterparty(),
                e.getExecutionTime(), dealRef, contractNum
        );
    }
}
