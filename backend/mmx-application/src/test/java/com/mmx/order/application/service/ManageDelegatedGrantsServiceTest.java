package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ManageDelegatedGrantsUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.domain.exception.InvalidDelegatedGrantException;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.DelegatedGrantKey;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManageDelegatedGrantsServiceTest {

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");
    private static final BigDecimal MIN_SUB = new BigDecimal("1000000.00");
    private static final BigDecimal MIN_LIFE = new BigDecimal("250000.00");

    private FakeGrantRepository grantRepository;
    private FakeInstitutionRepository institutionRepository;
    private FakeManagedCurrencyRepository currencyRepository;
    private ManageDelegatedGrantsService service;

    @BeforeEach
    void setUp() {
        grantRepository = new FakeGrantRepository();
        institutionRepository = new FakeInstitutionRepository();
        currencyRepository = new FakeManagedCurrencyRepository();
        service = new ManageDelegatedGrantsService(grantRepository, institutionRepository, currencyRepository);

        institutionRepository.put(new Institution("BNP", "BNP", true));
        currencyRepository.put(
                new ManagedCurrency(
                        "EUR",
                        true,
                        MIN_SUB,
                        MIN_LIFE,
                        EnumSet.of(Tenor._1M, Tenor._3M, Tenor._6M),
                        EnumSet.of(NoticePeriod._24H, NoticePeriod._48H)));
    }

    private static ScopeContext traderOnHub() {
        return new ScopeContext(LOC, Role.TRADER);
    }

    private static ManageDelegatedGrantsUseCase.CreateGrantCommand createCommand(
            ScopeContext scope, String hubInstitution, EnumSet<Tenor> tenors, EnumSet<NoticePeriod> notices) {
        return new ManageDelegatedGrantsUseCase.CreateGrantCommand(
                scope, hubInstitution, PAR, "EUR", tenors, notices);
    }

    @Test
    void trader_createsGrant_success() {
        DelegatedInstitutionGrant created =
                service.createGrant(
                        createCommand(
                                traderOnHub(),
                                "BNP",
                                EnumSet.of(Tenor._1M, Tenor._3M),
                                EnumSet.noneOf(NoticePeriod.class)));

        assertThat(created.key()).isEqualTo(new DelegatedGrantKey("BNP", PAR, "EUR"));
        assertThat(created.getEnabledTenors()).containsExactly(Tenor._1M, Tenor._3M);
        assertThat(created.isActive()).isTrue();
        assertThat(grantRepository.all()).hasSize(1);
    }

    @Test
    void clientRepresentative_createGrant_rejected() {
        assertThatThrownBy(() ->
                        service.createGrant(
                                createCommand(
                                        new ScopeContext(PAR, Role.CLIENT_REPRESENTATIVE),
                                        "BNP",
                                        EnumSet.of(Tenor._1M),
                                        EnumSet.noneOf(NoticePeriod.class))))
                .isInstanceOf(UnauthorizedUserException.class);

        assertThat(grantRepository.all()).isEmpty();
    }

    @Test
    void inactiveHubInstitution_createGrant_rejected() {
        institutionRepository.put(new Institution("OLD", "Old Bank", false));

        assertThatThrownBy(() ->
                        service.createGrant(
                                createCommand(
                                        traderOnHub(),
                                        "OLD",
                                        EnumSet.of(Tenor._1M),
                                        EnumSet.noneOf(NoticePeriod.class))))
                .isInstanceOf(InvalidDelegatedGrantException.class)
                .hasMessageContaining("active");

        assertThat(grantRepository.all()).isEmpty();
    }

    @Test
    void subsetViolation_createGrant_rejected() {
        assertThatThrownBy(() ->
                        service.createGrant(
                                createCommand(
                                        traderOnHub(),
                                        "BNP",
                                        EnumSet.of(Tenor._1M, Tenor._1Y),
                                        EnumSet.noneOf(NoticePeriod.class))))
                .isInstanceOf(InvalidDelegatedGrantException.class)
                .hasMessageContaining("enabledTenors");

        assertThat(grantRepository.all()).isEmpty();
    }

    @Test
    void trader_updatesGrant_replacesEnabledSets() {
        service.createGrant(
                createCommand(
                        traderOnHub(),
                        "BNP",
                        EnumSet.of(Tenor._1M, Tenor._3M),
                        EnumSet.noneOf(NoticePeriod.class)));

        DelegatedInstitutionGrant updated =
                service.updateGrant(
                        new ManageDelegatedGrantsUseCase.UpdateGrantCommand(
                                traderOnHub(),
                                "BNP",
                                PAR,
                                "EUR",
                                EnumSet.of(Tenor._6M),
                                EnumSet.of(NoticePeriod._24H)));

        assertThat(updated.getEnabledTenors()).containsExactly(Tenor._6M);
        assertThat(updated.getEnabledNoticePeriods()).containsExactly(NoticePeriod._24H);
    }

    @Test
    void trader_deactivatesGrant_togglesActiveFalseWithoutHardDelete() {
        service.createGrant(
                createCommand(
                        traderOnHub(),
                        "BNP",
                        EnumSet.of(Tenor._1M),
                        EnumSet.noneOf(NoticePeriod.class)));

        DelegatedInstitutionGrant deactivated =
                service.deactivateGrant(new DelegatedGrantKey("BNP", PAR, "EUR"), traderOnHub());

        assertThat(deactivated.isActive()).isFalse();
        assertThat(grantRepository.findByKey(new DelegatedGrantKey("BNP", PAR, "EUR")))
                .isPresent()
                .get()
                .matches(g -> !g.isActive(), "is inactive (not hard-deleted)");
    }

    @Test
    void clientRepresentative_deactivateGrant_rejected() {
        assertThatThrownBy(() ->
                        service.deactivateGrant(
                                new DelegatedGrantKey("BNP", PAR, "EUR"),
                                new ScopeContext(PAR, Role.CLIENT_REPRESENTATIVE)))
                .isInstanceOf(UnauthorizedUserException.class);
    }

    @Test
    void trader_listsGrants() {
        service.createGrant(
                createCommand(
                        traderOnHub(),
                        "BNP",
                        EnumSet.of(Tenor._1M),
                        EnumSet.noneOf(NoticePeriod.class)));

        assertThat(service.listGrants(traderOnHub()))
                .hasSize(1)
                .first()
                .extracting(DelegatedInstitutionGrant::getHubInstitutionCode)
                .isEqualTo("BNP");
    }

    // --- fakes ---

    private static final class FakeGrantRepository implements DelegatedGrantRepository {
        private final Map<DelegatedGrantKey, DelegatedInstitutionGrant> store = new LinkedHashMap<>();

        @Override
        public List<DelegatedInstitutionGrant> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<DelegatedInstitutionGrant> findByClientLegalEntityCode(LegalEntityCode clientLegalEntityCode) {
            return store.values().stream()
                    .filter(g -> g.getClientLegalEntityCode().equals(clientLegalEntityCode))
                    .toList();
        }

        @Override
        public Optional<DelegatedInstitutionGrant> findByKey(DelegatedGrantKey key) {
            return Optional.ofNullable(store.get(key));
        }

        @Override
        public boolean existsByKey(DelegatedGrantKey key) {
            return store.containsKey(key);
        }

        @Override
        public boolean existsActiveGrantForHubInstitutionAndClient(
                String hubInstitutionCode, LegalEntityCode clientLegalEntityCode) {
            return store.values().stream()
                    .anyMatch(
                            g ->
                                    g.getHubInstitutionCode().equals(hubInstitutionCode)
                                            && g.getClientLegalEntityCode().equals(clientLegalEntityCode)
                                            && g.isActive());
        }

        @Override
        public DelegatedInstitutionGrant save(DelegatedInstitutionGrant grant) {
            store.put(grant.key(), grant);
            return grant;
        }

        List<DelegatedInstitutionGrant> all() {
            return findAll();
        }
    }

    private static final class FakeInstitutionRepository implements InstitutionRepository {
        private final Map<String, Institution> store = new LinkedHashMap<>();

        void put(Institution institution) {
            store.put(institution.getInstitutionCode(), institution);
        }

        @Override
        public List<Institution> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<Institution> findActive() {
            return store.values().stream().filter(Institution::isActive).toList();
        }

        @Override
        public List<Institution> findNativeByLegalEntityCode(LegalEntityCode legalEntityCode) {
            return findAll();
        }

        @Override
        public Optional<Institution> findByInstitutionCode(String institutionCode) {
            return Optional.ofNullable(store.get(institutionCode));
        }

        @Override
        public boolean existsAny() {
            return !store.isEmpty();
        }

        @Override
        public int maxSuffixForAcronym(String acronymBase) {
            return 0;
        }

        @Override
        public Institution save(Institution institution) {
            store.put(institution.getInstitutionCode(), institution);
            return institution;
        }
    }

    private static final class FakeManagedCurrencyRepository implements ManagedCurrencyRepository {
        private final Map<String, ManagedCurrency> store = new LinkedHashMap<>();

        void put(ManagedCurrency currency) {
            store.put(currency.getCode(), currency);
        }

        @Override
        public List<ManagedCurrency> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<ManagedCurrency> findAllByLegalEntityCode(LegalEntityCode legalEntityCode) {
            return findAll();
        }

        @Override
        public Optional<ManagedCurrency> findByCode(String code) {
            return Optional.ofNullable(store.get(code));
        }

        @Override
        public boolean existsByCode(String code) {
            return store.containsKey(code);
        }

        @Override
        public ManagedCurrency save(ManagedCurrency currency) {
            store.put(currency.getCode(), currency);
            return currency;
        }
    }
}
