package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.mapper.OrderCreationRestMapper;
import com.mmx.order.application.ordercreation.LiveContractResult;
import com.mmx.order.application.ordercreation.LiveContractsResult;
import com.mmx.order.application.port.in.GetContractInfoUseCase;
import com.mmx.order.application.port.in.ListLiveContractsUseCase;
import com.mmx.order.application.port.in.ListOnCallCounterpartiesUseCase;
import com.mmx.order.application.port.in.ListOnCallCurrenciesUseCase;
import com.mmx.order.application.port.in.ListOnCallNoticePeriodsUseCase;
import com.mmx.order.application.port.in.ListOnCallOperationsUseCase;
import com.mmx.order.application.port.in.ListTermCounterpartiesUseCase;
import com.mmx.order.application.port.in.ListTermCurrenciesUseCase;
import com.mmx.order.application.port.in.ListTermOperationsUseCase;
import com.mmx.order.application.port.in.ListTermTenorsUseCase;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class OrderCreationOptionsControllerTest {

    @Mock
    ListTermCurrenciesUseCase listTermCurrenciesUseCase;

    @Mock
    ListOnCallCurrenciesUseCase listOnCallCurrenciesUseCase;

    @Mock
    ListTermOperationsUseCase listTermOperationsUseCase;

    @Mock
    ListOnCallOperationsUseCase listOnCallOperationsUseCase;

    @Mock
    ListTermTenorsUseCase listTermTenorsUseCase;

    @Mock
    ListOnCallNoticePeriodsUseCase listOnCallNoticePeriodsUseCase;

    @Mock
    ListTermCounterpartiesUseCase listTermCounterpartiesUseCase;

    @Mock
    ListOnCallCounterpartiesUseCase listOnCallCounterpartiesUseCase;

    @Mock
    GetContractInfoUseCase getContractInfoUseCase;

    @Mock
    ListLiveContractsUseCase listLiveContractsUseCase;

    org.springframework.test.web.servlet.MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                standaloneSetup(
                                new OrderCreationOptionsController(
                                        listTermCurrenciesUseCase,
                                        listOnCallCurrenciesUseCase,
                                        listTermOperationsUseCase,
                                        listOnCallOperationsUseCase,
                                        listTermTenorsUseCase,
                                        listOnCallNoticePeriodsUseCase,
                                        listTermCounterpartiesUseCase,
                                        listOnCallCounterpartiesUseCase,
                                        getContractInfoUseCase,
                                        listLiveContractsUseCase,
                                        new OrderCreationRestMapper()))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void listLiveContracts_returns200WithLiveContractsResponse() throws Exception {
        when(listLiveContractsUseCase.listLiveContracts("PF-001", OrderType.ON_CALL))
                .thenReturn(
                        new LiveContractsResult(
                                List.of(
                                        new LiveContractResult(
                                                "CT-00042",
                                                OrderType.ON_CALL,
                                                "EUR",
                                                NoticePeriod._24H,
                                                null,
                                                LocalDate.of(2026, 6, 1),
                                                null,
                                                new BigDecimal("5000000.00")))));

        mockMvc.perform(
                        get("/api/v1/order-creation/contracts")
                                .param("portfolioNumber", "PF-001")
                                .param("orderType", "ON_CALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contracts.length()").value(1))
                .andExpect(jsonPath("$.contracts[0].contractNumber").value("CT-00042"))
                .andExpect(jsonPath("$.contracts[0].orderType").value("ON_CALL"))
                .andExpect(jsonPath("$.contracts[0].currency").value("EUR"))
                .andExpect(jsonPath("$.contracts[0].noticePeriod").value("24H"))
                .andExpect(jsonPath("$.contracts[0].valueDate").value("2026-06-01"))
                .andExpect(jsonPath("$.contracts[0].originalAmount").value(5000000.0));

        verify(listLiveContractsUseCase).listLiveContracts("PF-001", OrderType.ON_CALL);
    }

    @Test
    void listLiveContracts_missingPortfolioNumber_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/order-creation/contracts").param("orderType", "ON_CALL"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listLiveContracts_missingOrderType_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/order-creation/contracts").param("portfolioNumber", "PF-001"))
                .andExpect(status().isBadRequest());
    }
}
