package com.mmx.order.rest;

import com.mmx.order.MmxApplication;
import com.mmx.order.application.port.out.DelegatedGrantDirectory;
import com.mmx.order.application.port.out.GrantResolution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.support.SharedPostgresTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Smoke test: {@link DelegatedGrantDirectory} bean is wired for Change B routing intake. */
@Tag("integration")
@SpringBootTest(classes = MmxApplication.class)
@ActiveProfiles("rest-test")
class DelegatedGrantDirectorySmokeTest extends SharedPostgresTestBase {

    @Autowired
    DelegatedGrantDirectory delegatedGrantDirectory;

    @Test
    void directoryBeanResolvable_andReturnsNoActiveGrantWhenEmpty() {
        assertThat(delegatedGrantDirectory).isNotNull();
        assertThat(
                        delegatedGrantDirectory.resolveTenor(
                                new LegalEntityCode("PAR"), "UNKNOWN-01", "EUR", Tenor._3M))
                .isEqualTo(GrantResolution.NO_ACTIVE_GRANT);
    }
}
