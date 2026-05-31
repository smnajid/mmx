package com.mmx.order.adapter.in.rest;

import com.mmx.order.application.port.in.ConfirmOnCallRateUseCase;
import com.mmx.order.domain.exception.OnCallSegmentCanceledException;
import com.mmx.order.domain.exception.OnCallSegmentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class OnCallRateConfirmationCallbackControllerTest {

    @Mock
    ConfirmOnCallRateUseCase confirmOnCallRateUseCase;

    org.springframework.test.web.servlet.MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                standaloneSetup(new OnCallRateConfirmationCallbackController(confirmOnCallRateUseCase))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void confirm_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(confirmOnCallRateUseCase.confirm(id)).thenReturn(ConfirmOnCallRateUseCase.Outcome.CONFIRMED);

        mockMvc.perform(post("/api/v1/back-office/oncall-rates/" + id + "/confirmed"))
                .andExpect(status().isOk());
    }

    @Test
    void confirm_canceled_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(confirmOnCallRateUseCase.confirm(id)).thenThrow(new OnCallSegmentCanceledException(id));

        mockMvc.perform(post("/api/v1/back-office/oncall-rates/" + id + "/confirmed"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ONCALL_SEGMENT_CANCELED"));
    }

    @Test
    void confirm_unknown_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(confirmOnCallRateUseCase.confirm(id)).thenThrow(new OnCallSegmentNotFoundException(id));

        mockMvc.perform(post("/api/v1/back-office/oncall-rates/" + id + "/confirmed"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ONCALL_SEGMENT_NOT_FOUND"));
    }
}
