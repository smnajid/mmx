package com.mmx.order.application.service;

import com.mmx.order.application.port.in.InstitutionListView;
import com.mmx.order.application.port.in.ListInstitutionsUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ProxyInstitutionRepository;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.ThinProxyInstitution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListInstitutionsServiceTest {

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");

    private FakeInstitutionRepository institutionRepository;
    private FakeProxyRepository proxyRepository;
    private ListInstitutionsService service;

    @BeforeEach
    void setUp() {
        institutionRepository = new FakeInstitutionRepository();
        proxyRepository = new FakeProxyRepository();
        service = new ListInstitutionsService(institutionRepository, proxyRepository);

        institutionRepository.put(new Institution("BNP", "BNP", true));
        institutionRepository.put(new Institution("HSBC", "HSBC", true));
        proxyRepository.put(PAR, new ThinProxyInstitution("BVL-01", "BNP via LOC", LOC, "BNP", true));
    }

    @Test
    void trader_listReturnsNativeHubInstitutions() {
        InstitutionListView view = service.list(new ScopeContext(LOC, Role.TRADER));

        assertThat(view).isInstanceOf(InstitutionListView.Native.class);
        List<Institution> natives = ((InstitutionListView.Native) view).institutions();
        assertThat(natives).extracting(Institution::getInstitutionCode).contains("BNP", "HSBC");
    }

    @Test
    void client_listReturnsOnlyProxies_andExcludesNativeHubInstitutions() {
        InstitutionListView view = service.list(new ScopeContext(PAR, Role.CLIENT_REPRESENTATIVE));

        assertThat(view).isInstanceOf(InstitutionListView.Proxies.class);
        List<ThinProxyInstitution> proxies = ((InstitutionListView.Proxies) view).proxies();
        assertThat(proxies).extracting(ThinProxyInstitution::getInstitutionCode).containsExactly("BVL-01");
        assertThat(proxies).extracting(ThinProxyInstitution::getDisplayName).containsExactly("BNP via LOC");
        // Native hub institutions do NOT appear in the client's list.
        assertThat(proxies).extracting(ThinProxyInstitution::getInstitutionCode).doesNotContain("BNP", "HSBC");
    }

    @Test
    void client_withoutProxies_returnsEmptyList() {
        InstitutionListView view =
                service.list(new ScopeContext(new LegalEntityCode("SIN"), Role.CLIENT_REPRESENTATIVE));

        assertThat(view).isInstanceOf(InstitutionListView.Proxies.class);
        assertThat(((InstitutionListView.Proxies) view).proxies()).isEmpty();
    }

    @Test
    void unknownRole_rejected() {
        assertThatThrownBy(() -> service.list(null)).isInstanceOf(UnauthorizedUserException.class);
    }

    // --- fakes ---

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
        public List<Institution> findNativeByLegalEntityCode(LegalEntityCode legalEntityCode) {
            if (LOC.equals(legalEntityCode)) {
                return new ArrayList<>(store.values());
            }
            return List.of();
        }

        @Override
        public List<Institution> findActive() {
            return store.values().stream().filter(Institution::isActive).toList();
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

    private static final class FakeProxyRepository implements ProxyInstitutionRepository {
        private final Map<LegalEntityCode, List<ThinProxyInstitution>> byClient = new LinkedHashMap<>();

        void put(LegalEntityCode client, ThinProxyInstitution proxy) {
            byClient.computeIfAbsent(client, c -> new ArrayList<>()).add(proxy);
        }

        @Override
        public List<ThinProxyInstitution> findAll() {
            return byClient.values().stream().flatMap(List::stream).toList();
        }

        @Override
        public List<ThinProxyInstitution> findByClientLegalEntity(LegalEntityCode clientLegalEntityCode) {
            return byClient.getOrDefault(clientLegalEntityCode, List.of());
        }

        @Override
        public Optional<ThinProxyInstitution> findByInstitutionCode(String institutionCode) {
            return findAll().stream().filter(p -> p.getInstitutionCode().equals(institutionCode)).findFirst();
        }

        @Override
        public int maxSuffixForAcronym(String acronymBase) {
            return 0;
        }

        @Override
        public ThinProxyInstitution save(ThinProxyInstitution proxy) {
            return proxy;
        }
    }
}
