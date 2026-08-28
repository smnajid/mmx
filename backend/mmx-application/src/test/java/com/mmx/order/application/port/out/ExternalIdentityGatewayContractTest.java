package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.PortfolioNumber;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Port contract for {@link ExternalIdentityGateway} — CGED's cross-org account resolver. Verifies
 * the port maps {@code (client LegalEntityCode, client portfolioNumber, hub LegalEntityCode) →
 * hub-side portfolioNumber} before the leg-A send, and that an unresolved tuple is reported as
 * empty so the application can transition the client-side order to {@code Rejected} directly (no
 * round-trip to the hub).
 *
 * <p>Spec: {@code order-routing} — "Remote account resolution via ExternalIdentityGateway". The hub
 * deployment trusts the resolved account and does not revalidate it; resolution is owned by the
 * client deployment.
 *
 * <p>Distinct from {@link GlobalAccountDirectory} (local routing): the key here is the client
 * portfolioNumber, not currency, and the resolver lives CGED-side, pre-send (design D3).
 */
@Tag("fast")
class ExternalIdentityGatewayContractTest {

    private static final LegalEntityCode CGD = new LegalEntityCode("CGD");
    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");

    @Test
    void resolve_returnsHubSidePortfolioNumber_whenMappingExists() {
        FakeGateway gateway = new FakeGateway();
        gateway.put(CGD, new PortfolioNumber("CGD-PM-77"), LOC, new PortfolioNumber("CGD-LOC-001"));

        Optional<PortfolioNumber> resolved =
                gateway.resolveHubSidePortfolioNumber(CGD, new PortfolioNumber("CGD-PM-77"), LOC);

        // Spec scenario: "Resolved account travels in the leg-A payload" — CGD resolves
        // CGD-LOC-001 for a route to LOC; this value is what RemoteRoutingRequest.portfolioNumber
        // will carry.
        assertThat(resolved).contains(new PortfolioNumber("CGD-LOC-001"));
    }

    @Test
    void resolve_isKeyedByClientPortfolioNumber_notCurrency() {
        // Design D3 rationale: a new port (not a GlobalAccountDirectory implementation) because the
        // key is the client portfolioNumber, not currency. The same (client, hub) pair can resolve
        // to a different hub-side account per client portfolio.
        FakeGateway gateway = new FakeGateway();
        gateway.put(CGD, new PortfolioNumber("CGD-PM-77"), LOC, new PortfolioNumber("CGD-LOC-001"));
        gateway.put(CGD, new PortfolioNumber("CGD-PM-88"), LOC, new PortfolioNumber("CGD-LOC-002"));

        Optional<PortfolioNumber> first =
                gateway.resolveHubSidePortfolioNumber(CGD, new PortfolioNumber("CGD-PM-77"), LOC);
        Optional<PortfolioNumber> second =
                gateway.resolveHubSidePortfolioNumber(CGD, new PortfolioNumber("CGD-PM-88"), LOC);

        assertThat(first).contains(new PortfolioNumber("CGD-LOC-001"));
        assertThat(second).contains(new PortfolioNumber("CGD-LOC-002"));
    }

    @Test
    void resolve_returnsEmpty_whenUnresolved_signalsRoutingFailure() {
        // Spec scenario: "Unresolved account rejects client-side directly" — when the
        // ExternalIdentityGateway cannot resolve an account, the client deployment rejects
        // client-side with no round-trip. The port reports unresolved as empty; the application
        // owns the Received→Rejected transition (and the ROUTING_FAILURE origin).
        FakeGateway gateway = new FakeGateway();

        Optional<PortfolioNumber> resolved =
                gateway.resolveHubSidePortfolioNumber(CGD, new PortfolioNumber("CGD-PM-77"), LOC);

        assertThat(resolved).isEmpty();
    }

    @Test
    void resolve_isKeyedByTargetHub_sameClientPortfolioMayDifferPerHub() {
        // The target hub LegalEntityCode is part of the key: the same client portfolio resolves to
        // a hub-specific account. An unmapped hub tuple is unresolved even when the client portfolio
        // is known for another hub.
        FakeGateway gateway = new FakeGateway();
        LegalEntityCode otherHub = new LegalEntityCode("OTH");
        gateway.put(CGD, new PortfolioNumber("CGD-PM-77"), LOC, new PortfolioNumber("CGD-LOC-001"));

        assertThat(gateway.resolveHubSidePortfolioNumber(CGD, new PortfolioNumber("CGD-PM-77"), LOC))
                .contains(new PortfolioNumber("CGD-LOC-001"));
        assertThat(gateway.resolveHubSidePortfolioNumber(CGD, new PortfolioNumber("CGD-PM-77"), otherHub))
                .isEmpty();
    }

    /** Minimal fake honouring the port contract; mirrors how the test asserts the contract. */
    private static final class FakeGateway implements ExternalIdentityGateway {

        private final Map<Key, PortfolioNumber> mappings = new HashMap<>();

        void put(
                LegalEntityCode client,
                PortfolioNumber clientPortfolio,
                LegalEntityCode hub,
                PortfolioNumber hubSide) {
            mappings.put(new Key(client, clientPortfolio, hub), hubSide);
        }

        @Override
        public Optional<PortfolioNumber> resolveHubSidePortfolioNumber(
                LegalEntityCode clientLegalEntityCode,
                PortfolioNumber clientPortfolioNumber,
                LegalEntityCode hubLegalEntityCode) {
            return Optional.ofNullable(
                    mappings.get(
                            new Key(
                                    clientLegalEntityCode,
                                    clientPortfolioNumber,
                                    hubLegalEntityCode)));
        }

        private record Key(
                LegalEntityCode client, PortfolioNumber clientPortfolio, LegalEntityCode hub) {}
    }
}
