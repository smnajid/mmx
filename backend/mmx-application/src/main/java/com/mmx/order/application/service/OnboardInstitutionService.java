package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase;
import com.mmx.order.application.port.in.OnboardInstitutionUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.HubInstitutionCatalog;
import com.mmx.order.application.port.out.InstitutionExportOutbox;
import com.mmx.order.application.port.out.InstitutionExportOutbox.ChangeReason;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.domain.exception.InstitutionAlreadyOnboardedException;
import com.mmx.order.domain.exception.InvalidDelegatedGrantException;
import com.mmx.order.domain.exception.InvalidInstitutionException;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.TradingClientRole;

import java.util.Optional;

public final class OnboardInstitutionService implements OnboardInstitutionUseCase {

    private final InstitutionRepository institutionRepository;
    private final DelegatedGrantRepository grantRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final HubInstitutionCatalog hubInstitutionCatalog;
    private final InstitutionExportOutbox exportOutbox;
    private final ManageInstitutionSettingsUseCase nativeOnboard;

    public OnboardInstitutionService(
            InstitutionRepository institutionRepository,
            DelegatedGrantRepository grantRepository,
            LegalEntityRepository legalEntityRepository,
            HubInstitutionCatalog hubInstitutionCatalog,
            InstitutionExportOutbox exportOutbox,
            ManageInstitutionSettingsUseCase nativeOnboard) {
        this.institutionRepository = institutionRepository;
        this.grantRepository = grantRepository;
        this.legalEntityRepository = legalEntityRepository;
        this.hubInstitutionCatalog = hubInstitutionCatalog;
        this.exportOutbox = exportOutbox;
        this.nativeOnboard = nativeOnboard;
    }

    @Override
    public Result onboard(OnboardCommand command) {
        ScopeContext scope = command.scope();
        if (scope == null) {
            throw new UnauthorizedUserException("Active scope is required");
        }
        if (scope.role() == Role.TRADER) {
            return onboardNative(command);
        }
        if (scope.role() == Role.CLIENT_REPRESENTATIVE) {
            return onboardGranted(command);
        }
        throw new UnauthorizedUserException("Unsupported role for institution onboard");
    }

    private Result onboardNative(OnboardCommand command) {
        if (hasText(command.hubInstitutionCode())) {
            throw new InvalidInstitutionException(
                    "A Trader onboards a native institution by displayName; hubInstitutionCode is not accepted");
        }
        if (!hasText(command.displayName())) {
            throw new InvalidInstitutionException("displayName is required for native institution onboard");
        }
        Institution created =
                nativeOnboard.onboard(
                        new ManageInstitutionSettingsUseCase.OnboardCommand(
                                command.scope().legalEntityCode(),
                                command.displayName(),
                                command.termCounterpartyAccount(),
                                command.onCallCounterpartyAccount()));
        return new Result(created, true);
    }

    private Result onboardGranted(OnboardCommand command) {
        String hubInstitutionCode = command.hubInstitutionCode();
        if (hasText(command.displayName()) && !hasText(hubInstitutionCode)) {
            throw new UnauthorizedUserException("A ClientRepresentative cannot onboard a native institution");
        }
        if (hasText(command.displayName())) {
            throw new InvalidInstitutionException(
                    "A ClientRepresentative cannot supply a free-form displayName; it is derived from the hub institution");
        }
        if (!hasText(hubInstitutionCode)) {
            throw new InvalidInstitutionException("hubInstitutionCode is required to onboard a granted institution");
        }
        CounterpartyAccounts accounts =
                CounterpartyAccounts.of(command.termCounterpartyAccount(), command.onCallCounterpartyAccount());
        LegalEntity client =
                legalEntityRepository
                        .findByCode(command.scope().legalEntityCode())
                        .orElseThrow(() -> new InvalidInstitutionException(
                                "Unknown active LegalEntity: " + command.scope().legalEntityCode()));
        if (!(client.getRole() instanceof TradingClientRole clientRole)) {
            throw new UnauthorizedUserException(
                    "Institution onboarding is only available to a ClientRepresentative on a TradingClient");
        }
        if (!grantRepository.existsActiveGrantForHubInstitutionAndClient(hubInstitutionCode, client.getCode())) {
            throw new InvalidDelegatedGrantException(
                    "No active grant for hub institution " + hubInstitutionCode + " to client " + client.getCode());
        }
        HubInstitutionLink hubLink = new HubInstitutionLink(clientRole.connectedHubCode(), hubInstitutionCode);

        Optional<Institution> existing = institutionRepository.findOnboarded(client.getCode(), hubLink);
        if (existing.isPresent()) {
            return reonboard(existing.get(), accounts);
        }

        Institution hubInstitution =
                hubInstitutionCatalog
                        .findByInstitutionCode(hubInstitutionCode)
                        .orElseThrow(() -> new InvalidInstitutionException("Unknown hub institution: " + hubInstitutionCode));
        String derivedName = Institution.deriveDisplayName(hubInstitution.getDisplayName(), hubLink.hubLegalEntityCode());
        Institution onboarded =
                institutionRepository.save(
                        Institution.onboardFromGrant(
                                ManageInstitutionSettingsService.nextInstitutionCode(institutionRepository, derivedName),
                                hubInstitution.getDisplayName(),
                                hubLink,
                                client.getCode(),
                                accounts));
        exportOutbox.schedule(onboarded, ChangeReason.ONBOARDED);
        return new Result(onboarded, true);
    }

    /** Reopens an offboarded record; supplied accounts replace the stored ones, absent ones are kept. */
    private Result reonboard(Institution institution, CounterpartyAccounts supplied) {
        if (!institution.isClosedToNewBusiness()) {
            throw new InstitutionAlreadyOnboardedException(
                    "Hub institution " + institution.getHubLink().orElseThrow().hubInstitutionCode()
                            + " is already onboarded as " + institution.getInstitutionCode());
        }
        CounterpartyAccounts current = institution.getCounterpartyAccounts();
        institution.changeAccounts(
                new CounterpartyAccounts(
                        supplied.term().or(current::term), supplied.onCall().or(current::onCall)));
        institution.reopen();
        Institution saved = institutionRepository.save(institution);
        exportOutbox.schedule(saved, ChangeReason.REONBOARDED);
        return new Result(saved, false);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
