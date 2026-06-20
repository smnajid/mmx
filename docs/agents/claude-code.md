# Claude Code setup (mmx)

This repo was migrated from **Cursor** (`cursor-agent` + `.cursor/skills`) to **Claude Code** (`.claude/skills` and `.claude/commands`).

## What lives where

| Tool | Claude paths | Legacy Cursor paths (optional) |
|------|----------------|--------------------------------|
| **OpenSpec** | `.claude/skills/openspec-*`, `.claude/commands/opsx/` | `.cursor/skills/openspec-*`, `.cursor/commands/opsx-*` |
| **Project rules** | `CLAUDE.md` | `.cursor/rules/*.mdc` |
| **Specs & contracts** | `openspec/`, `contracts/` | unchanged |

## Daily commands

**OpenSpec** (capability specs under `openspec/specs/`, changes under `openspec/changes/`):

1. `/opsx:propose "…"` or `/opsx:new` + `/opsx:continue`
2. `/opsx:apply` — implement from `tasks.md`
3. `/opsx:archive` — merge into `openspec/specs/`

Terminal: `openspec validate`, `openspec status --json`.

## Refresh tool files

```bash
openspec init --tools claude --force
```

## LiteLLM proxy (Claude Code → OpenRouter)

Claude Code is configured with `ANTHROPIC_BASE_URL=http://localhost:4000` (see `~/.claude/settings.json`). Start the proxy **before** `claude`:

```bash
~/.litellm/start-proxy.sh --port 4000
```

The proxy runs **without a database** (fast startup). Track spend on the [OpenRouter dashboard](https://openrouter.ai/activity), not LiteLLM UI.

## Cursor coexistence

You can keep `.cursor/` for teammates still on Cursor. Prefer **one** primary agent per branch to avoid editing the wrong skill copy. `AGENTS.md` applies to any agent; `CLAUDE.md` is the Claude Code entry point.
