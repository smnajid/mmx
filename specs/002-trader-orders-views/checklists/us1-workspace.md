# User Story 1 (workspace) checklist: Trader order views

**Purpose**: Requirements-quality review (“unit tests for English”) for **User Story 1** — Term vs OnCall workspace structure, navigation, and non-mixing of order types — in [spec.md](../spec.md).  
**Created**: 2026-05-09  
**Feature**: [spec.md](../spec.md)  
**Audience / depth**: Peer or author review before merging spec changes; standard rigor.

**Note**: Items interrogate whether requirements are **written** completely, clearly, and consistently—not whether running code behaves correctly.

---

## Requirement completeness

- [ ] CHK001 Are **all** behaviours needed to ship US1 independently documented beyond list mixing (default workspace, session boundary, chrome on every queue tier)? [Completeness, Spec § User Story 1, FR-001]
- [ ] CHK002 Is **“desk queue screen”** (where two-tier chrome applies) delineated sharply enough versus screens that **may** omit chrome (e.g. order detail)—or is boundary left only as an illustrative example opening interpretation gaps? [Clarity / Completeness, Spec § FR-001]
- [ ] CHK003 Are requirements stated for screens that **reuse** workspace context indirectly (deep links, bookmarked URLs, return from detail) except where deliberately out of scope for US1? [Coverage / Gap, Spec § User Story 1 § Acceptance 1–6]
- [ ] CHK004 For US1 **Executed** cohort (“shell” before accounting refinements), is the **difference** versus later FR-009 (executed-not-yet-accounted) explained so readers do not treat US1 Executed semantics as final product law? [Completeness / Traceability, Spec § User Story 1 narrative vs § FR-009]

---

## Requirement clarity & measurability

- [ ] CHK005 Is **“visually distinguished”** (active workspace/sub-view) defined with criteria objective enough for acceptance without prescribing pixels—e.g. one active state vs multiple, coexistence rules with focus styles? [Clarity / Measurability, Spec § User Story 1, Clarifications § 2026-05-09]
- [ ] CHK006 Can **SC-007** be applied without circular reference to unstated reviewer judgment (“correct” highlighting)—or does it need sharper definition of mismatched-state failure modes? [Measurability, Spec § SC-007]
- [ ] CHK007 Is **“new authenticated session”** aligned with measurable acceptance (same glossary as Clarifications)—or does it collide with SPA-only sessions where “session start” is ambiguous? [Ambiguity / Clarity, Spec § Clarifications; User Story 1 § Acceptance 5]

---

## Consistency

- [ ] CHK008 Do US1 acceptance scenarios **avoid** implying desk-wide Assigned (US2) when describing Assigned lists—today’s backlog may blend reader expectations? [Consistency, Spec § User Story 1 vs § User Story 2 / FR-002]
- [ ] CHK009 Do **workspace default OnCall** statements match across User Story prose, Clarifications, FR-001, edge cases (“New session vs last workspace”), and assumptions—without contradiction? [Consistency, Spec § FR-001; Edge Cases; Assumptions]
- [ ] CHK010 Does **Executed** wording in US1 align with Success Criteria referencing “scripted UX checks covering … Executed” before US4 narrows cohort—risk of testers validating wrong cohort semantics? [Consistency, Spec § SC-007 vs § User Story 4]

---

## Scenario & edge-case coverage

- [ ] CHK011 Are recovery / ambiguity requirements documented when **workspace** switches mid-task (partial navigation, stale list refresh expectations), or intentionally deferred with scope note? [Coverage / Gap, Spec § User Story 1 § Acceptance 4]
- [ ] CHK012 Is simultaneous **dual-workspace visibility** prohibited or undefined—could a dense layout implicitly show mixed types despite “focused workspace surface”? [Edge case / Gap, Spec § FR-001 “same workspace surface”]
- [ ] CHK013 For **bookmark or shared URL** landing on Assigned/Executed inside a workspace, do requirements forbid misleading chrome state when data fails to load? [Exception / Gap, Spec § User Story 1 § Acceptance 6]

---

## Non-functional & assumptions

- [ ] CHK014 Are accessibility requirements for **keyboard or assistive-tech** traversal of workspace + sub-view navigation documented—or explicitly out of scope for US1 with rationale? [NFR Coverage / Gap, Spec § User Story 1 § persistent primary navigation]

---

## Dependencies & downstream risk

- [ ] CHK015 Are **research / data-model** artefacts referenced where US1 relies on terminology (workspace vs `OrderType`, assignee vs desk-wide Assigned) without recopying contradictory semantics? [Dependencies, Spec § research.md § R-002; data-model.md]

---

## Notes

- Check items `[x]` when the **spec text** satisfies the question; annotate findings inline if not.
- Re-run after material edits to US1, FR-001, Clarifications, or SC-005/SC-007.
