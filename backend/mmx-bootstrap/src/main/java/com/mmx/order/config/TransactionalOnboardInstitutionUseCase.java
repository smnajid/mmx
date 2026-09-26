package com.mmx.order.config;

import com.mmx.order.application.port.in.OnboardInstitutionUseCase;
import com.mmx.order.application.service.OnboardInstitutionService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Onboarding and re-onboarding commit with their institution export row. */
@Service
@Primary
public class TransactionalOnboardInstitutionUseCase implements OnboardInstitutionUseCase {

    private final OnboardInstitutionService delegate;

    public TransactionalOnboardInstitutionUseCase(OnboardInstitutionService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public Result onboard(OnboardCommand command) {
        return delegate.onboard(command);
    }
}
