package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.domain.exception.InstitutionSuffixOverflowException;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.service.InstitutionCodeAcronym;

import java.util.List;

public final class ManageInstitutionSettingsService implements ManageInstitutionSettingsUseCase {

    private final InstitutionRepository repository;

    public ManageInstitutionSettingsService(InstitutionRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Institution> listAll(boolean activeOnly) {
        return activeOnly ? repository.findActive() : repository.findAll();
    }

    @Override
    public Institution getByCode(String institutionCode) {
        String code = Institution.validateCode(institutionCode);
        return repository
                .findByInstitutionCode(code)
                .orElseThrow(() -> new InstitutionNotFoundException(code));
    }

    @Override
    public Institution onboard(OnboardCommand command) {
        String displayName = Institution.validateDisplayName(command.displayName());
        String base = InstitutionCodeAcronym.deriveAcronym(displayName);
        int next = repository.maxSuffixForAcronym(base) + 1;
        if (next > 99) {
            throw new InstitutionSuffixOverflowException(base);
        }
        String institutionCode = base + "-" + String.format("%02d", next);
        Institution created = new Institution(institutionCode, displayName, true);
        return repository.save(created);
    }

    @Override
    public Institution deactivate(String institutionCode) {
        Institution existing = getByCode(institutionCode);
        return repository.save(existing.withActive(false));
    }

    @Override
    public Institution activate(String institutionCode) {
        Institution existing = getByCode(institutionCode);
        return repository.save(existing.withActive(true));
    }

    public static final class InstitutionNotFoundException extends RuntimeException {
        public InstitutionNotFoundException(String code) {
            super("Institution not found: " + code);
        }
    }
}
