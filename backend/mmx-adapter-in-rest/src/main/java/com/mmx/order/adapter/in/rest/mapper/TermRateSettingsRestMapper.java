package com.mmx.order.adapter.in.rest.mapper;

import com.mmx.order.adapter.in.rest.generated.termrate.model.TenorCode;
import com.mmx.order.adapter.in.rest.generated.termrate.model.TermRateResponse;
import com.mmx.order.adapter.in.rest.generated.termrate.model.TermRateTradingDayResponse;
import com.mmx.order.adapter.in.rest.generated.termrate.model.TermRateUploadResponse;
import com.mmx.order.application.port.in.UploadTermRatesUseCase;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.application.termrate.TermRateRowError;
import com.mmx.order.domain.model.Tenor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class TermRateSettingsRestMapper {

    public TermRateUploadResponse toUploadResponse(UploadTermRatesUseCase.UploadResult result) {
        return new TermRateUploadResponse()
                .tradingDate(result.tradingDate())
                .rowCount(result.rowCount())
                .uploadedAt(result.uploadedAt().atOffset(ZoneOffset.UTC));
    }

    public TermRateResponse toResponse(TermRateAuditRow row) {
        return new TermRateResponse()
                .tradingDate(row.tradingDate())
                .institutionCode(row.institutionCode())
                .currency(row.currency())
                .tenor(toTenorCode(row.tenor()))
                .rate(row.rate().doubleValue())
                .uploadedAt(row.uploadedAt().atOffset(ZoneOffset.UTC))
                .uploadedBy(row.uploadedBy());
    }

    public TermRateTradingDayResponse toTradingDayResponse(LocalDate tradingDate) {
        return new TermRateTradingDayResponse().tradingDate(tradingDate);
    }

    public com.mmx.order.adapter.in.rest.generated.termrate.model.TermRateRowError toRowError(
            TermRateRowError error) {
        com.mmx.order.adapter.in.rest.generated.termrate.model.TermRateRowError dto =
                new com.mmx.order.adapter.in.rest.generated.termrate.model.TermRateRowError()
                        .line(error.line())
                        .message(error.message());
        if (error.field() != null) {
            dto.field(error.field());
        }
        return dto;
    }

    private TenorCode toTenorCode(Tenor tenor) {
        return TenorCode.fromValue(tenor.getCode());
    }
}
