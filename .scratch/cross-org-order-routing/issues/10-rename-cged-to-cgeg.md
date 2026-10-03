Status: ready-for-agent

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
