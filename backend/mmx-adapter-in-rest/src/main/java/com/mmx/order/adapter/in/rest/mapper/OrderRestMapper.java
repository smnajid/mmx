package com.mmx.order.adapter.in.rest.mapper;

import com.mmx.order.adapter.in.rest.dto.OrderDetailsResponse;
import com.mmx.order.adapter.in.rest.dto.OrderSummaryResponse;
import com.mmx.order.domain.model.Assignment;
import com.mmx.order.domain.model.ExecutionDetails;
import com.mmx.order.domain.model.MoneyMarketOrder;
import org.springframework.stereotype.Component;

@Component
public class OrderRestMapper {

    public OrderSummaryResponse toSummary(MoneyMarketOrder order) {
        Assignment assignment = order.getAssignment();
        return new OrderSummaryResponse(
                order.getId(),
                order.getExternalOrderReference().value(),
                order.getOrderType().name(),
                order.getOrderOperation().name(),
                order.getPortfolioNumber().value(),
                order.getCurrency(),
                order.getAmount(),
                order.getValueDate(),
                order.getStatus().name(),
                assignment != null ? assignment.traderId().value() : null
        );
    }

    public OrderDetailsResponse toDetails(MoneyMarketOrder order) {
        Assignment assignment = order.getAssignment();
        ExecutionDetails ex = order.getExecutionDetails();
        return new OrderDetailsResponse(
                order.getId(),
                order.getExternalOrderReference().value(),
                order.getOrderType().name(),
                order.getOrderOperation().name(),
                order.getPortfolioNumber().value(),
                order.getCurrency(),
                order.getAmount(),
                order.getValueDate(),
                order.getMinimumRate(),
                order.getTenor() != null ? order.getTenor().name() : null,
                order.getNoticePeriod() != null ? order.getNoticePeriod().name() : null,
                order.getSourceContractNumber() != null ? order.getSourceContractNumber().value() : null,
                order.getDesiredCounterpartyComment(),
                order.getStatus().name(),
                assignment != null ? assignment.traderId().value() : null,
                assignment != null ? assignment.assignedAt() : null,
                ex != null ? ex.executedRate() : null,
                ex != null ? ex.counterparty() : null,
                ex != null ? ex.executionTime() : null,
                ex != null ? ex.dealingReference().value() : null,
                ex != null ? ex.generatedContractNumber().value() : null,
                order.getRejectionReason(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
