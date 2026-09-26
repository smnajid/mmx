package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.mapper.OnCallRateRestMapper;
import com.mmx.order.application.port.in.AddOnCallRateUseCase;
import com.mmx.order.application.port.in.CancelOnCallRateUseCase;
import com.mmx.order.application.port.in.ListOnCallRateSegmentsUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.ScopeContextProvider;
import com.mmx.order.application.port.out.ReferenceDataMutationGuard;
import com.mmx.order.domain.exception.OnCallBackdatedValueDateException;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.exception.OnCallPendingExistsException;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallCurveKey;
import com.mmx.order.domain.model.OnCallRateSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
@Tag("fast")

@ExtendWith(MockitoExtension.class)
class OnCallRateTraderControllerTest {

    @Mock
    AddOnCallRateUseCase addOnCallRateUseCase;

    @Mock
    CancelOnCallRateUseCase cancelOnCallRateUseCase;

    @Mock
    ListOnCallRateSegmentsUseCase listOnCallRateSegmentsUseCase;

    @Mock
    ScopeContextProvider scopeContextProvider;

    org.springframework.test.web.servlet.MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                standaloneSetup(
                                new OnCallRateTraderController(
                                        addOnCallRateUseCase,
                                        cancelOnCallRateUseCase,
                                        listOnCallRateSegmentsUseCase,
                                        new OnCallRateRestMapper(),
                                        scopeContextProvider,
                                        new ReferenceDataMutationGuard()))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void addOnCallRate_returns201() throws Exception {
        when(scopeContextProvider.requireActiveScope())
                .thenReturn(new ScopeContext(new LegalEntityCode("LOC"), Role.TRADER));
        UUID id = UUID.randomUUID();
        OnCallRateSegment segment =
                OnCallRateSegment.createPending(
                        id,
                        new OnCallCurveKey("HSBC-01", "EUR", NoticePeriod._24H),
                        new BigDecimal("3.25"),
                        LocalDate.of(2026, 6, 1));
        when(addOnCallRateUseCase.add(any())).thenReturn(segment);

        mockMvc.perform(
                        post("/api/v1/settings/institutions/HSBC-01/oncall-rates")
                                .header("X-User-Id", "trader-a")
                                .contentType("application/json")
                                .content(
                                        """
                                        {
                                          "currency": "EUR",
                                          "noticePeriod": "24H",
                                          "rate": 3.25,
                                          "valueDate": "2026-06-01"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.segmentId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"));
    }

    @Test
    void addOnCallRate_backdated_returns400() throws Exception {
        when(scopeContextProvider.requireActiveScope())
                .thenReturn(new ScopeContext(new LegalEntityCode("LOC"), Role.TRADER));
        when(addOnCallRateUseCase.add(any()))
                .thenThrow(new OnCallBackdatedValueDateException(LocalDate.of(2026, 5, 30), LocalDate.of(2026, 5, 31)));

        mockMvc.perform(
                        post("/api/v1/settings/institutions/HSBC-01/oncall-rates")
                                .header("X-User-Id", "trader-a")
                                .contentType("application/json")
                                .content(
                                        """
                                        {
                                          "currency": "EUR",
                                          "noticePeriod": "24H",
                                          "rate": 3.25,
                                          "valueDate": "2026-05-30"
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ONCALL_BACKDATED_VALUE_DATE"));
    }

    @Test
    void addOnCallRate_pendingExists_returns409() throws Exception {
        when(scopeContextProvider.requireActiveScope())
                .thenReturn(new ScopeContext(new LegalEntityCode("LOC"), Role.TRADER));
        when(addOnCallRateUseCase.add(any()))
                .thenThrow(
                        new OnCallPendingExistsException(
                                new OnCallCurveKey("HSBC-01", "EUR", NoticePeriod._24H)));

        mockMvc.perform(
                        post("/api/v1/settings/institutions/HSBC-01/oncall-rates")
                                .header("X-User-Id", "trader-a")
                                .contentType("application/json")
                                .content(
                                        """
                                        {
                                          "currency": "EUR",
                                          "noticePeriod": "24H",
                                          "rate": 3.30,
                                          "valueDate": "2026-06-02"
                                        }
                                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ONCALL_PENDING_EXISTS"));
    }

    @Test
    void cancelOnCallRate_returns200() throws Exception {
        when(scopeContextProvider.requireActiveScope())
                .thenReturn(new ScopeContext(new LegalEntityCode("LOC"), Role.TRADER));
        UUID id = UUID.randomUUID();
        when(cancelOnCallRateUseCase.cancel(any()))
                .thenReturn(
                        OnCallRateSegment.createPending(
                                        id,
                                        new OnCallCurveKey("HSBC-01", "EUR", NoticePeriod._48H),
                                        new BigDecimal("3.5"),
                                        LocalDate.of(2026, 6, 1))
                                .cancel());

        mockMvc.perform(
                        post("/api/v1/settings/institutions/HSBC-01/oncall-rates/" + id + "/cancel")
                                .header("X-User-Id", "trader-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void listOnCallRateSegments_returns200() throws Exception {
        when(listOnCallRateSegmentsUseCase.listByInstitution("HSBC-01")).thenReturn(List.of());

        mockMvc.perform(
                        get("/api/v1/settings/institutions/HSBC-01/oncall-rates")
                                .header("X-User-Id", "trader-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
