# Migration plan: consolidate on OpenSpec, retire Spec Kit

Status: complete (uncommitted) — all phases done on branch
`migration/openspec-only-phase-0-1`. Backend full reactor `mvn test` green;
`openspec validate --all` 21/23 (2 pre-existing failures, no regression); no live
Spec Kit references remain (archive history intentionally preserved).

## Current state (reconciled)

Work is staged on `migration/openspec-only-phase-0-1`. `openspec validate --all`
reports **21 passed, 2 failed**; the two failures (`institution-onboarding`,
`pm-order-creation-widget`) are **pre-existing and out of scope**.

| Phase | Status | Evidence |
|-------|--------|----------|
| 1 — relocate contracts | Done (staged) | `contracts/00N-*/` populated; `specs/00N-*/contracts/` renamed via `git mv`; poms, `ContractSyncVerificationTest`, `register-schemas.sh`, `application.yml` updated |
| 2 — repoint live refs | Done | `config.yaml`, `CONTEXT.md`, `codebase-map.md`, live `openspec/specs/*` repointed; no dangling `specs/00N-` refs outside archive / files pending deletion |
| 3 — audit & back-fill | Done | New `money-market-order-lifecycle/spec.md` (validates) + targeted deltas below |
| 4 — fold constitution | Done | `docs/governance.md` created (de-Speckit-ified principles + blocking parity); `openspec/config.yaml` SDD section expanded |
| 5 — delete Spec Kit | Done | `.specify/`, 28 `speckit-*` skills, `specs/` tree, `scripts/migrate-cursor-skills-to-claude.py` all removed (staged) |
| 6 — scrub docs | Done | `CLAUDE.md`, `AGENTS.md`, `docs/agents/claude-code.md` rewritten OpenSpec-only; governance repointed to `docs/governance.md` |
| 7 — final verification | Done | full `mvn test` green; `openspec validate --all` no regression; residue scan clean |

### Phase 3 audit reconciliation (all implemented)

- **New capability `money-market-order-lifecycle`** closes the high-priority 001 gaps:
  state machine + transitions (FR-020), intake channel (FR-001), structural
  validation (FR-002/003/007/008), idempotency (FR-010), assign/unassign
  (FR-012/013), assignee-only mutation/execute (FR-014, 002 FR-003), update
  (FR-016), execute + system metadata (FR-017/018), MinimumRate floor at execute,
  cancel/reject (FR-019), audit (FR-021), any-trader read (FR-023), Subscription
  discards sourceContractNumber (FR-024).
- **Targeted deltas:** `trader-executed-queue` (staleness + longest-waiting default
  sort, FR-014); `trader-received-list-shell` (tenor/notice on Assigned + Executed,
  FR-006/007); `trader-desk-navigation` + `currency-settings-ui` (settings sub-nav
  Currencies/Institutions/Term rates/OnCall rates); `order-institution-constraints`
  (execute-below-minimumRate rejected scenario).
- **CONTEXT.md** repointed to the lifecycle capability, desk capabilities, and
  `contracts/*/api-v1.md`.

### Loose ends

- Empty leftover dir `specs/005-term-rate-daily-upload/contracts/` from relocation
  (untracked by git; remove during Phase 5).
- Two correct edits still unstaged: `config.yaml` SDD wording, `currency-settings-ui`
  sub-nav strip.
- Optional (audit low-priority, not yet captured): NFR SC-* criteria, FR-015 legacy
  endpoint deprecation requirement, FR-009 exact-decimal domain requirement,
  `data-model.md` value-object constraints. Capture in `docs/` or skip.

---

## Original plan

## Objective

Keep **OpenSpec** as the sole spec-driven development (SDD) framework. Remove Spec
Kit (the `.specify/` framework, `speckit-*` skills, and the `specs/NNN-*` process
artifacts) and merge any Speckit requirement content into OpenSpec — **without**
breaking the contract-first build.

## Locked decisions

- **Contracts**: relocate to a neutral top-level `contracts/` dir; update all build
  files and live references.
- **Merge depth**: audit each Speckit `spec.md` against existing OpenSpec
  capabilities; back-fill only genuine gaps via a single new OpenSpec change.
- **Constitution**: fold any principles not already in `openspec/config.yaml` into
  it, plus a short governance note under `docs/`.

## Key assumption

We will **not** rewrite the `specs/NNN-*/...` path references inside
`openspec/changes/archive/**`. Archived changes are an immutable historical record.
Only *live* references (build files, `openspec/specs/`, `openspec/config.yaml`,
`CONTEXT.md`, agent docs) are repointed.

## Core tension (why this isn't a simple delete)

The `specs/` folder is overloaded:

1. **Speckit process artifacts** — `spec.md`, `plan.md`, `tasks.md`, `research.md`,
   `quickstart.md`, `checklists/`. Safe to retire; content largely already mirrored
   in the 22 OpenSpec capabilities (built via 26 archived changes).
2. **Canonical contracts** — `specs/NNN-*/contracts/{openapi.yaml,api-v1.md,asyncapi.yaml,schemas/*.json}`.
   **Not Speckit.** Load-bearing build inputs:
   - `backend/mmx-adapter-in-rest/pom.xml` runs OpenAPI codegen against 002/003/004/005.
   - `backend/mmx-bootstrap/pom.xml` packages 001 + 002 schemas for Swagger UI.
   - `ContractSyncVerificationTest.java` and `scripts/register-schemas.sh` read 002 schemas.
   - ~20+ live OpenSpec capability specs cite these paths as source of truth.

## Success criteria

- `rg -i 'speckit|spec kit|\.specify|/speckit-'` returns only archive history (or nothing).
- `cd backend && mvn test` green; `openspec validate --all` no worse than baseline.
- No `specs/NNN-*/` directory remains; contracts live under `contracts/`.

---

## Phase 0 — Safety baseline (verify before touching anything)

1. `cd backend && mvn -q -DskipTests compile` — exercises OpenAPI codegen against
   the 002/003/004/005 contracts; confirm it builds.
2. Record `openspec validate --all` baseline. (Known pre-existing failures:
   `institution-onboarding`, `pm-order-creation-widget` — out of scope.)
3. `git status` clean / create a working branch.

**Gate:** codegen + compile succeed; baseline validation status recorded.

---

## Phase 1 — Relocate contracts (build-critical, in isolation)

`git mv` the contract trees, preserving subfolder names to minimize the diff:

| From | To |
|------|----|
| `specs/001-mm-order-processing/contracts/` | `contracts/001-mm-order-processing/` |
| `specs/002-trader-orders-views/contracts/` | `contracts/002-trader-orders-views/` |
| `specs/003-managed-currency-settings/contracts/` | `contracts/003-managed-currency-settings/` |
| `specs/004-institution-settings/contracts/` | `contracts/004-institution-settings/` |
| `specs/005-term-rate-settings/contracts/` | `contracts/005-term-rate-settings/` |

(002 includes `asyncapi.yaml`, `asyncapi-v1.md`, `schemas/*.json`.)

Update live build wiring:

- `backend/mmx-adapter-in-rest/pom.xml` — 8 `<inputSpec>` paths (`../../specs/...` → `../../contracts/...`).
- `backend/mmx-bootstrap/pom.xml` — `<resource>` dir (001 contracts) + `<testResource>` dir (002 schemas); update "Spec Kit contracts" comment.
- `backend/mmx-bootstrap/src/test/java/com/mmx/order/contract/ContractSyncVerificationTest.java` — 4 path literals.
- `scripts/register-schemas.sh` — `SCHEMAS_DIR`.
- `backend/mmx-bootstrap/src/main/resources/application.yml` — comment reference.

**Gate (highest risk):** `cd backend && mvn -q -DskipTests compile` green +
`ContractSyncVerificationTest` passes. Must pass before continuing.

---

## Phase 2 — Repoint live (non-archive) references

- `openspec/config.yaml` — context block citing `specs/002.../contracts/openapi.yaml`
  → `contracts/002.../...`; update the "Specifications (SDD)" note that says specs
  live "under `specs/`".
- Live `openspec/specs/*.md` citing contract paths: `term-rate-daily-upload`,
  `order-institution-constraints`, `pm-order-creation-options`,
  `institution-onboarding`, `back-office-outbound-messaging` → repoint to `contracts/...`.
- `CONTEXT.md` — the two "authoritative rules" links point at `specs/001/spec.md` and
  `specs/002/spec.md` (being deleted) → repoint to relevant OpenSpec capabilities
  (e.g. `desk-order-queries`, `trader-*`) and/or `contracts/.../api-v1.md`.
- `docs/agents/codebase-map.md` — `specs/001/plan.md` link and `.specify/` layout entries.

**Gate:** `openspec validate --specs` no worse than baseline;
`rg 'specs/00[0-9]-' -- . ':!openspec/changes/archive'` returns nothing.

---

## Phase 3 — Audit & back-fill Speckit spec content into OpenSpec

For each `specs/NNN-*/spec.md` (+ `data-model.md`), enumerate FRs / requirements and
map each to an existing OpenSpec capability.

- Produce a coverage table (FR → capability, or GAP).
- For genuine gaps, open **one** OpenSpec change (`proposal.md` + delta `specs/` +
  `tasks.md`), or fold directly into the matching capability spec where it's pure
  documentation alignment.
- Fold any unique `data-model.md` domain content into the relevant capability
  `Purpose`/requirements or a `docs/` note.

**Gate:** every Speckit FR maps to an OpenSpec requirement (no unresolved GAP);
new/edited specs pass `openspec validate`.

---

## Phase 4 — Fold constitution governance

- Diff `.specify/memory/constitution.md` principles against `openspec/config.yaml`
  context (already covers stack/hexagonal/contract-first/TDD).
- Add only the **missing** governance (notably **Principle VI spec–code parity** and
  versioning intent) into `config.yaml` context, plus a short `docs/governance.md`
  (or ADR) capturing the constitution's intent.

**Gate:** parity / contract-first governance is discoverable without `.specify/`.

---

## Phase 5 — Delete Spec Kit

- `rm -rf .specify/` (framework, templates, scripts, `extensions.yml` hooks, git
  extension — disables `auto_execute_hooks`).
- Delete 14 `.cursor/skills/speckit-*/` + 14 `.claude/skills/speckit-*/` dirs.
- Delete remaining process artifacts in `specs/NNN-*/` (`spec.md`, `plan.md`,
  `tasks.md`, `research.md`, `quickstart.md`, `checklists/`, and `data-model.md`
  once Phase 3 confirms coverage), then remove the now-empty `specs/` tree.
- Delete `scripts/migrate-cursor-skills-to-claude.py` (Speckit-specific).

**Gate:** `rg -i 'speckit|/speckit-'` clean outside archive history.

---

## Phase 6 — Scrub shared docs

- `CLAUDE.md` — remove Spec Kit workflow table, `/speckit-*` skills, hooks section,
  `.specify/memory/constitution.md` pointer, and `specs/001`/`002` default-feature-root
  language; keep/strengthen the OpenSpec (OPSX) section; repoint governance.
- `AGENTS.md` — remove "Spec Kit Git hook" section and `.specify` refs; keep
  SDD/TDD/OpenSpec parity rules; fix dangling `spec-sdd-sync.mdc` reference.
- `docs/agents/claude-code.md` — drop Spec Kit rows, keep OpenSpec.
- Resolve broken refs to `spec-sdd-sync.mdc` and `specify-rules.mdc` (already missing).

**Gate:** docs describe an OpenSpec-only workflow; no broken internal links.

---

## Phase 7 — Final verification

1. `cd backend && mvn test` — full reactor green (proves codegen relocation + contract test).
2. `openspec validate --all` — no regressions vs Phase 0 baseline.
3. Residue scan: `rg -i 'speckit|spec kit|\.specify|/speckit-|specs/00[0-9]-'` → only archive history.
4. Frontend `npm run test` only if a frontend file was touched (not expected).

---

## Risk notes

- **Highest risk = Phase 1** (build wiring). Isolated and gated by a compile +
  contract-test checkpoint before anything else proceeds.
- Archived OpenSpec changes keep their historical `specs/NNN-*` text by design.
- The 2 pre-existing `openspec validate` failures are out of scope unless they fall
  naturally into the Phase 3 back-fill.
