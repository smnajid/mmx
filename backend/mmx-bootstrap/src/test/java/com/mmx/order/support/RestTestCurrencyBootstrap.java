package com.mmx.order.support;

import com.mmx.order.application.exception.CurrencyNotFoundException;
import com.mmx.order.application.port.in.ManageCurrencySettingsUseCase;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumSet;

/**
 * Seeds permissive EUR/USD catalog entries for REST integration tests (strict cold start).
 */
@Component
@Profile("rest-test")
public class RestTestCurrencyBootstrap implements ApplicationRunner {

    private final ManageCurrencySettingsUseCase manageCurrencySettingsUseCase;

    public RestTestCurrencyBootstrap(ManageCurrencySettingsUseCase manageCurrencySettingsUseCase) {
        this.manageCurrencySettingsUseCase = manageCurrencySettingsUseCase;
    }

    @Override
    public void run(ApplicationArguments args) {
        onboardIfMissing("EUR");
        onboardIfMissing("USD");
    }

    private void onboardIfMissing(String code) {
        try {
            manageCurrencySettingsUseCase.getByCode(code);
        } catch (CurrencyNotFoundException ex) {
            manageCurrencySettingsUseCase.onboard(
                    new ManageCurrencySettingsUseCase.OnboardCommand(
                            code,
                            new BigDecimal("1.00"),
                            new BigDecimal("1.00"),
                            EnumSet.allOf(Tenor.class),
                            EnumSet.allOf(NoticePeriod.class)));
        }
    }
}
