package com.mmx.order.support;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

/**
 * Shared singleton PostgreSQL Testcontainer base for {@code integration}-tagged (and {@code e2e})
 * test classes in {@code mmx-bootstrap}. Exactly one container is started per JVM and shared by
 * every class that extends this base; the schema is reset (Flyway clean + migrate) before each test
 * class, then the {@code rest-test} reference-data seeders are re-run, so every class starts from
 * the same pristine, seeded schema the per-class containers used to provide.
 *
 * <p>Subclasses extend this base instead of declaring their own {@code @Container PostgreSQL}; the
 * datasource is wired via the inherited {@link DynamicPropertySource}. To let the reset re-run the
 * seeders (institutions/currencies/users) against the shared container's freshly-cleaned schema,
 * this base uses {@link TestInstance.Lifecycle#PER_CLASS} and an instance {@code @BeforeAll} that
 * invokes the autowired {@code rest-test} seeders after Flyway migrate.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class SharedPostgresTestBase {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        try {
            POSTGRES.start();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start shared PostgreSQL container "
                    + "(Docker must be available to run integration/e2e tests)", e);
        }
    }

    @Autowired(required = false)
    private List<ApplicationRunner> seeders = List.of();

    protected static PostgreSQLContainer<?> postgres() {
        return POSTGRES;
    }

    @DynamicPropertySource
    static void postgresProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    /**
     * Reset the schema (Flyway clean + migrate) once per test class, then re-run the
     * {@code rest-test} reference-data seeders — exactly reproducing the pristine, seeded schema a
     * fresh per-class container used to provide, but at ONE container cold-start per JVM
     * (spec {@code test-feedback-loop}).
     */
    @BeforeAll
    void resetSchema() throws Exception {
        Flyway flyway =
                Flyway.configure()
                        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                        .cleanDisabled(false)
                        .load();
        flyway.clean();
        flyway.migrate();
        for (ApplicationRunner seeder : seeders) {
            seeder.run(null);
        }
    }
}