## Spec-Driven Development (SDD)

Agents MUST keep **specifications and code aligned** for any material change (API, domain behavior, persistence, Trader-facing UX). Operational checklist: `CLAUDE.md`. Governance: `docs/governance.md` Principle VI (Spec–code parity). Treat missing spec updates as a **blocking** defect, not a follow-up.


## Test-driven development (TDD)

For **new behavior** and **bug fixes**, the default workflow is **strict TDD**: write a **failing** automated test first (red), implement the **smallest** change that makes it pass (green), then refactor without changing behavior (refactor). Only then extend specs/contracts if the change is material per SDD above.

**Stack**: prefer **JUnit 5** (backend), **Angular unit tests** (`ng test` / Vitest) for components and services, and **Cypress** where an end-to-end assertion locks acceptance. Choose the narrowest level that can fail for the wrong reason (unit vs integration vs e2e).

**Waivers**: skipping red-first TDD is allowed only when the user explicitly agrees or when the change is purely mechanical (e.g. rename, comment-only). If TDD was skipped, note it briefly in the PR or commit message.

Agents SHOULD read the `tdd` skill (`.claude/skills/tdd/SKILL.md` on Claude Code, `.cursor/skills/tdd/SKILL.md` on Cursor) when the user asks for TDD, red-green-refactor, or test-first delivery.

## OpenSpec changes

Use OpenSpec for spec-driven work: propose a change (`/opsx:propose` or `/opsx:new`), implement from `tasks.md` (`/opsx:apply`), and merge into `openspec/specs/` (`/opsx:archive`). CLI: `openspec validate`, `openspec status --json`. Project context and task-generation rules live in `openspec/config.yaml`.

## Agent skills

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
