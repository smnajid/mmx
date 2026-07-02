package com.mmx.order.adapter.out.persistence.globalaccount;

import com.mmx.order.adapter.out.persistence.JpaGlobalAccountDirectory;
import com.mmx.order.adapter.out.persistence.entity.GlobalAccountEntity;
import com.mmx.order.adapter.out.persistence.repository.SpringDataGlobalAccountRepository;
import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.LegalEntityCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JpaGlobalAccountDirectoryIntegrationTest {

    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");
    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");

    @Autowired
    SpringDataGlobalAccountRepository repository;

    JpaGlobalAccountDirectory directory;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        directory = new JpaGlobalAccountDirectory(repository);
    }

    @Test
    void resolve_returns_configured_account() {
        repository.save(new GlobalAccountEntity("PAR", "LOC", "EUR", "PAR-EUR-001"));

        assertThat(directory.resolve(PAR, LOC, "EUR"))
                .contains(new GlobalAccount(PAR, LOC, "EUR", "PAR-EUR-001"));
    }

    @Test
    void missing_tuple_signals_unresolved() {
        assertThat(directory.resolve(PAR, LOC, "EUR")).isEmpty();
    }
}
