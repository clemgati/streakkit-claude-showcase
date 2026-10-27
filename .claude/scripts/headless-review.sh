#!/usr/bin/env bash
#
# Headless review of the staged diff. No session, no terminal UI — just `claude -p` with a
# structured answer, suitable for a git hook, a Makefile target, or a CI step.
#
#   .claude/scripts/headless-review.sh            # review staged changes
#   .claude/scripts/headless-review.sh HEAD~3     # review a range instead
#
# Exit code: 0 clean, 1 blocker found, 2 could not run. That makes it usable as a gate.
#
# Three things here are worth copying into your own repo:
#   --permission-mode plan   the run can read and reason but cannot edit anything. For a review
#                            that is not a restriction, it is a correctness guarantee.
#   --output-format json     a parseable envelope, so the script decides what to do, not the model
#   --max-turns              a hard ceiling on a job that runs unattended
set -uo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_DIR" || exit 2

command -v claude >/dev/null 2>&1 || { echo "claude CLI not found; skipping review." >&2; exit 2; }

if [ $# -gt 0 ]; then
  DIFF="$(git diff "$1"..HEAD)"
  LABEL="$1..HEAD"
else
  DIFF="$(git diff --cached)"
  LABEL="staged changes"
fi

if [ -z "$DIFF" ]; then
  echo "Nothing to review ($LABEL is empty)."
  exit 0
fi

read -r -d '' PROMPT <<'EOF' || true
Review this diff against the rules in CLAUDE.md. Report ONLY defects from this list:

1. Business logic in a Compose composable or a SwiftUI view — arithmetic, rounding, string
   formatting, or date handling. In this repo those belong in HabitsUiState.summarize() so both
   apps get the same value. This is a BLOCKER.
2. A change to one app's rendering of habit data without the matching change to the other.
   BLOCKER.
3. A new or changed `expect` declaration without both `actual`s. BLOCKER.
4. Shared logic changed with no test added or updated in shared/src/commonTest. WARNING.
5. A new persisted field on Habit with no default value — older stored JSON would stop decoding.
   BLOCKER.

Ignore formatting, naming, and idiomatic differences between Compose and SwiftUI.

Answer with ONE line per defect, in the form:
  BLOCKER <file>:<line> — <what breaks>
  WARNING <file>:<line> — <what breaks>
If there are none, answer with exactly: CLEAN
EOF

RESPONSE="$(
  printf '%s\n\n--- diff (%s) ---\n%s\n' "$PROMPT" "$LABEL" "$DIFF" \
  | claude -p \
      --permission-mode plan \
      --max-turns 6 \
      --output-format json \
      --model sonnet 2>/dev/null
)" || { echo "claude invocation failed; not blocking." >&2; exit 2; }

TEXT="$(printf '%s' "$RESPONSE" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("result",""))' 2>/dev/null)"

if [ -z "$TEXT" ]; then
  echo "No usable response; not blocking." >&2
  exit 2
fi

echo "── Review of $LABEL ────────────────────────────"
echo "$TEXT"
echo "────────────────────────────────────────────────"

if printf '%s' "$TEXT" | grep -q '^BLOCKER'; then
  echo "Blocking: fix the BLOCKER items above, or re-run with --no-verify to override." >&2
  exit 1
fi

exit 0
