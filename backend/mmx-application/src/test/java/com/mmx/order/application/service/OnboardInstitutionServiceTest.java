package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase;
import com.mmx.order.application.port.in.OnboardedInstitution;
import com.mmx.order.application.port.in.OnboardInstitutionUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ProxyInstitutionRepository;
import com.mmx.order.domain.exception.InvalidDelegatedGrantException;
import com.mmx.order.domain.exception.InvalidInstitutionException;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.DelegatedGrantKey;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.LegalEntityRegistry;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Organisation;
import com.mmx.order.domain.model.OrganisationCode;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.ThinProxyInstitution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@Tag("fast")

class OnboardInstitutionServiceTest {

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");

    private FakeInstitutionRepository institutionRepository;
    private FakeProxyRepository proxyRepository;
    private FakeGrantRepository grantRepository;
    private FakeLegalEntityRepository legalEntityRepository;
    private OnboardInstitutionService service;

    @BeforeEach
    void setUp() {
        institutionRepository = new FakeInstitutionRepository();
        proxyRepository = new FakeProxyRepository();
        grantRepository = new FakeGrantRepository();
        legalEntityRepository = new FakeLegalEntityRepository();

        Organisation lodh = new Organisation(new OrganisationCode("LODH"));
        LegalEntityRegistry registry = new LegalEntityRegistry();
        LegalEntity loc = lodh.createTradingHub(LOC, registry);
        LegalEntity par = lodh.createTradingClient(PAR, loc, registry);
        legalEntityRepository.put(loc);
        legalEntityRepository.put(par);

        institutionRepository.put(new Institution("BNP", "BNP", true));

        service = new OnboardInstitutionService(
                institutionRepository,
                proxyRepository,
                grantRepository,
                legalEntityRepository,
                new ManageInstitutionSettingsUseCase() {
                    @Override
                    public List<Institution> listAll(boolean activeOnly) {
                        return institutionRepository.findAll();
                    }

                    @Override
                    public Institution getByCode(String institutionCode) {
                        return institutionRepository.findByInstitutionCode(institutionCode).orElseThrow();
                    }

                    @Override
                    public Institution onboard(OnboardCommand command) {
                        String base = command.displayName().toUpperCase().replaceAll("[^A-Z0-9]", "");
                        if (base.length() > 6) {
                            base = base.substring(0, 6);
                        }
                        int next = institutionRepository.maxSuffixForAcronym(base) + 1;
                        String code = base + "-" + String.format("%02d", next);
                        Institution created = new Institution(code, command.displayName(), true);
                        return institutionRepository.save(created);
                    }

                    @Override
                    public Institution deactivate(String institutionCode) {
                        return null;
                    }

                    @Override
                    public Institution activate(String institutionCode) {
                        return null;
                    }
                });
    }

    private static ScopeContext traderOnHub() {
        return new ScopeContext(LOC, Role.TRADER);
    }

    private static ScopeContext clientOnClient() {
        return new ScopeContext(PAR, Role.CLIENT_REPRESENTATIVE);
    }

    private void grantActiveForBnpParEur() {
        grantRepository.save(
                new DelegatedInstitutionGrant(
                        "BNP",
                        PAR,
                        "EUR",
                        EnumSet.of(Tenor._1M, Tenor._3M),
                        EnumSet.noneOf(NoticePeriod.class),
                        true));
    }

    @Test
    void trader_onboardsNativeInstitution_withDisplayName() {
        OnboardedInstitution result =
                service.onboard(
                        new OnboardInstitutionUseCase.OnboardCommand(traderOnHub(), "HSBC", null));

        assertThat(result).isInstanceOf(OnboardedInstitution.Native.class);
        Institution nativeInst = ((OnboardedInstitution.Native) result).institution();
        assertThat(nativeInst.getDisplayName()).isEqualTo("HSBC");
        assertThat(nativeInst.getInstitutionCode()).startsWith("HSBC-");
        assertThat(nativeInst.isActive()).isTrue();
    }

    @Test
    void client_onboardsProxy_fromGrant_success() {
        grantActiveForBnpParEur();

        OnboardedInstitution result =
                service.onboard(
                        new OnboardInstitutionUseCase.OnboardCommand(clientOnClient(), null, "BNP"));

        assertThat(result).isInstanceOf(OnboardedInstitution.Proxy.class);
        ThinProxyInstitution proxy = ((OnboardedInstitution.Proxy) result).proxy();
        assertThat(proxy.getDisplayName()).isEqualTo("BNP via LOC");
        assertThat(proxy.getHubLegalEntityCode()).isEqualTo(LOC);
        assertThat(proxy.getHubInstitutionCode()).isEqualTo("BNP");
        assertThat(proxy.getInstitutionCode()).startsWith("BVL-");
        assertThat(proxy.isActive()).isTrue();
        assertThat(proxyRepository.savedProxies).hasSize(1);
    }

    @Test
    void client_proxyWithoutGrant_rejected() {
        assertThatThrownBy(() ->
                        service.onboard(
                                new OnboardInstitutionUseCase.OnboardCommand(clientOnClient(), null, "BNP")))
                .isInstanceOf(InvalidDelegatedGrantException.class)
                .hasMessageContaining("grant");

        assertThat(proxyRepository.savedProxies).isEmpty();
    }

    @Test
    void client_freeFormProxyName_rejected() {
        grantActiveForBnpParEur();

        assertThatThrownBy(() ->
                        service.onboard(
                                new OnboardInstitutionUseCase.OnboardCommand(
                                        clientOnClient(), "My Custom Name", "BNP")))
                .isInstanceOf(InvalidInstitutionException.class);

        assertThat(proxyRepository.savedProxies).isEmpty();
    }

    @Test
    void trader_cannotOnboardProxy() {
        grantActiveForBnpParEur();

        assertThatThrownBy(() ->
                        service.onboard(
                                new OnboardInstitutionUseCase.OnboardCommand(traderOnHub(), null, "BNP")))
                .isInstanceOf(InvalidInstitutionException.class);
    }

    @Test
    void client_cannotOnboardNativeWithDisplayName() {
        assertThatThrownBy(() ->
                        service.onboard(
                                new OnboardInstitutionUseCase.OnboardCommand(
                                        clientOnClient(), "HSBC", null)))
                .isInstanceOf(UnauthorizedUserException.class);
    }

    // --- fakes ---

    private static final class FakeInstitutionRepository implements InstitutionRepository {
        private final Map<String, Institution> store = new LinkedHashMap<>();
        private final Map<String, Integer> suffixes = new LinkedHashMap<>();

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
            return suffixes.getOrDefault(acronymBase, 0);
        }

        @Override
        public Institution save(Institution institution) {
            store.put(institution.getInstitutionCode(), institution);
            String base = institution.getInstitutionCode().split("-")[0];
            suffixes.merge(base, 1, Integer::sum);
            return institution;
        }
    }

    private static final class FakeProxyRepository implements ProxyInstitutionRepository {
        private final Map<String, ThinProxyInstitution> store = new LinkedHashMap<>();
        private final List<ThinProxyInstitution> savedProxies = new ArrayList<>();
        private int suffixCounter = 0;

        @Override
        public List<ThinProxyInstitution> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<ThinProxyInstitution> findByClientLegalEntity(LegalEntityCode clientLegalEntityCode) {
            return store.values().stream()
                    .filter(p -> belongsToClient(p, clientLegalEntityCode))
                    .toList();
        }

        private boolean belongsToClient(ThinProxyInstitution proxy, LegalEntityCode client) {
            // In tests the proxy is stored with the client encoded in its reference; track via a side map.
            return clientOwners.getOrDefault(proxy.getInstitutionCode(), client).equals(client);
        }

        private final Map<String, LegalEntityCode> clientOwners = new LinkedHashMap<>();

        @Override
        public Optional<ThinProxyInstitution> findByInstitutionCode(String institutionCode) {
            return Optional.ofNullable(store.get(institutionCode));
        }

        @Override
        public int maxSuffixForAcronym(String acronymBase) {
            return suffixCounter;
        }

        @Override
        public ThinProxyInstitution save(ThinProxyInstitution proxy) {
            store.put(proxy.getInstitutionCode(), proxy);
            savedProxies.add(proxy);
            suffixCounter++;
            return proxy;
        }
    }

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
    }

    private static final class FakeLegalEntityRepository implements LegalEntityRepository {
        private final Map<LegalEntityCode, LegalEntity> store = new LinkedHashMap<>();

        void put(LegalEntity entity) {
            store.put(entity.getCode(), entity);
        }

        @Override
        public Optional<LegalEntity> findByCode(LegalEntityCode code) {
            return Optional.ofNullable(store.get(code));
        }

        @Override
        public List<LegalEntity> findByOrganisationCode(OrganisationCode organisationCode) {
            return new ArrayList<>(store.values());
        }

        @Override
        public boolean belongsToOrganisation(LegalEntityCode code, OrganisationCode organisationCode) {
            return true;
        }
    }
}
