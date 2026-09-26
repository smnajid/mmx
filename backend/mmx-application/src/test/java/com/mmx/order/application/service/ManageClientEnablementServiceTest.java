package com.mmx.order.application.service;

import com.mmx.order.application.exception.InstitutionNotFoundException;
import com.mmx.order.application.port.in.ManageClientEnablementUseCase.CurrencyEnablement;
import com.mmx.order.application.port.in.ManageClientEnablementUseCase.UpdateCommand;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.support.InMemoryClientEnablementRepository;
import com.mmx.order.application.support.InMemoryDelegatedGrantRepository;
import com.mmx.order.application.support.InMemoryInstitutionExportOutbox;
import com.mmx.order.application.support.InMemoryInstitutionRepository;
import com.mmx.order.domain.exception.InvalidInstitutionException;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.ClientEnablement;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("fast")
class ManageClientEnablementServiceTest {

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");
    private static final ScopeContext CLIENT_REP_PAR = new ScopeContext(PAR, Role.CLIENT_REPRESENTATIVE);

    private InMemoryInstitutionRepository institutionRepository;
    private InMemoryDelegatedGrantRepository grantRepository;
    private InMemoryClientEnablementRepository enablementRepository;
    private InMemoryInstitutionExportOutbox exportOutbox;
    private ManageClientEnablementService subject;

    @BeforeEach
    void setUp() {
        institutionRepository = new InMemoryInstitutionRepository();
        grantRepository = new InMemoryDelegatedGrantRepository();
        enablementRepository = new InMemoryClientEnablementRepository();
        exportOutbox = new InMemoryInstitutionExportOutbox();
        subject = new ManageClientEnablementService(institutionRepository, grantRepository, enablementRepository);
        institutionRepository.put(Institution.createNative("BNP-01", "BNP", LOC, CounterpartyAccounts.none()));
        institutionRepository.put(
                Institution.onboardFromGrant(
                        "BVL-01",
                        "BNP",
                        new HubInstitutionLink(LOC, "BNP-01"),
                        PAR,
                        CounterpartyAccounts.of("PAR-BNP-T", "PAR-BNP-OC")));
        grantRepository.save(
                new DelegatedInstitutionGrant(
                        "BNP-01", PAR, "EUR", EnumSet.of(Tenor._1M, Tenor._3M), EnumSet.of(NoticePeriod._24H), true));
    }

    private UpdateCommand enable(String code, Set<Tenor> tenors, Set<NoticePeriod> notices) {
        return new UpdateCommand(CLIENT_REP_PAR, code, "EUR", tenors, notices);
    }

    @Test
    void fullReplacementPerCurrency_persists_andSchedulesNoExport() {
        subject.update(enable("BVL-01", Set.of(Tenor._1M, Tenor._3M), Set.of(NoticePeriod._24H)));
        subject.update(enable("BVL-01", Set.of(Tenor._3M), Set.of()));

        assertThat(enablementRepository.find("BVL-01", "EUR"))
                .isEqualTo(new ClientEnablement("BVL-01", "EUR", Set.of(Tenor._3M), Set.of()));
        assertThat(exportOutbox.scheduled()).isEmpty();
    }

    @Test
    void trader_isRejected() {
        UpdateCommand byTrader =
                new UpdateCommand(new ScopeContext(LOC, Role.TRADER), "BVL-01", "EUR", Set.of(Tenor._1M), Set.of());

        assertThatThrownBy(() -> subject.update(byTrader)).isInstanceOf(UnauthorizedUserException.class);
        assertThat(enablementRepository.find("BVL-01", "EUR").isEmpty()).isTrue();
    }

    @Test
    void institutionOfAnotherLegalEntity_orNative_isNotFound() {
        assertThatThrownBy(() -> subject.update(enable("BNP-01", Set.of(Tenor._1M), Set.of())))
                .isInstanceOf(InstitutionNotFoundException.class);
        assertThatThrownBy(() -> subject.update(enable("NOPE-01", Set.of(Tenor._1M), Set.of())))
                .isInstanceOf(InstitutionNotFoundException.class);
    }

    @Test
    void enablingOutsideTheCurrentGrant_isRejected_andEnablementUnchanged() {
        assertThatThrownBy(() -> subject.update(enable("BVL-01", Set.of(Tenor._6M), Set.of())))
                .isInstanceOf(InvalidInstitutionException.class);
        assertThat(enablementRepository.find("BVL-01", "EUR").isEmpty()).isTrue();
    }

    @Test
    void enablingUnderAnInactiveGrant_isRejected() {
        grantRepository.save(grantRepository.findAll().getFirst().withActive(false));

        assertThatThrownBy(() -> subject.update(enable("BVL-01", Set.of(Tenor._1M), Set.of())))
                .isInstanceOf(InvalidInstitutionException.class);
    }

    @Test
    void enablingATermTenorWithoutTheTermAccount_isRejected() {
        institutionRepository.findByInstitutionCode("BVL-01").orElseThrow()
                .changeAccounts(CounterpartyAccounts.of(null, "PAR-BNP-OC"));

        assertThatThrownBy(() -> subject.update(enable("BVL-01", Set.of(Tenor._3M), Set.of())))
                .isInstanceOf(InvalidInstitutionException.class)
                .hasMessageContaining("Term counterparty account");
        assertThat(enablementRepository.find("BVL-01", "EUR").isEmpty()).isTrue();
    }

    @Test
    void enablingAnOnCallNoticeWithoutTheOnCallAccount_isRejected() {
        institutionRepository.findByInstitutionCode("BVL-01").orElseThrow()
                .changeAccounts(CounterpartyAccounts.of("PAR-BNP-T", null));

        assertThatThrownBy(() -> subject.update(enable("BVL-01", Set.of(), Set.of(NoticePeriod._24H))))
                .isInstanceOf(InvalidInstitutionException.class)
                .hasMessageContaining("OnCall counterparty account");
    }

    @Test
    void keepingAValueNoLongerGranted_isAllowed_andReportedAsEnabledNotGranted() {
        subject.update(enable("BVL-01", Set.of(Tenor._1M, Tenor._3M), Set.of()));
        grantRepository.save(grantRepository.findAll().getFirst().withEnabledSets(
                EnumSet.of(Tenor._1M), EnumSet.of(NoticePeriod._24H)));

        subject.update(enable("BVL-01", Set.of(Tenor._1M, Tenor._3M), Set.of()));

        CurrencyEnablement eur = subject.enablementsOf("BVL-01").getFirst();
        assertThat(eur.currency()).isEqualTo("EUR");
        assertThat(eur.grantedTenors()).containsExactly(Tenor._1M);
        assertThat(eur.enabledTenors()).containsExactly(Tenor._1M, Tenor._3M);
    }

    @Test
    void enablementsOf_listsGrantedOrClientEnabledCurrencies() {
        grantRepository.save(
                new DelegatedInstitutionGrant(
                        "BNP-01", PAR, "USD", EnumSet.of(Tenor._6M), EnumSet.noneOf(NoticePeriod.class), true));
        grantRepository.save(
                new DelegatedInstitutionGrant(
                        "BNP-01", PAR, "CHF", EnumSet.of(Tenor._6M), EnumSet.noneOf(NoticePeriod.class), false));
        subject.update(enable("BVL-01", Set.of(Tenor._1M), Set.of(NoticePeriod._24H)));

        assertThat(subject.enablementsOf("BVL-01"))
                .containsExactly(
                        new CurrencyEnablement(
                                "EUR",
                                Set.of(Tenor._1M, Tenor._3M),
                                Set.of(NoticePeriod._24H),
                                Set.of(Tenor._1M),
                                Set.of(NoticePeriod._24H)),
                        new CurrencyEnablement("USD", Set.of(Tenor._6M), Set.of(), Set.of(), Set.of()));
    }

    @Test
    void enablementsOf_nativeInstitution_isEmpty() {
        assertThat(subject.enablementsOf("BNP-01")).isEmpty();
    }
}
