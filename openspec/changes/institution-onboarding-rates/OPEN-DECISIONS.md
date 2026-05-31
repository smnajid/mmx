# Open decisions — institution & rates programme

Status: `open` | `deferred` | `decided`

---

## Programme & delivery

| ID | Topic | Decision | Status |
|----|--------|----------|--------|
| P-01 | Execute counterparty vs catalog | Execution **requires** an onboarded, **active** institution. Trader selects via **autocomplete** from the catalog (not free text). API accepts `institutionCode`; server validates and persists order **counterparty** from the institution record (see P-02). | **decided** |
| P-02 | Ubiquitous language | **Institution** — bank entity in **Settings** (catalog, future rates, risk limits). **Counterparty** — label and persisted field in **order/execution** context. Execute UI shows institution choices; wire and domain keep `counterparty` on execution facts. | **decided** |
| P-03 | Cold start | **Strict empty catalog for execute** (mirror currencies discipline): zero onboarded institutions ⇒ **execute rejected**. Traders onboard institutions before executing orders. Intake unchanged in phase 1 (PM does not supply institution). Demo/quickstart must onboard institutions before execute. | **decided** |
| P-04 | Institution code assignment | **`institutionCode` is system-generated** on onboard (trader supplies **`displayName`** only). Format: **`{ACRONYM}-{nn}`** (e.g. `HSBC-01`, `BCI-02`) — acronym from display name + **zero-padded numeric suffix** (`01`…`99`) per acronym base; immutable, unique, returned in onboard response. See design §2 for derivation rules. | **decided** |

Spec reference (when written): `order-institution-constraints`, `institution-onboarding`; design §1–3, §2.

---

## Term rates (phase 2)

| ID | Topic | Notes | Status |
|----|--------|-------|--------|
| T-01 | CSV row grain | Rate per **(institutionCode, currency, tenor)** per `tradingDate`. See [`term-rate-daily-upload`](../term-rate-daily-upload/) design §2. | **decided** |
| T-02 | Same-day re-upload | **Replace whole day** (delete all rows for date, insert new set). | **decided** |
| T-03 | Trading day | **Explicit `tradingDate` column** in CSV; single date per file. | **decided** |
| T-04 | Back office Term feed | Morning CSV is **trader-only reference**; no async BO handoff in phase 2. | **decided** |
| T-05 | Sample CSV | Server-generated sample + UI **Download sample CSV**; no dependency on trader-provided files. | **decided** |

---

## OnCall rates (phase 3)

| ID | Topic | Notes | Status |
|----|--------|-------|--------|
| O-01 | Curve point key | Point keyed by **`(institution, currency, noticePeriod)`**. | **decided** |
| O-02 | Value date / end date | **Inclusive** dates. Last (open) segment end = sentinel **`2999-12-31`** (no-end). Adding a rate with `valueDate = V` supersedes the prior segment by setting its end to **`V−1`** (**provisional** until BO confirms — Option A). `valueDate ≥ today` (no backdating); **no further ordering restriction** relative to the prior segment — the curve reflects the institution's actual rates. First-ever segment for a curve point spans `[valueDate, 2999-12-31]` with nothing to supersede. | **decided** |
| O-03 | Canceled / lifecycle semantics | Three states: **`PENDING_CONFIRMATION → VALID \| CANCELED`**. Cancel is legal **only** from `PENDING_CONFIRMATION` (before BO confirms). On cancel, the prior segment's end reverts to `2999-12-31` (or the pending first segment is discarded). At most **one `PENDING` segment per curve point** at a time. The BO **cannot reject** — `PENDING` resolves only to `VALID` or `CANCELED`. **A pending rate is already active for pricing new orders** whose value date falls in the segment; the `PENDING_CONFIRMATION` status only tells the trader whether the BO has refreshed **in-life contracts**. | **decided** |
| O-04 | Kafka payload | **Thin delta**, keyed by **`segmentId`** (Kafka record key + confirmation address). | **decided** |
| O-05 | BO contract impact | **Outbound `OnCallRateUpdatedV1`** = `segmentId` + curve key `(institution, currency, noticePeriod)` + `rate` + `valueDate`. **No `priorEndDate`** — implicit (a segment ends the day before the next starts; BO derives it). **Inbound confirmation** = a minimal **ack keyed by `segmentId`** delivered as an **atomic compare-and-set** (PENDING→VALID = `200`; canceled = `409`; unknown = `404`); BO **confirms first, impacts in-life contracts only on `200`**. Transport is **synchronous HTTP + idempotent retry** (async-event alternative rejected — see design §6). mmx stamps its own `validatedAt`. | **decided** |

---

## Settings UX (cross-phase)

| ID | Topic | Decision | Status |
|----|--------|----------|--------|
| U-01 | Settings hub shape | **Single Settings** entry in the header (replace per-area links such as “Currencies”). Routes live under `/settings/*` with **in-settings sub-navigation**: phase 1 tabs/sections **Currencies** and **Institutions**; phase 2/3 add Term rates and OnCall rates in the same shell. Desk tabs hidden on any `/settings` path (existing `showDeskNav()` behaviour). | **decided** |
| U-02 | Return-to-desk | **Reuse `currency-settings-ux` pattern** on all settings screens: `DeskReturnService` + “← Back to desk” toolbar link, shared `settings-panel` / `settings-toolbar` / `--mmx-*` tokens, `settings-back` styling. Institution screens (and hub layout) follow the same structure as `currency-settings-list` / `edit`. | **decided** |

---

## How to use this file

1. Discuss in explore or review; record decisions here and in `design.md`.
2. Phase 2/3: resolve T-* / O-* before locking those changes' specs.
