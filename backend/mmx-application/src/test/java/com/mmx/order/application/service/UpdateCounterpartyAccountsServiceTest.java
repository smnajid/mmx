package com.mmx.order.application.service;

import com.mmx.order.application.exception.InstitutionNotFoundException;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.in.UpdateCounterpartyAccountsUseCase.UpdateCommand;
import com.mmx.order.application.port.out.InstitutionExportOutbox.ChangeReason;
import com.mmx.order.application.support.InMemoryClientEnablementRepository;
import com.mmx.order.application.support.InMemoryInstitutionExportOutbox;
import com.mmx.order.application.support.InMemoryInstitutionRepository;
import com.mmx.order.domain.exception.CounterpartyAccountInUseException;
import com.mmx.order.domain.exception.InvalidInstitutionException;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.ClientEnablement;
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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("fast")
class UpdateCounterpartyAccountsServiceTest {

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");
    private static final ScopeContext TRADER_LOC = new ScopeContext(LOC, Role.TRADER);
    private static final ScopeContext CLIENT_REP_PAR = new ScopeContext(PAR, Role.CLIENT_REPRESENTATIVE);

    private InMemoryInstitutionRepository repository;
    private InMemoryClientEnablementRepository enablementRepository;
    private InMemoryInstitutionExportOutbox exportOutbox;
    private UpdateCounterpartyAccountsService subject;

    @BeforeEach
    void setUp() {
        repository = new InMemoryInstitutionRepository();
        enablementRepository = new InMemoryClientEnablementRepository();
        exportOutbox = new InMemoryInstitutionExportOutbox();
        subject = new UpdateCounterpartyAccountsService(repository, enablementRepository, exportOutbox);
        repository.put(Institution.createNative("BNP-01", "BNP", LOC, CounterpartyAccounts.none()));
        repository.put(
                Institution.onboardFromGrant(
                        "BVL-01",
                        "BNP",
                        new HubInstitutionLink(LOC, "BNP-01"),
                        PAR,
                        CounterpartyAccounts.of("PAR-BNP-T", "PAR-BNP-OC")));
    }

    @Test
    void trader_setsHubAccounts_andSchedulesAccountsChanged() {
        Institution updated = subject.update(new UpdateCommand(TRADER_LOC, "BNP-01", "LOC-BNP-T", "LOC-BNP-OC"));

        assertThat(updated.getCounterpartyAccounts()).isEqualTo(CounterpartyAccounts.of("LOC-BNP-T", "LOC-BNP-OC"));
        assertThat(repository.findByInstitutionCode("BNP-01").orElseThrow().getCounterpartyAccounts())
                .isEqualTo(CounterpartyAccounts.of("LOC-BNP-T", "LOC-BNP-OC"));
        assertThat(exportOutbox.reasons()).containsExactly(ChangeReason.ACCOUNTS_CHANGED);
    }

    @Test
    void clientRepresentative_setsClientAccounts_onOwnInstitutionOnly() {
        subject.update(new UpdateCommand(CLIENT_REP_PAR, "BVL-01", "PAR-BNP-T", "PAR-BNP-OC2"));

        assertThat(repository.findByInstitutionCode("BVL-01").orElseThrow().getCounterpartyAccounts().onCall())
                .contains("PAR-BNP-OC2");
        assertThat(repository.findByInstitutionCode("BNP-01").orElseThrow().getCounterpartyAccounts())
                .isEqualTo(CounterpartyAccounts.none());
    }

    @Test
    void crossScope_clientRepresentativeOnHubInstitution_isNotFound() {
        assertThatThrownBy(() -> subject.update(new UpdateCommand(CLIENT_REP_PAR, "BNP-01", "X", null)))
                .isInstanceOf(InstitutionNotFoundException.class);
        assertThat(exportOutbox.scheduled()).isEmpty();
    }

    @Test
    void crossScope_traderOnClientInstitution_isNotFound() {
        assertThatThrownBy(() -> subject.update(new UpdateCommand(TRADER_LOC, "BVL-01", "X", null)))
                .isInstanceOf(InstitutionNotFoundException.class);
    }

    @Test
    void wrongRoleForOwningScope_isRejected() {
        ScopeContext clientRepOnLoc = new ScopeContext(LOC, Role.CLIENT_REPRESENTATIVE);

        assertThatThrownBy(() -> subject.update(new UpdateCommand(clientRepOnLoc, "BNP-01", "X", null)))
                .isInstanceOf(UnauthorizedUserException.class);
    }

    @Test
    void unknownInstitution_isNotFound() {
        assertThatThrownBy(() -> subject.update(new UpdateCommand(TRADER_LOC, "NOPE-01", "X", null)))
                .isInstanceOf(InstitutionNotFoundException.class);
    }

    @Test
    void unchangedValues_scheduleNoExport() {
        Institution unchanged = subject.update(new UpdateCommand(CLIENT_REP_PAR, "BVL-01", " PAR-BNP-T ", "PAR-BNP-OC"));

        assertThat(unchanged.getVersion()).isEqualTo(1);
        assertThat(exportOutbox.scheduled()).isEmpty();
        assertThat(repository.saved()).isEmpty();
    }

    @Test
    void blankAccount_isRejected_andStoredAccountUnchanged() {
        assertThatThrownBy(() -> subject.update(new UpdateCommand(TRADER_LOC, "BNP-01", "   ", null)))
                .isInstanceOf(InvalidInstitutionException.class);
        assertThat(repository.findByInstitutionCode("BNP-01").orElseThrow().getCounterpartyAccounts())
                .isEqualTo(CounterpartyAccounts.none());
    }

    @Test
    void allowedWhileClosedToNewBusiness() {
        Institution client = repository.findByInstitutionCode("BVL-01").orElseThrow();
        client.offboard();

        Institution updated = subject.update(new UpdateCommand(CLIENT_REP_PAR, "BVL-01", "PAR-BNP-T2", "PAR-BNP-OC"));

        assertThat(updated.isClosedToNewBusiness()).isTrue();
        assertThat(updated.getCounterpartyAccounts().term()).contains("PAR-BNP-T2");
        assertThat(exportOutbox.reasons()).containsExactly(ChangeReason.ACCOUNTS_CHANGED);
    }

    @Test
    void clearingOnCallAccount_whileANoticePeriodIsClientEnabled_isRejected() {
        enablementRepository.save(
                PAR, new ClientEnablement("BVL-01", "USD", Set.of(), Set.of(NoticePeriod._24H)));

        assertThatThrownBy(() -> subject.update(new UpdateCommand(CLIENT_REP_PAR, "BVL-01", "PAR-BNP-T", null)))
                .isInstanceOf(CounterpartyAccountInUseException.class);
        assertThat(repository.findByInstitutionCode("BVL-01").orElseThrow().getCounterpartyAccounts().onCall())
                .contains("PAR-BNP-OC");
        assertThat(exportOutbox.scheduled()).isEmpty();
    }

    @Test
    void clearingTermAccount_whileATenorIsClientEnabled_isRejected() {
        enablementRepository.save(PAR, new ClientEnablement("BVL-01", "EUR", Set.of(Tenor._3M), Set.of()));

        assertThatThrownBy(() -> subject.update(new UpdateCommand(CLIENT_REP_PAR, "BVL-01", null, "PAR-BNP-OC")))
                .isInstanceOf(CounterpartyAccountInUseException.class);
    }

    @Test
    void clearingOnCallAccount_afterSwitchingOffEveryNoticePeriod_succeeds() {
        enablementRepository.save(PAR, new ClientEnablement("BVL-01", "EUR", Set.of(Tenor._3M), Set.of()));

        Institution updated = subject.update(new UpdateCommand(CLIENT_REP_PAR, "BVL-01", "PAR-BNP-T", null));

        assertThat(updated.getCounterpartyAccounts().onCall()).isEmpty();
        assertThat(exportOutbox.reasons()).containsExactly(ChangeReason.ACCOUNTS_CHANGED);
    }
}
