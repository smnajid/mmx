# Global agent skills — curation backlog

**Purpose:** Working document to build a personal, tool-agnostic skill set (Cursor, Claude Code, etc.) installable via [skills.sh](https://skills.sh) and `npx skills`.

**Status key:** `[ ]` candidate · `[x]` installed globally · `[-]` skipped · `[~]` duplicate of project skill

**Last researched:** 2026-07-03 · Refresh with `npx skills find <query>` and the [leaderboard](https://skills.sh).

---

## How to use this doc

1. Pick a category below.
2. Try the skill on a real task before marking `[x]`.
3. Install globally: `npx skills add <owner/repo@skill> -g -y`
4. Check updates periodically: `npx skills check` / `npx skills update`
5. Prefer skills with **1K+ installs** and known sources (`mattpocock`, `obra/superpowers`, `anthropics`, `vercel-labs`, `github/awesome-copilot`, `wshobson`).

**Do not duplicate** skills already shipped in mmx — see [skills-curation-mmx.md](./skills-curation-mmx.md).

---

## Tier 1 — Workflow & quality (high signal, any stack)

| Status | Skill | Installs | Source | Install |
|--------|-------|----------|--------|---------|
| [ ] | `to-prd` | ~323K | mattpocock/skills | `npx skills add mattpocock/skills@to-prd -g -y` |
| [ ] | `to-issues` | ~310K | mattpocock/skills | `npx skills add mattpocock/skills@to-issues -g -y` |
| [ ] | `triage` | ~283K | mattpocock/skills | `npx skills add mattpocock/skills@triage -g -y` |
| [ ] | `brainstorming` | ~256K | obra/superpowers | `npx skills add obra/superpowers@brainstorming -g -y` |
| [ ] | `writing-plans` | ~168K | obra/superpowers | `npx skills add obra/superpowers@writing-plans -g -y` |
| [ ] | `executing-plans` | ~139K | obra/superpowers | `npx skills add obra/superpowers@executing-plans -g -y` |
| [ ] | `test-driven-development` | ~150K | obra/superpowers | `npx skills add obra/superpowers@test-driven-development -g -y` |
| [ ] | `verification-before-completion` | ~130K | obra/superpowers | `npx skills add obra/superpowers@verification-before-completion -g -y` |
| [ ] | `systematic-debugging` | ~170K | obra/superpowers | `npx skills add obra/superpowers@systematic-debugging -g -y` |
| [ ] | `requesting-code-review` | ~152K | obra/superpowers | `npx skills add obra/superpowers@requesting-code-review -g -y` |
| [ ] | `receiving-code-review` | ~125K | obra/superpowers | `npx skills add obra/superpowers@receiving-code-review -g -y` |
| [ ] | `review-pr` | ~10K | warpdotdev/common-skills | `npx skills add warpdotdev/common-skills@review-pr -g -y` |
| [ ] | `find-skills` | ~2.3M | vercel-labs/skills | `npx skills add vercel-labs/skills@find-skills -g -y` |

**Notes**

- Superpowers skills overlap with Cursor plugin cache; install globally only if you also use Claude Code CLI without that plugin.
- `to-prd` → `to-issues` pairs well with spec-driven repos before opening a formal change.

---

## Tier 2 — Spec-driven & domain language

| Status | Skill | Installs | Source | Install |
|--------|-------|----------|--------|---------|
| [ ] | `ubiquitous-language` | ~94K | mattpocock/skills | `npx skills add mattpocock/skills@ubiquitous-language -g -y` |
| [ ] | `spec-driven-implementation` | ~10K | warpdotdev/common-skills | `npx skills add warpdotdev/common-skills@spec-driven-implementation -g -y` |
| [ ] | `spec-driven-development` | ~8.3K | addyosmani/agent-skills | `npx skills add addyosmani/agent-skills@spec-driven-development -g -y` |
| [ ] | `grill-me` | ~442K | mattpocock/skills | `npx skills add mattpocock/skills@grill-me -g -y` |

**Notes**

- Generic SDD skills are weaker than mmx's in-repo OpenSpec skills — use globally for non-mmx projects only.
- `ubiquitous-language` helps DDD glossary work (`CONTEXT.md`-style docs).

---

## Tier 3 — Architecture & code quality

| Status | Skill | Installs | Source | Install |
|--------|-------|----------|--------|---------|
| [ ] | `improve-codebase-architecture` | ~363K | mattpocock/skills | `npx skills add mattpocock/skills@improve-codebase-architecture -g -y` |
| [ ] | `hexagonal-architecture` | ~4.1K | affaan-m/everything-claude-code | `npx skills add affaan-m/everything-claude-code@hexagonal-architecture -g -y` |
| [ ] | `request-refactor-plan` | ~97K | mattpocock/skills | `npx skills add mattpocock/skills@request-refactor-plan -g -y` |
| [ ] | `qa` | ~93K | mattpocock/skills | `npx skills add mattpocock/skills@qa -g -y` |

---

## Tier 4 — Frontend (general)

| Status | Skill | Installs | Source | Install |
|--------|-------|----------|--------|---------|
| [ ] | `frontend-design` | ~619K | anthropics/skills | `npx skills add anthropics/skills@frontend-design -g -y` |
| [ ] | `web-design-guidelines` | ~434K | vercel-labs/agent-skills | `npx skills add vercel-labs/agent-skills@web-design-guidelines -g -y` |
| [ ] | `webapp-testing` | ~108K | anthropics/skills | `npx skills add anthropics/skills@webapp-testing -g -y` |
| [ ] | `vitest` | ~1.2K | jezweb/claude-skills | `npx skills add jezweb/claude-skills@vitest -g -y` |

---

## Tier 5 — Backend & contracts (general)

| Status | Skill | Installs | Source | Install |
|--------|-------|----------|--------|---------|
| [ ] | `java-springboot` | ~17K | github/awesome-copilot | `npx skills add github/awesome-copilot@java-springboot -g -y` |
| [ ] | `openapi-spec-generation` | ~13K | wshobson/agents | `npx skills add wshobson/agents@openapi-spec-generation -g -y` |
| [ ] | `openapi-to-typescript` | ~3.7K | softaworks/agent-toolkit | `npx skills add softaworks/agent-toolkit@openapi-to-typescript -g -y` |
| [ ] | `openapi-to-application-code` | ~8.9K | github/awesome-copilot | `npx skills add github/awesome-copilot@openapi-to-application-code -g -y` |
| [ ] | `flyway-migrations` | ~114 | ashchupliak/dream-team | `npx skills add ashchupliak/dream-team@flyway-migrations -g -y` |

---

## Tier 6 — DevOps & CI

| Status | Skill | Installs | Source | Install |
|--------|-------|----------|--------|---------|
| [ ] | `github-actions-docs` | ~265K | xixu-me/skills | `npx skills add xixu-me/skills@github-actions-docs -g -y` |
| [ ] | `github-actions-templates` | ~12K | wshobson/agents | `npx skills add wshobson/agents@github-actions-templates -g -y` |
| [ ] | `docker-compose-orchestration` | ~1.9K | manutej/luxor-claude-marketplace | `npx skills add manutej/luxor-claude-marketplace@docker-compose-orchestration -g -y` |

Browse Azure bundle if needed: [microsoft/azure-skills](https://skills.sh/microsoft/azure-skills).

---

## Suggested minimal global bundle (start here)

Uncomment/mark as you install:

```bash
# Workflow
npx skills add mattpocock/skills@to-prd -g -y
npx skills add mattpocock/skills@to-issues -g -y
npx skills add obra/superpowers@verification-before-completion -g -y
npx skills add warpdotdev/common-skills@review-pr -g -y

# Discovery
npx skills add vercel-labs/skills@find-skills -g -y
```

---

## Curation log

| Date | Action | Notes |
|------|--------|-------|
| 2026-07-03 | Initial backlog from skills.sh research | See mmx-specific doc for project skills |
| | | |
| | | |

---

## References

- [skills.sh leaderboard](https://skills.sh)
- CLI: `npx skills find <query>`, `npx skills add`, `npx skills check`, `npx skills update`
- Author new skills: `npx skills init <name>`
