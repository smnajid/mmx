## Spec-Driven Development (SDD)

Agents MUST keep **specifications and code aligned** for any material change (API, domain behavior, persistence, Trader-facing UX). Operational checklist: `.cursor/rules/spec-sdd-sync.mdc`. Governance: `.specify/memory/constitution.md` Principle VI (Spec–code parity), version **1.6.0+**. Treat missing spec updates as a **blocking** defect, not a follow-up.

## Spec Kit Git hook (`/speckit.specify`)

Starting a **new** feature with `/speckit.specify` MUST flow through Spec Kit’s **`before_specify`** hook (see `.specify/extensions.yml`): it runs **`speckit.git.feature`**, which creates and checks out the next numbered feature branch (e.g. `003-…`). Repo setting **`auto_execute_hooks: true`** keeps that hook automatic. If you run specify without going through the normal Speckit command path, create the feature branch yourself with the same script or `git checkout -b …` so branch name and spec folder stay aligned.

## Agent skills

### Issue tracker

Issues are tracked as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Triage labels

This repo uses the default canonical triage labels. See `docs/agents/triage-labels.md`.

### Domain docs

This repo uses a single-context layout (`CONTEXT.md` + `docs/adr/` at repo root). See `docs/agents/domain.md`.
