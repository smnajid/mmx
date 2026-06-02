## Spec-Driven Development (SDD)

Agents MUST keep **specifications and code aligned** for any material change (API, domain behavior, persistence, Trader-facing UX). Operational checklist: `.cursor/rules/spec-sdd-sync.mdc` (Cursor) or `CLAUDE.md` (Claude Code). Governance: `.specify/memory/constitution.md` Principle VI (Spec–code parity), version **1.6.0+**. Treat missing spec updates as a **blocking** defect, not a follow-up.

**Claude Code**: skills and OPSX commands live under `.claude/` — see `docs/agents/claude-code.md`.

## Test-driven development (TDD)

For **new behavior** and **bug fixes**, the default workflow is **strict TDD**: write a **failing** automated test first (red), implement the **smallest** change that makes it pass (green), then refactor without changing behavior (refactor). Only then extend specs/contracts if the change is material per SDD above.

**Stack**: prefer **JUnit 5** (backend), **Angular unit tests** (`ng test` / Vitest) for components and services, and **Cypress** where an end-to-end assertion locks acceptance. Choose the narrowest level that can fail for the wrong reason (unit vs integration vs e2e).

**Waivers**: skipping red-first TDD is allowed only when the user explicitly agrees or when the change is purely mechanical (e.g. rename, comment-only). If TDD was skipped, note it briefly in the PR or commit message.

Agents SHOULD read the `tdd` skill (`.claude/skills/tdd/SKILL.md` on Claude Code, `.cursor/skills/tdd/SKILL.md` on Cursor) when the user asks for TDD, red-green-refactor, or test-first delivery.

## Spec Kit Git hook (`/speckit.specify`)

Starting a **new** feature with `/speckit.specify` MUST flow through Spec Kit’s **`before_specify`** hook (see `.specify/extensions.yml`): it runs **`speckit.git.feature`**, which creates and checks out the next numbered feature branch (e.g. `003-…`). Repo setting **`auto_execute_hooks: true`** keeps that hook automatic. If you run specify without going through the normal Speckit command path, create the feature branch yourself with the same script or `git checkout -b …` so branch name and spec folder stay aligned.

## Agent skills

### Issue tracker

Issues are tracked as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Triage labels

This repo uses the default canonical triage labels. See `docs/agents/triage-labels.md`.

### Domain docs and navigation

| File | Purpose |
|------|---------|
| [CONTEXT.md](CONTEXT.md) | Domain glossary and relationships (ubiquitous language) |
| [docs/agents/codebase-map.md](docs/agents/codebase-map.md) | Where modules, routes, contracts, and tests live |
| [docs/agents/domain.md](docs/agents/domain.md) | How skills consume CONTEXT and ADRs |
| [docs/adr/](docs/adr/) | Architecture decision records (when present) |

Agents SHOULD read **CONTEXT.md** and **codebase-map.md** before broad codebase exploration.
