package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.generated.institution.api.InstitutionSettingsApi;
import com.mmx.order.adapter.in.rest.generated.institution.model.InstitutionResponse;
import com.mmx.order.adapter.in.rest.generated.institution.model.OnboardInstitutionRequest;
import com.mmx.order.adapter.in.rest.mapper.InstitutionSettingsRestMapper;
import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase;
import com.mmx.order.domain.model.Institution;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class InstitutionSettingsController implements InstitutionSettingsApi {

    private final ManageInstitutionSettingsUseCase manageInstitutionSettingsUseCase;
    private final InstitutionSettingsRestMapper mapper;

    public InstitutionSettingsController(
            ManageInstitutionSettingsUseCase manageInstitutionSettingsUseCase,
            InstitutionSettingsRestMapper mapper) {
        this.manageInstitutionSettingsUseCase = manageInstitutionSettingsUseCase;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<InstitutionResponse>> listInstitutions(String xTraderId, Boolean activeOnly) {
        boolean filterActive = Boolean.TRUE.equals(activeOnly);
        List<InstitutionResponse> body =
                manageInstitutionSettingsUseCase.listAll(filterActive).stream()
                        .map(mapper::toResponse)
                        .toList();
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<InstitutionResponse> getInstitution(String xTraderId, String institutionCode) {
        return ResponseEntity.ok(mapper.toResponse(manageInstitutionSettingsUseCase.getByCode(institutionCode)));
    }

    @Override
    public ResponseEntity<InstitutionResponse> onboardInstitution(
            String xTraderId, OnboardInstitutionRequest onboardInstitutionRequest) {
        Institution created =
                manageInstitutionSettingsUseCase.onboard(mapper.toOnboardCommand(onboardInstitutionRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    @Override
    public ResponseEntity<InstitutionResponse> deactivateInstitution(String xTraderId, String institutionCode) {
        return ResponseEntity.ok(mapper.toResponse(manageInstitutionSettingsUseCase.deactivate(institutionCode)));
    }

    @Override
    public ResponseEntity<InstitutionResponse> activateInstitution(String xTraderId, String institutionCode) {
        return ResponseEntity.ok(mapper.toResponse(manageInstitutionSettingsUseCase.activate(institutionCode)));
    }
}
