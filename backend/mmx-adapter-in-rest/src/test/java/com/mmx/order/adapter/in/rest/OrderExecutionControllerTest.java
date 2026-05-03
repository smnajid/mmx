package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.mapper.OrderRestMapper;
import com.mmx.order.application.command.ExecuteOrderCommand;
import com.mmx.order.application.port.in.CancelOrderUseCase;
import com.mmx.order.application.port.in.ExecuteOrderUseCase;
import com.mmx.order.application.port.in.RejectOrderUseCase;
import com.mmx.order.application.service.AssignmentService;
import com.mmx.order.application.service.OrderQueryService;
import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import com.mmx.order.domain.exception.UnauthorizedTraderException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;
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
class OrderExecutionControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");

    @Mock
    OrderQueryService orderQueryService;

    @Mock
    AssignmentService assignmentService;

    @Mock
    ExecuteOrderUseCase executeOrderUseCase;

    @Mock
    CancelOrderUseCase cancelOrderUseCase;

    @Mock
    RejectOrderUseCase rejectOrderUseCase;

    org.springframework.test.web.servlet.MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderRestMapper mapper = new OrderRestMapper();
        mockMvc =
                standaloneSetup(
                                new OrderManagementController(
                                        orderQueryService,
                                        assignmentService,
                                        executeOrderUseCase,
                                        cancelOrderUseCase,
                                        rejectOrderUseCase,
                                        mapper))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void postExecute_returns200_withExecutionFields() throws Exception {
        MoneyMarketOrder order = assignedOrderExecuted();
        when(executeOrderUseCase.execute(any(ExecuteOrderCommand.class))).thenReturn(order);

        mockMvc.perform(
                        post("/api/v1/orders/" + order.getId() + "/execute")
                                .header("X-Trader-Id", "trader-a")
                                .contentType(APPLICATION_JSON)
                                .content("{\"executedRate\":3.55,\"counterparty\":\"BankCo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.orderId").value(order.getId().toString()))
                .andExpect(jsonPath("$.executedRate").value(3.55))
                .andExpect(jsonPath("$.counterparty").value("BankCo International"))
                .andExpect(jsonPath("$.dealingReference").value("DL-exec-test"))
                .andExpect(jsonPath("$.generatedContractNumber").value("CN-exec-test"));
    }

    @Test
    void postExecute_returns400_whenRequiredFieldsMissing() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(
                        post("/api/v1/orders/" + id + "/execute")
                                .header("X-Trader-Id", "trader-a")
                                .contentType(APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void postExecute_returns403_whenWrongTrader() throws Exception {
        UUID id = UUID.randomUUID();
        when(executeOrderUseCase.execute(any(ExecuteOrderCommand.class)))
                .thenThrow(new UnauthorizedTraderException("Only the assigned Trader may execute the order"));

        mockMvc.perform(
                        post("/api/v1/orders/" + id + "/execute")
                                .header("X-Trader-Id", "intruder")
                                .contentType(APPLICATION_JSON)
                                .content("{\"executedRate\":3.5,\"counterparty\":\"BankCo\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_TRADER"));
    }

    @Test
    void postExecute_returns409_whenWrongStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(executeOrderUseCase.execute(any(ExecuteOrderCommand.class)))
                .thenThrow(new InvalidStatusTransitionException(OrderStatus.RECEIVED, OrderStatus.EXECUTED));

        mockMvc.perform(
                        post("/api/v1/orders/" + id + "/execute")
                                .header("X-Trader-Id", "trader-a")
                                .contentType(APPLICATION_JSON)
                                .content("{\"executedRate\":3.5,\"counterparty\":\"BankCo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
    }

    private static MoneyMarketOrder assignedOrderExecuted() {
        MoneyMarketOrder order =
                MoneyMarketOrder.create(
                        new ExternalOrderReference("REF-EXEC-" + UUID.randomUUID()),
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
        order.assign(new TraderId("trader-a"), NOW);
        order.execute(
                new BigDecimal("3.55000000"),
                "BankCo International",
                new DealingReference("DL-exec-test"),
                new ContractNumber("CN-exec-test"),
                new TraderId("trader-a"),
                NOW);
        return order;
    }
}
