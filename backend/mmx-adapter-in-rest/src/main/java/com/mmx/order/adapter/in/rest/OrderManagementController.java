package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.generated.api.OrdersApi;
import com.mmx.order.adapter.in.rest.generated.model.ExecuteOrderRequest;
import com.mmx.order.adapter.in.rest.generated.model.OrderDetailsResponse;
import com.mmx.order.adapter.in.rest.generated.model.OrderSummaryPage;
import com.mmx.order.adapter.in.rest.generated.model.RejectOrderRequest;
import com.mmx.order.adapter.in.rest.generated.model.UpdateOrderRequest;
import com.mmx.order.adapter.in.rest.mapper.OrderRestMapper;
import com.mmx.order.application.service.OrderQueryService;
import com.mmx.order.domain.exception.OrderNotFoundException;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Implements {@link OrdersApi}. Web-mapping annotations are declared here so Spring MVC registers routes; Bean
 * Validation constraints stay on {@link OrdersApi} only (HV000151 — overrides must not redefine them).
 */
@RestController
public class OrderManagementController implements OrdersApi {

    private final OrderQueryService orderQueryService;
    private final OrderRestMapper orderRestMapper;

    public OrderManagementController(OrderQueryService orderQueryService, OrderRestMapper orderRestMapper) {
        this.orderQueryService = orderQueryService;
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
    @GetMapping(value = "/api/v1/orders/assigned", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderSummaryPage> listAssignedOrders(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    @PostMapping(value = "/api/v1/orders/{orderId}/assign", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderDetailsResponse> assignOrder(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @PathVariable("orderId") UUID orderId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    @PostMapping(value = "/api/v1/orders/{orderId}/unassign", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderDetailsResponse> unassignOrder(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @PathVariable("orderId") UUID orderId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
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
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    @PostMapping(value = "/api/v1/orders/{orderId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderDetailsResponse> cancelOrder(
            @RequestHeader(value = "X-Trader-Id", required = true) String xTraderId,
            @PathVariable("orderId") UUID orderId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
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
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
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
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
