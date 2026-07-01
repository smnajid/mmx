package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.generated.institution.api.InstitutionSettingsApi;
import com.mmx.order.adapter.in.rest.generated.institution.model.InstitutionResponse;
import com.mmx.order.adapter.in.rest.generated.institution.model.OnboardInstitutionRequest;
import com.mmx.order.adapter.in.rest.mapper.InstitutionSettingsRestMapper;
import com.mmx.order.application.port.in.InstitutionListView;
import com.mmx.order.application.port.in.ListInstitutionsUseCase;
import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase;
import com.mmx.order.application.port.in.OnboardInstitutionUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.ScopeContextProvider;
import com.mmx.order.application.port.out.ReferenceDataMutationGuard;
import com.mmx.order.domain.model.Institution;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class InstitutionSettingsController implements InstitutionSettingsApi {

    private final ManageInstitutionSettingsUseCase manageInstitutionSettingsUseCase;
    private final ListInstitutionsUseCase listInstitutionsUseCase;
    private final OnboardInstitutionUseCase onboardInstitutionUseCase;
    private final InstitutionSettingsRestMapper mapper;
    private final ScopeContextProvider scopeContextProvider;
    private final ReferenceDataMutationGuard mutationGuard;

    public InstitutionSettingsController(
            ManageInstitutionSettingsUseCase manageInstitutionSettingsUseCase,
            ListInstitutionsUseCase listInstitutionsUseCase,
            OnboardInstitutionUseCase onboardInstitutionUseCase,
            InstitutionSettingsRestMapper mapper,
            ScopeContextProvider scopeContextProvider,
            ReferenceDataMutationGuard mutationGuard) {
        this.manageInstitutionSettingsUseCase = manageInstitutionSettingsUseCase;
        this.listInstitutionsUseCase = listInstitutionsUseCase;
        this.onboardInstitutionUseCase = onboardInstitutionUseCase;
        this.mapper = mapper;
        this.scopeContextProvider = scopeContextProvider;
        this.mutationGuard = mutationGuard;
    }

    @Override
    public ResponseEntity<List<InstitutionResponse>> listInstitutions(String xTraderId, Boolean activeOnly) {
        ScopeContext scope = scopeContextProvider.requireActiveScope();
        InstitutionListView view = listInstitutionsUseCase.list(scope);
        boolean filterActive = Boolean.TRUE.equals(activeOnly);
        List<InstitutionResponse> body =
                switch (view) {
                    case InstitutionListView.Native n ->
                            n.institutions().stream()
                                    .filter(i -> !filterActive || i.isActive())
                                    .map(mapper::toResponse)
                                    .toList();
                    case InstitutionListView.Proxies p ->
                            p.proxies().stream()
                                    .filter(i -> !filterActive || i.isActive())
                                    .map(mapper::toResponse)
                                    .toList();
                };
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<InstitutionResponse> getInstitution(String xTraderId, String institutionCode) {
        return ResponseEntity.ok(mapper.toResponse(manageInstitutionSettingsUseCase.getByCode(institutionCode)));
    }

    @Override
    public ResponseEntity<InstitutionResponse> onboardInstitution(
            String xTraderId, OnboardInstitutionRequest onboardInstitutionRequest) {
        ScopeContext scope = scopeContextProvider.requireActiveScope();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toResponse(onboardInstitutionUseCase.onboard(
                        mapper.toOnboardCommand(scope, onboardInstitutionRequest))));
    }

    @Override
    public ResponseEntity<InstitutionResponse> deactivateInstitution(String xTraderId, String institutionCode) {
        ScopeContext scope = scopeContextProvider.requireActiveScope();
        mutationGuard.ensureTrader(scope);
        Institution deactivated = manageInstitutionSettingsUseCase.deactivate(institutionCode);
        return ResponseEntity.ok(mapper.toResponse(deactivated));
    }

    @Override
    public ResponseEntity<InstitutionResponse> activateInstitution(String xTraderId, String institutionCode) {
        ScopeContext scope = scopeContextProvider.requireActiveScope();
        mutationGuard.ensureTrader(scope);
        Institution activated = manageInstitutionSettingsUseCase.activate(institutionCode);
        return ResponseEntity.ok(mapper.toResponse(activated));
    }
}
