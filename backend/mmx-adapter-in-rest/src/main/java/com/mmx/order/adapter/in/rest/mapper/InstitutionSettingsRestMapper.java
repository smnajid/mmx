package com.mmx.order.adapter.in.rest.mapper;

import com.mmx.order.adapter.in.rest.generated.institution.model.ClientEnablementResponse;
import com.mmx.order.adapter.in.rest.generated.institution.model.GrantedInstitutionResponse;
import com.mmx.order.adapter.in.rest.generated.institution.model.InstitutionResponse;
import com.mmx.order.adapter.in.rest.generated.institution.model.NoticePeriodCode;
import com.mmx.order.adapter.in.rest.generated.institution.model.OnboardInstitutionRequest;
import com.mmx.order.adapter.in.rest.generated.institution.model.TenorCode;
import com.mmx.order.adapter.in.rest.generated.institution.model.UpdateClientEnablementRequest;
import com.mmx.order.adapter.in.rest.generated.institution.model.UpdateCounterpartyAccountsRequest;
import com.mmx.order.application.port.in.ListGrantedInstitutionsUseCase.GrantedInstitution;
import com.mmx.order.application.port.in.ManageClientEnablementUseCase;
import com.mmx.order.application.port.in.ManageClientEnablementUseCase.CurrencyEnablement;
import com.mmx.order.application.port.in.OnboardInstitutionUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.in.UpdateCounterpartyAccountsUseCase;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class InstitutionSettingsRestMapper {

    public InstitutionResponse toResponse(Institution institution) {
        InstitutionResponse response =
                new InstitutionResponse()
                        .institutionCode(institution.getInstitutionCode())
                        .displayName(institution.getDisplayName())
                        .active(institution.isActive())
                        .closedToNewBusiness(institution.isClosedToNewBusiness())
                        .termCounterpartyAccount(institution.getCounterpartyAccounts().term().orElse(null))
                        .onCallCounterpartyAccount(institution.getCounterpartyAccounts().onCall().orElse(null));
        institution.getHubLink().ifPresent(link -> response
                .hubInstitutionCode(link.hubInstitutionCode())
                .hubLegalEntityCode(link.hubLegalEntityCode().value()));
        return response;
    }

    public InstitutionResponse toResponse(Institution institution, List<CurrencyEnablement> enablements) {
        InstitutionResponse response = toResponse(institution);
        if (institution.isOnboarded()) {
            response.enablements(enablements.stream().map(InstitutionSettingsRestMapper::toEnablementResponse).toList());
        }
        return response;
    }

    public GrantedInstitutionResponse toGrantedResponse(GrantedInstitution granted) {
        GrantedInstitutionResponse response =
                new GrantedInstitutionResponse()
                        .hubLegalEntityCode(granted.hubLegalEntityCode().value())
                        .hubInstitutionCode(granted.hubInstitutionCode())
                        .displayName(granted.displayName())
                        .currencies(granted.currencies());
        granted.onboardedInstitutionCode().ifPresent(response::onboardedInstitutionCode);
        granted.closedToNewBusiness().ifPresent(response::closedToNewBusiness);
        return response;
    }

    public UpdateCounterpartyAccountsUseCase.UpdateCommand toAccountsCommand(
            ScopeContext scope, String institutionCode, UpdateCounterpartyAccountsRequest request) {
        return new UpdateCounterpartyAccountsUseCase.UpdateCommand(
                scope,
                institutionCode,
                request == null ? null : request.getTermCounterpartyAccount(),
                request == null ? null : request.getOnCallCounterpartyAccount());
    }

    public ManageClientEnablementUseCase.UpdateCommand toEnablementCommand(
            ScopeContext scope, String institutionCode, String currency, UpdateClientEnablementRequest request) {
        return new ManageClientEnablementUseCase.UpdateCommand(
                scope,
                institutionCode,
                currency,
                request.getEnabledTenors().stream().map(code -> Tenor.fromCode(code.getValue()).orElseThrow()).collect(Collectors.toSet()),
                request.getEnabledNoticePeriods().stream()
                        .map(code -> NoticePeriod.fromCode(code.getValue()).orElseThrow())
                        .collect(Collectors.toSet()));
    }

    private static ClientEnablementResponse toEnablementResponse(CurrencyEnablement enablement) {
        return new ClientEnablementResponse()
                .currency(enablement.currency())
                .grantedTenors(tenorCodes(enablement.grantedTenors()))
                .grantedNoticePeriods(noticeCodes(enablement.grantedNoticePeriods()))
                .enabledTenors(tenorCodes(enablement.enabledTenors()))
                .enabledNoticePeriods(noticeCodes(enablement.enabledNoticePeriods()));
    }

    private static List<TenorCode> tenorCodes(Collection<Tenor> tenors) {
        return tenors.stream().sorted().map(t -> TenorCode.fromValue(t.getCode())).toList();
    }

    private static List<NoticePeriodCode> noticeCodes(Set<NoticePeriod> notices) {
        return notices.stream().sorted().map(n -> NoticePeriodCode.fromValue(n.getCode())).toList();
    }

    public OnboardInstitutionUseCase.OnboardCommand toOnboardCommand(
            ScopeContext scope, OnboardInstitutionRequest request) {
        return new OnboardInstitutionUseCase.OnboardCommand(
                scope,
                request.getDisplayName(),
                request.getHubInstitutionCode(),
                request.getTermCounterpartyAccount(),
                request.getOnCallCounterpartyAccount());
    }
}
