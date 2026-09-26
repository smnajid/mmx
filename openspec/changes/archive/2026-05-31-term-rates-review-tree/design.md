## Context

[`term-rates-settings-ux`](../term-rates-settings-ux/) delivered the Term rates morning workflow at `/settings/term-rates` (Prepare / Upload / Review cards, day chips, replace-day confirm, day summary). **Review** still uses a flat `mmx-table` with `institutionCode`, `currency`, `tenor`, and `rate` on every row (~50+ rows for a typical POC day). Institution **display names** exist on `GET /api/v1/settings/institutions` but are not used on this screen; the prior UX change explicitly deferred them.

Traders verify uploads **per institution** (did this bank’s currencies and tenors land?) rather than by scanning one global grid. The data model is already hierarchical: PK `(trading_date, institution_code, currency, tenor)`.

### Product decisions (resolved)

| Question | Decision |
|----------|----------|
| Display name source | **Current** `displayName` from Institutions catalog via client join; code as secondary label on institution header only |
| Default expand state | **All collapsed** when rates for a day load or trading day changes |
| Bulk expand | **Expand all** and **Collapse all** controls for the review tree |
| Primary review task | **Per-institution verification** (completeness of that bank’s sheet); no flat-table alternate view in this change |
| Name placement | **Institution header only** — currency and tenor rows show codes/tenors only |

## Goals / Non-Goals

**Goals:**

- Hierarchical Review UI: institution → currency → tenor + rate leaves.
- Institution headers show `displayName` + mono `institutionCode`; fallback to code if catalog miss.
- Default collapsed; Expand all / Collapse all.
- Stable sort: institutions by `institutionCode`; currencies ISO ascending; tenors in catalog order `1W, 2W, 1M, 3M, 6M, 1Y`.
- Header counts: institution shows currency count and total rate count; currency shows tenor count.
- Vitest for grouping, names, expand/collapse, default collapsed.
- SDD: sync `specs/005-term-rate-settings/spec.md` Review UX.

**Non-Goals:**

- OpenAPI / backend / `TermRate` DTO changes.
- Flat table toggle or cross-bank tenor comparison mode.
- `displayName` on currency/tenor rows.
- Session-persisted expand state.
- Changes to Prepare, Upload, day chips, replace-day confirm, or CSV ingest.

## Decisions

### 1. Client-side join for display names

**Choice:** On load (or when `tradingDate` changes), fetch institutions with existing `InstitutionSettingsApiService.list(traderId)` in parallel with `listForDay`. Build `Map<institutionCode, displayName>` for header labels.

**Alternatives:**

- Add `institutionDisplayName` to term-rates list API — rejected (unnecessary contract churn for POC).
- Embed names in CSV — rejected (duplicates catalog, drift risk).

**Rationale:** Matches Institutions settings; one extra GET acceptable for settings screen; rename in catalog updates review labels (acceptable per product).

### 2. Pure function grouping helper

**Choice:** Extract `groupTermRatesForReview(rates: TermRate[]): ReviewTree` (institution → currency → leaves) in a small TS module under `term-rate-settings/` (or `shared/` if tests prefer isolation). Component holds expand state (`Set` of keys or boolean flags per level).

**Tree node shape (conceptual):**

```text
ReviewInstitutionNode { institutionCode, displayName?, currencies: ReviewCurrencyNode[] }
ReviewCurrencyNode { currency, tenors: { tenor, rate }[] }
```

**Rationale:** Keeps template thin; Vitest can test sort/group without `TestBed`.

### 3. Disclosure UI pattern

**Choice:** Native `<details>`/`<summary>` per institution and per currency, styled via new `.settings-review-tree` classes in `settings.scss` (chevron, indent, mono for codes, right-aligned rate on leaves). Expand-all sets `open` on all details elements (or drives a signal that binds `[open]`).

**Alternatives:**

- Custom accordion component — rejected for POC (no existing pattern; details is accessible by default).

**Rationale:** No new dependency; keyboard support from platform.

### 4. Expand all / collapse all

**Choice:** Two secondary buttons in Review toolbar row (below day summary, above tree): **Expand all**, **Collapse all**. They affect only the current day’s tree. Changing trading day resets to **all collapsed**.

**Rationale:** Matches product default; avoids persisting state complexity.

### 5. Replace flat table requirement

**Choice:** Remove `mmx-table` from Review when rates exist; keep upload row errors unchanged (still `.settings-error` list).

**Rationale:** Spec delta MODIFIED replaces “Rates table optimized for scanning”.

### 6. Institution ordering

**Choice:** Sort institution groups by `institutionCode` ascending (stable, predictable). Same order as flat table sort would imply.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Two HTTP calls on review load | Parallel `forkJoin` / `combineLatest`; institutions list is small |
| Catalog miss for code in rates | Show `institutionCode` as primary label; no error banner |
| `details` styling inconsistent across browsers | Use shared settings tokens; manual smoke in Final verification |
| Expand-all with many nodes | POC scale (~4×3×5) fine; no virtualisation in scope |
| Vitest tests assumed flat table | Update `term-rate-settings.component.spec.ts` assertions |

## Migration Plan

Frontend-only deploy. No Flyway or API rollout.

1. Ship grouping helper + tests.
2. Replace Review table with tree + institution fetch.
3. Update feature spec `005-term-rate-settings`.
4. Manual smoke: collapsed default → expand one bank → expand all → collapse all → switch day chip resets collapse.

Rollback: revert frontend commit; no data migration.

## Open Questions

_None — product decisions locked in table above._
