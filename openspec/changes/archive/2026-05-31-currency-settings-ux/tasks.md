## 1. Navigation (`frontend/` app shell)

- [x] 1.1 Add `DeskReturnService` (or equivalent) to capture last `/oncall|term/{queue}` URL before entering `/settings/*`
- [x] 1.2 Add **Desk** header link using remembered URL; fall back to `/oncall/received`
- [x] 1.3 Style `.header-link` / `.header-nav` in `app.scss` with `--mmx-*` and `.active` state
- [x] 1.4 Add optional in-page **Back to desk** on currency list pointing to same return URL
- [x] 1.5 Extend `app.spec.ts`: Desk link visible on settings; active states on desk vs settings routes; return URL behaviour (mock service or router)

## 2. Theme alignment (`frontend/` currency-settings)

- [x] 2.1 Replace inline teal/slate fallbacks in list and edit components with `--mmx-*` tokens
- [x] 2.2 Align table, buttons, forms, and fieldsets with desk patterns (mono labels, borders, hover)
- [x] 2.3 Remove unused `DecimalPipe` import from edit component if still unused (NG8113 warning)

## 3. List readability (`frontend/` currency-settings-list)

- [x] 3.1 Add status badge/chip for Active vs Inactive per row
- [x] 3.2 Add enabled tenors and notice periods summary column (from API arrays)
- [x] 3.3 Wrap table for narrow viewports (horizontal scroll)
- [x] 3.4 Update `currency-settings-list.component.spec.ts` for badges and rules summary in mock flush

## 4. Verification

- [x] 4.1 Run `npm run test` in `frontend/` — green
- [x] 4.2 Manual smoke: `/term/assigned` → Currencies → Desk returns to Assigned; list shows EUR active state and tenor/notice summary
