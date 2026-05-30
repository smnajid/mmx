package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.Institution;

import java.util.List;

public interface ManageInstitutionSettingsUseCase {

    List<Institution> listAll(boolean activeOnly);

    Institution getByCode(String institutionCode);

    Institution onboard(OnboardCommand command);

    Institution deactivate(String institutionCode);

    Institution activate(String institutionCode);

    record OnboardCommand(String displayName) {}
}
