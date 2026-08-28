## Spec-Driven Development (SDD)

Agents MUST keep **specifications and code aligned** for any material change (API, domain behavior, persistence, Trader-facing UX). Governance: `docs/governance.md` Principle VI (Spec–code parity). Treat missing spec updates as a **blocking** defect, not a follow-up.


## Test-driven development (TDD)

For **new behavior** and **bug fixes**, the default workflow is **strict TDD**: write a **failing** automated test first (red), implement the **smallest** change that makes it pass (green), then refactor without changing behavior (refactor). Only then extend specs/contracts if the change is material per SDD above.

**Stack**: prefer **JUnit 5** (backend), **Angular unit tests** (`ng test` / Vitest) for components and services, and **Cypress** where an end-to-end assertion locks acceptance. Choose the narrowest level that can fail for the wrong reason (unit vs integration vs e2e).

**Waivers**: skipping red-first TDD is allowed only when the user explicitly agrees or when the change is purely mechanical (e.g. rename, comment-only). If TDD was skipped, note it briefly in the PR or commit message.

Agents SHOULD read the `tdd` skill ([`.agents/skills/tdd/SKILL.md`](.agents/skills/tdd/SKILL.md)) when the user asks for TDD, red-green-refactor, or test-first delivery.

## Test feedback loop

Every backend test class carries **exactly one** JUnit 5 category tag — `fast`, `integration`, `e2e`, or `architecture` — enforced by the `TestCategoryTaggingTest` meta-test (fails the build on a missing/duplicated category). Pick the narrowest loop that can fail for the right reason; reserve the full reactor for final verification only.

| Category | Meaning | Where the tests live |
|----------|---------|----------------------|
| `fast` | Pure JVM: no Spring context, no containers, no broker (in-memory fakes allowed) | `mmx-domain`, `mmx-application`, pure adapter units |
| `integration` | Spring context + relational Testcontainer; HTTP/persistence-surface assertions | `mmx-bootstrap` `rest/`/`config/`/`migration`/`contract`, persistence integration tests |
| `e2e` | Full stack incl. Kafka broker / full workflow | `mmx-bootstrap` `e2e/*` |
| `architecture` | ArchUnit / taxonomy rule enforcement | `DomainArchitectureTest`, `HexagonalArchitectureTest`, `TestCategoryTaggingTest` |

**Selection is opt-in** (`mvn test` with no `-Dgroups` still runs everything):

```bash
cd backend && mvn test -Dgroups=fast                      # unit loop, no containers (all modules)
cd backend && mvn test -pl mmx-domain -Dtest=<Class>      # one domain test class
cd backend && mvn test -pl mmx-application -Dtest=<Class> # one application test class
cd backend && mvn test -pl mmx-adapter-in-rest -Dtest=<Class> # pure REST-controller unit
cd backend && mvn test -Dgroups='fast|integration'        # compose categories
cd backend && mvn test -Dgroups=integration               # includes PostgreSQL integration
cd backend && mvn test -Dgroups=e2e                       # Kafka / full-workflow acceptance
```

**Choose the loop by what changed** (inward-first, hexagonal):

- **`mmx-domain` change** → `mvn test -pl mmx-domain -Dtest=<Class>` (fast; frequently `-Dtest=DomainArchitectureTest`).
- **`mmx-application` change** → `mvn test -pl mmx-application -Dtest=<Class>` (fast; assert against in-memory `port/out` fakes).
- **REST contract change** → `mvn test -pl mmx-adapter-in-rest -Dtest=<ControllerTest>` plus the single matching `integration` class in `mmx-bootstrap` (`mvn test -pl mmx-bootstrap -am -Dtest=<Rest...IntegrationTest>`).
- **REST-controller/unit iteration (no contract change)** → `mvn test -pl mmx-adapter-in-rest -Dtest=<ControllerTest> -DskipOpenApiGenerate=true` — skips the 12 OpenAPI-generator executions so the loop is fast; safe because the generated sources persist in `target/generated-sources/`.
- **Persistence change** → `mvn test -pl mmx-adapter-out-persistence -am -Dtest='<Jpa...Test>'` (`integration`).
- **`e2e`** → on demand only: `mvn test -pl mmx-bootstrap -am -Dgroups=e2e`.
- **Final verification** → the **single** full-reactor run `cd backend && mvn test` (all categories). Do not run the full reactor mid-change; rely on the scoped `-Dtest`/`-Dgroups` loops above.

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

Agents MUST read **CONTEXT.md** and **codebase-map.md** before broad codebase exploration. These are the codebase map — do not re-explore the directory tree from scratch each session.
