package com.mmx.order.adapter.in.rest.mapper;

import com.mmx.order.adapter.in.rest.generated.model.ExecuteOrderRequest;
import com.mmx.order.adapter.in.rest.generated.model.RejectOrderRequest;
import com.mmx.order.adapter.in.rest.generated.model.ReceiveOrderRequest;
import com.mmx.order.adapter.in.rest.generated.model.UpdateOrderRequest;
import com.mmx.order.adapter.in.rest.generated.model.OrderDetailsResponse;
import com.mmx.order.adapter.in.rest.generated.model.OrderOperation;
import com.mmx.order.adapter.in.rest.generated.model.OrderStatus;
import com.mmx.order.adapter.in.rest.generated.model.OrderSummaryResponse;
import com.mmx.order.adapter.in.rest.generated.model.OrderType;
import com.mmx.order.adapter.in.rest.generated.model.ReceiveOrderResponse;
import com.mmx.order.adapter.in.rest.generated.model.OrderSummaryPage;
import com.mmx.order.application.command.CancelOrderCommand;
import com.mmx.order.application.command.ExecuteOrderCommand;
import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.command.RejectOrderCommand;
import com.mmx.order.application.command.UpdateOrderCommand;
import com.mmx.order.application.port.in.OrderPage;
import com.mmx.order.application.port.in.ReceiveOrderUseCase;
import com.mmx.order.domain.model.Assignment;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.ExecutionDetails;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.TraderId;
import com.mmx.order.domain.exception.InvalidOrderException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
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

    public ExecuteOrderCommand toExecuteCommand(ExecuteOrderRequest request, UUID orderId, String xTraderId) {
        BigDecimal executedRate =
                request.getExecutedRate() != null
                        ? BigDecimal.valueOf(request.getExecutedRate())
                        : null;
        return new ExecuteOrderCommand(orderId, new TraderId(xTraderId), executedRate, request.getCounterparty());
    }

    public CancelOrderCommand toCancelCommand(UUID orderId, String xTraderId) {
        return new CancelOrderCommand(orderId, new TraderId(xTraderId));
    }

    public UpdateOrderCommand toUpdateCommand(UpdateOrderRequest request, UUID orderId, String xTraderId) {
        BigDecimal amount =
                request.getAmount() != null ? BigDecimal.valueOf(request.getAmount()) : null;
        BigDecimal minimumRate =
                request.getMinimumRate() != null ? BigDecimal.valueOf(request.getMinimumRate()) : null;
        return new UpdateOrderCommand(
                orderId, new TraderId(xTraderId), amount, request.getValueDate(), minimumRate);
    }

    public RejectOrderCommand toRejectCommand(RejectOrderRequest request, UUID orderId, String xTraderId) {
        return new RejectOrderCommand(orderId, request.getReason(), new TraderId(xTraderId));
    }

    public ReceiveOrderCommand toCommand(ReceiveOrderRequest request) {
        return new ReceiveOrderCommand(
                new ExternalOrderReference(request.getExternalOrderReference()),
                com.mmx.order.domain.model.OrderType.valueOf(request.getOrderType().name()),
                com.mmx.order.domain.model.OrderOperation.valueOf(request.getOrderOperation().name()),
                new PortfolioNumber(request.getPortfolioNumber()),
                request.getCurrency(),
                BigDecimal.valueOf(request.getAmount()),
                request.getValueDate(),
                BigDecimal.valueOf(request.getMinimumRate()),
                mapApiTenorToDomain(request.getTenor()),
                mapApiNoticeToDomain(request.getNoticePeriod()),
                request.getSourceContractNumber() == null
                        ? null
                        : new ContractNumber(request.getSourceContractNumber()),
                request.getDesiredCounterpartyComment());
    }

    public ReceiveOrderResponse toReceiveResponse(ReceiveOrderUseCase.Result result) {
        return new ReceiveOrderResponse()
                .orderId(result.orderId())
                .status(OrderStatus.fromValue(result.status().name()));
    }

    public OrderSummaryPage toSummaryPage(OrderPage page) {
        var items = page.content().stream().map(this::toSummary).toList();
        return new OrderSummaryPage()
                .content(items)
                .totalElements(page.totalElements())
                .page(page.page())
                .size(page.size());
    }

    private static Tenor mapApiTenorToDomain(com.mmx.order.adapter.in.rest.generated.model.Tenor api) {
        if (api == null) {
            return null;
        }
        String code = api.getValue();
        for (Tenor t : Tenor.values()) {
            if (t.getCode().equals(code)) {
                return t;
            }
        }
        throw new InvalidOrderException("Unknown tenor: " + code);
    }

    private static NoticePeriod mapApiNoticeToDomain(
            com.mmx.order.adapter.in.rest.generated.model.NoticePeriod api) {
        if (api == null) {
            return null;
        }
        return switch (api) {
            case _24_H -> NoticePeriod._24H;
            case _48_H -> NoticePeriod._48H;
        };
    }
}
