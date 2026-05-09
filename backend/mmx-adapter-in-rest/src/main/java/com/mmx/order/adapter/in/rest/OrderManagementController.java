package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.generated.api.OrdersApi;
import com.mmx.order.adapter.in.rest.generated.model.ExecuteOrderRequest;
import com.mmx.order.adapter.in.rest.generated.model.OrderDetailsResponse;
import com.mmx.order.adapter.in.rest.generated.model.OrderSummaryPage;
import com.mmx.order.adapter.in.rest.generated.model.RejectOrderRequest;
import com.mmx.order.adapter.in.rest.generated.model.UpdateOrderRequest;
import com.mmx.order.adapter.in.rest.mapper.OrderRestMapper;
import com.mmx.order.application.command.AssignOrderCommand;
import com.mmx.order.application.command.UnassignOrderCommand;
import com.mmx.order.application.port.in.CancelOrderUseCase;
import com.mmx.order.application.port.in.ExecuteOrderUseCase;
import com.mmx.order.application.port.in.RejectOrderUseCase;
import com.mmx.order.application.port.in.UpdateAssignedOrderUseCase;
import com.mmx.order.application.service.AssignmentService;
import com.mmx.order.application.service.OrderQueryService;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.model.TraderId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * Implements {@link OrdersApi}. Web-mapping annotations are declared here so Spring MVC registers routes; Bean
 * Validation constraints stay on {@link OrdersApi} only (HV000151 — overrides must not redefine them).
 */
@RestController
public class OrderManagementController implements OrdersApi {

    private final OrderQueryService orderQueryService;
    private final AssignmentService assignmentService;
    private final ExecuteOrderUseCase executeOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final RejectOrderUseCase rejectOrderUseCase;
    private final UpdateAssignedOrderUseCase updateAssignedOrderUseCase;
    private final OrderRestMapper orderRestMapper;

    public OrderManagementController(
            OrderQueryService orderQueryService,
            AssignmentService assignmentService,
            ExecuteOrderUseCase executeOrderUseCase,
            CancelOrderUseCase cancelOrderUseCase,
            RejectOrderUseCase rejectOrderUseCase,
            UpdateAssignedOrderUseCase updateAssignedOrderUseCase,
            OrderRestMapper orderRestMapper) {
        this.orderQueryService = orderQueryService;
        this.assignmentService = assignmentService;
        this.executeOrderUseCase = executeOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.rejectOrderUseCase = rejectOrderUseCase;
        this.updateAssignedOrderUseCase = updateAssignedOrderUseCase;
        this.orderRestMapper = orderRestMapper;
    }

    @Override
    @GetMapping(value = "/api/v1/orders/term/received", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderSummaryPage> listReceivedTermOrders(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        var result = orderQueryService.listReceivedTermOrders(page, size);
        return ResponseEntity.ok(orderRestMapper.toSummaryPage(result));
    }

    @Override
    @GetMapping(value = "/api/v1/orders/oncall/received", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderSummaryPage> listReceivedOnCallOrders(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        var result = orderQueryService.listReceivedOnCallOrders(page, size);
        return ResponseEntity.ok(orderRestMapper.toSummaryPage(result));
    }

    @Override
    @GetMapping(value = "/api/v1/orders/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderDetailsResponse> getOrderDetails(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @PathVariable("orderId") UUID orderId) {
        return orderQueryService
                .getOrderDetails(orderId)
                .map(orderRestMapper::toDetails)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Override
    @Deprecated
    @GetMapping(value = "/api/v1/orders/assigned", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderSummaryPage> listAssignedOrders(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        var result = assignmentService.listAssignedOrders(new TraderId(xTraderId), page, size);
        return ResponseEntity.ok(orderRestMapper.toSummaryPage(result));
    }

    @Override
    @GetMapping(value = "/api/v1/orders/term/assigned", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderSummaryPage> listAssignedTermOrders(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        Objects.requireNonNull(xTraderId, "X-Trader-Id");
        var result = assignmentService.listAssignedTermOrders(page, size);
        return ResponseEntity.ok(orderRestMapper.toSummaryPage(result));
    }

    @Override
    @GetMapping(value = "/api/v1/orders/oncall/assigned", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderSummaryPage> listAssignedOnCallOrders(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        Objects.requireNonNull(xTraderId, "X-Trader-Id");
        var result = assignmentService.listAssignedOnCallOrders(page, size);
        return ResponseEntity.ok(orderRestMapper.toSummaryPage(result));
    }

    @Override
    @GetMapping(value = "/api/v1/orders/term/executed", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderSummaryPage> listExecutedTermOrders(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        var result = orderQueryService.listExecutedTermOrders(page, size);
        return ResponseEntity.ok(orderRestMapper.toSummaryPage(result));
    }

    @Override
    @GetMapping(value = "/api/v1/orders/oncall/executed", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderSummaryPage> listExecutedOnCallOrders(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        var result = orderQueryService.listExecutedOnCallOrders(page, size);
        return ResponseEntity.ok(orderRestMapper.toSummaryPage(result));
    }

    @Override
    @PostMapping(value = "/api/v1/orders/{orderId}/assign", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderDetailsResponse> assignOrder(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @PathVariable("orderId") UUID orderId) {
        var order = assignmentService.assign(new AssignOrderCommand(orderId, new TraderId(xTraderId)));
        return ResponseEntity.ok(orderRestMapper.toDetails(order));
    }

    @Override
    @PostMapping(value = "/api/v1/orders/{orderId}/unassign", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderDetailsResponse> unassignOrder(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @PathVariable("orderId") UUID orderId) {
        var order = assignmentService.unassign(new UnassignOrderCommand(orderId, new TraderId(xTraderId)));
        return ResponseEntity.ok(orderRestMapper.toDetails(order));
    }

    @Override
    @PostMapping(
            value = "/api/v1/orders/{orderId}/execute",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderDetailsResponse> executeOrder(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @PathVariable("orderId") UUID orderId,
            @RequestBody ExecuteOrderRequest executeOrderRequest) {
        var order =
                executeOrderUseCase.execute(
                        orderRestMapper.toExecuteCommand(executeOrderRequest, orderId, xTraderId));
        return ResponseEntity.ok(orderRestMapper.toDetails(order));
    }

    @Override
    @PostMapping(value = "/api/v1/orders/{orderId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderDetailsResponse> cancelOrder(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @PathVariable("orderId") UUID orderId) {
        var order = cancelOrderUseCase.cancel(orderRestMapper.toCancelCommand(orderId, xTraderId));
        return ResponseEntity.ok(orderRestMapper.toDetails(order));
    }

    @Override
    @PostMapping(
            value = "/api/v1/orders/{orderId}/reject",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderDetailsResponse> rejectOrder(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @PathVariable("orderId") UUID orderId,
            @RequestBody RejectOrderRequest rejectOrderRequest) {
        var order =
                rejectOrderUseCase.reject(orderRestMapper.toRejectCommand(rejectOrderRequest, orderId, xTraderId));
        return ResponseEntity.ok(orderRestMapper.toDetails(order));
    }

    @Override
    @PutMapping(
            value = "/api/v1/orders/{orderId}",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderDetailsResponse> updateAssignedOrder(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @PathVariable("orderId") UUID orderId,
            @RequestBody UpdateOrderRequest updateOrderRequest) {
        var order =
                updateAssignedOrderUseCase.update(
                        orderRestMapper.toUpdateCommand(updateOrderRequest, orderId, xTraderId));
        return ResponseEntity.ok(orderRestMapper.toDetails(order));
    }
}
