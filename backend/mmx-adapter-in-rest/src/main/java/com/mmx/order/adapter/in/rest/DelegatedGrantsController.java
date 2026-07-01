package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.generated.grants.api.DelegatedGrantsApi;
import com.mmx.order.adapter.in.rest.generated.grants.model.CreateDelegatedGrantRequest;
import com.mmx.order.adapter.in.rest.generated.grants.model.DelegatedGrantResponse;
import com.mmx.order.adapter.in.rest.generated.grants.model.UpdateDelegatedGrantRequest;
import com.mmx.order.adapter.in.rest.mapper.DelegatedGrantsRestMapper;
import com.mmx.order.application.port.in.ManageDelegatedGrantsUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.ScopeContextProvider;
import com.mmx.order.domain.model.DelegatedGrantKey;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.LegalEntityCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DelegatedGrantsController implements DelegatedGrantsApi {

    private final ManageDelegatedGrantsUseCase manageDelegatedGrantsUseCase;
    private final DelegatedGrantsRestMapper mapper;
    private final ScopeContextProvider scopeContextProvider;

    public DelegatedGrantsController(
            ManageDelegatedGrantsUseCase manageDelegatedGrantsUseCase,
            DelegatedGrantsRestMapper mapper,
            ScopeContextProvider scopeContextProvider) {
        this.manageDelegatedGrantsUseCase = manageDelegatedGrantsUseCase;
        this.mapper = mapper;
        this.scopeContextProvider = scopeContextProvider;
    }

    @Override
    public ResponseEntity<List<DelegatedGrantResponse>> listDelegatedGrants(String xUserId) {
        ScopeContext scope = scopeContextProvider.requireActiveScope();
        List<DelegatedGrantResponse> body =
                manageDelegatedGrantsUseCase.listGrants(scope).stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<List<DelegatedGrantResponse>> listClientDelegatedGrants(String xUserId) {
        ScopeContext scope = scopeContextProvider.requireActiveScope();
        List<DelegatedGrantResponse> body =
                manageDelegatedGrantsUseCase.listClientGrants(scope).stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<DelegatedGrantResponse> getDelegatedGrant(
            String xUserId, String hubInstitutionCode, String clientLegalEntityCode, String currency) {
        ScopeContext scope = scopeContextProvider.requireActiveScope();
        DelegatedInstitutionGrant grant = manageDelegatedGrantsUseCase.getByKey(
                new DelegatedGrantKey(hubInstitutionCode, new LegalEntityCode(clientLegalEntityCode), currency),
                scope);
        return ResponseEntity.ok(mapper.toResponse(grant));
    }

    @Override
    public ResponseEntity<DelegatedGrantResponse> createDelegatedGrant(
            String xUserId, CreateDelegatedGrantRequest createDelegatedGrantRequest) {
        ScopeContext scope = scopeContextProvider.requireActiveScope();
        DelegatedInstitutionGrant created =
                manageDelegatedGrantsUseCase.createGrant(
                        mapper.toCreateCommand(scope, createDelegatedGrantRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    @Override
    public ResponseEntity<DelegatedGrantResponse> updateDelegatedGrant(
            String xUserId,
            String hubInstitutionCode,
            String clientLegalEntityCode,
            String currency,
            UpdateDelegatedGrantRequest updateDelegatedGrantRequest) {
        ScopeContext scope = scopeContextProvider.requireActiveScope();
        DelegatedInstitutionGrant updated =
                manageDelegatedGrantsUseCase.updateGrant(
                        mapper.toUpdateCommand(
                                scope,
                                hubInstitutionCode,
                                new LegalEntityCode(clientLegalEntityCode),
                                currency,
                                updateDelegatedGrantRequest));
        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<DelegatedGrantResponse> deactivateDelegatedGrant(
            String xUserId, String hubInstitutionCode, String clientLegalEntityCode, String currency) {
        ScopeContext scope = scopeContextProvider.requireActiveScope();
        DelegatedInstitutionGrant deactivated =
                manageDelegatedGrantsUseCase.deactivateGrant(
                        new DelegatedGrantKey(hubInstitutionCode, new LegalEntityCode(clientLegalEntityCode), currency),
                        scope);
        return ResponseEntity.ok(mapper.toResponse(deactivated));
    }

    @Override
    public ResponseEntity<DelegatedGrantResponse> reactivateDelegatedGrant(
            String xUserId, String hubInstitutionCode, String clientLegalEntityCode, String currency) {
        ScopeContext scope = scopeContextProvider.requireActiveScope();
        DelegatedInstitutionGrant reactivated =
                manageDelegatedGrantsUseCase.reactivateGrant(
                        new DelegatedGrantKey(hubInstitutionCode, new LegalEntityCode(clientLegalEntityCode), currency),
                        scope);
        return ResponseEntity.ok(mapper.toResponse(reactivated));
    }
}
