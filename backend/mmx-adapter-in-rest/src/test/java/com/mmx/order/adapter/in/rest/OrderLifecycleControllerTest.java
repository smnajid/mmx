package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.mapper.OrderRestMapper;
import com.mmx.order.application.command.CancelOrderCommand;
import com.mmx.order.application.command.RejectOrderCommand;
import com.mmx.order.application.port.in.CancelOrderUseCase;
import com.mmx.order.application.port.in.ExecuteOrderUseCase;
import com.mmx.order.application.port.in.RejectOrderUseCase;
import com.mmx.order.application.port.in.UpdateAssignedOrderUseCase;
import com.mmx.order.application.port.in.DeskOrderQueries;
import com.mmx.order.application.service.AssignmentService;
import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import com.mmx.order.domain.exception.UnauthorizedTraderException;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.TraderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class OrderLifecycleControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");

    @Mock
    DeskOrderQueries deskOrderQueries;

    @Mock
    AssignmentService assignmentService;

    @Mock
    ExecuteOrderUseCase executeOrderUseCase;

    @Mock
    CancelOrderUseCase cancelOrderUseCase;

    @Mock
    RejectOrderUseCase rejectOrderUseCase;

    @Mock
    UpdateAssignedOrderUseCase updateAssignedOrderUseCase;

    org.springframework.test.web.servlet.MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderRestMapper mapper = new OrderRestMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc =
                standaloneSetup(
                                new OrderManagementController(
                                        deskOrderQueries,
                                        assignmentService,
                                        executeOrderUseCase,
                                        cancelOrderUseCase,
                                        rejectOrderUseCase,
                                        updateAssignedOrderUseCase,
                                        mapper))
                        .setValidator(validator)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void postCancel_returns200_whenSuccessful() throws Exception {
        MoneyMarketOrder order = receivedOrder();
        order.cancel(NOW);
        when(cancelOrderUseCase.cancel(any(CancelOrderCommand.class))).thenReturn(order);

        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/cancel").header("X-Trader-Id", "trader-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.orderId").value(order.getId().toString()));
    }

    @Test
    void postReject_returns200_withReason() throws Exception {
        MoneyMarketOrder order = receivedOrder();
        order.reject(new TraderId("trader-a"), "Below desk minimum", NOW);
        when(rejectOrderUseCase.reject(any(RejectOrderCommand.class))).thenReturn(order);

        mockMvc.perform(
                        post("/api/v1/orders/" + order.getId() + "/reject")
                                .header("X-Trader-Id", "trader-a")
                                .contentType(APPLICATION_JSON)
                                .content("{\"reason\":\"Below desk minimum\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Below desk minimum"));
    }

    @Test
    void postCancel_returns409_whenWrongStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(cancelOrderUseCase.cancel(any(CancelOrderCommand.class)))
                .thenThrow(new InvalidStatusTransitionException(OrderStatus.ASSIGNED, OrderStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/orders/" + id + "/cancel").header("X-Trader-Id", "trader-a"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void postReject_returns409_whenWrongStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(rejectOrderUseCase.reject(any(RejectOrderCommand.class)))
                .thenThrow(new InvalidStatusTransitionException(OrderStatus.EXECUTED, OrderStatus.REJECTED));

        mockMvc.perform(
                        post("/api/v1/orders/" + id + "/reject")
                                .header("X-Trader-Id", "trader-a")
                                .contentType(APPLICATION_JSON)
                                .content("{\"reason\":\"No capacity\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void postReject_returns403_whenNotAssigneeOfAssignedOrder() throws Exception {
        UUID id = UUID.randomUUID();
        when(rejectOrderUseCase.reject(any(RejectOrderCommand.class)))
                .thenThrow(new UnauthorizedTraderException(
                        "Only the assigned Trader may reject an Assigned order"));

        mockMvc.perform(
                        post("/api/v1/orders/" + id + "/reject")
                                .header("X-Trader-Id", "intruder")
                                .contentType(APPLICATION_JSON)
                                .content("{\"reason\":\"No capacity\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_TRADER"));
    }

    @Test
    void postReject_returns400_whenReasonMissing() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/orders/" + id + "/reject")
                                .header("X-Trader-Id", "trader-a")
                                .contentType(APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    private static MoneyMarketOrder receivedOrder() {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("REF-LIFE-" + UUID.randomUUID()),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-1"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(3),
                new BigDecimal("3.25000000"),
                Tenor._3M,
                null,
                null,
                null,
                TODAY);
    }
}
