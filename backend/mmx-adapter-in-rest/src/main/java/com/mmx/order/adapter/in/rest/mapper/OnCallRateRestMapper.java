package com.mmx.order.adapter.in.rest.mapper;

import com.mmx.order.adapter.in.rest.generated.model.AddOnCallRateRequest;
import com.mmx.order.adapter.in.rest.generated.model.OnCallNoticePeriod;
import com.mmx.order.adapter.in.rest.generated.model.OnCallRateSegmentResponse;
import com.mmx.order.adapter.in.rest.generated.model.OnCallRateSegmentStatus;
import com.mmx.order.application.command.AddOnCallRateCommand;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallRateSegment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class OnCallRateRestMapper {

    public AddOnCallRateCommand toAddCommand(String institutionCode, AddOnCallRateRequest request) {
        return new AddOnCallRateCommand(
                institutionCode,
                request.getCurrency(),
                toDomainNoticePeriod(request.getNoticePeriod()),
                BigDecimal.valueOf(request.getRate()),
                request.getValueDate());
    }

    public OnCallRateSegmentResponse toResponse(OnCallRateSegment segment) {
        return new OnCallRateSegmentResponse()
                .segmentId(segment.getSegmentId())
                .institutionCode(segment.getCurveKey().institutionCode())
                .currency(segment.getCurveKey().currency())
                .noticePeriod(toApiNoticePeriod(segment.getCurveKey().noticePeriod()))
                .rate(segment.getRate().doubleValue())
                .valueDate(segment.getValueDate())
                .endDate(segment.getEndDate())
                .status(toApiStatus(segment.getStatus()))
                .validatedAt(toOffsetDateTime(segment.getValidatedAt()));
    }

    private static NoticePeriod toDomainNoticePeriod(OnCallNoticePeriod noticePeriod) {
        return switch (noticePeriod) {
            case _24_H -> NoticePeriod._24H;
            case _48_H -> NoticePeriod._48H;
        };
    }

    private static OnCallNoticePeriod toApiNoticePeriod(NoticePeriod noticePeriod) {
        return switch (noticePeriod) {
            case _24H -> OnCallNoticePeriod._24_H;
            case _48H -> OnCallNoticePeriod._48_H;
        };
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }

    private static OnCallRateSegmentStatus toApiStatus(
            com.mmx.order.domain.model.OnCallRateSegmentStatus status) {
        return switch (status) {
            case PENDING_CONFIRMATION -> OnCallRateSegmentStatus.PENDING_CONFIRMATION;
            case VALID -> OnCallRateSegmentStatus.VALID;
            case CANCELED -> OnCallRateSegmentStatus.CANCELED;
        };
    }
}
