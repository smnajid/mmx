package com.mmx.order.application.service;

import com.mmx.order.application.port.in.InstitutionListView;
import com.mmx.order.application.port.in.ListInstitutionsUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ProxyInstitutionRepository;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.Role;

public final class ListInstitutionsService implements ListInstitutionsUseCase {

    private final InstitutionRepository institutionRepository;
    private final ProxyInstitutionRepository proxyRepository;

    public ListInstitutionsService(
            InstitutionRepository institutionRepository, ProxyInstitutionRepository proxyRepository) {
        this.institutionRepository = institutionRepository;
        this.proxyRepository = proxyRepository;
    }

    @Override
    public InstitutionListView list(ScopeContext scope) {
        if (scope == null) {
            throw new UnauthorizedUserException("Active scope is required");
        }
        if (scope.role() == Role.TRADER) {
            return new InstitutionListView.Native(
                    institutionRepository.findNativeByLegalEntityCode(scope.legalEntityCode()));
        }
        if (scope.role() == Role.CLIENT_REPRESENTATIVE) {
            return new InstitutionListView.Proxies(
                    proxyRepository.findByClientLegalEntity(scope.legalEntityCode()));
        }
        throw new UnauthorizedUserException("Unsupported role for institution list");
    }
}
