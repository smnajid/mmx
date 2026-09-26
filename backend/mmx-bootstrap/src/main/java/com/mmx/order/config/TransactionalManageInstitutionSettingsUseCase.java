package com.mmx.order.config;

import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.service.ManageInstitutionSettingsService;
import com.mmx.order.domain.model.Institution;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Native onboarding, deactivation/offboarding and reactivation/re-onboarding commit with their export row. */
@Service
@Primary
public class TransactionalManageInstitutionSettingsUseCase implements ManageInstitutionSettingsUseCase {

    private final ManageInstitutionSettingsService delegate;

    public TransactionalManageInstitutionSettingsUseCase(ManageInstitutionSettingsService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Institution> listAll(boolean activeOnly) {
        return delegate.listAll(activeOnly);
    }

    @Override
    @Transactional(readOnly = true)
    public Institution getByCode(String institutionCode) {
        return delegate.getByCode(institutionCode);
    }

    @Override
    @Transactional
    public Institution onboard(OnboardCommand command) {
        return delegate.onboard(command);
    }

    @Override
    @Transactional
    public Institution deactivate(ScopeContext scope, String institutionCode) {
        return delegate.deactivate(scope, institutionCode);
    }

    @Override
    @Transactional
    public Institution activate(ScopeContext scope, String institutionCode) {
        return delegate.activate(scope, institutionCode);
    }
}
