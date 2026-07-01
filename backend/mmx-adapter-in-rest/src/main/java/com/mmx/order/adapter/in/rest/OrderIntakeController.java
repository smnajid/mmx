package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.generated.api.IntakeApi;
import com.mmx.order.adapter.in.rest.generated.model.ReceiveOrderRequest;
import com.mmx.order.adapter.in.rest.generated.model.ReceiveOrderResponse;
import com.mmx.order.adapter.in.rest.mapper.OrderRestMapper;
import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.port.in.ReceiveOrderUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderIntakeController implements IntakeApi {

    private final ReceiveOrderUseCase receiveOrderUseCase;
    private final OrderRestMapper orderRestMapper;

    public OrderIntakeController(ReceiveOrderUseCase receiveOrderUseCase, OrderRestMapper orderRestMapper) {
        this.receiveOrderUseCase = receiveOrderUseCase;
        this.orderRestMapper = orderRestMapper;
    }

    /**
     * Receive order (idempotent). Returns {@code 201} when a new order is stored, {@code 200} when the same
     * external reference was already received (existing order returned, duplicate body ignored).
     */
    @Override
    public ResponseEntity<ReceiveOrderResponse> receiveOrder(ReceiveOrderRequest receiveOrderRequest) {
        ReceiveOrderCommand command = orderRestMapper.toCommand(receiveOrderRequest);
        ReceiveOrderUseCase.Result result = receiveOrderUseCase.receive(command);
        ReceiveOrderResponse body = orderRestMapper.toReceiveResponse(result, command.legalEntityCode());
        if (result.newlyCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        }
        return ResponseEntity.ok(body);
    }
}
