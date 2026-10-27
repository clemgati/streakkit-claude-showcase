#!/usr/bin/env bash
#
# Stop hook — a one-line "what changed" summary when Claude finishes a turn.
#
# Deliberately *not* a quality gate. A Stop hook can block (exit 2) to force another turn, and it
# is tempting to run the test suite here — don't. On a KMP repo the gates take minutes, and a Stop
# hook that blocks turns the session into a build loop. Run tests from `/kmp:test`, where the user
# chose to wait.
set -uo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$PROJECT_DIR" || exit 0

cat >/dev/null  # drain stdin; this hook does not need the payload

changed="$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
[ "$changed" = "0" ] && exit 0

modules="$(git status --porcelain 2>/dev/null | awk '{print $NF}' | cut -d/ -f1 | sort -u | tr '\n' ' ')"

command -v python3 >/dev/null 2>&1 || exit 0
CHANGED="$changed" MODULES="$modules" python3 - <<'PY'
import json, os
print(json.dumps({
    "systemMessage": f"{os.environ['CHANGED']} uncommitted file(s) in: {os.environ['MODULES'].strip()}",
    "suppressOutput": True,
}))
PY
