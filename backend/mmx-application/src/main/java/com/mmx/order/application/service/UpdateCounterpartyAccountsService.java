package com.mmx.order.application.service;

import com.mmx.order.application.exception.InstitutionNotFoundException;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.in.UpdateCounterpartyAccountsUseCase;
import com.mmx.order.application.port.out.ClientEnablementRepository;
import com.mmx.order.application.port.out.InstitutionExportOutbox;
import com.mmx.order.application.port.out.InstitutionExportOutbox.ChangeReason;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.domain.exception.CounterpartyAccountInUseException;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.ClientEnablement;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.policy.CounterpartyAccountPolicy;

import java.util.List;

public final class UpdateCounterpartyAccountsService implements UpdateCounterpartyAccountsUseCase {

    private final InstitutionRepository repository;
    private final ClientEnablementRepository enablementRepository;
    private final InstitutionExportOutbox exportOutbox;

    public UpdateCounterpartyAccountsService(
            InstitutionRepository repository,
            ClientEnablementRepository enablementRepository,
            InstitutionExportOutbox exportOutbox) {
        this.repository = repository;
        this.enablementRepository = enablementRepository;
        this.exportOutbox = exportOutbox;
    }

    @Override
    public Institution update(UpdateCommand command) {
        CounterpartyAccounts accounts =
                CounterpartyAccounts.of(command.termCounterpartyAccount(), command.onCallCounterpartyAccount());
        Institution institution = getInOwningScope(command.scope(), command.institutionCode());
        if (institution.isOnboarded()) {
            requireNotInUse(institution, accounts);
        }
        if (!institution.changeAccounts(accounts)) {
            return institution;
        }
        Institution saved = repository.save(institution);
        exportOutbox.schedule(saved, ChangeReason.ACCOUNTS_CHANGED);
        return saved;
    }

    /** Owned by the active LegalEntity (else not found), and edited by that scope's settings role. */
    private Institution getInOwningScope(ScopeContext scope, String institutionCode) {
        if (scope == null) {
            throw new UnauthorizedUserException("Active scope is required");
        }
        String code = Institution.validateCode(institutionCode);
        Institution institution =
                repository
                        .findByInstitutionCode(code)
                        .filter(i -> scope.legalEntityCode().equals(i.getOwningLegalEntityCode()))
                        .orElseThrow(() -> new InstitutionNotFoundException(code));
        Role expected = institution.isOnboarded() ? Role.CLIENT_REPRESENTATIVE : Role.TRADER;
        if (scope.role() != expected) {
            throw new UnauthorizedUserException(
                    "Counterparty accounts of " + code + " are maintained by the " + expected + " role");
        }
        return institution;
    }

    /** An account cannot be cleared while any tenor / notice period of its OrderType is client-enabled. */
    private void requireNotInUse(Institution institution, CounterpartyAccounts replacement) {
        List<ClientEnablement> enablements = enablementRepository.findByInstitutionCode(institution.getInstitutionCode());
        if (replacement.term().isEmpty() && enablements.stream().anyMatch(e -> !e.tenors().isEmpty())) {
            throw inUse(institution, OrderType.TERM);
        }
        if (replacement.onCall().isEmpty() && enablements.stream().anyMatch(e -> !e.noticePeriods().isEmpty())) {
            throw inUse(institution, OrderType.ON_CALL);
        }
    }

    private static CounterpartyAccountInUseException inUse(Institution institution, OrderType orderType) {
        String label = CounterpartyAccountPolicy.label(orderType);
        return new CounterpartyAccountInUseException(
                "The " + label + " counterparty account of " + institution.getInstitutionCode()
                        + " cannot be cleared while " + label + " business is client-enabled; switch it off first");
    }
}
