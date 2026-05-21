#!/usr/bin/env python3
"""Copy Speckit and project skills from .cursor/skills to .claude/skills with Claude frontmatter."""

from __future__ import annotations

import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / ".cursor" / "skills"
DST = ROOT / ".claude" / "skills"

ARGUMENT_HINTS = {
    "specify": "Describe the feature you want to specify",
    "plan": "Optional guidance for the planning phase",
    "tasks": "Optional task generation constraints",
    "implement": "Optional implementation guidance or task filter",
    "analyze": "Optional focus areas for analysis",
    "clarify": "Optional areas to clarify in the spec",
    "constitution": "Principles or values for the project constitution",
    "checklist": "Domain or focus area for the checklist",
    "taskstoissues": "Optional filter or label for GitHub issues",
}

HOOK_NOTE = (
    "  - When constructing slash commands from hook command names, replace dots (`.`) with hyphens (`-`). "
    "For example, `speckit.git.commit` → `/speckit-git-commit`.\n"
)

OTHER_SKILLS = frozenset(
    {
        "tdd",
        "frontend-design",
        "grill-with-docs",
        "improve-codebase-architecture",
        "zoom-out",
        "setup-matt-pocock-skills",
    }
)


def inject_fm_flag(content: str, key: str, value: str = "true") -> str:
    parts = content.split("---", 2)
    if len(parts) >= 2 and re.search(rf"(?m)^{re.escape(key)}:", parts[1]):
        return content
    lines = content.splitlines(keepends=True)
    out: list[str] = []
    dash = 0
    injected = False
    for line in lines:
        s = line.rstrip("\n\r")
        if s == "---":
            dash += 1
            if dash == 2 and not injected:
                eol = "\r\n" if line.endswith("\r\n") else ("\n" if line.endswith("\n") else "")
                out.append(f"{key}: {value}{eol}")
                injected = True
            out.append(line)
            continue
        out.append(line)
    return "".join(out)


def inject_argument_hint(content: str, hint: str) -> str:
    if "argument-hint:" in content:
        return content
    lines = content.splitlines(keepends=True)
    out: list[str] = []
    dash = 0
    injected = False
    for line in lines:
        s = line.rstrip("\n\r")
        if s == "---":
            dash += 1
            out.append(line)
            continue
        if dash == 1 and not injected and s.startswith("description:"):
            out.append(line)
            eol = "\r\n" if line.endswith("\r\n") else ("\n" if line.endswith("\n") else "")
            escaped = hint.replace("\\", "\\\\").replace('"', '\\"')
            out.append(f'argument-hint: "{escaped}"{eol}')
            injected = True
            continue
        out.append(line)
    return "".join(out)


def inject_hook_note(content: str) -> str:
    if "replace dots" in content:
        return content

    def repl(m: re.Match[str]) -> str:
        return m.group(1) + HOOK_NOTE.rstrip("\n") + m.group(3) + m.group(1) + m.group(2) + m.group(3)

    return re.sub(
        r"(?m)^(\s*)(- For each executable hook, output the following[^\r\n]*)(\r\n|\n|$)",
        repl,
        content,
    )


def should_copy(name: str) -> bool:
    return name.startswith("speckit-") or name in OTHER_SKILLS


def migrate() -> list[str]:
    if not SRC.is_dir():
        print(f"Missing {SRC}", file=sys.stderr)
        sys.exit(1)
    DST.mkdir(parents=True, exist_ok=True)
    copied: list[str] = []
    for skill_dir in sorted(SRC.iterdir()):
        if not skill_dir.is_dir() or not should_copy(skill_dir.name):
            continue
        name = skill_dir.name
        dest = DST / name
        if dest.exists():
            shutil.rmtree(dest)
        shutil.copytree(skill_dir, dest)
        skill_md = dest / "SKILL.md"
        if skill_md.exists() and name.startswith("speckit-"):
            text = skill_md.read_text(encoding="utf-8")
            text = inject_fm_flag(text, "user-invocable")
            text = inject_fm_flag(text, "disable-model-invocation", "false")
            text = inject_hook_note(text)
            stem = name[len("speckit-") :]
            if stem in ARGUMENT_HINTS:
                text = inject_argument_hint(text, ARGUMENT_HINTS[stem])
            skill_md.write_text(text, encoding="utf-8")
        copied.append(name)
    return copied


if __name__ == "__main__":
    names = migrate()
    print("Copied:", ", ".join(names) if names else "(none)")
