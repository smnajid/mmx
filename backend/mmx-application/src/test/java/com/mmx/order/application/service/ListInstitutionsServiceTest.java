package com.mmx.order.application.service;

import com.mmx.order.application.port.in.InstitutionListView;
import com.mmx.order.application.port.in.ListInstitutionsUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.support.InMemoryInstitutionRepository;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@Tag("fast")

class ListInstitutionsServiceTest {

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");

    private InMemoryInstitutionRepository institutionRepository;
    private ListInstitutionsService service;

    @BeforeEach
    void setUp() {
        institutionRepository = new InMemoryInstitutionRepository();
        service = new ListInstitutionsService(institutionRepository);

        institutionRepository.put(Institution.createNative("BNP", "BNP", LOC, CounterpartyAccounts.none()));
        institutionRepository.put(Institution.createNative("HSBC", "HSBC", LOC, CounterpartyAccounts.none()));
        institutionRepository.put(
                Institution.onboardFromGrant(
                        "BVL-01", "BNP", new HubInstitutionLink(LOC, "BNP"), PAR, CounterpartyAccounts.none()));
    }

    @Test
    void trader_listReturnsNativeHubInstitutions() {
        InstitutionListView view = service.list(new ScopeContext(LOC, Role.TRADER));

        assertThat(view).isInstanceOf(InstitutionListView.Native.class);
        List<Institution> natives = ((InstitutionListView.Native) view).institutions();
        assertThat(natives).extracting(Institution::getInstitutionCode).containsExactly("BNP", "HSBC");
    }

    @Test
    void client_listReturnsOnlyOnboardedInstitutions_andExcludesNativeHubInstitutions() {
        InstitutionListView view = service.list(new ScopeContext(PAR, Role.CLIENT_REPRESENTATIVE));

        assertThat(view).isInstanceOf(InstitutionListView.Onboarded.class);
        List<Institution> onboarded = ((InstitutionListView.Onboarded) view).institutions();
        assertThat(onboarded).extracting(Institution::getInstitutionCode).containsExactly("BVL-01");
        assertThat(onboarded).extracting(Institution::getDisplayName).containsExactly("BNP via LOC");
        // Native hub institutions do NOT appear in the client's list.
        assertThat(onboarded).extracting(Institution::getInstitutionCode).doesNotContain("BNP", "HSBC");
    }

    @Test
    void client_withoutOnboardedInstitutions_returnsEmptyList() {
        InstitutionListView view =
                service.list(new ScopeContext(new LegalEntityCode("SIN"), Role.CLIENT_REPRESENTATIVE));

        assertThat(view).isInstanceOf(InstitutionListView.Onboarded.class);
        assertThat(((InstitutionListView.Onboarded) view).institutions()).isEmpty();
    }

    @Test
    void unknownRole_rejected() {
        assertThatThrownBy(() -> service.list(null)).isInstanceOf(UnauthorizedUserException.class);
    }
}
