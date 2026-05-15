package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.mapper.OrderRestMapper;
import com.mmx.order.application.command.AssignOrderCommand;
import com.mmx.order.application.command.UnassignOrderCommand;
import com.mmx.order.application.port.in.CancelOrderUseCase;
import com.mmx.order.application.port.in.ExecuteOrderUseCase;
import com.mmx.order.application.port.in.OrderPage;
import com.mmx.order.application.port.in.RejectOrderUseCase;
import com.mmx.order.application.port.in.UpdateAssignedOrderUseCase;
import com.mmx.order.application.service.AssignmentService;
import com.mmx.order.application.service.OrderQueryService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class OrderAssignmentControllerTest {

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

    @Mock
    UpdateAssignedOrderUseCase updateAssignedOrderUseCase;

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
                                        updateAssignedOrderUseCase,
                                        mapper))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void postAssign_returns200_whenSuccessful() throws Exception {
        MoneyMarketOrder order = receivedOrder();
        order.assign(new TraderId("trader-a"), NOW);
        when(assignmentService.assign(org.mockito.ArgumentMatchers.any(AssignOrderCommand.class))).thenReturn(order);

        mockMvc.perform(
                        post("/api/v1/orders/" + order.getId() + "/assign").header("X-Trader-Id", "trader-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.orderId").value(order.getId().toString()));
    }

    @Test
    void postAssign_returns409_whenWrongStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(assignmentService.assign(org.mockito.ArgumentMatchers.any(AssignOrderCommand.class)))
                .thenThrow(new InvalidStatusTransitionException(OrderStatus.ASSIGNED, OrderStatus.ASSIGNED));

        mockMvc.perform(post("/api/v1/orders/" + id + "/assign").header("X-Trader-Id", "trader-a"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void postUnassign_returns403_whenWrongTrader() throws Exception {
        UUID id = UUID.randomUUID();
        when(assignmentService.unassign(org.mockito.ArgumentMatchers.any(UnassignOrderCommand.class)))
                .thenThrow(new UnauthorizedTraderException("Only the assigned Trader may unassign the order"));

        mockMvc.perform(post("/api/v1/orders/" + id + "/unassign").header("X-Trader-Id", "intruder"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_TRADER"));
    }

    @Test
    void getAssigned_passesTraderIdToUseCase() throws Exception {
        when(assignmentService.listAssignedOrders(eq(new TraderId("alice")), eq(0), eq(20)))
                .thenReturn(new OrderPage(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/v1/orders/assigned").header("X-Trader-Id", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isArray());

        ArgumentCaptor<TraderId> traderCaptor = ArgumentCaptor.forClass(TraderId.class);
        verify(assignmentService).listAssignedOrders(traderCaptor.capture(), eq(0), eq(20));
        assertThat(traderCaptor.getValue()).isEqualTo(new TraderId("alice"));
    }

    @Test
    void getTermAssigned_deskWide_doesNotPassTraderToListingUseCase() throws Exception {
        when(assignmentService.listAssignedTermOrders(eq(0), eq(20)))
                .thenReturn(new OrderPage(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/v1/orders/term/assigned").header("X-Trader-Id", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(assignmentService).listAssignedTermOrders(eq(0), eq(20));
    }

    @Test
    void getOnCallAssigned_deskWide_doesNotPassTraderToListingUseCase() throws Exception {
        when(assignmentService.listAssignedOnCallOrders(eq(0), eq(20)))
                .thenReturn(new OrderPage(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/v1/orders/oncall/assigned").header("X-Trader-Id", "bob"))
                .andExpect(status().isOk());

        verify(assignmentService).listAssignedOnCallOrders(eq(0), eq(20));
    }

    private static MoneyMarketOrder receivedOrder() {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("REF-CTL-" + UUID.randomUUID()),
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
