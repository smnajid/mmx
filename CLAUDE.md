# mmx — agent instructions (Claude Code)

This project uses **OpenSpec** for spec-driven development. Prefer the skills and commands under `.claude/` (not `.cursor/`).

## Spec-Driven Development (SDD)

Agents MUST keep **specifications and code aligned** for any material change (API, domain behavior, persistence, Trader-facing UX). Governance: `docs/governance.md` Principle VI (Spec–code parity). Treat missing spec updates as a **blocking** defect.

### Spec–code parity (blocking)

If the change affects HTTP contract, DTOs/OpenAPI, domain rules, persistence/Flyway, queue/list/detail UX, acceptance criteria, user-facing language, or documented data model — update the matching artifacts **in the same delivery**.

| If you changed… | Update at least… |
|-----------------|------------------|
| REST paths, payloads, responses, errors | `contracts/<feature>/openapi.yaml`, regenerate codegen; `contracts/<feature>/api-v1.md` |
| Async/event messages | `contracts/<feature>/asyncapi.yaml` + JSON schemas |
| Domain rules, lifecycle, behavior, AC, edge cases | matching `openspec/specs/<capability>/spec.md` |
| Architecture, tech stack, boundaries | `openspec/config.yaml`, `docs/governance.md` |

Do not mark work complete if code changed but the matching `openspec/specs/*/spec.md` or `contracts/*` still describe old behavior.

### Stack and contract context

Canonical contracts live under `contracts/<feature>/` (e.g. `contracts/002-trader-orders-views/openapi.yaml`). REST follows **contract-first OpenAPI** (`docs/governance.md` Principle I). OpenSpec project context: `openspec/config.yaml`.

## Agent navigation

Before exploring implementation for a non-trivial task:

1. [CONTEXT.md](CONTEXT.md) — domain glossary
2. [docs/agents/codebase-map.md](docs/agents/codebase-map.md) — module, route, and contract locations
3. Relevant `openspec/specs/<capability>/spec.md` + `contracts/<feature>/openapi.yaml`

Search order: **domain → application → adapters**. Contract-first for HTTP.

## Test-driven development (TDD)

For **new behavior** and **bug fixes**: failing test first (red), smallest change to pass (green), refactor. Backend: JUnit 5; frontend: Angular/Vitest; e2e: Cypress where appropriate.

Use skill `tdd` when the user asks for TDD or red-green-refactor.

## OpenSpec (OPSX)

Commands live under `.claude/commands/opsx/` (e.g. `/opsx:propose`, `/opsx:apply`, `/opsx:archive`). Matching skills: `openspec-*` under `.claude/skills/`.

CLI: `openspec validate`, `openspec archive <change>`, `openspec status --json`.

## LiteLLM proxy

Claude Code routes API calls through a local LiteLLM proxy (`ANTHROPIC_BASE_URL=http://localhost:4000`). Start it before a session:

```bash
~/.litellm/start-proxy.sh --port 4000
```

Proxy is **stateless** (no Postgres). Use the OpenRouter dashboard for usage and billing.

## Domain and issues

- Domain: `CONTEXT.md`, `docs/adr/` — see `docs/agents/domain.md`
- Issues: `.scratch/` — see `docs/agents/issue-tracker.md`
- Triage labels: `docs/agents/triage-labels.md`
