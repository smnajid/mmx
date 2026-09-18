package com.mmx.order.config;

import com.mmx.order.MmxApplication;
import com.mmx.order.application.port.out.ExternalIdentityGateway;
import com.mmx.order.application.port.out.HubLocalityResolver;
import com.mmx.order.domain.model.HubLocality;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.application.port.out.RemoteRoutingGateway;
import com.mmx.order.application.service.ResilientRemoteRoutingGateway;
import com.mmx.order.support.SharedPostgresTestBase;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the FULL deployment context with {@code mmx.cross-org.role=client} — the CGEG deployment
 * shape used by {@code application-cgeg.yml} — against the shared Testcontainer Postgres.
 *
 * <p>Guards the client-side cross-org wiring: Leg-A outbound {@link RemoteRoutingGateway} wrapped
 * in the retry/circuit-breaker resilience decorator, the external-identity resolution hook
 * ({@link ExternalIdentityGateway}),
 * {@code ApplyRemoteOrderOutcomeUseCase}, and a {@link HubLocalityResolver} that always resolves
 * REMOTE (V1 deployment-role-based locality). The property block mirrors
 * {@code application-cgeg.yml}'s {@code mmx.cross-org.*} shape so a binding typo (e.g. a renamed
 * retry key silently binding to null) fails here instead of at deployment start-up.
 * The Leg-B Kafka listener is left disabled ({@code consumer-enabled=false}) — no broker exists in
 * the {@code fast|integration} loop; its wiring is covered by the e2e suite.
 *
 * <p>The {@code rest-test} profile is deliberately NOT activated: its {@code ApplicationRunner}
 * seeders onboard hub reference data through the primary repository, which under
 * {@code reference-data-remote=true} is the read-only remote-backed adapter — a thin client does
 * not master reference data, so hub-shaped seeding fails by design. The datasource comes from the
 * shared-container {@link SharedPostgresTestBase} dynamic properties alone.
 */
@Tag("integration")
@TestPropertySource(properties = {
        "mmx.cross-org.role=client",
        "mmx.cross-org.own-legal-entity-codes=CGD",
        "mmx.cross-org.reference-data-remote=true",
        "mmx.cross-org.remote-routing-gateway.base-url=http://localhost:8080",
        "mmx.cross-org.remote-routing-gateway.credential-key=test-client-key",
        "mmx.cross-org.external-identity.base-url=http://localhost:8090",
        "mmx.cross-org.retry.max-attempts=3",
        "mmx.cross-org.retry.initial-backoff-ms=500",
        "mmx.cross-org.retry.failure-threshold=5",
        "mmx.cross-org.retry.recovery-duration-ms=30000",
        "mmx.cross-org.consumer-enabled=false",
        "mmx.backoffice.outbox.relay-enabled=false",
        "mmx.oncall.outbox.relay-enabled=false",
})
@SpringBootTest(classes = MmxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CrossOrgClientDeploymentIntegrationTest extends SharedPostgresTestBase {

    @Autowired
    @Qualifier("remoteRoutingGateway")
    RemoteRoutingGateway remoteRoutingGateway;

    @Autowired
    HubLocalityResolver hubLocalityResolver;

    @Test
    void clientRoleContextBoots_withResilientGatewayAndRemoteLocality() {
        assertThat(remoteRoutingGateway).isInstanceOf(ResilientRemoteRoutingGateway.class);
        assertThat(hubLocalityResolver.resolveForClient(new LegalEntityCode("CGD")))
                .isEqualTo(HubLocality.REMOTE);
    }
}
