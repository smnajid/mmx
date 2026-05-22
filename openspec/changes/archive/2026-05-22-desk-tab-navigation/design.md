## Context

The trader shell (`frontend/src/app/app.html`) uses two link rows in the **header**: workspace links (“On-call workspace”, “Term workspace”) and queue links (Received / Assigned / Executed). Routing and highlight logic in `app.ts` already implement the correct two-tier model (`/oncall|term` + queue segment, order-details `?ws=&queue=`). Feature **002** (`specs/002-trader-orders-views/spec.md` User Story 1) requires both tiers to stay visually distinguished on every desk screen.

Traders and product language prefer **tabs** without the word “Workspace”; the ON-CALL product mode label is **ON-CALL** (all caps, hyphenated).

## Goals / Non-Goals

**Goals:**

- Present **primary tabs** (**ON-CALL**, **Term**) and **sub-tabs** (Received, Assigned, Executed) in **main content**, directly under a slim top bar (brand + trader only).
- Keep existing URLs, default route (`oncall/received`), and `resolveDeskNavContext()` behaviour unchanged.
- Preserve dual-tier active state on queue lists and order details (with `?ws=&queue=`).
- Ship a **responsive** layout that works on narrow viewports without hiding sub-tabs.
- Sync **002** `spec.md` navigation acceptance text in the same delivery (SDD).

**Non-Goals:**

- New REST endpoints, OpenAPI changes, or backend modules.
- Angular Material or a new tab library (use semantic HTML + CSS on existing `routerLink` anchors).
- Persisting last-selected tab across sessions.
- Collapsing sub-tabs into a menu on mobile (violates always-visible two-tier requirement).

## Decisions

### 1. Layout: desk chrome in `main`, not `header`

**Decision:** Top bar holds brand + trader input only. A `desk-nav` block at the top of `<main>` contains primary then sub tab strips; `<router-outlet>` sits below.

```
┌─ topbar: brand ····················· trader ─┐
├─ main ────────────────────────────────────────┤
│  [ ON-CALL │ Term ]          ← primary tabs   │
│  [ Received │ Assigned │ Executed ]           │
│  ─────────────────────────────────────────    │
│  <router-outlet>  list / details              │
└───────────────────────────────────────────────┘
```

**Rationale:** Matches “screen” mental model; lists read as content under the active desk context.

**Alternative:** Keep tabs in header — rejected per product direction.

### 2. Tabs = styled `routerLink` anchors (no new routing)

**Decision:** Reuse `workspaceActive()`, `queueActive()`, `queuePrefix()`; replace `.workspace-nav` / `.sub-nav` class names with `.desk-primary-tabs` / `.desk-sub-tabs` (or equivalent) and tab-specific CSS (underline / connected bar).

**Rationale:** Zero risk to bookmarkable URLs and existing `app.spec.ts` scenarios; tests update selectors only.

**Alternative:** `MatTabGroup` — no Material dependency in project.

### 3. Labels and ARIA

**Decision:**

- Primary: **ON-CALL**, **Term** (exact casing).
- Sub: **Received**, **Assigned**, **Executed** (unchanged).
- `role="tablist"` on each nav; links `role="tab"` with `aria-selected` bound to `.active` (or `aria-current="page"` where appropriate).
- `aria-label="Desk"` on primary; `aria-label="Queue"` on sub (or “Order queues”).

**Rationale:** Accessible tabs without changing navigation model.

### 4. Mobile / narrow viewports (≤720px)

**Decision:** **Horizontal scroll** for each tab row inside a full-width container:

- `overflow-x: auto`, `flex-wrap: nowrap`, `-webkit-overflow-scrolling: touch`
- Optional subtle **fade mask** at scroll edges (CSS gradient) to hint more tabs
- **Minimum tap target** ~44px height on tab links
- **Both rows remain visible** — no accordion or “more” menu for sub-tabs
- On order-details-only routes without `ws`/`queue`, primary/sub may show no selection (unchanged); scroll still applies if tabs render

**Rationale:** User had no fixed mobile pattern; scroll preserves spec (both tiers visible) and avoids losing Assigned/Executed on small screens. Test at 320px and ~720px breakpoints.

**Alternatives considered:**

- Stack six tabs vertically — wastes vertical space on lists.
- Hide sub-tabs until primary tap — breaks FR for Assigned/Executed context.

### 5. Page-level headings

**Decision:** Align in-content H1s to **ON-CALL** where they reference the mode (e.g. `Received — ON-CALL`); keep queue name in title or shorten if redundant with sub-tab (implementation may use `Received` only if sub-tab is clearly active — prefer consistency with **ON-CALL** spelling).

**Rationale:** Removes “On call” / “workspace” drift.

### 6. Spec artifacts

**Decision:** Delta spec `openspec/changes/desk-tab-navigation/specs/trader-desk-navigation/spec.md` (ADDED). Mirror navigation clauses in `specs/002-trader-orders-views/spec.md` User Story 1 during implementation.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Tests break on DOM/class moves | Update `app.spec.ts` selectors first (TDD); grep for `.workspace-nav` |
| Double vertical space on mobile | Compact tab padding on small screens; list padding unchanged |
| Order details: tabs in main while detail fills outlet — tabs still visible above detail | Acceptable; matches “always know context”; verify back link still works |
| Scrollable tabs obscure “Executed” off-screen | Default route OnCall+Received; active tab scrolled into view on init (optional `scrollIntoView` in `ngAfterViewInit` if needed) |

## Migration Plan

Single frontend deploy; no DB or API migration. Rollback = revert `app` template/styles. No feature flags.

## Open Questions

- **Active tab scroll-into-view** on load: implement only if manual QA shows active tab off-screen on common phones (defer to apply phase).
- **Sticky desk-nav** while scrolling long lists: not in v1; revisit if traders request it.
