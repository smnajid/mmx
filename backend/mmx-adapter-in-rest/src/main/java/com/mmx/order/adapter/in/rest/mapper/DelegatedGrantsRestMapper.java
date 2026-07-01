package com.mmx.order.adapter.in.rest.mapper;

import com.mmx.order.adapter.in.rest.generated.grants.model.CreateDelegatedGrantRequest;
import com.mmx.order.adapter.in.rest.generated.grants.model.DelegatedGrantResponse;
import com.mmx.order.adapter.in.rest.generated.grants.model.NoticePeriodCode;
import com.mmx.order.adapter.in.rest.generated.grants.model.TenorCode;
import com.mmx.order.adapter.in.rest.generated.grants.model.UpdateDelegatedGrantRequest;
import com.mmx.order.application.port.in.ManageDelegatedGrantsUseCase;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DelegatedGrantsRestMapper {

    public DelegatedGrantResponse toResponse(DelegatedInstitutionGrant grant) {
        return new DelegatedGrantResponse()
                .hubInstitutionCode(grant.getHubInstitutionCode())
                .clientLegalEntityCode(grant.getClientLegalEntityCode().value())
                .currency(grant.getCurrency())
                .enabledTenors(grant.getEnabledTenors().stream().map(this::toTenorCode).collect(Collectors.toList()))
                .enabledNoticePeriods(
                        grant.getEnabledNoticePeriods().stream()
                                .map(this::toNoticeCode)
                                .collect(Collectors.toList()))
                .active(grant.isActive());
    }

    public ManageDelegatedGrantsUseCase.CreateGrantCommand toCreateCommand(
            com.mmx.order.application.port.in.ScopeContext scope, CreateDelegatedGrantRequest request) {
        return new ManageDelegatedGrantsUseCase.CreateGrantCommand(
                scope,
                request.getHubInstitutionCode(),
                new LegalEntityCode(request.getClientLegalEntityCode()),
                request.getCurrency(),
                toTenorSet(request.getEnabledTenors()),
                toNoticeSet(request.getEnabledNoticePeriods()));
    }

    public ManageDelegatedGrantsUseCase.UpdateGrantCommand toUpdateCommand(
            com.mmx.order.application.port.in.ScopeContext scope,
            String hubInstitutionCode,
            LegalEntityCode clientLegalEntityCode,
            String currency,
            UpdateDelegatedGrantRequest request) {
        return new ManageDelegatedGrantsUseCase.UpdateGrantCommand(
                scope,
                hubInstitutionCode,
                clientLegalEntityCode,
                currency,
                optionalTenorSet(request.getEnabledTenors()),
                optionalNoticeSet(request.getEnabledNoticePeriods()));
    }

    private Set<Tenor> optionalTenorSet(List<TenorCode> codes) {
        if (codes == null) {
            return null;
        }
        if (codes.isEmpty()) {
            return EnumSet.noneOf(Tenor.class);
        }
        return toTenorSet(codes);
    }

    private Set<NoticePeriod> optionalNoticeSet(List<NoticePeriodCode> codes) {
        if (codes == null) {
            return null;
        }
        if (codes.isEmpty()) {
            return EnumSet.noneOf(NoticePeriod.class);
        }
        return toNoticeSet(codes);
    }

    private Set<Tenor> toTenorSet(List<TenorCode> codes) {
        EnumSet<Tenor> set = EnumSet.noneOf(Tenor.class);
        if (codes != null) {
            for (TenorCode code : codes) {
                set.add(fromTenorCode(code));
            }
        }
        return set;
    }

    private Set<NoticePeriod> toNoticeSet(List<NoticePeriodCode> codes) {
        EnumSet<NoticePeriod> set = EnumSet.noneOf(NoticePeriod.class);
        if (codes != null) {
            for (NoticePeriodCode code : codes) {
                set.add(fromNoticeCode(code));
            }
        }
        return set;
    }

    private TenorCode toTenorCode(Tenor tenor) {
        return TenorCode.fromValue(tenor.getCode());
    }

    private NoticePeriodCode toNoticeCode(NoticePeriod notice) {
        return NoticePeriodCode.fromValue(notice.getCode());
    }

    private Tenor fromTenorCode(TenorCode code) {
        return switch (code) {
            case _1_W -> Tenor._1W;
            case _2_W -> Tenor._2W;
            case _1_M -> Tenor._1M;
            case _3_M -> Tenor._3M;
            case _6_M -> Tenor._6M;
            case _1_Y -> Tenor._1Y;
        };
    }

    private NoticePeriod fromNoticeCode(NoticePeriodCode code) {
        return switch (code) {
            case _24_H -> NoticePeriod._24H;
            case _48_H -> NoticePeriod._48H;
        };
    }
}
