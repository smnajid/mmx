package com.mmx.order.adapter.in.rest.mapper;

import com.mmx.order.adapter.in.rest.generated.settings.model.ManagedCurrencyResponse;
import com.mmx.order.adapter.in.rest.generated.settings.model.NoticePeriodCode;
import com.mmx.order.adapter.in.rest.generated.settings.model.OnboardCurrencyRequest;
import com.mmx.order.adapter.in.rest.generated.settings.model.TenorCode;
import com.mmx.order.adapter.in.rest.generated.settings.model.UpdateCurrencyRulesRequest;
import com.mmx.order.application.port.in.ManageCurrencySettingsUseCase;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CurrencySettingsRestMapper {

    public ManagedCurrencyResponse toResponse(ManagedCurrency currency) {
        return new ManagedCurrencyResponse()
                .code(currency.getCode())
                .active(currency.isActive())
                .minSubscriptionAmount(currency.getMinSubscriptionAmount())
                .minIncreaseDecreaseAmount(currency.getMinIncreaseDecreaseAmount())
                .enabledTenors(currency.getEnabledTenors().stream().map(this::toTenorCode).collect(Collectors.toList()))
                .enabledNoticePeriods(
                        currency.getEnabledNoticePeriods().stream()
                                .map(this::toNoticeCode)
                                .collect(Collectors.toList()));
    }

    public ManageCurrencySettingsUseCase.OnboardCommand toOnboardCommand(OnboardCurrencyRequest request) {
        return new ManageCurrencySettingsUseCase.OnboardCommand(
                request.getCode(),
                request.getMinSubscriptionAmount(),
                request.getMinIncreaseDecreaseAmount(),
                toTenorSet(request.getEnabledTenors()),
                toNoticeSet(request.getEnabledNoticePeriods()));
    }

    public ManageCurrencySettingsUseCase.UpdateRulesCommand toUpdateCommand(UpdateCurrencyRulesRequest request) {
        return new ManageCurrencySettingsUseCase.UpdateRulesCommand(
                request.getMinSubscriptionAmount(),
                request.getMinIncreaseDecreaseAmount(),
                optionalTenorSet(request.getEnabledTenors()),
                optionalNoticeSet(request.getEnabledNoticePeriods()));
    }

    /** PATCH: absent field leaves rules unchanged; empty list clears that workspace. */
    private Set<Tenor> optionalTenorSet(java.util.List<TenorCode> codes) {
        if (codes == null) {
            return null;
        }
        if (codes.isEmpty()) {
            return EnumSet.noneOf(Tenor.class);
        }
        return toTenorSet(codes);
    }

    private Set<NoticePeriod> optionalNoticeSet(java.util.List<NoticePeriodCode> codes) {
        if (codes == null) {
            return null;
        }
        if (codes.isEmpty()) {
            return EnumSet.noneOf(NoticePeriod.class);
        }
        return toNoticeSet(codes);
    }

    private Set<Tenor> toTenorSet(java.util.List<TenorCode> codes) {
        EnumSet<Tenor> set = EnumSet.noneOf(Tenor.class);
        for (TenorCode code : codes) {
            set.add(fromTenorCode(code));
        }
        return set;
    }

    private Set<NoticePeriod> toNoticeSet(java.util.List<NoticePeriodCode> codes) {
        EnumSet<NoticePeriod> set = EnumSet.noneOf(NoticePeriod.class);
        for (NoticePeriodCode code : codes) {
            set.add(fromNoticeCode(code));
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
