## 1. Grouping helper (TDD)

- [x] 1.1 Add `group-oncall-rate-segments-for-review.ts` with failing Vitest: groups by `(currency, noticePeriod)`, filters `CANCELED`, currency/notice sort, segments by `valueDate` desc, derives `currentSegment` for open (`2999-12-31`)
- [x] 1.2 Implement grouping until `group-oncall-rate-segments-for-review.spec.ts` is green

## 2. Review tree styles (`frontend/src/app/settings.scss`)

- [x] 2.1 Extend `.settings-review-tree` with curve-point node and in-body segment table styles (reuse toolbar/expand pattern from term rates; add selected-state class for add-form coupling)

## 3. OnCall rates component — tests first

- [x] 3.1 Write failing Vitest: curve-point tree renders instead of flat table; canceled segments omitted; headers show current rate summary
- [x] 3.2 Write failing Vitest: review tree default collapsed on load; Expand all / Collapse all; institution change resets collapsed state
- [x] 3.3 Write failing Vitest: selecting/expanding curve point sets add form `currency` and `noticePeriod` and applies selected highlight
- [x] 3.4 Write failing Vitest: sentinel `2999-12-31` displays as **Open** in segment rows
- [x] 3.5 Update existing `oncall-rate-settings.component.spec.ts` (pending badge, add, cancel) for tree layout while keeping API behaviour assertions

## 4. OnCall rates component — implementation

- [x] 4.1 Replace flat `mmx-table` with curve-point `<details>` review tree using grouped data; filter canceled client-side
- [x] 4.2 Add Expand all / Collapse all toolbar; reset collapsed on institution change
- [x] 4.3 Wire curve-point selection to add form sync and selected highlight; implement until all Vitest in §3 pass

## 5. SDD feature spec sync

- [x] 5.1 Add OnCall settings UX bullets to `specs/002-trader-orders-views/spec.md` (curve-point review tree, current-rate headers, add-form coupling, canceled hidden — no REST/OpenAPI change)

## 6. Final verification

- [x] 6.1 Run `npm run test` in `frontend/` — green
- [x] 6.2 Manual smoke: `/settings/oncall-rates` — load institution → collapsed headers show current rates → expand curve point → add form pre-filled → add/cancel still work → Expand all → Collapse all → switch institution resets
