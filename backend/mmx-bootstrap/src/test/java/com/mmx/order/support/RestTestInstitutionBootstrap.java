package com.mmx.order.support;

import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds institutions for REST integration tests (strict execute cold start).
 */
@Component
@Profile("rest-test")
public class RestTestInstitutionBootstrap implements ApplicationRunner {

    private final ManageInstitutionSettingsUseCase manageInstitutionSettingsUseCase;

    public RestTestInstitutionBootstrap(ManageInstitutionSettingsUseCase manageInstitutionSettingsUseCase) {
        this.manageInstitutionSettingsUseCase = manageInstitutionSettingsUseCase;
    }

    @Override
    public void run(ApplicationArguments args) {
        onboardIfMissing("BankCo International", RestTestInstitutions.BANKCO_CODE);
        onboardIfMissing("CP-OC", RestTestInstitutions.CP_OC_CODE);
    }

    private void onboardIfMissing(String displayName, String expectedCode) {
        boolean exists =
                manageInstitutionSettingsUseCase.listAll(false).stream()
                        .anyMatch(i -> expectedCode.equals(i.getInstitutionCode()));
        if (exists) {
            return;
        }
        var created =
                manageInstitutionSettingsUseCase.onboard(
                        new ManageInstitutionSettingsUseCase.OnboardCommand(displayName));
        if (!expectedCode.equals(created.getInstitutionCode())) {
            throw new IllegalStateException(
                    "Expected institution " + expectedCode + " but got " + created.getInstitutionCode());
        }
    }
}
