package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.mapper.CurrencySettingsRestMapper;
import com.mmx.order.application.port.in.ManageCurrencySettingsUseCase;
import com.mmx.order.domain.exception.DuplicateManagedCurrencyException;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class CurrencySettingsControllerTest {

    @Mock
    ManageCurrencySettingsUseCase manageCurrencySettingsUseCase;

    org.springframework.test.web.servlet.MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                standaloneSetup(
                                new CurrencySettingsController(
                                        manageCurrencySettingsUseCase, new CurrencySettingsRestMapper()))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void listManagedCurrencies_returns200() throws Exception {
        when(manageCurrencySettingsUseCase.listAll()).thenReturn(List.of(sampleEur()));

        mockMvc.perform(get("/api/v1/settings/currencies").header("X-Trader-Id", "trader-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("EUR"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void onboardManagedCurrency_returns201() throws Exception {
        when(manageCurrencySettingsUseCase.onboard(any())).thenReturn(sampleEur());

        mockMvc.perform(
                        post("/api/v1/settings/currencies")
                                .header("X-Trader-Id", "trader-a")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "code": "EUR",
                                          "minSubscriptionAmount": 1000000.00,
                                          "minIncreaseDecreaseAmount": 250000.00,
                                          "enabledTenors": ["3M"],
                                          "enabledNoticePeriods": ["24H"]
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("EUR"));
    }

    @Test
    void onboard_duplicate_returns409() throws Exception {
        when(manageCurrencySettingsUseCase.onboard(any())).thenThrow(new DuplicateManagedCurrencyException("EUR"));

        mockMvc.perform(
                        post("/api/v1/settings/currencies")
                                .header("X-Trader-Id", "trader-a")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "code": "EUR",
                                          "minSubscriptionAmount": 1000000.00,
                                          "minIncreaseDecreaseAmount": 250000.00,
                                          "enabledTenors": ["3M"],
                                          "enabledNoticePeriods": ["24H"]
                                        }
                                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_CURRENCY"));
    }

    @Test
    void update_emptyTenors_returns400() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/settings/currencies/EUR")
                                .header("X-Trader-Id", "trader-a")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "enabledTenors": []
                                        }
                                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disableManagedCurrency_returns200() throws Exception {
        when(manageCurrencySettingsUseCase.disable("EUR")).thenReturn(sampleEur().withActive(false));

        mockMvc.perform(
                        post("/api/v1/settings/currencies/EUR/disable").header("X-Trader-Id", "trader-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(manageCurrencySettingsUseCase).disable("EUR");
    }

    private static ManagedCurrency sampleEur() {
        return new ManagedCurrency(
                "EUR",
                true,
                new BigDecimal("1000000.00"),
                new BigDecimal("250000.00"),
                EnumSet.allOf(Tenor.class),
                EnumSet.allOf(NoticePeriod.class));
    }
}
