## 1. Shared settings styles (`frontend/src/app/settings.scss`)

- [x] 1.1 Add `.settings-card` (or equivalent) sharing border/surface/shadow with `settings-table-wrap` for workflow sections
- [x] 1.2 Add `.settings-file-input` / `.settings-upload-row` and date input styling aligned with `.settings-form` inputs
- [x] 1.3 Add shared `.btn-secondary` (move from term-rate component); ensure `.btn-primary` on `<button>` has `cursor: pointer` and disabled state

## 2. Term rates component — tests first (`frontend/`)

- [x] 2.1 Write failing Vitest: `listTradingDays` called on init; chips rendered from mock days; chip click changes `tradingDate` and list request
- [x] 2.2 Write failing Vitest: day summary shows row count and last upload when rates returned
- [x] 2.3 Write failing Vitest: upload with existing rates for selected day triggers confirm; cancel skips POST; accept proceeds
- [x] 2.4 Write failing Vitest: empty state includes sample download affordance
- [x] 2.5 Write failing Vitest: lede and prepare/upload/review section headings present

## 3. Term rates component — implementation

- [x] 3.1 Refactor template: page lede; Prepare / Upload / Review cards using shared surfaces; remove duplicate local button/error styles
- [x] 3.2 Wire `listTradingDays()` on init and after upload success; implement day chips + manual date control
- [x] 3.3 Implement day summary strip from loaded rates (`length`, max `uploadedAt`)
- [x] 3.4 Implement replace-day `confirm` guard before `upload()` when `rates().length > 0` for selected day
- [x] 3.5 Style file and date inputs; drop `uploadedBy` column; use `.settings-error` for errors
- [x] 3.6 Implement actionable empty state with sample download link/button
- [x] 3.7 Implement until all Vitest in §2 pass (re-run `term-rate-settings.component.spec.ts` only during iteration)

## 4. SDD feature spec sync

- [x] 4.1 Update `specs/005-term-rate-settings/spec.md` Scope / UI bullets to match workflow, `/days` quick-select, replace-day confirm, and presentation requirements (no REST contract change)

## 5. Final verification

- [x] 5.1 Run `npm run test` in `frontend/` — green
- [ ] 5.2 Manual smoke: `/settings/term-rates` — download sample, upload, chips switch days, re-upload shows confirm, back to desk works
