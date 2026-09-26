# mmx agent skills — curation backlog

**Purpose:** Curate skills for this repo's stack and **Spec-Driven Development (SDD)** workflow: OpenSpec, contract-first OpenAPI, Java hexagonal backend, Angular 21 frontend, JUnit / Vitest / Cypress, Flyway, Docker.

**Companion doc:** [skills-curation-global.md](./skills-curation-global.md) — personal skills usable on any project.

**Status key:** `[x]` in repo · `[ ]` candidate to add · `[~]` install globally instead · `[-]` skip

**Last researched:** 2026-07-03

---

## mmx stack snapshot

| Layer | Technology | Agent touchpoints |
|-------|------------|-------------------|
| SDD | OpenSpec + `openspec/specs/` | `.claude/skills/openspec-*`, `/opsx:*` commands |
| Contracts | OpenAPI under `contracts/00N-*/` | Codegen in `mmx-adapter-in-rest` |
| Backend | Java / Maven hexagonal monolith | `backend/mmx-{domain,application,adapter-*}` |
| Frontend | Angular 21 trader SPA | `frontend/src/app/` |
| Tests | JUnit 5, Vitest, Cypress | ArchUnit, domain/application tests |
| Persistence | Flyway + Postgres | Migrations in backend adapters |
| Local ops | `docker-compose.yml`, `mmx-start.sh` | scripts/ |

Navigation: [codebase-map.md](./codebase-map.md) · [CONTEXT.md](../../CONTEXT.md) · [CLAUDE.md](../../CLAUDE.md)

---

## Already in repo (keep — do not replace with skills.sh copies)

These live under `.claude/skills/` (mirrored in `.cursor/skills/` where present). **Prefer these over external OpenSpec/SDD skills.**

| Status | Skill | Path | Role in mmx |
|--------|-------|------|-------------|
| [x] | OpenSpec workflow | `.claude/skills/openspec-*` | propose, apply, verify, archive, sync, explore, onboard |
| [x] | `tdd` | `.claude/skills/tdd/` | Red-green-refactor per AGENTS.md |
| [x] | `grill-with-docs` | `.claude/skills/grill-with-docs/` | Stress-test plans against CONTEXT.md / ADRs |
| [x] | `improve-codebase-architecture` | `.claude/skills/improve-codebase-architecture/` | Hexagonal boundaries, module map |
| [x] | `frontend-design` | `.claude/skills/frontend-design/` | Trader-facing UI quality |
| [x] | `zoom-out` | `.claude/skills/zoom-out/` | Big-picture review |
| [x] | `setup-matt-pocock-skills` | `.claude/skills/setup-matt-pocock-skills/` | Bootstrap related skills |

**External OpenSpec on skills.sh** ([forztf/open-skilled-sdd](https://skills.sh/forztf/open-skilled-sdd/openspec-implementation), ~1K installs): likely redundant with in-repo skills — `[-]` skip unless comparing workflows.

---

## Recommended additions — mmx-specific (project or global)

### A. SDD pipeline (before / alongside OpenSpec)

| Status | Skill | Installs | Where | Install | mmx use |
|--------|-------|----------|-------|---------|---------|
| [~] | `to-prd` | ~323K | global | `npx skills add mattpocock/skills@to-prd -g -y` | Shape intent before `/opsx:propose` |
| [ ] | `to-issues` | ~310K | global | `npx skills add mattpocock/skills@to-issues -g -y` | Break work into `.scratch/` issues |
| [-] | `triage` | ~283K | global | `npx skills add mattpocock/skills@triage -g -y` | **Skip** for default mmx flow — label vocabulary is already in [triage-labels.md](./triage-labels.md); `to-prd` applies `ready-for-agent` directly. Install only if GitHub/GitLab `.scratch/` backlog triage becomes a first-class intake step |
| [ ] | `ubiquitous-language` | ~94K | global | `npx skills add mattpocock/skills@ubiquitous-language -g -y` | Keep CONTEXT.md terms consistent |

**Suggested flow:** `to-prd` → `to-issues` → `/opsx:propose` → `/opsx:apply` → `/opsx:verify` → `/opsx:archive`

**Not in the core flow:** `triage` — optional hygiene for raw issue backlogs only. OpenSpec changes are the implementation queue; `setup-matt-pocock-skills` + `triage-labels.md` cover label strings for `to-prd` / `to-issues` without installing the skill.

---

### B. Contract-first OpenAPI

| Status | Skill | Installs | Where | Install | mmx use |
|--------|-------|----------|-------|---------|---------|
| [ ] | `openapi-spec-generation` | ~13K | project or global | `npx skills add wshobson/agents@openapi-spec-generation -g -y` | Edit `contracts/*/openapi.yaml` |
| [ ] | `openapi-to-application-code` | ~8.9K | project or global | `npx skills add github/awesome-copilot@openapi-to-application-code -g -y` | Adapter/codegen alignment |
| [ ] | `openapi-to-typescript` | ~3.7K | project or global | `npx skills add softaworks/agent-toolkit@openapi-to-typescript -g -y` | Angular client types from spec |

**Spec–code parity checklist** (manual until custom skill exists):

- [ ] `openapi.yaml` updated
- [ ] Matching `api-v1.md` prose
- [ ] `openspec/specs/<capability>/spec.md` delta if behaviour changed
- [ ] `mvn compile` regenerates adapter DTOs
- [ ] Tests cover new contract paths

---

### C. Backend — Java hexagonal

| Status | Skill | Installs | Where | Install | mmx use |
|--------|-------|----------|-------|---------|---------|
| [ ] | `java-springboot` | ~17K | global | `npx skills add github/awesome-copilot@java-springboot -g -y` | Spring REST, general patterns |
| [ ] | `hexagonal-architecture` | ~4.1K | global | `npx skills add affaan-m/everything-claude-code@hexagonal-architecture -g -y` | Layer rules |
| [ ] | `hexagonal-architecture-layers-java` | ~164 | project | `npx skills add gentleman-programming/gentleman-skills@hexagonal-architecture-layers-java -y` | Java-specific ports/adapters |
| [ ] | `flyway-migrations` | ~114 | global | `npx skills add ashchupliak/dream-team@flyway-migrations -g -y` | Schema migrations |
| [ ] | `java-maven-best-practices` | ~237 | global | `npx skills add jabrena/cursor-rules-java@110-java-maven-best-practices -g -y` | Multi-module Maven |

**Code touchpoints:** `ArchitectureRules`, `HexagonalArchitectureTest`, `mmx-domain` → `mmx-application` → adapters.

---

### D. Frontend — Angular 21

| Status | Skill | Installs | Where | Install | mmx use |
|--------|-------|----------|-------|---------|---------|
| [~] | `frontend-design` | — | **in repo** | — | Already covered |
| [ ] | `angular-signals` | ~7K | project or global | `npx skills add analogjs/angular-skills@angular-signals -g -y` | Modern Angular state |
| [ ] | `angular-component` | ~9.3K | project or global | `npx skills add analogjs/angular-skills@angular-component -g -y` | Desk feature components |
| [ ] | `angular-forms` | ~6K | project or global | `npx skills add analogjs/angular-skills@angular-forms -g -y` | Settings / upload forms |
| [ ] | `angular-routing` | ~5.9K | project or global | `npx skills add analogjs/angular-skills@angular-routing -g -y` | `app.routes.ts`, desk shell |
| [ ] | `angular-http` | ~5.6K | project or global | `npx skills add analogjs/angular-skills@angular-http -g -y` | REST integration |
| [ ] | `vitest` | ~1.2K | global | `npx skills add jezweb/claude-skills@vitest -g -y` | Component/service unit tests |

**Code touchpoints:** `features/order-details/`, term-rate settings UI, trader desk navigation spec.

---

### E. Testing & delivery quality

| Status | Skill | Installs | Where | Install | mmx use |
|--------|-------|----------|-------|---------|---------|
| [~] | `tdd` | — | **in repo** | — | Primary test-first skill |
| [ ] | `test-driven-development` | ~150K | global | `npx skills add obra/superpowers@test-driven-development -g -y` | Reinforce AGENTS.md default |
| [ ] | `verification-before-completion` | ~130K | global | `npx skills add obra/superpowers@verification-before-completion -g -y` | Run tests before "done" |
| [ ] | `webapp-testing` | ~108K | global | `npx skills add anthropics/skills@webapp-testing -g -y` | E2E / browser testing patterns |
| [ ] | `systematic-debugging` | ~170K | global | `npx skills add obra/superpowers@systematic-debugging -g -y` | Failures in CI or local stack |
| [ ] | `review-pr` | ~10K | global | `npx skills add warpdotdev/common-skills@review-pr -g -y` | PR review against specs |

**Low-install niche (evaluate before adopting):**

| Skill | Installs | Notes |
|-------|----------|-------|
| `cypress` (teachingai/full-stack-skills) | ~50 | mmx uses Cypress — only if skill content is good |
| `junit-parameterized` (thebushidocollective/han) | ~26 | Backend unit test patterns |

---

### F. DevOps (local + CI)

| Status | Skill | Installs | Where | Install | mmx use |
|--------|-------|----------|-------|---------|---------|
| [ ] | `docker-compose-orchestration` | ~1.9K | global | `npx skills add manutej/luxor-claude-marketplace@docker-compose-orchestration -g -y` | `docker-compose.yml` |
| [ ] | `github-actions-docs` | ~265K | global | `npx skills add xixu-me/skills@github-actions-docs -g -y` | CI pipelines |

---

## Suggested mmx starter bundle

Install after reviewing; project-local install omits `-g` and targets `.claude/skills/`:

```bash
# SDD pipeline (global)
npx skills add mattpocock/skills@to-prd -g -y
npx skills add mattpocock/skills@to-issues -g -y
npx skills add mattpocock/skills@ubiquitous-language -g -y

# Contracts + backend (global)
npx skills add wshobson/agents@openapi-spec-generation -g -y
npx skills add github/awesome-copilot@java-springboot -g -y
npx skills add affaan-m/everything-claude-code@hexagonal-architecture -g -y

# Frontend (global)
npx skills add analogjs/angular-skills@angular-signals -g -y
npx skills add jezweb/claude-skills@vitest -g -y

# Quality gate (global)
npx skills add obra/superpowers@verification-before-completion -g -y
npx skills add warpdotdev/common-skills@review-pr -g -y
```

---

## Custom skills to author (high value for mmx)

Nothing on skills.sh covers these well. Track as future `.claude/skills/` additions:

| Priority | Working name | Scope | Trigger phrases |
|----------|--------------|-------|-----------------|
| P1 | `mmx-openapi-workflow` | Edit contract → regen Java → update `api-v1.md` → run ArchUnit | "change the API", "update openapi", "codegen" |
| P1 | `mmx-spec-code-parity` | Same PR must update OpenSpec + contract + tests | "spec parity", "blocking defect" |
| P2 | `mmx-desk-feature-slice` | Route → Angular feature → REST → use case → Flyway | "add desk feature", "trader view" |
| P2 | `mmx-oncall-rates` | Domain: `OnCallRateSegment`, contract 002 | "on-call rate", "rate curve" |
| P3 | `mmx-term-rate-upload` | CSV upload flow, contract 005 | "term rate upload" |

Scaffold: `npx skills init mmx-openapi-workflow` then commit under `.claude/skills/`.

---

## Project vs global — decision guide

| Install to | When |
|------------|------|
| **Global** (`-g`) | Workflow, TDD, debugging, PR review, generic Java/Angular/OpenAPI |
| **Project** (mmx `.claude/skills/`) | References mmx paths, OpenSpec conventions, contract folders, ArchUnit rules |
| **Skip external** | Duplicates in-repo OpenSpec skills or mattpocock skills already copied |

After adding project skills, mention them in [claude-code.md](./claude-code.md) if they become part of standard workflow.

---

## Curation log

| Date | Action | Notes |
|------|--------|-------|
| 2026-07-03 | Initial backlog from skills.sh research | Paired with global curation doc |
| 2026-07-05 | Mark `triage` as skip for default SDD flow | OpenSpec is implementation queue; `triage-labels.md` covers vocabulary |
| 2026-07-05 | Mark `to-prd` as globally installed | `~/.agents/skills/to-prd` |

---

## Quick search commands

```bash
npx skills find openapi
npx skills find angular
npx skills find "spec driven"
npx skills find hexagonal
npx skills find flyway
npx skills find openspec
npx skills check
```
