package com.mmx.order.adapter.out.persistence.currency;

import com.mmx.order.adapter.out.persistence.FixedScopeContextProvider;
import com.mmx.order.adapter.out.persistence.JpaManagedCurrencyRepository;
import com.mmx.order.adapter.out.persistence.mapper.ManagedCurrencyPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataManagedCurrencyRepository;
import com.mmx.order.application.port.out.ScopeContextProvider;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
@Tag("integration")

@SpringBootTest
@ActiveProfiles("test")
class JpaManagedCurrencyRepositoryTest {

    @Autowired
    SpringDataManagedCurrencyRepository springDataRepository;

    @Autowired
    ManagedCurrencyPersistenceMapper mapper;

    JpaManagedCurrencyRepository repository;

    @BeforeEach
    void setUp() {
        ScopeContextProvider scopeContextProvider = new FixedScopeContextProvider();
        repository = new JpaManagedCurrencyRepository(springDataRepository, mapper, scopeContextProvider);
        springDataRepository.deleteAll();
    }

    @Test
    void saveAndFindByCode() {
        ManagedCurrency saved = repository.save(sampleEur());
        assertThat(repository.findByCode("EUR")).isPresent();
        assertThat(repository.findByCode("EUR").orElseThrow().getCode()).isEqualTo(saved.getCode());
    }

    @Test
    void findAll_includesActiveAndInactive() {
        repository.save(sampleEur());
        repository.save(
                new ManagedCurrency(
                        "USD",
                        false,
                        new BigDecimal("500000.00"),
                        new BigDecimal("100000.00"),
                        EnumSet.of(Tenor._1M),
                        EnumSet.of(NoticePeriod._48H)));
        List<ManagedCurrency> all = repository.findAll();
        assertThat(all).hasSize(2);
        assertThat(all).anyMatch(c -> c.getCode().equals("USD") && !c.isActive());
    }

    @Test
    void existsByCode() {
        repository.save(sampleEur());
        assertThat(repository.existsByCode("EUR")).isTrue();
        assertThat(repository.existsByCode("CHF")).isFalse();
    }

    @Test
    void findAllByLegalEntityCode_returnsHubScopedRows() {
        repository.save(sampleEur());
        assertThat(repository.findAllByLegalEntityCode(new LegalEntityCode("LOC"))).hasSize(1);
        assertThat(repository.findAllByLegalEntityCode(new LegalEntityCode("PAR"))).isEmpty();
    }

    @Test
    void secondSaveWithSameCode_updatesExistingRow() {
        repository.save(sampleEur());
        ManagedCurrency deactivated =
                new ManagedCurrency(
                        "EUR",
                        false,
                        new BigDecimal("2000000.00"),
                        new BigDecimal("500000.00"),
                        EnumSet.of(Tenor._3M),
                        EnumSet.of(NoticePeriod._24H));
        repository.save(deactivated);

        assertThat(springDataRepository.count()).isEqualTo(1);
        ManagedCurrency loaded = repository.findByCode("EUR").orElseThrow();
        assertThat(loaded.isActive()).isFalse();
        assertThat(loaded.getMinSubscriptionAmount()).isEqualByComparingTo("2000000.00");
        assertThat(loaded.getEnabledTenors()).containsExactly(Tenor._3M);
        assertThat(loaded.getEnabledNoticePeriods()).containsExactly(NoticePeriod._24H);
    }

    private static ManagedCurrency sampleEur() {
        return new ManagedCurrency(
                "EUR",
                true,
                new BigDecimal("1000000.00"),
                new BigDecimal("250000.00"),
                EnumSet.allOf(Tenor.class),
                EnumSet.allOf(NoticePeriod.class));
    }
}
