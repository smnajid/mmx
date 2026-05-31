## 1. Contracts — async (outbound) first

- [x] 1.1 Add `OnCallRateUpdatedV1` schema under `specs/002-trader-orders-views/contracts/schemas/OnCallRateUpdatedV1.json` (fields: `eventType` const, `segmentId`, `institution`, `currency`, `noticePeriod`, `rate`, `valueDate`; no `priorEndDate`) per design §3
- [x] 1.2 Add `OnCallRateCanceledV1` schema (thin: `eventType` const, `segmentId`) for the cancel notification per spec `back-office-outbound-messaging`
- [x] 1.3 Update `specs/002-trader-orders-views/contracts/asyncapi.yaml` with the OnCall channel + both messages keyed by `segmentId`; mirror in `asyncapi-v1.md` (idempotent consumer keyed by `segmentId`)
- [x] 1.4 Register schemas via `scripts/register-schemas.sh` and confirm `BACKWARD` compatibility subjects are created

## 2. Contracts — sync (trader + back-office) HTTP

- [x] 2.1 Update `specs/002-trader-orders-views/contracts/openapi.yaml`: trader operations to add a rate and cancel a pending rate (institution-scoped, `X-Trader-Id` authenticated); mirror in `api-v1.md`
- [x] 2.2 Update `openapi.yaml`: inbound back-office confirmation `POST /api/v1/back-office/oncall-rates/{segmentId}/confirmed` with responses `200` (confirmed / idempotent no-op), `409` (segment canceled), `404` (unknown); `security: []` (POC, mirror accounted callback); mirror in `api-v1.md`
- [x] 2.3 Run REST codegen and `mvn -pl mmx-adapter-in-rest -am compile` to regenerate API interfaces/models

## 3. Domain — segment & curve lifecycle (mmx-domain, TDD)

- [x] 3.1 Write failing `OnCallRateSegmentTest` — `mvn test -pl mmx-domain -Dtest=OnCallRateSegmentTest` (segmentId/rate/inclusive valueDate/end, `2999-12-31` no-end sentinel, status `PENDING_CONFIRMATION`/`VALID`/`CANCELED`)
- [x] 3.2 Implement `OnCallRateSegment` value object + `NoticePeriod`/curve-key types until green
- [x] 3.3 Write failing `OnCallRateCurvePolicyTest` — `mvn test -pl mmx-domain -Dtest=OnCallRateCurvePolicyTest` (add supersedes prior end → `V−1`; first-ever segment spans `[V, 2999-12-31]`; `valueDate ≥ today`; one `PENDING` per curve point)
- [x] 3.4 Implement curve/supersede policy + add-rate rules until green
- [x] 3.5 Write failing `OnCallRateTransitionTest` — `mvn test -pl mmx-domain -Dtest=OnCallRateTransitionTest` (cancel only from `PENDING`; cancel reverts prior end to `2999-12-31` / discards first-ever; confirm `PENDING→VALID`; confirm of `CANCELED` rejected; confirm of `VALID` idempotent)
- [x] 3.6 Implement lifecycle transitions + domain exceptions until green

## 4. Application — ports & services (mmx-application, TDD)

- [x] 4.1 Define `port/in` use cases (`AddOnCallRate`, `CancelOnCallRate`, `ConfirmOnCallRate`) and `port/out` (`OnCallRateRepository`, `OnCallRateHandoffOutbox`, reuse `Clock`)
- [x] 4.2 Write failing `AddOnCallRateServiceTest` — `mvn test -pl mmx-application -Dtest=AddOnCallRateServiceTest` (persist `PENDING` segment + provisional supersede + outbox row in the same transaction; reject second pending; reject backdated)
- [x] 4.3 Implement `AddOnCallRateService` until green
- [x] 4.4 Write failing `CancelOnCallRateServiceTest` — `mvn test -pl mmx-application -Dtest=CancelOnCallRateServiceTest` (cancel pending → revert prior, write cancel-notification outbox row in same transaction; reject cancel of `VALID`)
- [x] 4.5 Implement `CancelOnCallRateService` until green
- [x] 4.6 Write failing `ConfirmOnCallRateServiceTest` — `mvn test -pl mmx-application -Dtest=ConfirmOnCallRateServiceTest` (atomic compare-and-set: `PENDING→VALID` stamps `validatedAt`; `CANCELED`→conflict; unknown→not found; already-`VALID`→idempotent no-op) per design §5
- [x] 4.7 Implement `ConfirmOnCallRateService` until green

## 5. Persistence adapter (mmx-adapter-out-persistence)

- [x] 5.1 Add Flyway migration for `oncall_rate_segment` (segmentId PK, curve-key columns, rate, value_date, end_date, status, validated_at; partial unique index enforcing one `PENDING` per `(institution, currency, notice_period)`)
- [x] 5.2 Write failing `OnCallRateSegmentJpaAdapterTest` — `mvn test -pl mmx-adapter-out-persistence -Dtest=OnCallRateSegmentJpaAdapterTest` (persist/load segments by curve key; pending-uniqueness constraint)
- [x] 5.3 Implement JPA entity + Spring Data repository + `OnCallRateRepository` adapter until green

## 6. Outbound messaging adapter (mmx-adapter-out-messaging, TDD)

- [x] 6.1 Write failing `OnCallRateUpdatedV1PayloadMapperTest` — `mvn test -pl mmx-adapter-out-messaging -Dtest=OnCallRateUpdatedV1PayloadMapperTest` (maps segment → schema-valid `OnCallRateUpdatedV1`, no `priorEndDate`)
- [x] 6.2 Implement payload mapper(s) for `OnCallRateUpdatedV1` and `OnCallRateCanceledV1` + `OnCallRateHandoffOutbox` adapter until green
- [x] 6.3 Extend the outbox relay to publish OnCall rows to the OnCall channel with record key = `segmentId`, reusing existing retry/`FAILED` semantics

## 7. Inbound REST adapter (mmx-adapter-in-rest)

- [x] 7.1 Write failing `OnCallRateTraderControllerTest` — `mvn test -pl mmx-adapter-in-rest -Dtest=OnCallRateTraderControllerTest` (add rate, cancel pending; maps domain errors to contract error codes)
- [x] 7.2 Implement trader controller against generated interfaces until green
- [x] 7.3 Write failing `OnCallRateConfirmationCallbackControllerTest` — `mvn test -pl mmx-adapter-in-rest -Dtest=OnCallRateConfirmationCallbackControllerTest` (`200`/`409`/`404` per §5; unauthenticated)
- [x] 7.4 Implement confirmation callback controller against generated interface until green

## 8. Frontend — OnCall rate editor (frontend/)

- [x] 8.1 Add OnCall rate API client methods (add/cancel) matching `openapi.yaml`, following existing settings client conventions
- [x] 8.2 Build standalone OnCall rate editor under `/settings/*` (curve point list per institution, add-rate form with value date, cancel action for `PENDING` rows, pending-confirmation status badge) reusing `settings-panel`/`settings-toolbar`/`--mmx-*` tokens and `DeskReturnService` (U-01/U-02)
- [x] 8.3 Add Vitest specs next to the editor component/service (add, cancel-when-pending, status rendering)

## 9. Bootstrap wiring (mmx-bootstrap)

- [x] 9.1 Wire OnCall use cases, repository, outbox adapter, and relay beans in `@Configuration`; ensure OnCall outbox rows are picked up by the relay schedule

## 10. Final verification

- [x] 10.1 Run full `cd backend && mvn test` — all modules green
- [x] 10.2 Run `npm run test` in `frontend/` — green
- [x] 10.3 SDD parity check: `openapi.yaml`/`api-v1.md`, `asyncapi.yaml`/`asyncapi-v1.md`, and `spec.md` all reflect shipped behaviour; `openspec validate oncall-rate-curve-handoff` passes
