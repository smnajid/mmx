package com.mmx.order.adapter.in.rest.mapper;

import com.mmx.order.adapter.in.rest.generated.institution.model.InstitutionResponse;
import com.mmx.order.adapter.in.rest.generated.institution.model.OnboardInstitutionRequest;
import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase;
import com.mmx.order.domain.model.Institution;
import org.springframework.stereotype.Component;

@Component
public class InstitutionSettingsRestMapper {

    public InstitutionResponse toResponse(Institution institution) {
        return new InstitutionResponse()
                .institutionCode(institution.getInstitutionCode())
                .displayName(institution.getDisplayName())
                .active(institution.isActive());
    }

    public ManageInstitutionSettingsUseCase.OnboardCommand toOnboardCommand(OnboardInstitutionRequest request) {
        return new ManageInstitutionSettingsUseCase.OnboardCommand(request.getDisplayName());
    }
}
