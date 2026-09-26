package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ListGrantedInstitutionsUseCase.GrantedInstitution;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.support.InMemoryDelegatedGrantRepository;
import com.mmx.order.application.support.InMemoryHubInstitutionCatalog;
import com.mmx.order.application.support.InMemoryInstitutionRepository;
import com.mmx.order.application.support.InMemoryLegalEntityRepository;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.LegalEntityRegistry;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Organisation;
import com.mmx.order.domain.model.OrganisationCode;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("fast")
class ListGrantedInstitutionsServiceTest {

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");
    private static final ScopeContext CLIENT_REP_PAR = new ScopeContext(PAR, Role.CLIENT_REPRESENTATIVE);

    private InMemoryInstitutionRepository institutionRepository;
    private InMemoryDelegatedGrantRepository grantRepository;
    private ListGrantedInstitutionsService subject;

    @BeforeEach
    void setUp() {
        institutionRepository = new InMemoryInstitutionRepository();
        grantRepository = new InMemoryDelegatedGrantRepository();
        InMemoryHubInstitutionCatalog hubCatalog =
                new InMemoryHubInstitutionCatalog()
                        .put(new Institution("BNP-01", "BNP", true))
                        .put(new Institution("SG-01", "Societe Generale", true))
                        .put(new Institution("HSBC-01", "HSBC", true));

        Organisation lodh = new Organisation(new OrganisationCode("LODH"));
        LegalEntityRegistry registry = new LegalEntityRegistry();
        LegalEntity loc = lodh.createTradingHub(LOC, registry);
        LegalEntity par = lodh.createTradingClient(PAR, loc, registry);
        InMemoryLegalEntityRepository legalEntities = new InMemoryLegalEntityRepository().put(loc).put(par);

        subject = new ListGrantedInstitutionsService(grantRepository, hubCatalog, institutionRepository, legalEntities);
    }

    private void grant(String hubInstitutionCode, String currency, boolean active) {
        grantRepository.save(
                new DelegatedInstitutionGrant(
                        hubInstitutionCode, PAR, currency, EnumSet.of(Tenor._1M), EnumSet.noneOf(NoticePeriod.class), active));
    }

    private Institution onboard(String code, String hubInstitutionCode, String hubName) {
        return institutionRepository.save(
                Institution.onboardFromGrant(
                        code, hubName, new HubInstitutionLink(LOC, hubInstitutionCode), PAR, CounterpartyAccounts.none()));
    }

    @Test
    void grantedButNotOnboarded_isListedWithDerivedNameAndCurrencies_andNoOnboardedCode() {
        grant("SG-01", "USD", true);

        List<GrantedInstitution> granted = subject.list(CLIENT_REP_PAR);

        assertThat(granted)
                .containsExactly(
                        new GrantedInstitution(
                                LOC, "SG-01", "Societe Generale via LOC", List.of("USD"), Optional.empty(), Optional.empty()));
    }

    @Test
    void onboardedInstitution_carriesItsCodeAndClosedFlag_andCurrenciesAreActiveOnlyAndSorted() {
        grant("BNP-01", "USD", true);
        grant("BNP-01", "EUR", true);
        grant("BNP-01", "CHF", false);
        Institution bnp = onboard("BVL-01", "BNP-01", "BNP");
        bnp.offboard();

        List<GrantedInstitution> granted = subject.list(CLIENT_REP_PAR);

        assertThat(granted).hasSize(1);
        GrantedInstitution entry = granted.getFirst();
        assertThat(entry.displayName()).isEqualTo("BNP via LOC");
        assertThat(entry.currencies()).containsExactly("EUR", "USD");
        assertThat(entry.onboardedInstitutionCode()).contains("BVL-01");
        assertThat(entry.closedToNewBusiness()).contains(true);
    }

    @Test
    void institutionRevokedEverywhere_dropsOut_evenIfOnboarded() {
        grant("BNP-01", "EUR", false);
        grant("SG-01", "EUR", true);
        onboard("BVL-01", "BNP-01", "BNP");

        assertThat(subject.list(CLIENT_REP_PAR))
                .extracting(GrantedInstitution::hubInstitutionCode)
                .containsExactly("SG-01");
    }

    @Test
    void grantsOfOtherClients_areNotListed() {
        grantRepository.save(
                new DelegatedInstitutionGrant(
                        "HSBC-01", new LegalEntityCode("MIL"), "EUR", EnumSet.of(Tenor._1M), EnumSet.noneOf(NoticePeriod.class), true));

        assertThat(subject.list(CLIENT_REP_PAR)).isEmpty();
    }

    @Test
    void trader_isRejected() {
        assertThatThrownBy(() -> subject.list(new ScopeContext(LOC, Role.TRADER)))
                .isInstanceOf(UnauthorizedUserException.class);
    }
}
