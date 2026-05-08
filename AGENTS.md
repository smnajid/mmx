## Spec-Driven Development (SDD)

Agents MUST keep **specifications and code aligned** for any material change (API, domain behavior, persistence, Trader-facing UX). Operational checklist: `.cursor/rules/spec-sdd-sync.mdc`. Governance: `.specify/memory/constitution.md` Principle VI (Spec–code parity), version **1.6.0+**. Treat missing spec updates as a **blocking** defect, not a follow-up.

## Agent skills

### Issue tracker

Issues are tracked as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Triage labels

This repo uses the default canonical triage labels. See `docs/agents/triage-labels.md`.

### Domain docs

This repo uses a single-context layout (`CONTEXT.md` + `docs/adr/` at repo root). See `docs/agents/domain.md`.
