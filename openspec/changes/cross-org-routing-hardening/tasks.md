# Tasks

## 1. Domain — RejectionOrigin value (red-first TDD)

- [ ] 1.1 Write failing `RejectionOriginTest` (`mmx-domain`): a `RejectionOrigin` takes values `TRADER` or `ROUTING_FAILURE` only; `MoneyMarketOrder` reject mutations record the origin alongside `rejectionReason`. `mvn test -pl mmx-domain -Dtest=RejectionOriginTest`
- [ ] 1.2 Implement `RejectionOrigin` + the `MoneyMarketOrder` reject-mutation origin parameter in `mmx-domain` until green

## 2. Persistence — nullable rejection_origin column (additive Flyway)

- [ ] 2.1 Flyway migration in `mmx-adapter-out-persistence`: add nullable `rejection_origin` column (no backfill; historical rows stay `null`); add the JPA mapping on the order entity
- [ ] 2.2 Compile gate: `mvn compile -pl mmx-adapter-out-persistence -am`

## 3. Application — record origin at every reject site (red-first TDD)

- [ ] 3.1 Write failing `RejectionOriginRecordingTest` (`mmx-application`): each reject site records the correct origin — local intake routing failure (unresolved account / grant violation) → `ROUTING_FAILURE`; remote client-side account unresolved → `ROUTING_FAILURE`; remote accept routing failure (HTTP reject) → `ROUTING_FAILURE`; trader `reject(...)` → `TRADER`; `propagateRejectFromHub` (local) → `TRADER`; leg-B trader-`REJECTED` apply → `TRADER`. `mvn test -pl mmx-application -Dtest=RejectionOriginRecordingTest`
- [ ] 3.2 Implement origin recording at each reject site in `mmx-application` until green

## 4. Application — broken-pair retry-then-DLQ policy (red-first TDD)

- [ ] 4.1 Write failing `RemoteOutcomeDlqPolicyTest` (`mmx-application`): a deterministic `RoutedOrderPairIntegrityException` recurring across `mmx.cross-org.outcome.max-attempts` (default 3) on the same offset routes to the DLQ + emits an alert + advances the offset; a transient failure (e.g. `DataAccessException`) does NOT route to the DLQ (retries via normal backoff). `mvn test -pl mmx-application -Dtest=RemoteOutcomeDlqPolicyTest`
- [ ] 4.2 Implement the retry-then-DLQ policy in `mmx-application` until green

## 5. adapter-out-messaging — DLQ producer + health metrics (red-first TDD)

- [ ] 5.1 Write failing `RoutingOutcomeDlqProducerTest` (`mmx-adapter-out-messaging`): publishes the original `RoutingOutcomeV1` message plus a diagnostic envelope (failure reason, failing offset, attempt count, detected-at) to `mmx.routed-order-outcome.{orgCode}.dlq`; the topic is owned/produced by the consuming deployment. `mvn test -pl mmx-adapter-out-messaging -Dtest=RoutingOutcomeDlqProducerTest`
- [ ] 5.2 Implement the DLQ producer + DLQ-depth metric in `mmx-adapter-out-messaging` until green
- [ ] 5.3 Write failing `RoutingOutcomeHealthMetricsTest` (`mmx-adapter-out-messaging`): consumer-lag, outbox-age, and broker-health for the routed-order-outcome channel are exposed as metrics with configurable alert thresholds. `mvn test -pl mmx-adapter-out-messaging -Dtest=RoutingOutcomeHealthMetricsTest`
- [ ] 5.4 Implement the health metrics + alert hooks in `mmx-adapter-out-messaging` until green

## 6. Application — stale-Routed sweep (red-first TDD)

- [ ] 6.1 Write failing `StaleRoutedSweepTest` (`mmx-application`): a remote client-side order in `ROUTED` past `mmx.cross-org.stale-routed.threshold` emits an operational alert with NO state transition; an order that reached terminal within the threshold does not alert. `mvn test -pl mmx-application -Dtest=StaleRoutedSweepTest`
- [ ] 6.2 Implement the periodic stale-`Routed` sweep in `mmx-application` until green

## 7. Contracts — routing-key addressed accounted callback (contract-first)

- [ ] 7.1 Update `contracts/002-trader-orders-views/openapi.yaml` (+ `api-v1.md` mirror): add `POST /api/v1/back-office/orders/by-routing-key/{originatingLegalEntityCode}/{routingId}/accounted` with 200/404/409 responses (the existing `orderId`-addressed operation is unchanged)
- [ ] 7.2 Run codegen gate: `mvn compile -pl mmx-adapter-in-rest -am`

## 8. adapter-in-rest — routing-key accounted controller (red-first TDD)

- [ ] 8.1 Write failing `RoutingKeyAccountedCallbackControllerTest` (`mmx-adapter-in-rest`): resolves the client-side order by `(originatingLegalEntityCode, routingId)` and delegates to `EXECUTED → ACCOUNTED`; idempotent on repeat; unknown key → 404 `ORDER_NOT_FOUND`; non-`EXECUTED` → 409 `INVALID_STATUS_TRANSITION`. `mvn test -pl mmx-adapter-in-rest -Dtest=RoutingKeyAccountedCallbackControllerTest`
- [ ] 8.2 Implement the routing-key accounted controller (against the generated interface) in `mmx-adapter-in-rest` until green

## 9. adapter-out-persistence — routing-key order lookup (red-first TDD)

- [ ] 9.1 Write failing `OrderByRoutingKeyLookupTest` (`mmx-adapter-out-persistence`): finds the client-side order by `(originatingLegalEntityCode, routingId)`; returns empty when no match. `mvn test -pl mmx-adapter-out-persistence -Dtest=OrderByRoutingKeyLookupTest`
- [ ] 9.2 Implement the routing-key lookup on the order repository in `mmx-adapter-out-persistence` until green

## 10. Bootstrap — wiring + DLQ integration

- [ ] 10.1 Wire the DLQ policy, DLQ producer, health metrics/alert hooks, stale-`Routed` sweep, and routing-key accounted controller in `mmx-bootstrap`
- [ ] 10.2 Provision the `mmx.routed-order-outcome.{orgCode}.dlq` topic (consumer-deployment-owned) + consumer retry/DLQ config
- [ ] 10.3 Integration test (`mmx-bootstrap`, Testcontainers): end-to-end — a broken remote pair (missing client) retries to `max-attempts`, is dead-lettered with diagnostics, and the consumer advances; the routing-key accounted callback transitions a remote client-side order `EXECUTED → ACCOUNTED`; plus a local-routing regression assertion (local throw-and-rollback on a missing local client is unchanged, no DLQ involved)

## 11. Docs — ADR-0002 + CONTEXT

- [ ] 11.1 Update `docs/adr/0002-routed-order-is-two-linked-records.md` with the completed "detect + alert" relaxation: local pairs keep atomic throw-and-rollback; remote pairs detect (`ApplyRemoteOrderOutcomeUseCase` throw) then alert (DLQ + operational alert after bounded retries)
- [ ] 11.2 Update `CONTEXT.md`: add `RejectionOrigin` (`TRADER` | `ROUTING_FAILURE`), the dead-letter topic (`mmx.routed-order-outcome.{orgCode}.dlq`, consumer-owned), stale-`Routed` alert, and routing-key accounted correlation

## 12. Final verification (single gate before marking complete)

- [ ] 12.1 Run full `cd backend && mvn test` — all modules green, including rejection-origin recording at each reject site, broken-pair retry-then-DLQ, routing-key accounted callback, stale-`Routed` sweep, health metrics, and the Testcontainers DLQ/accounting integration
- [ ] 12.2 `openspec validate cross-org-routing-hardening` passes; spec–code parity confirmed (rejection origin classification, dead-letter quarantine, routing-key accounting, operational health SLOs, ADR-0002 completion, no local-routing regression)
