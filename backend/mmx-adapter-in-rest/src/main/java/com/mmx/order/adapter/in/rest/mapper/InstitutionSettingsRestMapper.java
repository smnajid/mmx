package com.mmx.order.adapter.in.rest.mapper;

import com.mmx.order.adapter.in.rest.generated.institution.model.InstitutionResponse;
import com.mmx.order.adapter.in.rest.generated.institution.model.OnboardInstitutionRequest;
import com.mmx.order.application.port.in.OnboardInstitutionUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.domain.model.Institution;
import org.springframework.stereotype.Component;

@Component
public class InstitutionSettingsRestMapper {

    public InstitutionResponse toResponse(Institution institution) {
        InstitutionResponse response =
                new InstitutionResponse()
                        .institutionCode(institution.getInstitutionCode())
                        .displayName(institution.getDisplayName())
                        .active(institution.isActive())
                        .closedToNewBusiness(institution.isClosedToNewBusiness())
                        .termCounterpartyAccount(institution.getCounterpartyAccounts().term().orElse(null))
                        .onCallCounterpartyAccount(institution.getCounterpartyAccounts().onCall().orElse(null));
        institution.getHubLink().ifPresent(link -> response
                .hubInstitutionCode(link.hubInstitutionCode())
                .hubLegalEntityCode(link.hubLegalEntityCode().value()));
        return response;
    }

    public OnboardInstitutionUseCase.OnboardCommand toOnboardCommand(
            ScopeContext scope, OnboardInstitutionRequest request) {
        return new OnboardInstitutionUseCase.OnboardCommand(
                scope,
                request.getDisplayName(),
                request.getHubInstitutionCode(),
                request.getTermCounterpartyAccount(),
                request.getOnCallCounterpartyAccount());
    }
}
