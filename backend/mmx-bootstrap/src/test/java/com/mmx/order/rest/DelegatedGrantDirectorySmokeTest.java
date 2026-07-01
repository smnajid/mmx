package com.mmx.order.rest;

import com.mmx.order.MmxApplication;
import com.mmx.order.application.port.out.DelegatedGrantDirectory;
import com.mmx.order.application.port.out.GrantResolution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** Smoke test: {@link DelegatedGrantDirectory} bean is wired for Change B routing intake. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MmxApplication.class)
@ActiveProfiles("rest-test")
class DelegatedGrantDirectorySmokeTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    DelegatedGrantDirectory delegatedGrantDirectory;

    @DynamicPropertySource
    static void registerPostgresProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void directoryBeanResolvable_andReturnsNoActiveGrantWhenEmpty() {
        assertThat(delegatedGrantDirectory).isNotNull();
        assertThat(
                        delegatedGrantDirectory.resolveTenor(
                                new LegalEntityCode("PAR"), "UNKNOWN-01", "EUR", Tenor._3M))
                .isEqualTo(GrantResolution.NO_ACTIVE_GRANT);
    }
}
