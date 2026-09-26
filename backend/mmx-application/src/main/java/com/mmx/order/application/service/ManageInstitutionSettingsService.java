package com.mmx.order.application.service;

import com.mmx.order.application.exception.InstitutionNotFoundException;
import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionExportOutbox;
import com.mmx.order.application.port.out.InstitutionExportOutbox.ChangeReason;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.domain.exception.InstitutionSuffixOverflowException;
import com.mmx.order.domain.exception.InvalidDelegatedGrantException;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.service.InstitutionCodeAcronym;

import java.util.List;

public final class ManageInstitutionSettingsService implements ManageInstitutionSettingsUseCase {

    private final InstitutionRepository repository;
    private final DelegatedGrantRepository grantRepository;
    private final InstitutionExportOutbox exportOutbox;

    public ManageInstitutionSettingsService(
            InstitutionRepository repository,
            DelegatedGrantRepository grantRepository,
            InstitutionExportOutbox exportOutbox) {
        this.repository = repository;
        this.grantRepository = grantRepository;
        this.exportOutbox = exportOutbox;
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
    public Institution getInScope(ScopeContext scope, String institutionCode) {
        if (scope == null) {
            throw new UnauthorizedUserException("Active scope is required");
        }
        Institution institution = getByCode(institutionCode);
        if (!scope.legalEntityCode().equals(institution.getOwningLegalEntityCode())) {
            throw new InstitutionNotFoundException(institution.getInstitutionCode());
        }
        return institution;
    }

    @Override
    public Institution onboard(OnboardCommand command) {
        String displayName = Institution.validateDisplayName(command.displayName());
        CounterpartyAccounts accounts =
                CounterpartyAccounts.of(command.termCounterpartyAccount(), command.onCallCounterpartyAccount());
        Institution created =
                repository.save(
                        Institution.createNative(
                                nextInstitutionCode(repository, displayName),
                                displayName,
                                command.owningLegalEntityCode(),
                                accounts));
        exportOutbox.schedule(created, ChangeReason.ONBOARDED);
        return created;
    }

    @Override
    public Institution deactivate(ScopeContext scope, String institutionCode) {
        Institution institution = getForMutation(scope, institutionCode);
        boolean changed = institution.isOnboarded() ? institution.offboard() : institution.deactivate();
        return saveAndExport(
                institution, changed, institution.isOnboarded() ? ChangeReason.OFFBOARDED : ChangeReason.DEACTIVATED);
    }

    @Override
    public Institution activate(ScopeContext scope, String institutionCode) {
        Institution institution = getForMutation(scope, institutionCode);
        if (!institution.isOnboarded()) {
            return saveAndExport(institution, institution.reactivate(), ChangeReason.REACTIVATED);
        }
        requireActiveGrant(scope, institution.getHubLink().orElseThrow());
        return saveAndExport(institution, institution.reopen(), ChangeReason.REONBOARDED);
    }

    /**
     * An institution owned by the active LegalEntity. A Trader never acts on a client's onboarded institution;
     * anything owned by another LegalEntity is not found.
     */
    private Institution getForMutation(ScopeContext scope, String institutionCode) {
        Institution institution = getByCode(institutionCode);
        if (institution.isOnboarded() && scope.role() == Role.TRADER) {
            throw new UnauthorizedUserException("A Trader cannot offboard or re-onboard a client institution");
        }
        if (!scope.legalEntityCode().equals(institution.getOwningLegalEntityCode())) {
            throw new InstitutionNotFoundException(institution.getInstitutionCode());
        }
        return institution;
    }

    private void requireActiveGrant(ScopeContext scope, HubInstitutionLink hubLink) {
        if (!grantRepository.existsActiveGrantForHubInstitutionAndClient(
                hubLink.hubInstitutionCode(), scope.legalEntityCode())) {
            throw new InvalidDelegatedGrantException(
                    "No active grant for hub institution " + hubLink.hubInstitutionCode() + " to client "
                            + scope.legalEntityCode());
        }
    }

    private Institution saveAndExport(Institution institution, boolean changed, ChangeReason reason) {
        if (!changed) {
            return institution;
        }
        Institution saved = repository.save(institution);
        exportOutbox.schedule(saved, reason);
        return saved;
    }

    static String nextInstitutionCode(InstitutionRepository repository, String displayName) {
        String base = InstitutionCodeAcronym.deriveAcronym(displayName);
        int next = repository.maxSuffixForAcronym(base) + 1;
        if (next > 99) {
            throw new InstitutionSuffixOverflowException(base);
        }
        return base + "-" + String.format("%02d", next);
    }
}
