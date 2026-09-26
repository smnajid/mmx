package com.mmx.order.adapter.in.rest.mapper;

import com.mmx.order.adapter.in.rest.generated.crossorg.model.AcceptRoutedOrderRequest;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.NoticePeriodCode;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.OrderOperation;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.OrderType;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.RoutedOrderAcceptResponse;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.RoutedOrderRejectResponse;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.TenorCode;
import com.mmx.order.application.port.out.RemoteRoutingRequest;
import com.mmx.order.application.port.out.RemoteRoutingResponse;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.Tenor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class CrossOrgRoutingRestMapper {

    public RemoteRoutingRequest toRemoteRoutingRequest(
            AcceptRoutedOrderRequest request, LegalEntityCode provenOriginatingLegalEntityCode) {
        return new RemoteRoutingRequest(
                provenOriginatingLegalEntityCode,
                new RoutingId(request.getRoutingId()),
                new PortfolioNumber(request.getPortfolioNumber()),
                request.getInstitutionCode(),
                new ExternalOrderReference(request.getOriginatingExternalOrderReference()),
                request.getCurrency(),
                BigDecimal.valueOf(request.getAmount()),
                request.getValueDate(),
                com.mmx.order.domain.model.OrderType.valueOf(request.getOrderType().name()),
                com.mmx.order.domain.model.OrderOperation.valueOf(request.getOrderOperation().name()),
                request.getTenor() != null ? fromTenorCode(request.getTenor()) : null,
                request.getNoticePeriod() != null ? fromNoticeCode(request.getNoticePeriod()) : null,
                request.getMinimumRate() != null ? BigDecimal.valueOf(request.getMinimumRate()) : null,
                request.getSourceContractNumber() != null
                        ? new ContractNumber(request.getSourceContractNumber())
                        : null);
    }

    public RoutedOrderAcceptResponse toAcceptResponse(
            LegalEntityCode proven, UUID routingId, RemoteRoutingResponse.Accept accept) {
        return new RoutedOrderAcceptResponse()
                .outcome(RoutedOrderAcceptResponse.OutcomeEnum.ACCEPTED)
                .originatingLegalEntityCode(proven.value())
                .routingId(routingId)
                .acceptedAt(toOffsetDateTime(accept.acceptedAt()));
    }

    public RoutedOrderRejectResponse toRejectResponse(
            LegalEntityCode proven, UUID routingId, RemoteRoutingResponse.Reject reject) {
        return new RoutedOrderRejectResponse()
                .outcome(RoutedOrderRejectResponse.OutcomeEnum.REJECTED)
                .originatingLegalEntityCode(proven.value())
                .routingId(routingId)
                .reason(reject.reason());
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Tenor fromTenorCode(TenorCode code) {
        return Tenor.fromCode(code.getValue())
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenor: " + code.getValue()));
    }

    private static NoticePeriod fromNoticeCode(NoticePeriodCode code) {
        return switch (code) {
            case _24_H -> NoticePeriod._24H;
            case _48_H -> NoticePeriod._48H;
        };
    }
}
