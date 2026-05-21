# mmx — agent instructions (Claude Code)

This project uses **Spec Kit (Speckit)** and **OpenSpec** for spec-driven development. Prefer the skills and commands under `.claude/` (not `.cursor/`).

## Spec-Driven Development (SDD)

Agents MUST keep **specifications and code aligned** for any material change (API, domain behavior, persistence, Trader-facing UX). Governance: `.specify/memory/constitution.md` Principle VI (Spec–code parity), version **1.6.0+**. Treat missing spec updates as a **blocking** defect.

### Spec–code parity (blocking)

If the change affects HTTP contract, DTOs/OpenAPI, domain rules, persistence/Flyway, queue/list/detail UX, acceptance criteria, user-facing language, or documented data model — update the matching feature artifacts **in the same delivery**.

**Default feature root**: `specs/001-mm-order-processing/` (use `specs/002-trader-orders-views/` when that feature is active).

| If you changed… | Update at least… |
|-----------------|------------------|
| REST paths, payloads, responses, errors | `contracts/openapi.yaml`, regenerate codegen; `contracts/api-v1.md` |
| Entities, fields, relationships | `data-model.md`; OpenAPI if on the wire |
| User stories, behavior, AC, edge cases | `spec.md` |
| Architecture, tech stack, boundaries | `plan.md` |
| Task scope or done status | `tasks.md` |

Do not mark work complete if code changed but `spec.md`, `api-v1.md`, or `data-model.md` still describe old behavior.

### Stack and plan context

Read the current plan at `specs/002-trader-orders-views/plan.md` for structure and commands. REST follows **contract-first OpenAPI** (`.specify/memory/constitution.md` Principle I).

OpenSpec project context: `openspec/config.yaml`.

## Test-driven development (TDD)

For **new behavior** and **bug fixes**: failing test first (red), smallest change to pass (green), refactor. Backend: JUnit 5; frontend: Angular/Vitest; e2e: Cypress where appropriate.

Use skill `tdd` when the user asks for TDD or red-green-refactor.

## Speckit workflow

Slash skills (user-invocable):

| Skill | Purpose |
|-------|---------|
| `/speckit-specify` | New or updated feature spec |
| `/speckit-clarify` | Clarify underspecified areas |
| `/speckit-plan` | Implementation plan and design artifacts |
| `/speckit-tasks` | Generate `tasks.md` |
| `/speckit-implement` | Execute tasks |
| `/speckit-analyze` | Cross-artifact consistency check |
| `/speckit-constitution` | Update project constitution |
| `/speckit-checklist` | Requirements checklist |
| `/speckit-taskstoissues` | Tasks → GitHub issues |

Git extension skills: `/speckit-git-feature`, `/speckit-git-commit`, `/speckit-git-initialize`, `/speckit-git-remote`, `/speckit-git-validate`.

**New feature**: run `/speckit-specify` so the `before_specify` hook creates the next numbered feature branch (`speckit.git.feature` → `/speckit-git-feature`). Hooks: `.specify/extensions.yml` (`auto_execute_hooks: true`).

Hook command names use dots in YAML; invoke as hyphenated skills (e.g. `speckit.git.commit` → `/speckit-git-commit`).

## OpenSpec (OPSX)

Commands live under `.claude/commands/opsx/` (e.g. `/opsx:propose`, `/opsx:apply`, `/opsx:archive`). Matching skills: `openspec-*` under `.claude/skills/`.

CLI: `openspec validate`, `openspec archive <change>`, `openspec status --json`.

## Domain and issues

- Domain: `CONTEXT.md`, `docs/adr/` — see `docs/agents/domain.md`
- Issues: `.scratch/` — see `docs/agents/issue-tracker.md`
- Triage labels: `docs/agents/triage-labels.md`
