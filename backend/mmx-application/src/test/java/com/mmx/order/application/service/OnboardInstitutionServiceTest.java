package com.mmx.order.application.service;

import com.mmx.order.application.port.in.OnboardInstitutionUseCase;
import com.mmx.order.application.port.in.OnboardInstitutionUseCase.OnboardCommand;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.InstitutionExportOutbox.ChangeReason;
import com.mmx.order.application.support.InMemoryDelegatedGrantRepository;
import com.mmx.order.application.support.InMemoryHubInstitutionCatalog;
import com.mmx.order.application.support.InMemoryInstitutionExportOutbox;
import com.mmx.order.application.support.InMemoryInstitutionRepository;
import com.mmx.order.application.support.InMemoryLegalEntityRepository;
import com.mmx.order.domain.exception.InstitutionAlreadyOnboardedException;
import com.mmx.order.domain.exception.InvalidDelegatedGrantException;
import com.mmx.order.domain.exception.InvalidInstitutionException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("fast")
class OnboardInstitutionServiceTest {

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");

    private InMemoryInstitutionRepository institutionRepository;
    private InMemoryDelegatedGrantRepository grantRepository;
    private InMemoryHubInstitutionCatalog hubCatalog;
    private InMemoryInstitutionExportOutbox exportOutbox;
    private OnboardInstitutionService service;

    @BeforeEach
    void setUp() {
        institutionRepository = new InMemoryInstitutionRepository();
        grantRepository = new InMemoryDelegatedGrantRepository();
        hubCatalog = new InMemoryHubInstitutionCatalog().put(new Institution("BNP", "BNP", true));
        exportOutbox = new InMemoryInstitutionExportOutbox();

        Organisation lodh = new Organisation(new OrganisationCode("LODH"));
        LegalEntityRegistry registry = new LegalEntityRegistry();
        LegalEntity loc = lodh.createTradingHub(LOC, registry);
        LegalEntity par = lodh.createTradingClient(PAR, loc, registry);
        InMemoryLegalEntityRepository legalEntityRepository = new InMemoryLegalEntityRepository().put(loc).put(par);

        service = new OnboardInstitutionService(
                institutionRepository,
                grantRepository,
                legalEntityRepository,
                hubCatalog,
                exportOutbox,
                new ManageInstitutionSettingsService(institutionRepository, grantRepository, exportOutbox));
    }

    private static ScopeContext traderOnHub() {
        return new ScopeContext(LOC, Role.TRADER);
    }

    private static ScopeContext clientRepresentative() {
        return new ScopeContext(PAR, Role.CLIENT_REPRESENTATIVE);
    }

    private void grantBnpToPar(boolean active) {
        grantRepository.save(
                new DelegatedInstitutionGrant(
                        "BNP", PAR, "EUR", EnumSet.of(Tenor._1M, Tenor._3M), EnumSet.noneOf(NoticePeriod.class), active));
    }

    private OnboardInstitutionUseCase.Result onboardBnp() {
        return service.onboard(new OnboardCommand(clientRepresentative(), null, "BNP"));
    }

    // --- Trader: native institution ---

    @Test
    void trader_onboardsNativeInstitution_withDisplayName() {
        OnboardInstitutionUseCase.Result result = service.onboard(new OnboardCommand(traderOnHub(), "HSBC", null));

        Institution created = result.institution();
        assertThat(result.created()).isTrue();
        assertThat(created.getDisplayName()).isEqualTo("HSBC");
        assertThat(created.getInstitutionCode()).isEqualTo("HSBC-01");
        assertThat(created.getOwningLegalEntityCode()).isEqualTo(LOC);
        assertThat(created.isOnboarded()).isFalse();
        assertThat(created.isActive()).isTrue();
        assertThat(created.getCounterpartyAccounts()).isEqualTo(CounterpartyAccounts.none());
        assertThat(exportOutbox.reasons()).containsExactly(ChangeReason.ONBOARDED);
    }

    @Test
    void trader_onboardsNativeInstitution_withOptionalAccounts() {
        Institution created =
                service.onboard(new OnboardCommand(traderOnHub(), "HSBC", null, "LOC-HSBC-T", "LOC-HSBC-OC"))
                        .institution();

        assertThat(created.getCounterpartyAccounts()).isEqualTo(CounterpartyAccounts.of("LOC-HSBC-T", "LOC-HSBC-OC"));
    }

    @Test
    void trader_cannotOnboardAGrantedInstitution() {
        grantBnpToPar(true);

        assertThatThrownBy(() -> service.onboard(new OnboardCommand(traderOnHub(), null, "BNP")))
                .isInstanceOf(InvalidInstitutionException.class);
        assertThat(exportOutbox.scheduled()).isEmpty();
    }

    // --- ClientRepresentative: institution onboarding ---

    @Test
    void client_onboardsGrantedInstitution() {
        grantBnpToPar(true);

        OnboardInstitutionUseCase.Result result = onboardBnp();

        Institution onboarded = result.institution();
        assertThat(result.created()).isTrue();
        assertThat(onboarded.getDisplayName()).isEqualTo("BNP via LOC");
        assertThat(onboarded.getHubLink()).contains(new HubInstitutionLink(LOC, "BNP"));
        assertThat(onboarded.getOwningLegalEntityCode()).isEqualTo(PAR);
        assertThat(onboarded.getInstitutionCode()).startsWith("BVL-");
        assertThat(onboarded.isClosedToNewBusiness()).isFalse();
        assertThat(institutionRepository.saved()).hasSize(1);
        assertThat(exportOutbox.scheduled())
                .containsExactly(new InMemoryInstitutionExportOutbox.Scheduled(
                        onboarded.getInstitutionCode(), 1, false, ChangeReason.ONBOARDED));
    }

    @Test
    void client_onboardsGrantedInstitution_withOptionalAccounts() {
        grantBnpToPar(true);

        Institution onboarded =
                service.onboard(new OnboardCommand(clientRepresentative(), null, "BNP", "PAR-BNP-T", null))
                        .institution();

        assertThat(onboarded.getCounterpartyAccounts()).isEqualTo(CounterpartyAccounts.of("PAR-BNP-T", null));
    }

    @Test
    void client_onboardingANonGrantedInstitution_isRejected() {
        assertThatThrownBy(this::onboardBnp)
                .isInstanceOf(InvalidDelegatedGrantException.class)
                .hasMessageContaining("grant");

        assertThat(institutionRepository.saved()).isEmpty();
        assertThat(exportOutbox.scheduled()).isEmpty();
    }

    @Test
    void client_onboardingWithOnlyAnInactiveGrant_isRejected() {
        grantBnpToPar(false);

        assertThatThrownBy(this::onboardBnp).isInstanceOf(InvalidDelegatedGrantException.class);
    }

    @Test
    void client_onboardingAHubInstitutionMissingFromTheHubCatalog_isRejected() {
        grantRepository.save(
                new DelegatedInstitutionGrant(
                        "GONE", PAR, "EUR", EnumSet.of(Tenor._1M), EnumSet.noneOf(NoticePeriod.class), true));

        assertThatThrownBy(() -> service.onboard(new OnboardCommand(clientRepresentative(), null, "GONE")))
                .isInstanceOf(InvalidInstitutionException.class);
    }

    @Test
    void client_freeFormDisplayName_isRejected() {
        grantBnpToPar(true);

        assertThatThrownBy(() -> service.onboard(new OnboardCommand(clientRepresentative(), "My Custom Name", "BNP")))
                .isInstanceOf(InvalidInstitutionException.class);
        assertThat(institutionRepository.saved()).isEmpty();
    }

    @Test
    void client_cannotOnboardANativeInstitutionByDisplayName() {
        assertThatThrownBy(() -> service.onboard(new OnboardCommand(clientRepresentative(), "HSBC", null)))
                .isInstanceOf(UnauthorizedUserException.class);
    }

    @Test
    void client_duplicateOpenOnboarding_isAConflict() {
        grantBnpToPar(true);
        onboardBnp();

        assertThatThrownBy(this::onboardBnp).isInstanceOf(InstitutionAlreadyOnboardedException.class);
        assertThat(institutionRepository.findOnboardedByLegalEntityCode(PAR)).hasSize(1);
        assertThat(exportOutbox.scheduled()).hasSize(1);
    }

    @Test
    void client_reonboardingAnOffboardedRecord_reopensTheSameCodeWithItsAccounts() {
        grantBnpToPar(true);
        Institution first =
                service.onboard(new OnboardCommand(clientRepresentative(), null, "BNP", "PAR-BNP-T", null))
                        .institution();
        first.offboard();
        institutionRepository.save(first);

        OnboardInstitutionUseCase.Result again = onboardBnp();

        assertThat(again.created()).isFalse();
        assertThat(again.institution().getInstitutionCode()).isEqualTo(first.getInstitutionCode());
        assertThat(again.institution().isClosedToNewBusiness()).isFalse();
        assertThat(again.institution().getCounterpartyAccounts().term()).contains("PAR-BNP-T");
        assertThat(institutionRepository.findOnboardedByLegalEntityCode(PAR)).hasSize(1);
        assertThat(exportOutbox.reasons()).containsExactly(ChangeReason.ONBOARDED, ChangeReason.REONBOARDED);
    }

    @Test
    void client_reonboardingWithAccounts_replacesSuppliedAndKeepsAbsentOnes() {
        grantBnpToPar(true);
        Institution first =
                service.onboard(new OnboardCommand(clientRepresentative(), null, "BNP", "PAR-BNP-T", "PAR-BNP-OC"))
                        .institution();
        first.offboard();
        institutionRepository.save(first);

        Institution again =
                service.onboard(new OnboardCommand(clientRepresentative(), null, "BNP", "PAR-BNP-T2", null))
                        .institution();

        assertThat(again.getCounterpartyAccounts().term()).contains("PAR-BNP-T2");
        assertThat(again.getCounterpartyAccounts().onCall()).contains("PAR-BNP-OC");
    }

    @Test
    void client_reonboardingWithoutAnActiveGrant_isRejectedAndStaysClosed() {
        grantBnpToPar(true);
        Institution first = onboardBnp().institution();
        first.offboard();
        institutionRepository.save(first);
        grantBnpToPar(false);

        assertThatThrownBy(this::onboardBnp).isInstanceOf(InvalidDelegatedGrantException.class);
        assertThat(institutionRepository.findByInstitutionCode(first.getInstitutionCode()).orElseThrow()
                        .isClosedToNewBusiness())
                .isTrue();
        assertThat(exportOutbox.reasons()).containsExactly(ChangeReason.ONBOARDED);
    }
}
