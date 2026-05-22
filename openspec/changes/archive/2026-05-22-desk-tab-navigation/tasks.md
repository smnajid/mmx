## 1. Specification sync (SDD)

- [x] 1.1 Update `specs/002-trader-orders-views/spec.md` User Story 1: tab labels **ON-CALL** / **Term**, main-content placement, no “Workspace” in trader chrome; keep routing/default-session behaviour
- [x] 1.2 Adjust `specs/002-trader-orders-views/quickstart.md` manual checks if navigation steps reference header workspace buttons

## 2. Tests (TDD — shell navigation)

- [x] 2.1 Update `frontend/src/app/app.spec.ts`: expect desk tabs in main (e.g. `.desk-primary-tabs`, `.desk-sub-tabs`); assert **ON-CALL** / **Term** link text; keep highlight scenarios for `/term/assigned` and order-details `?ws=oncall&queue=executed`
- [x] 2.2 Update `oncall-order-list.component.spec.ts` (and any spec asserting “On call” / “workspace” in headings) for **ON-CALL** copy
- [x] 2.3 Run `cd frontend && npm run test` — confirm red then green after implementation

## 3. Shell layout and tab chrome

- [x] 3.1 Refactor `app.html`: slim `topbar` (brand + trader); move primary + sub navigation into `<main>` above `<router-outlet>` with semantic `role="tablist"` / `role="tab"`
- [x] 3.2 Rename/restyle in `app.scss`: tab underline treatment, `.desk-primary-tabs` / `.desk-sub-tabs`; remove header `nav-cluster` styles that no longer apply
- [x] 3.3 Add responsive rules: horizontal scroll per tab row, nowrap, min-height ~44px, touch scrolling; optional edge fade; verify at 320px and 720px
- [x] 3.4 Keep `app.ts` routing helpers unchanged unless selector/DOM hooks require renames only

## 4. In-page copy consistency

- [x] 4.1 Align list headings and messages: `oncall-order-list`, `executed-order-list` (`workspaceLabel`), `assigned-order-list` help text — use **ON-CALL**, drop trader-facing “workspace” where shown
- [x] 4.2 Grep `frontend/` for “On call”, “On-call workspace”, “workspace” in templates; fix trader-visible strings

## 5. Verification

- [x] 5.1 Manual: default load → **ON-CALL** + **Received** active; switch Term → Assigned → Executed; open order **View** → tabs stay highlighted; back returns to correct queue
- [x] 5.2 Manual mobile: narrow window — both tab rows scroll horizontally; **Executed** reachable on ON-CALL
- [x] 5.3 Run `openspec validate desk-tab-navigation` (if available) and full frontend unit suite
