package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase;
import com.mmx.order.application.port.in.OnboardedInstitution;
import com.mmx.order.application.port.in.OnboardInstitutionUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ProxyInstitutionRepository;
import com.mmx.order.domain.exception.InstitutionSuffixOverflowException;
import com.mmx.order.domain.exception.InvalidDelegatedGrantException;
import com.mmx.order.domain.exception.InvalidInstitutionException;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.ThinProxyInstitution;
import com.mmx.order.domain.model.TradingClientRole;
import com.mmx.order.domain.service.InstitutionCodeAcronym;

public final class OnboardInstitutionService implements OnboardInstitutionUseCase {

    private final InstitutionRepository institutionRepository;
    private final ProxyInstitutionRepository proxyRepository;
    private final DelegatedGrantRepository grantRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final ManageInstitutionSettingsUseCase nativeOnboard;

    public OnboardInstitutionService(
            InstitutionRepository institutionRepository,
            ProxyInstitutionRepository proxyRepository,
            DelegatedGrantRepository grantRepository,
            LegalEntityRepository legalEntityRepository,
            ManageInstitutionSettingsUseCase nativeOnboard) {
        this.institutionRepository = institutionRepository;
        this.proxyRepository = proxyRepository;
        this.grantRepository = grantRepository;
        this.legalEntityRepository = legalEntityRepository;
        this.nativeOnboard = nativeOnboard;
    }

    @Override
    public OnboardedInstitution onboard(OnboardCommand command) {
        ScopeContext scope = command.scope();
        if (scope == null) {
            throw new UnauthorizedUserException("Active scope is required");
        }
        if (scope.role() == Role.TRADER) {
            return onboardNative(command);
        }
        if (scope.role() == Role.CLIENT_REPRESENTATIVE) {
            return onboardProxy(command);
        }
        throw new UnauthorizedUserException("Unsupported role for institution onboard");
    }

    private OnboardedInstitution onboardNative(OnboardCommand command) {
        if (command.hubInstitutionCode() != null && !command.hubInstitutionCode().isBlank()) {
            throw new InvalidInstitutionException(
                    "A Trader onboards a native institution by displayName; hubInstitutionCode is not accepted");
        }
        if (command.displayName() == null || command.displayName().isBlank()) {
            throw new InvalidInstitutionException("displayName is required for native institution onboard");
        }
        Institution nativeInst =
                nativeOnboard.onboard(new ManageInstitutionSettingsUseCase.OnboardCommand(command.displayName()));
        return new OnboardedInstitution.Native(nativeInst);
    }

    private OnboardedInstitution onboardProxy(OnboardCommand command) {
        String hubInstitutionCode = command.hubInstitutionCode();
        boolean hasDisplayName = command.displayName() != null && !command.displayName().isBlank();
        if (hasDisplayName && (hubInstitutionCode == null || hubInstitutionCode.isBlank())) {
            throw new UnauthorizedUserException(
                    "A ClientRepresentative cannot onboard a native institution");
        }
        if (hasDisplayName) {
            throw new InvalidInstitutionException(
                    "A ClientRepresentative cannot supply a free-form proxy displayName; it is derived from the grant");
        }
        if (hubInstitutionCode == null || hubInstitutionCode.isBlank()) {
            throw new InvalidInstitutionException("hubInstitutionCode is required to onboard a proxy from a grant");
        }
        LegalEntity client =
                legalEntityRepository
                        .findByCode(command.scope().legalEntityCode())
                        .orElseThrow(
                                () ->
                                        new InvalidInstitutionException(
                                                "Unknown active LegalEntity: " + command.scope().legalEntityCode()));
        if (!(client.getRole() instanceof TradingClientRole clientRole)) {
            throw new UnauthorizedUserException(
                    "Proxy onboard is only available to a ClientRepresentative on a TradingClient");
        }
        if (!grantRepository.existsActiveGrantForHubInstitutionAndClient(
                hubInstitutionCode, client.getCode())) {
            throw new InvalidDelegatedGrantException(
                    "No active grant for hub institution " + hubInstitutionCode + " to client " + client.getCode());
        }
        Institution hubInstitution =
                institutionRepository
                        .findByInstitutionCode(hubInstitutionCode)
                        .orElseThrow(
                                () ->
                                        new InvalidInstitutionException(
                                                "Unknown hub institution: " + hubInstitutionCode));

        String derivedName =
                ThinProxyInstitution.deriveDisplayName(hubInstitution.getDisplayName(), clientRole.connectedHubCode());
        String acronymBase = InstitutionCodeAcronym.deriveAcronym(derivedName);
        int next = proxyRepository.maxSuffixForAcronym(acronymBase) + 1;
        if (next > 99) {
            throw new InstitutionSuffixOverflowException(acronymBase);
        }
        String institutionCode = acronymBase + "-" + String.format("%02d", next);
        ThinProxyInstitution proxy =
                ThinProxyInstitution.forHubInstitution(institutionCode, hubInstitution, clientRole.connectedHubCode());
        return new OnboardedInstitution.Proxy(proxyRepository.save(proxy));
    }
}
