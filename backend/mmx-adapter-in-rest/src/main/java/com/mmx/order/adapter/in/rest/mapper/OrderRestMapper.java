package com.mmx.order.adapter.in.rest.mapper;

import com.mmx.order.adapter.in.rest.generated.model.OrderDetailsResponse;
import com.mmx.order.adapter.in.rest.generated.model.OrderOperation;
import com.mmx.order.adapter.in.rest.generated.model.OrderStatus;
import com.mmx.order.adapter.in.rest.generated.model.OrderSummaryResponse;
import com.mmx.order.adapter.in.rest.generated.model.OrderType;
import com.mmx.order.domain.model.Assignment;
import com.mmx.order.domain.model.ExecutionDetails;
import com.mmx.order.domain.model.MoneyMarketOrder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class OrderRestMapper {

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    public OrderSummaryResponse toSummary(MoneyMarketOrder order) {
        Assignment assignment = order.getAssignment();
        return new OrderSummaryResponse()
                .orderId(order.getId())
                .externalOrderReference(order.getExternalOrderReference().value())
                .orderType(OrderType.fromValue(order.getOrderType().name()))
                .orderOperation(OrderOperation.fromValue(order.getOrderOperation().name()))
                .portfolioNumber(order.getPortfolioNumber().value())
                .currency(order.getCurrency())
                .amount(order.getAmount().doubleValue())
                .valueDate(order.getValueDate())
                .minimumRate(order.getMinimumRate().doubleValue())
                .status(OrderStatus.fromValue(order.getStatus().name()))
                .assignedTraderId(assignment != null ? assignment.traderId().value() : null)
                .createdAt(OffsetDateTime.ofInstant(order.getCreatedAt(), UTC));
    }

    public OrderDetailsResponse toDetails(MoneyMarketOrder order) {
        Assignment assignment = order.getAssignment();
        ExecutionDetails ex = order.getExecutionDetails();
        var d =
                new OrderDetailsResponse()
                        .orderId(order.getId())
                        .externalOrderReference(order.getExternalOrderReference().value())
                        .orderType(OrderType.fromValue(order.getOrderType().name()))
                        .orderOperation(OrderOperation.fromValue(order.getOrderOperation().name()))
                        .portfolioNumber(order.getPortfolioNumber().value())
                        .currency(order.getCurrency())
                        .amount(order.getAmount().doubleValue())
                        .valueDate(order.getValueDate())
                        .minimumRate(order.getMinimumRate().doubleValue())
                        .status(OrderStatus.fromValue(order.getStatus().name()))
                        .createdAt(OffsetDateTime.ofInstant(order.getCreatedAt(), UTC))
                        .updatedAt(OffsetDateTime.ofInstant(order.getUpdatedAt(), UTC));

        d.setTenor(order.getTenor() != null ? order.getTenor().getCode() : null);
        d.setNoticePeriod(order.getNoticePeriod() != null ? order.getNoticePeriod().getCode() : null);
        d.setSourceContractNumber(
                order.getSourceContractNumber() != null ? order.getSourceContractNumber().value() : null);
        d.setDesiredCounterpartyComment(order.getDesiredCounterpartyComment());
        d.setAssignedTraderId(assignment != null ? assignment.traderId().value() : null);
        d.setAssignedAt(assignment != null
                ? OffsetDateTime.ofInstant(assignment.assignedAt(), UTC)
                : null);
        if (ex != null) {
            d.setExecutedRate(ex.executedRate().doubleValue());
            d.setCounterparty(ex.counterparty());
            d.setExecutionTime(OffsetDateTime.ofInstant(ex.executionTime(), UTC));
            d.setDealingReference(ex.dealingReference().value());
            d.setGeneratedContractNumber(ex.generatedContractNumber().value());
        } else {
            d.setExecutedRate(null);
            d.setCounterparty(null);
            d.setExecutionTime(null);
            d.setDealingReference(null);
            d.setGeneratedContractNumber(null);
        }
        d.setRejectionReason(order.getRejectionReason());
        return d;
    }
}
