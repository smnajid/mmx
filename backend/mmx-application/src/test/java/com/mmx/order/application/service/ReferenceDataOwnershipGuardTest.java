package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.HubScopeResolver;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ReferenceDataMutationGuard;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.LegalEntityRegistry;
import com.mmx.order.domain.model.Organisation;
import com.mmx.order.domain.model.OrganisationCode;
import com.mmx.order.domain.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ReferenceDataOwnershipGuardTest {

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");

    private FakeLegalEntityRepository legalEntityRepository;
    private ReferenceDataMutationGuard guard;
    private HubScopeResolver resolver;

    @BeforeEach
    void setUp() {
        legalEntityRepository = new FakeLegalEntityRepository();
        Organisation lodh = new Organisation(new OrganisationCode("LODH"));
        LegalEntityRegistry registry = new LegalEntityRegistry();
        LegalEntity loc = lodh.createTradingHub(LOC, registry);
        LegalEntity par = lodh.createTradingClient(PAR, loc, registry);
        legalEntityRepository.put(loc);
        legalEntityRepository.put(par);

        guard = new ReferenceDataMutationGuard();
        resolver = new HubScopeResolver(legalEntityRepository);
    }

    @Test
    void trader_mutationAllowed() {
        assertThatCode(() -> guard.ensureTrader(new ScopeContext(LOC, Role.TRADER)))
                .doesNotThrowAnyException();
    }

    @Test
    void client_currencyMutation_rejected() {
        assertThatThrownBy(() -> guard.ensureTrader(new ScopeContext(PAR, Role.CLIENT_REPRESENTATIVE)))
                .isInstanceOf(UnauthorizedUserException.class)
                .hasMessageContaining("Trader");
    }

    @Test
    void client_termRateAndOnCallMutation_rejected() {
        assertThatThrownBy(() -> guard.ensureTrader(new ScopeContext(PAR, Role.CLIENT_REPRESENTATIVE)))
                .isInstanceOf(UnauthorizedUserException.class);
    }

    @Test
    void trader_readTargetsOwnLegalEntity() {
        assertThat(resolver.resolveHubLegalEntityCode(new ScopeContext(LOC, Role.TRADER))).isEqualTo(LOC);
    }

    @Test
    void client_readTargetsConnectedHubLegalEntity() {
        assertThat(resolver.resolveHubLegalEntityCode(new ScopeContext(PAR, Role.CLIENT_REPRESENTATIVE)))
                .isEqualTo(LOC);
    }

    @Test
    void nullScope_rejected() {
        assertThatThrownBy(() -> guard.ensureTrader(null)).isInstanceOf(UnauthorizedUserException.class);
    }

    // --- fake ---

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
            return List.copyOf(store.values());
        }

        @Override
        public boolean belongsToOrganisation(LegalEntityCode code, OrganisationCode organisationCode) {
            return true;
        }
    }
}
