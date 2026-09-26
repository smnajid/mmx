package com.mmx.order.application.port.out;

/**
 * CGED's leg-A outbound port — sends a {@link RemoteRoutingRequest} to the connected hub
 * deployment's inbound REST endpoint and returns the {@link RemoteRoutingResponse}.
 *
 * <p>Spec: {@code order-routing} — cross-org routing transport backbone. Implementations own the
 * synchronous REST handshake plus retry/backoff/circuit-breaker for transients (silence is never
 * terminal; the order stays {@code Received} on circuit-open). The use case observes only:
 * <ul>
 *   <li>a confirmed accept/reject ({@link RemoteRoutingResponse}) — flips the client-side order; or
 *   <li>a sustained unreachability past the circuit-breaker threshold — surfaces as an ops signal,
 *       never a state transition (the use case sees an exception, the order stays {@code Received}).
 * </ul>
 *
 * <p>Adapter placement: REST client adapter in {@code mmx-adapter-out-integration}; selected when
 * the connected hub is remote ({@code isRemoteHub}).
 */
public interface RemoteRoutingGateway {

    RemoteRoutingResponse route(RemoteRoutingRequest request);
}
