package com.mmx.order.application.service;

import com.mmx.order.application.exception.InstitutionNotFoundException;
import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase.OnboardCommand;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.InstitutionExportOutbox.ChangeReason;
import com.mmx.order.application.support.InMemoryDelegatedGrantRepository;
import com.mmx.order.application.support.InMemoryInstitutionExportOutbox;
import com.mmx.order.application.support.InMemoryInstitutionRepository;
import com.mmx.order.domain.exception.InstitutionSuffixOverflowException;
import com.mmx.order.domain.exception.InvalidDelegatedGrantException;
import com.mmx.order.domain.exception.InvalidInstitutionException;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.HubInstitutionLink;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("fast")
class ManageInstitutionSettingsServiceTest {

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");
    private static final ScopeContext TRADER = new ScopeContext(LOC, Role.TRADER);
    private static final ScopeContext CLIENT_REP = new ScopeContext(PAR, Role.CLIENT_REPRESENTATIVE);

    private InMemoryInstitutionRepository repository;
    private InMemoryDelegatedGrantRepository grantRepository;
    private InMemoryInstitutionExportOutbox exportOutbox;
    private ManageInstitutionSettingsService subject;

    @BeforeEach
    void setUp() {
        repository = new InMemoryInstitutionRepository();
        grantRepository = new InMemoryDelegatedGrantRepository();
        exportOutbox = new InMemoryInstitutionExportOutbox();
        subject = new ManageInstitutionSettingsService(repository, grantRepository, exportOutbox);
    }

    private Institution nativeHsbc() {
        return repository.save(Institution.createNative("HSBC-01", "HSBC", LOC, CounterpartyAccounts.none()));
    }

    private Institution onboardedBnp() {
        return repository.save(
                Institution.onboardFromGrant(
                        "BVL-01",
                        "BNP",
                        new HubInstitutionLink(LOC, "BNP"),
                        PAR,
                        CounterpartyAccounts.of("PAR-BNP-T", null)));
    }

    private void grantBnpToPar(boolean active) {
        grantRepository.save(
                new DelegatedInstitutionGrant(
                        "BNP", PAR, "EUR", EnumSet.of(Tenor._1M), EnumSet.noneOf(NoticePeriod.class), active));
    }

    // --- native onboarding ---

    @Test
    void onboard_firstHsbc_assignsHsbc01AndSchedulesAnExport() {
        Institution created = subject.onboard(new OnboardCommand(LOC, "HSBC", null, null));

        assertThat(created.getInstitutionCode()).isEqualTo("HSBC-01");
        assertThat(created.getDisplayName()).isEqualTo("HSBC");
        assertThat(created.getOwningLegalEntityCode()).isEqualTo(LOC);
        assertThat(created.isActive()).isTrue();
        assertThat(exportOutbox.reasons()).containsExactly(ChangeReason.ONBOARDED);
    }

    @Test
    void onboard_secondHsbc_assignsHsbc02() {
        nativeHsbc();

        assertThat(subject.onboard(new OnboardCommand(LOC, "HSBC", null, null)).getInstitutionCode())
                .isEqualTo("HSBC-02");
    }

    @Test
    void onboard_multiWord_assignsBci01() {
        assertThat(subject.onboard(new OnboardCommand(LOC, "Bank Co International", null, null)).getInstitutionCode())
                .isEqualTo("BCI-01");
    }

    @Test
    void onboard_withAccounts_storesThem() {
        Institution created = subject.onboard(new OnboardCommand(LOC, "HSBC", " LOC-HSBC-T ", "LOC-HSBC-OC"));

        assertThat(created.getCounterpartyAccounts()).isEqualTo(CounterpartyAccounts.of("LOC-HSBC-T", "LOC-HSBC-OC"));
    }

    @Test
    void onboard_blankAccount_rejected() {
        assertThatThrownBy(() -> subject.onboard(new OnboardCommand(LOC, "HSBC", "  ", null)))
                .isInstanceOf(InvalidInstitutionException.class);
        assertThat(exportOutbox.scheduled()).isEmpty();
    }

    @Test
    void onboard_blankName_rejected() {
        assertThatThrownBy(() -> subject.onboard(new OnboardCommand(LOC, "  ", null, null)))
                .isInstanceOf(InvalidInstitutionException.class);
    }

    @Test
    void onboard_suffixOverflow_rejected() {
        for (int i = 1; i <= 99; i++) {
            repository.put(new Institution(String.format("INST-%02d", i), "Inst " + i, true));
        }

        assertThatThrownBy(() -> subject.onboard(new OnboardCommand(LOC, "!!!", null, null)))
                .isInstanceOf(InstitutionSuffixOverflowException.class);
    }

    // --- hub deactivate / activate ---

    @Test
    void trader_deactivate_closesToNewBusinessAndSchedulesDeactivated() {
        nativeHsbc();

        Institution result = subject.deactivate(TRADER, "HSBC-01");

        assertThat(result.isClosedToNewBusiness()).isTrue();
        assertThat(repository.findByInstitutionCode("HSBC-01").orElseThrow().isActive()).isFalse();
        assertThat(exportOutbox.scheduled())
                .containsExactly(new InMemoryInstitutionExportOutbox.Scheduled("HSBC-01", 2, true, ChangeReason.DEACTIVATED));
    }

    @Test
    void trader_activate_reopensAndSchedulesReactivated() {
        nativeHsbc();
        subject.deactivate(TRADER, "HSBC-01");

        Institution result = subject.activate(TRADER, "HSBC-01");

        assertThat(result.isClosedToNewBusiness()).isFalse();
        assertThat(exportOutbox.reasons()).containsExactly(ChangeReason.DEACTIVATED, ChangeReason.REACTIVATED);
    }

    @Test
    void trader_deactivateTwice_recordsASingleExport() {
        nativeHsbc();
        subject.deactivate(TRADER, "HSBC-01");

        subject.deactivate(TRADER, "HSBC-01");

        assertThat(exportOutbox.reasons()).containsExactly(ChangeReason.DEACTIVATED);
    }

    @Test
    void trader_cannotOffboardAClientInstitution() {
        onboardedBnp();

        assertThatThrownBy(() -> subject.deactivate(TRADER, "BVL-01")).isInstanceOf(UnauthorizedUserException.class);
        assertThat(repository.findByInstitutionCode("BVL-01").orElseThrow().isClosedToNewBusiness()).isFalse();
        assertThat(exportOutbox.scheduled()).isEmpty();
    }

    @Test
    void trader_cannotReonboardAClientInstitution() {
        Institution bnp = onboardedBnp();
        bnp.offboard();
        repository.save(bnp);

        assertThatThrownBy(() -> subject.activate(TRADER, "BVL-01")).isInstanceOf(UnauthorizedUserException.class);
    }

    // --- client offboard / re-onboard ---

    @Test
    void clientRepresentative_deactivate_offboardsKeepingAccounts() {
        onboardedBnp();

        Institution result = subject.deactivate(CLIENT_REP, "BVL-01");

        assertThat(result.isClosedToNewBusiness()).isTrue();
        assertThat(result.getCounterpartyAccounts().term()).contains("PAR-BNP-T");
        assertThat(repository.findByInstitutionCode("BVL-01")).isPresent();
        assertThat(exportOutbox.reasons()).containsExactly(ChangeReason.OFFBOARDED);
    }

    @Test
    void clientRepresentative_offboardIsIdempotent() {
        onboardedBnp();
        subject.deactivate(CLIENT_REP, "BVL-01");

        Institution again = subject.deactivate(CLIENT_REP, "BVL-01");

        assertThat(again.isClosedToNewBusiness()).isTrue();
        assertThat(exportOutbox.reasons()).containsExactly(ChangeReason.OFFBOARDED);
    }

    @Test
    void clientRepresentative_activate_reonboardsWhenAGrantIsActive() {
        onboardedBnp();
        subject.deactivate(CLIENT_REP, "BVL-01");
        grantBnpToPar(true);

        Institution result = subject.activate(CLIENT_REP, "BVL-01");

        assertThat(result.isClosedToNewBusiness()).isFalse();
        assertThat(exportOutbox.reasons()).containsExactly(ChangeReason.OFFBOARDED, ChangeReason.REONBOARDED);
    }

    @Test
    void clientRepresentative_activate_withoutAnActiveGrantIsRejected() {
        onboardedBnp();
        subject.deactivate(CLIENT_REP, "BVL-01");
        grantBnpToPar(false);

        assertThatThrownBy(() -> subject.activate(CLIENT_REP, "BVL-01"))
                .isInstanceOf(InvalidDelegatedGrantException.class);
        assertThat(repository.findByInstitutionCode("BVL-01").orElseThrow().isClosedToNewBusiness()).isTrue();
    }

    @Test
    void clientRepresentative_cannotDeactivateAHubInstitution() {
        nativeHsbc();

        assertThatThrownBy(() -> subject.deactivate(CLIENT_REP, "HSBC-01"))
                .isInstanceOf(InstitutionNotFoundException.class);
        assertThat(exportOutbox.scheduled()).isEmpty();
    }

    @Test
    void deactivate_unknownCode_isNotFound() {
        assertThatThrownBy(() -> subject.deactivate(TRADER, "NOPE-01")).isInstanceOf(InstitutionNotFoundException.class);
    }
}
