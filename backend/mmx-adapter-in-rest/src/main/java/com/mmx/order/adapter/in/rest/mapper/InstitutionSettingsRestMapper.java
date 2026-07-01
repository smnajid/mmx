package com.mmx.order.adapter.in.rest.mapper;

import com.mmx.order.adapter.in.rest.generated.institution.model.InstitutionResponse;
import com.mmx.order.adapter.in.rest.generated.institution.model.OnboardInstitutionRequest;
import com.mmx.order.application.port.in.OnboardInstitutionUseCase;
import com.mmx.order.application.port.in.OnboardedInstitution;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.ThinProxyInstitution;
import org.springframework.stereotype.Component;

@Component
public class InstitutionSettingsRestMapper {

    public InstitutionResponse toResponse(Institution institution) {
        return new InstitutionResponse()
                .institutionCode(institution.getInstitutionCode())
                .displayName(institution.getDisplayName())
                .active(institution.isActive());
    }

    public InstitutionResponse toResponse(ThinProxyInstitution proxy) {
        return new InstitutionResponse()
                .institutionCode(proxy.getInstitutionCode())
                .displayName(proxy.getDisplayName())
                .active(proxy.isActive())
                .hubInstitutionCode(proxy.getHubInstitutionCode())
                .hubLegalEntityCode(proxy.getHubLegalEntityCode().value());
    }

    public InstitutionResponse toResponse(OnboardedInstitution onboarded) {
        return switch (onboarded) {
            case OnboardedInstitution.Native n -> toResponse(n.institution());
            case OnboardedInstitution.Proxy p -> toResponse(p.proxy());
        };
    }

    public OnboardInstitutionUseCase.OnboardCommand toOnboardCommand(
            ScopeContext scope, OnboardInstitutionRequest request) {
        return new OnboardInstitutionUseCase.OnboardCommand(
                scope, request.getDisplayName(), request.getHubInstitutionCode());
    }
}
