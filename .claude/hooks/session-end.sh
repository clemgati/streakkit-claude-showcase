#!/usr/bin/env bash
#
# SessionEnd hook — records that the session finished and leaves the tree in a known state.
#
# Housekeeping only: no notifications (the session is over, nobody is watching) and no commits
# (committing on the user's behalf without being asked is exactly the kind of surprise a sample
# repo should not teach).
set -uo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$PROJECT_DIR" || exit 0

payload="$(cat)"
mkdir -p .claude/logs

reason="unknown"
if command -v python3 >/dev/null 2>&1; then
  reason="$(printf '%s' "$payload" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("reason","unknown"))' 2>/dev/null || echo unknown)"
fi

printf '%s\tended=%s\tdirty=%s\n' \
  "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
  "$reason" \
  "$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')" \
  >> .claude/logs/sessions.log

exit 0
