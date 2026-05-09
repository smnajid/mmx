## Spec-Driven Development (SDD)

Agents MUST keep **specifications and code aligned** for any material change (API, domain behavior, persistence, Trader-facing UX). Operational checklist: `.cursor/rules/spec-sdd-sync.mdc`. Governance: `.specify/memory/constitution.md` Principle VI (Spec–code parity), version **1.6.0+**. Treat missing spec updates as a **blocking** defect, not a follow-up.

## Test-driven development (TDD)

For **new behavior** and **bug fixes**, the default workflow is **strict TDD**: write a **failing** automated test first (red), implement the **smallest** change that makes it pass (green), then refactor without changing behavior (refactor). Only then extend specs/contracts if the change is material per SDD above.

**Stack**: prefer **JUnit 5** (backend), **Angular unit tests** (`ng test` / Vitest) for components and services, and **Cypress** where an end-to-end assertion locks acceptance. Choose the narrowest level that can fail for the wrong reason (unit vs integration vs e2e).

**Waivers**: skipping red-first TDD is allowed only when the user explicitly agrees or when the change is purely mechanical (e.g. rename, comment-only). If TDD was skipped, note it briefly in the PR or commit message.

Agents SHOULD read `.cursor/skills/tdd/SKILL.md` when the user asks for TDD, red-green-refactor, or test-first delivery.

## Spec Kit Git hook (`/speckit.specify`)

Starting a **new** feature with `/speckit.specify` MUST flow through Spec Kit’s **`before_specify`** hook (see `.specify/extensions.yml`): it runs **`speckit.git.feature`**, which creates and checks out the next numbered feature branch (e.g. `003-…`). Repo setting **`auto_execute_hooks: true`** keeps that hook automatic. If you run specify without going through the normal Speckit command path, create the feature branch yourself with the same script or `git checkout -b …` so branch name and spec folder stay aligned.

## Agent skills

### Issue tracker

Issues are tracked as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Triage labels

This repo uses the default canonical triage labels. See `docs/agents/triage-labels.md`.

### Domain docs

This repo uses a single-context layout (`CONTEXT.md` + `docs/adr/` at repo root). See `docs/agents/domain.md`.
