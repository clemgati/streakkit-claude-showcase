---
description: Draft release notes for the commits since the last tag, written for users rather than for the git log.
argument-hint: "[since-ref, default: last tag]"
allowed-tools: Bash(git log*), Bash(git describe*), Bash(git diff*), Read
---

## Range

```!
SINCE="${1:-$(git describe --tags --abbrev=0 2>/dev/null || git rev-list --max-parents=0 HEAD)}"
echo "since: $SINCE"
git log --no-merges --pretty=format:'%h %s' "$SINCE"..HEAD | head -60
```

## Files touched

```!
SINCE="${1:-$(git describe --tags --abbrev=0 2>/dev/null || git rev-list --max-parents=0 HEAD)}"
git diff --stat "$SINCE"..HEAD | tail -20
```

## Your task

Write release notes in Markdown with three sections — **Added**, **Fixed**, **Internal** — and
nothing else. Then stop; do not create a tag, a release, or a commit.

What separates usable notes from a reformatted git log:

- **Write the user-visible effect, not the change.** "Streaks no longer reset when you check in
  after midnight" beats "fix off-by-one in `currentStreak`".
- **Say which platform**, if a change landed on only one. In this repo that is itself a signal
  worth flagging: shared-module changes should affect both, and one that does not may be a
  parity bug rather than a deliberate scope.
- **Collapse the noise.** Five commits fixing one bug are one line. Formatting, dependency bumps
  and CI changes go under Internal or are dropped.
- **Omit empty sections** rather than writing "none".

If the range contains nothing user-visible, say so in one line instead of padding it.
