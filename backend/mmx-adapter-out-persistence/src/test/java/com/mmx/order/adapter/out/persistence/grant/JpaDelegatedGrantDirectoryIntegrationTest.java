package com.mmx.order.adapter.out.persistence.grant;

import com.mmx.order.adapter.out.persistence.FixedScopeContextProvider;
import com.mmx.order.adapter.out.persistence.JpaDelegatedGrantDirectory;
import com.mmx.order.adapter.out.persistence.JpaDelegatedGrantRepository;
import com.mmx.order.adapter.out.persistence.JpaInstitutionRepository;
import com.mmx.order.adapter.out.persistence.PersistenceTestCleanup;
import com.mmx.order.adapter.out.persistence.mapper.DelegatedGrantPersistenceMapper;
import com.mmx.order.adapter.out.persistence.mapper.InstitutionPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataDelegatedGrantRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataInstitutionRepository;
import com.mmx.order.application.port.out.GrantResolution;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.HubInstitutionLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
@Tag("integration")

@SpringBootTest
@ActiveProfiles("test")
class JpaDelegatedGrantDirectoryIntegrationTest {

    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");

    @Autowired
    SpringDataInstitutionRepository institutionRepository;

    @Autowired
    SpringDataDelegatedGrantRepository grantSpringData;

    @Autowired
    InstitutionPersistenceMapper institutionMapper;

    @Autowired
    DelegatedGrantPersistenceMapper grantMapper;

    JpaInstitutionRepository nativeRepository;
    JpaDelegatedGrantRepository grantRepository;
    JpaDelegatedGrantDirectory directory;
    FixedScopeContextProvider scopeProvider;

    @BeforeEach
    void setUp() {
        PersistenceTestCleanup.clearGrantsAndInstitutions(grantSpringData, institutionRepository);
        scopeProvider = new FixedScopeContextProvider();
        nativeRepository = new JpaInstitutionRepository(institutionRepository, institutionMapper, scopeProvider);
        grantRepository = new JpaDelegatedGrantRepository(grantSpringData, grantMapper);
        directory = new JpaDelegatedGrantDirectory(grantSpringData, grantMapper);
    }

    @Test
    void resolveTenor_grantedWhenInEnabledSet() {
        nativeRepository.save(new Institution("BI-01", "BankCo", true));
        grantRepository.save(
                new DelegatedInstitutionGrant(
                        "BI-01",
                        PAR,
                        "EUR",
                        EnumSet.of(Tenor._3M),
                        EnumSet.noneOf(NoticePeriod.class),
                        true));
        scopeProvider.setScope(new com.mmx.order.application.port.in.ScopeContext(PAR, com.mmx.order.domain.model.Role.CLIENT_REPRESENTATIVE));
        Institution proxy =
                nativeRepository.save(
                        Institution.onboardFromGrant("BVL-01", "BankCo", new HubInstitutionLink(LOC, "BI-01"), PAR, CounterpartyAccounts.none()));

        assertThat(directory.lookupTenor(PAR, proxy.getInstitutionCode(), "EUR", Tenor._3M))
                .isEqualTo(GrantResolution.GRANTED);
        assertThat(directory.lookupTenor(PAR, proxy.getInstitutionCode(), "EUR", Tenor._1Y))
                .isEqualTo(GrantResolution.NOT_IN_ENABLED_SET);
    }

    @Test
    void resolveTenor_inactiveGrant_returnsNoActiveGrant() {
        nativeRepository.save(new Institution("BI-01", "BankCo", true));
        grantRepository.save(
                new DelegatedInstitutionGrant(
                        "BI-01",
                        PAR,
                        "EUR",
                        EnumSet.of(Tenor._3M),
                        EnumSet.noneOf(NoticePeriod.class),
                        false));
        scopeProvider.setScope(new com.mmx.order.application.port.in.ScopeContext(PAR, com.mmx.order.domain.model.Role.CLIENT_REPRESENTATIVE));
        Institution proxy =
                nativeRepository.save(
                        Institution.onboardFromGrant("BVL-01", "BankCo", new HubInstitutionLink(LOC, "BI-01"), PAR, CounterpartyAccounts.none()));

        assertThat(directory.lookupTenor(PAR, proxy.getInstitutionCode(), "EUR", Tenor._3M))
                .isEqualTo(GrantResolution.NO_ACTIVE_GRANT);
    }
}
