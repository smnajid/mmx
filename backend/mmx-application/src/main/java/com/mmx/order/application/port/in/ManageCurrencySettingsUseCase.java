package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public interface ManageCurrencySettingsUseCase {

    List<com.mmx.order.domain.model.ManagedCurrency> listAll();

    com.mmx.order.domain.model.ManagedCurrency getByCode(String code);

    com.mmx.order.domain.model.ManagedCurrency onboard(OnboardCommand command);

    com.mmx.order.domain.model.ManagedCurrency updateRules(String code, UpdateRulesCommand command);

    com.mmx.order.domain.model.ManagedCurrency disable(String code);

    com.mmx.order.domain.model.ManagedCurrency enable(String code);

    record OnboardCommand(
            String code,
            BigDecimal minSubscriptionAmount,
            BigDecimal minIncreaseDecreaseAmount,
            Set<Tenor> enabledTenors,
            Set<NoticePeriod> enabledNoticePeriods) {}

    record UpdateRulesCommand(
            BigDecimal minSubscriptionAmount,
            BigDecimal minIncreaseDecreaseAmount,
            Set<Tenor> enabledTenors,
            Set<NoticePeriod> enabledNoticePeriods) {}
}
