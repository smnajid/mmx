package com.mmx.order.adapter.out.persistence.institution;

import com.mmx.order.adapter.out.persistence.FixedScopeContextProvider;
import com.mmx.order.adapter.out.persistence.JpaInstitutionRepository;
import com.mmx.order.adapter.out.persistence.PersistenceTestCleanup;
import com.mmx.order.adapter.out.persistence.mapper.InstitutionPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataDelegatedGrantRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataInstitutionRepository;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOnCallRateSegmentRepository;
import com.mmx.order.application.port.out.ScopeContextProvider;
import com.mmx.order.domain.model.Institution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
@Tag("integration")

@SpringBootTest
@ActiveProfiles("test")
class JpaInstitutionRepositoryTest {

    @Autowired
    SpringDataInstitutionRepository springDataRepository;

    @Autowired
    SpringDataOnCallRateSegmentRepository onCallRateSegmentRepository;

    @Autowired
    SpringDataDelegatedGrantRepository grantRepository;

    @Autowired
    InstitutionPersistenceMapper mapper;

    JpaInstitutionRepository repository;
    ScopeContextProvider scopeContextProvider;

    @BeforeEach
    void setUp() {
        scopeContextProvider = new FixedScopeContextProvider();
        repository = new JpaInstitutionRepository(springDataRepository, mapper, scopeContextProvider);
        PersistenceTestCleanup.clearInstitutionsAndOnCall(
                grantRepository, onCallRateSegmentRepository, springDataRepository);
    }

    @Test
    void saveAndList() {
        repository.save(new Institution("HSBC-01", "HSBC", true));
        List<Institution> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getInstitutionCode()).isEqualTo("HSBC-01");
    }

    @Test
    void maxSuffixForAcronym_hsbcPattern() {
        repository.save(new Institution("HSBC-01", "HSBC", true));
        repository.save(new Institution("HSBC-02", "HSBC duplicate", true));
        assertThat(repository.maxSuffixForAcronym("HSBC")).isEqualTo(2);
    }

    @Test
    void existsAny_falseWhenEmpty() {
        assertThat(repository.existsAny()).isFalse();
    }

    @Test
    void findActive_excludesInactive() {
        repository.save(new Institution("HSBC-01", "HSBC", true));
        repository.save(new Institution("BCI-01", "BCI", false));
        assertThat(repository.findActive()).extracting(Institution::getInstitutionCode).containsExactly("HSBC-01");
    }

    @Test
    void findNativeByLegalEntityCode_excludesProxies() {
        repository.save(new Institution("HSBC-01", "HSBC", true));
        assertThat(repository.findNativeByLegalEntityCode(new com.mmx.order.domain.model.LegalEntityCode("LOC")))
                .extracting(Institution::getInstitutionCode)
                .containsExactly("HSBC-01");
    }
}
