Status: done (2 literals flagged, see Comments)

# Rename the CGD client deployment from CGED to CGEG

## Problem

The cross-org client deployment for CGD is named **CGEG** in the Spring profile
(`application-cgeg.yml`), the seed migration (`V26__seed_cross_org_demo_topology.sql`)
and `mmx-cross-org-start.sh`. `CONTEXT.md` and ADR 0007 were corrected to CGEG on
2026-10-03, but `CGED` is still used in:

- Java sources and tests (javadoc/comments in `mmx-application`, `mmx-adapter-out-integration`,
  `mmx-adapter-out-messaging`, `mmx-bootstrap`, `mmx-domain` tests)
- `contracts/007-cross-org-routing/` (OpenAPI, AsyncAPI, JSON schema, `api-v1.md` prose mirrors)
- `scripts/provision-topics.sh`, `scripts/register-schemas.sh`
- `.scratch/cross-org-order-routing/` issues 01–08 and `map.md`

## Done when

- `grep -rn CGED` (excluding `openspec/changes/archive/` history) returns nothing.
- Contract changes are description/example text only; `npm run verify:contracts` passes.
- If `CGED` is used as a literal value anywhere (topic name, schema subject, config key),
  that occurrence is flagged rather than renamed blindly.

Mechanical rename: red-first TDD waived (note it in the commit message).

## Comments

2026-10-03 — Renamed everywhere outside `openspec/changes/archive/` (history, left as written): Java
javadoc/comments, test fixtures (`OrganisationCode("CGED")`, `CGED_ORG`, `cged`), error-message text, contract
descriptions (`contracts/007-cross-org-routing`), main specs and the in-flight `cross-org-routing-hardening`
change, `.scratch` issues and map, script comments. The ticket's list missed the main specs and the in-flight
change; both are covered.

Literal values, per the "flag, don't rename blindly" rule:

- `RoutingOutcomeKafkaListener` consumer-group default `mmx-cged-routed-outcome` → `mmx-cgeg-routed-outcome`.
  Renamed: `application-cgeg.yml` already sets `consumer-group: mmx-cgeg-routed-outcome` for the real CGEG
  deployment, so the default is only a fallback and no live group changes.
- **Flagged, not renamed:** `scripts/provision-topics.sh` still uses the env var `CGED_PRINCIPAL`, the default
  Kafka principal `User:CGED` and the consumer group `${CGED_PRINCIPAL}-consumer-group`. They name a broker identity
  that must match what the client deployment connects as; nothing in the repo says what that is (dev brokers run
  without an authorizer). Decide the real principal, then rename the variable (keeping `CGED_PRINCIPAL` as a
  fallback for existing environments) and the default.

