## Context

MMX ships **managed currencies** with `OrderAgainstCurrencyPolicy` on intake and amount update. **Execution** still accepts free-text **counterparty**. Phase 1 adds the **institution catalog** and enforces institutions at **execute** per decided programme items **P-01–P-03** ([`OPEN-DECISIONS.md`](OPEN-DECISIONS.md)).

**Ubiquitous language (P-02):** **Institution** = bank in Settings (catalog, future rates, risk limits). **Counterparty** = term and field in **order/execution** context; execution facts keep `counterparty` on the aggregate and in `OrderExecutedV1`, populated from the selected institution’s display name.

Later phases: [`term-rate-daily-upload`](../term-rate-daily-upload/), [`oncall-rate-curve-handoff`](../oncall-rate-curve-handoff/). Tracker: [`PROGRAMME.md`](PROGRAMME.md).

## Goals / Non-Goals

**Goals:**

- Institution catalog (settings REST/UI) — same hexagonal pattern as 003 currencies.
- **`OrderAgainstInstitutionPolicy`** + wiring in **`ExecuteOrderService`** (P-01, P-03).
- **Contract-first** execute change: required **`institutionCode`** on execute request; server sets **`counterparty`** from institution `displayName` (active catalog row).
- **Strict execute cold start:** zero institutions ⇒ execute fails (clear error).
- Trader **autocomplete** on execute (active institutions only); labels show `displayName`, value **`institutionCode`** (system-generated).
- Delta **`trader-desk-navigation`**, **`trader-order-detail-actions`** (or 002 execute requirements).
- TDD across policy, execute service, REST, UI.

**Non-Goals (phase 1):**

- Term/OnCall rates, CSV, Kafka rate events.
- PM intake institution field or intake-side institution policy.
- Risk limits on institutions (named in P-02 domain language; not modelled yet).
- LEI, hierarchies, institution delete.

## Decisions

### 1. Programme decisions P-01–P-03 (locked)

| ID | Decision | Implementation |
|----|----------|----------------|
| **P-01** | Execute requires onboarded **active** institution; UI autocomplete | `institutionCode` on execute API; policy in `ExecuteOrderService` |
| **P-02** | Institution vs Counterparty vocabulary | Settings: Institution; orders: `counterparty` string on execution, sourced from institution |
| **P-03** | Strict catalog for **execute** | `InstitutionRepository` empty ⇒ reject execute; no Flyway seed; quickstart onboards institutions before execute |

**Counterparty persistence (P-02 detail):** On successful execute, set `ExecutionDetails.counterparty` = selected institution’s **`displayName`** (human-readable for queues and `OrderExecutedV1`). Store **`institutionCode`** on execution if the order/execution model gains an optional field for audit; if not in v1 schema, code is only on the execute request and inferable from counterparty + catalog for POC—**prefer** adding `institutionCode` to execution persistence and handoff DTO in same delivery if SDD allows material execution shape change.

### 2. Institution identity model (P-04) — acronym + numeric suffix

- **`institutionCode`** — **system-generated** on onboard; format **`{ACRONYM}-{nn}`** (e.g. `HSBC-01`, `BANKCO-01`, `BCI-02`). Immutable, unique, max length 32. Not trader input.
- **`displayName`** — trader-supplied non-blank label (autocomplete label; copied to execution **`counterparty`**).
- **`active`** — inactive institutions excluded from autocomplete and rejected at execute.

**Onboard request:** `displayName` only. **Response:** includes generated `institutionCode`.

**Acronym derivation** (pure function in `mmx-domain`, e.g. `InstitutionCodeAcronym`):

1. Normalize: trim; collapse internal whitespace.
2. **Single word** (no whitespace): if the token is 2–6 characters and matches `[A-Za-z0-9]+`, use it uppercased as **ACRONYM** (e.g. `HSBC` → `HSBC`, `BankCo` → `BANKCO`).
3. **Multiple words:** take the first alphanumeric character of each word (split on non-alphanumerics), uppercase, concatenate, **cap at 6** characters (e.g. `Bank Co International` → `BCI`).
4. **Fallback:** if acronym would be empty (symbols-only name), use **`INST`**.

**Suffix assignment** (application + repository, same transaction as insert):

1. Let **base** = derived acronym.
2. Query existing codes matching pattern `{base}-%` (or `institution_code LIKE 'BCI-%'`).
3. Next suffix = `max(existing nn) + 1`, formatted **`%02d`** (`01`, `02`, … `99`).
4. If suffix would exceed `99` for that base, reject onboard with a clear error (unlikely in POC).
5. Persist `institutionCode` = `base + "-" + suffix`.

**Examples:**

| displayName | institutionCode |
|-------------|-----------------|
| HSBC | `HSBC-01` |
| HSBC (second row) | `HSBC-02` |
| Bank Co International | `BCI-01` |
| The Royal Bank | `TRB-01` (first letter per word; stop-word tuning optional later) |

**Components:**

| Piece | Where |
|-------|--------|
| `deriveAcronym(displayName)` | `mmx-domain` (unit-tested) |
| `allocateInstitutionCode(displayName)` | `mmx-application` — uses `InstitutionRepository.nextSuffixForAcronym(base)` or equivalent |
| Collision-safe insert | Single transaction in `ManageInstitutionSettingsService.onboard` |

**Not** using `ReferenceGenerator` / UUID for institutions (unlike `DL-` / `CN-` references).

**Contrast with currencies:** ISO code remains trader-chosen; institution codes are always server-issued readable ids.

No hard delete; deactivate only.

### 3. Validation architecture — hybrid (mirror currencies)

**Decision:** `OrderAgainstInstitutionPolicy` in **`mmx-domain`** (pure Java):

- `validateExecute(institutionCode, Institution snapshot)` — code exists, institution active.
- `validateCatalogNotEmpty(boolean hasAnyInstitution)` — at least one onboarded row before any execute (P-03).

**Orchestration in `ExecuteOrderService`:**

1. If catalog empty ⇒ fail (domain exception).
2. Load institution by `institutionCode` via **`InstitutionRepository`**.
3. Run policy.
4. Call aggregate execute with **counterparty** = `displayName` (and `institutionCode` if model extended).

**Intake / amount update:** No institution policy in phase 1.

### 4. Application layer

| Component | Module | Role |
|-----------|--------|------|
| `Institution` | `mmx-domain` | Catalog aggregate |
| `OrderAgainstInstitutionPolicy` | `mmx-domain` | Execute validation |
| `InstitutionRepository` | `mmx-application` `port.out` | `findByInstitutionCode`, `listActive`, `maxSuffixForAcronym(base)`, `save`, … |
| `InstitutionCodeAcronym` (or domain service) | `mmx-domain` | `deriveAcronym(displayName)` |
| `ManageInstitutionSettingsService` | `mmx-application` | Onboard: derive acronym → allocate next suffix → persist |
| `ExecuteOrderService` | `mmx-application` | Load institution + policy before execute |
| JPA / REST adapters | adapters | As in prior design |

### 5. HTTP surfaces (contract-first)

**Settings — `specs/004-institution-settings/`**

| Method | Path |
|--------|------|
| GET | `/api/v1/settings/institutions` |
| POST | `/api/v1/settings/institutions` |
| GET | `/api/v1/settings/institutions/{institutionCode}` |
| POST | `.../{institutionCode}/deactivate`, `.../activate` |

**Onboard** `POST /api/v1/settings/institutions` — request: `{ "displayName": "..." }`; response `201` includes generated **`institutionCode`**.

Optional for execute UI: **`GET /api/v1/settings/institutions?activeOnly=true`** (or filter client-side from list) for autocomplete.

**Execute — delta `specs/002-trader-orders-views/contracts/openapi.yaml`**

- **`ExecuteOrderRequest`:** require **`institutionCode`**; remove or deprecate free-text **`counterparty`** on input (breaking).
- **`ExecuteOrderResponse` / order detail:** continue exposing **`counterparty`** (display name); optionally expose **`institutionCode`** for clarity.

Regenerate codegen; update `OrderRestMapper` / `ExecuteOrderCommand`.

### 6. Persistence

```text
institution
  institution_code  VARCHAR(32)  PK   -- e.g. HSBC-01, BCI-02
  display_name      VARCHAR(128) NOT NULL
  active         BOOLEAN      NOT NULL DEFAULT true
  created_at     TIMESTAMPTZ  NOT NULL
  updated_at     TIMESTAMPTZ  NOT NULL
```

Optional on `order` / execution embed: **`institution_code`** VARCHAR(32) NULL until execute, then set — recommended for audit and phase 2 rate joins.

**Cold start:** empty table ⇒ execute fails; currencies and intake may still work if catalog configured.

### 7. Frontend (Angular) — Settings hub (U-01) and desk return (U-02)

**Header navigation (U-01):**

- Replace the standalone **Currencies** header link with a single **Settings** link (`routerLink="/settings"`, active when URL starts with `/settings`).
- Introduce a **settings shell** layout (e.g. `features/settings/settings-shell.component.ts`) rendered for all `/settings/**` child routes:
  - Sub-nav tabs: **Currencies** | **Institutions** (phase 1); room for **Term rates** | **OnCall rates** in later programme phases without header proliferation.
  - Default redirect: `/settings` → `/settings/currencies` (or last-visited settings section in sessionStorage — optional).
- Keep routes: `/settings/currencies`, `/settings/currencies/new`, `/settings/currencies/:code`; add `/settings/institutions`, `/settings/institutions/new` (onboard: **display name only**), `/settings/institutions/:institutionCode` (code read-only).
- `showDeskNav()` remains `false` for `/settings` (already in `app.ts`).

**Return-to-desk and visual pattern (U-02):**

- Reuse **`DeskReturnService`** (`getReturnUrl()`, sessionStorage last desk queue).
- Each settings list screen: `settings-panel` → `settings-toolbar` with `<a [routerLink]="deskReturn.getReturnUrl()" class="settings-back">← Back to desk</a>` (as `currency-settings-list`).
- Shared SCSS: `settings-header`, `settings-table-wrap`, `settings-error`, `settings-state`, `btn-primary`, desk theme tokens — extract to shared partial if institution screens duplicate; institution list/edit MUST match currency settings look-and-feel.
- Hub sub-nav does **not** replace per-screen “Back to list” on edit forms (`routerLink` to parent list).

**Institution feature:**

- **`institution-settings/`** — list shows `institutionCode` + `displayName`; onboard form has **display name only**; success shows generated code; activate/deactivate by `institutionCode`.

**Execute (order detail):**

- Autocomplete for active institutions (`displayName` / `institutionCode`); empty catalog → disabled execute + link to `/settings/institutions`.

### 8. OpenSpec capability artifacts

| Capability | Location |
|--------------|----------|
| `institution-onboarding` | `specs/institution-onboarding/spec.md` |
| `order-institution-constraints` | `specs/order-institution-constraints/spec.md` |
| `trader-desk-navigation` | delta: single **Settings** header entry, settings sub-nav, hide desk tabs under `/settings` |
| `currency-settings-ui` | delta (if archived to main): migrate header from “Currencies” to hub; optional thin delta if behaviour already in main spec |
| `trader-order-detail-actions` | delta (execute autocomplete + API) |

### 9. Testing strategy (TDD)

| Layer | Focus |
|-------|--------|
| `OrderAgainstInstitutionPolicyTest` | Unknown `institutionCode`, inactive, empty catalog |
| `InstitutionCodeAcronymTest` | Single/multi word, fallback INST, 6-char cap |
| `ManageInstitutionSettingsServiceTest` | First HSBC → `HSBC-01`, second → `HSBC-02`; suffix overflow |
| `ExecuteOrderServiceTest` | Mocks repository; execute sets counterparty from institution |
| Execute REST tests | 400 unknown institution; 400/503 empty catalog; 201 with institutionCode |
| `OrderExecutedV1` mapper | `counterparty` = displayName after execute |
| Vitest | Onboard without code field; list shows generated code; autocomplete uses institutionCode |

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| **Breaking** execute API for clients/tests | Update OpenAPI, integration tests, Cypress in same delivery |
| displayName change rewrites historical counterparty display | Accept for POC; institution code on execution row if added |
| Intake allowed but execute blocked (empty institutions) | Document in quickstart; UI disable execute |
| Autocomplete stale after deactivate | Reload list on execute screen focus; server rejects inactive |
| Settings hub refactor breaks header/tests | Single PR: settings shell, **Settings** header link, sub-nav; update `app.spec.ts` |

## Migration Plan

1. Flyway `institution` (+ optional `institution_code` on orders).
2. Deploy settings + execute policy together (avoid window where execute accepts text but catalog required).
3. Update demo scripts: onboard ≥1 institution before execute.
4. Existing executed orders: unchanged historical counterparty text.

## Open Questions

- **Execution persistence** — Add `institution_code` on order/execution + handoff vs counterparty-only (recommend persist generated code).
- **Duplicate displayName** — Allow (same acronym → different suffix, e.g. two “HSBC” rows → `HSBC-01`, `HSBC-02`) unless product later rejects duplicate names.
- **Stop words in acronyms** — Optional later tweak (“The Bank of …”); v1 uses first letter per token only.
- **T-*** / **O-*** — Phase 2 CSV references `institutionCode` column; see [`OPEN-DECISIONS.md`](OPEN-DECISIONS.md).
