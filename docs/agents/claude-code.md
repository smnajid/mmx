# Claude Code setup (mmx)

This repo was migrated from **Cursor** (`cursor-agent` + `.cursor/skills`) to **Claude Code** (`.claude/skills` and `.claude/commands`).

## What lives where

| Tool | Claude paths | Legacy Cursor paths (optional) |
|------|----------------|--------------------------------|
| **OpenSpec** | `.claude/skills/openspec-*`, `.claude/commands/opsx/` | `.cursor/skills/openspec-*`, `.cursor/commands/opsx-*` |
| **Spec Kit** | `.claude/skills/speckit-*`, `speckit-git-*` | `.cursor/skills/…` (same names) |
| **Project rules** | `CLAUDE.md` | `.cursor/rules/*.mdc` |
| **Shared SDD** | `.specify/`, `specs/`, `openspec/` | unchanged |

## Daily commands

**Speckit** (feature specs under `specs/NNN-…/`):

1. `/speckit-specify` — new feature (creates branch via git hook)
2. `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`

**OpenSpec** (cross-cutting deltas under `openspec/changes/`):

1. `/opsx:propose "…"` or `/opsx:new` + `/opsx:continue`
2. `/opsx:apply` — implement from `tasks.md`
3. `/opsx:archive` — merge into `openspec/specs/`

Terminal: `openspec validate`, `openspec status --json`.

## Refresh tool files

```bash
# OpenSpec only (Claude)
openspec init --tools claude --force

# Spec Kit: when GitHub releases spec-kit-template-claude-sh again
specify init --here --ai claude --force --no-git
```

Until that template exists, Speckit skills in `.claude/skills/speckit-*` are maintained as copies of the Cursor versions with Claude frontmatter (`user-invocable`, `argument-hint`, hook dot→hyphen note).

## Re-run migration script

From repo root, re-copy Speckit and project skills from `.cursor/skills` into `.claude/skills`:

```bash
python3 scripts/migrate-cursor-skills-to-claude.py
```

## Cursor coexistence

You can keep `.cursor/` for teammates still on Cursor. Prefer **one** primary agent per branch to avoid editing the wrong skill copy. `AGENTS.md` applies to any agent; `CLAUDE.md` is the Claude Code entry point.
