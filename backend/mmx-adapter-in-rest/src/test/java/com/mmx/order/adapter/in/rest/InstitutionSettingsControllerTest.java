package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.mapper.InstitutionSettingsRestMapper;
import com.mmx.order.application.exception.InstitutionNotFoundException;
import com.mmx.order.application.port.in.ListGrantedInstitutionsUseCase;
import com.mmx.order.application.port.in.ListGrantedInstitutionsUseCase.GrantedInstitution;
import com.mmx.order.application.port.in.ListInstitutionsUseCase;
import com.mmx.order.application.port.in.ManageClientEnablementUseCase;
import com.mmx.order.application.port.in.ManageClientEnablementUseCase.CurrencyEnablement;
import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase;
import com.mmx.order.application.port.in.OnboardInstitutionUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.in.UpdateCounterpartyAccountsUseCase;
import com.mmx.order.application.port.out.ScopeContextProvider;
import com.mmx.order.domain.exception.CounterpartyAccountInUseException;
import com.mmx.order.domain.exception.InstitutionAlreadyOnboardedException;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** Thin controller over {@code port.in} use cases; maps application/domain errors via {@link GlobalExceptionHandler}. */
@Tag("fast")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstitutionSettingsControllerTest {

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");
    private static final ScopeContext CLIENT_REP_PAR = new ScopeContext(PAR, Role.CLIENT_REPRESENTATIVE);

    @Mock ManageInstitutionSettingsUseCase manageInstitutionSettingsUseCase;
    @Mock ListInstitutionsUseCase listInstitutionsUseCase;
    @Mock OnboardInstitutionUseCase onboardInstitutionUseCase;
    @Mock ListGrantedInstitutionsUseCase listGrantedInstitutionsUseCase;
    @Mock UpdateCounterpartyAccountsUseCase updateCounterpartyAccountsUseCase;
    @Mock ManageClientEnablementUseCase manageClientEnablementUseCase;
    @Mock ScopeContextProvider scopeContextProvider;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(scopeContextProvider.requireActiveScope()).thenReturn(CLIENT_REP_PAR);
        when(manageClientEnablementUseCase.enablementsOf(any())).thenReturn(List.of());
        mockMvc =
                standaloneSetup(
                                new InstitutionSettingsController(
                                        manageInstitutionSettingsUseCase,
                                        listInstitutionsUseCase,
                                        onboardInstitutionUseCase,
                                        listGrantedInstitutionsUseCase,
                                        updateCounterpartyAccountsUseCase,
                                        manageClientEnablementUseCase,
                                        new InstitutionSettingsRestMapper(),
                                        scopeContextProvider))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    private static Institution bnpViaLoc() {
        return Institution.onboardFromGrant(
                "BVL-01", "BNP", new HubInstitutionLink(LOC, "BNP-01"), PAR, CounterpartyAccounts.of("PAR-BNP-T", null));
    }

    @Test
    void getInstitution_mapsAccountsHubLinkClosedFlagAndEnablements() throws Exception {
        when(manageInstitutionSettingsUseCase.getByCode("BVL-01")).thenReturn(bnpViaLoc());
        when(manageClientEnablementUseCase.enablementsOf("BVL-01"))
                .thenReturn(List.of(new CurrencyEnablement(
                        "EUR", Set.of(Tenor._1M, Tenor._3M), Set.of(NoticePeriod._24H), Set.of(Tenor._3M, Tenor._6M), Set.of())));

        mockMvc.perform(get("/api/v1/settings/institutions/BVL-01").header("X-User-Id", "rep-par"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institutionCode").value("BVL-01"))
                .andExpect(jsonPath("$.displayName").value("BNP via LOC"))
                .andExpect(jsonPath("$.closedToNewBusiness").value(false))
                .andExpect(jsonPath("$.termCounterpartyAccount").value("PAR-BNP-T"))
                .andExpect(jsonPath("$.onCallCounterpartyAccount").value(nullValue()))
                .andExpect(jsonPath("$.hubLegalEntityCode").value("LOC"))
                .andExpect(jsonPath("$.hubInstitutionCode").value("BNP-01"))
                .andExpect(jsonPath("$.enablements[0].currency").value("EUR"))
                .andExpect(jsonPath("$.enablements[0].grantedTenors[0]").value("1M"))
                .andExpect(jsonPath("$.enablements[0].grantedTenors[1]").value("3M"))
                .andExpect(jsonPath("$.enablements[0].grantedNoticePeriods[0]").value("24H"))
                .andExpect(jsonPath("$.enablements[0].enabledTenors[1]").value("6M"))
                .andExpect(jsonPath("$.enablements[0].enabledNoticePeriods").isEmpty());
    }

    @Test
    void listGrantedInstitutions_mapsEntries() throws Exception {
        when(listGrantedInstitutionsUseCase.list(CLIENT_REP_PAR))
                .thenReturn(List.of(
                        new GrantedInstitution(LOC, "SG-01", "SG via LOC", List.of("USD"), Optional.empty(), Optional.empty()),
                        new GrantedInstitution(LOC, "BNP-01", "BNP via LOC", List.of("EUR"), Optional.of("BVL-01"), Optional.of(true))));

        mockMvc.perform(get("/api/v1/settings/institutions/granted").header("X-User-Id", "rep-par"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hubInstitutionCode").value("SG-01"))
                .andExpect(jsonPath("$[0].hubLegalEntityCode").value("LOC"))
                .andExpect(jsonPath("$[0].displayName").value("SG via LOC"))
                .andExpect(jsonPath("$[0].currencies[0]").value("USD"))
                .andExpect(jsonPath("$[0].onboardedInstitutionCode").doesNotExist())
                .andExpect(jsonPath("$[1].onboardedInstitutionCode").value("BVL-01"))
                .andExpect(jsonPath("$[1].closedToNewBusiness").value(true));
    }

    @Test
    void listGrantedInstitutions_byATrader_is403() throws Exception {
        when(listGrantedInstitutionsUseCase.list(any())).thenThrow(new UnauthorizedUserException("Trader"));

        mockMvc.perform(get("/api/v1/settings/institutions/granted").header("X-User-Id", "trader-a"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCounterpartyAccounts_passesTheFullReplacement_andReturnsTheInstitution() throws Exception {
        when(updateCounterpartyAccountsUseCase.update(any())).thenReturn(bnpViaLoc());

        mockMvc.perform(put("/api/v1/settings/institutions/BVL-01/counterparty-accounts")
                        .header("X-User-Id", "rep-par")
                        .contentType(APPLICATION_JSON)
                        .content("{\"termCounterpartyAccount\":\"PAR-BNP-T\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termCounterpartyAccount").value("PAR-BNP-T"));

        ArgumentCaptor<UpdateCounterpartyAccountsUseCase.UpdateCommand> captor =
                ArgumentCaptor.forClass(UpdateCounterpartyAccountsUseCase.UpdateCommand.class);
        verify(updateCounterpartyAccountsUseCase).update(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new UpdateCounterpartyAccountsUseCase.UpdateCommand(CLIENT_REP_PAR, "BVL-01", "PAR-BNP-T", null));
    }

    @Test
    void updateCounterpartyAccounts_clearingAnAccountInUse_is409() throws Exception {
        when(updateCounterpartyAccountsUseCase.update(any())).thenThrow(new CounterpartyAccountInUseException("in use"));

        mockMvc.perform(put("/api/v1/settings/institutions/BVL-01/counterparty-accounts")
                        .header("X-User-Id", "rep-par")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("COUNTERPARTY_ACCOUNT_IN_USE"));
    }

    @Test
    void updateCounterpartyAccounts_outOfScope_is404() throws Exception {
        when(updateCounterpartyAccountsUseCase.update(any())).thenThrow(new InstitutionNotFoundException("BNP-01"));

        mockMvc.perform(put("/api/v1/settings/institutions/BNP-01/counterparty-accounts")
                        .header("X-User-Id", "rep-par")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("INSTITUTION_NOT_FOUND"));
    }

    @Test
    void updateClientEnablement_mapsCodesToTheCommand_andReturnsEnablements() throws Exception {
        when(manageClientEnablementUseCase.update(any())).thenReturn(bnpViaLoc());
        when(manageClientEnablementUseCase.enablementsOf("BVL-01"))
                .thenReturn(List.of(new CurrencyEnablement("EUR", Set.of(Tenor._3M), Set.of(), Set.of(Tenor._3M), Set.of())));

        mockMvc.perform(put("/api/v1/settings/institutions/BVL-01/enablement/EUR")
                        .header("X-User-Id", "rep-par")
                        .contentType(APPLICATION_JSON)
                        .content("{\"enabledTenors\":[\"3M\"],\"enabledNoticePeriods\":[\"24H\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enablements[0].enabledTenors[0]").value("3M"));

        ArgumentCaptor<ManageClientEnablementUseCase.UpdateCommand> captor =
                ArgumentCaptor.forClass(ManageClientEnablementUseCase.UpdateCommand.class);
        verify(manageClientEnablementUseCase).update(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new ManageClientEnablementUseCase.UpdateCommand(
                        CLIENT_REP_PAR, "BVL-01", "EUR", Set.of(Tenor._3M), Set.of(NoticePeriod._24H)));
    }

    @Test
    void onboard_createsWith201_andReopensWith200() throws Exception {
        when(onboardInstitutionUseCase.onboard(any()))
                .thenReturn(new OnboardInstitutionUseCase.Result(bnpViaLoc(), true))
                .thenReturn(new OnboardInstitutionUseCase.Result(bnpViaLoc(), false));
        String body = "{\"hubInstitutionCode\":\"BNP-01\",\"termCounterpartyAccount\":\"PAR-BNP-T\"}";

        mockMvc.perform(post("/api/v1/settings/institutions").header("X-User-Id", "rep-par")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.institutionCode").value("BVL-01"));
        mockMvc.perform(post("/api/v1/settings/institutions").header("X-User-Id", "rep-par")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void onboard_alreadyOpen_is409() throws Exception {
        when(onboardInstitutionUseCase.onboard(any())).thenThrow(new InstitutionAlreadyOnboardedException("open"));

        mockMvc.perform(post("/api/v1/settings/institutions").header("X-User-Id", "rep-par")
                        .contentType(APPLICATION_JSON).content("{\"hubInstitutionCode\":\"BNP-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INSTITUTION_ALREADY_ONBOARDED"));
    }
}
