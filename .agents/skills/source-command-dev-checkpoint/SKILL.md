---
name: "source-command-dev-checkpoint"
description: "Write a handoff note capturing where this session got to, so the next context window (or the next person) does not have to re-derive it."
---

# source-command-dev-checkpoint

Use this skill when the user asks to run the migrated source command `dev-checkpoint`.

## Command Template

## Current state

```!
git status --short --branch
```

```!
git log --oneline -5
```

```!
git diff --stat | tail -15
```

## Your task

Write `handoff.md` — overwriting whatever is there — with exactly these sections and
nothing else:

1. **Goal** — what this session is trying to achieve, in one sentence.
2. **Done** — changes that are complete *and* have passed their gate. Name the gate.
3. **In flight** — what is half-finished, with the specific file and the specific next edit.
4. **Decided** — choices made and the reason, especially ones that look arbitrary from the diff
   alone. This is the section that is expensive to reconstruct and cheap to write now.
5. **Open questions** — anything you were about to ask the user.

Rules that make this worth doing:

- Be concrete. "Fixed the streak bug" is worthless; "`currentStreak` now anchors on yesterday
  when today is unchecked — `StreakCalculatorTest` covers it" is not.
- Do not restate what `git diff` already shows. The diff survives compaction; your reasoning
  does not. Write down the *why*.
- Leave out anything already in `AGENTS.md` or the docs.

Then tell the user the note is written and that they can now run `/compact` safely, or `/clear`
and start fresh with `@handoff.md`.

Write it to `handoff.md` at the repository root, **not** under `.Codex/`. Files under `.Codex/`
are a protected path: Codex prompts before writing one even when an explicit
`Edit(/.Codex/handoff.md)` allow rule exists, so a headless run can never save it.

### Why this exists

`.Codex/hooks/pre-compact.sh` writes a mechanical version of this file automatically — git state
and build gates — because compaction can happen without warning. This command writes the half a
hook cannot: intent and rationale. Run it before you walk away from a long session, not after.
