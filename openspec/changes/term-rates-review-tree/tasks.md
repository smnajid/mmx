## 1. Grouping helper (TDD)

- [x] 1.1 Add `group-term-rates-for-review.ts` with failing Vitest: groups by institution/currency, tenor catalog order, institution and currency sort
- [x] 1.2 Implement grouping until `group-term-rates-for-review.spec.ts` is green

## 2. Shared review tree styles (`frontend/src/app/settings.scss`)

- [x] 2.1 Add `.settings-review-tree`, `.settings-review-tree__toolbar`, institution/currency/leaf row styles aligned with desk tokens (indent, mono code, numeric rate column)

## 3. Term rates component — tests first

- [x] 3.1 Write failing Vitest: loads institutions + rates; institution header shows displayName and code; missing catalog falls back to code only
- [x] 3.2 Write failing Vitest: review tree default collapsed on load; Expand all / Collapse all toggle visibility
- [x] 3.3 Write failing Vitest: changing trading day resets tree to collapsed
- [x] 3.4 Update/remove flat-table assertions in existing `term-rate-settings.component.spec.ts` (workflow, chips, confirm tests still pass)

## 4. Term rates component — implementation

- [x] 4.1 Fetch institutions in parallel with `listForDay`; build code → displayName map
- [x] 4.2 Replace Review `mmx-table` with hierarchical `<details>` tree using grouped data; institution header name + code only
- [x] 4.3 Add Expand all / Collapse all controls; reset collapsed on `tradingDate` change
- [x] 4.4 Implement until all Vitest in §3 pass (re-run `term-rate-settings.component.spec.ts` and grouping spec during iteration)

## 5. SDD feature spec sync

- [x] 5.1 Update `specs/005-term-rate-settings/spec.md` Review UX: hierarchical tree, display names on institution headers, expand/collapse all, default collapsed (no REST change)

## 6. Final verification

- [x] 6.1 Run `npm run test` in `frontend/` — green
- [ ] 6.2 Manual smoke: `/settings/term-rates` — load day with data → tree collapsed → expand one bank/currency → Expand all → Collapse all → switch day chip → names visible on headers
