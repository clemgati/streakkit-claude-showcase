#!/usr/bin/env bash
#
# SessionStart hook — injects the state of the toolchain into the session's opening context.
#
# Why: the single most common waste in a KMP session is Claude assuming a tool exists (xcodegen,
# ktlint, an emulator) and discovering otherwise three tool calls later. Telling it up front costs
# ~100 tokens and saves the round trips.
#
# Input  (stdin): {"session_id":..,"source":"startup|resume|clear|compact|fork",...}
# Output (stdout): {"hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"..."}}
set -uo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$PROJECT_DIR" || exit 0

have() { command -v "$1" >/dev/null 2>&1 && echo "yes" || echo "MISSING"; }

# Capture first, then substitute a fallback for an EMPTY result.
#
# Do not write `cmd | head -1 || echo MISSING` here: `head` closes the pipe early, the producer
# dies of SIGPIPE, the pipeline reports failure, and the fallback is appended *after* the real
# value — so the line reads "Xcode 26.6\nMISSING".
first_line_or() {
  local fallback="$1"; shift
  local value
  value="$("$@" 2>/dev/null | head -1)"
  [ -n "$value" ] && printf '%s' "$value" || printf '%s' "$fallback"
}

branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo 'not a git repo')"
dirty="$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
jdk="$(java -version 2>&1 | head -1)"
xcode="$(first_line_or 'MISSING' xcodebuild -version)"
simulators="$(xcrun simctl list devices booted 2>/dev/null | grep -c Booted || true)"
emulators="$(adb devices 2>/dev/null | grep -c 'device$' || true)"

read -r -d '' CONTEXT <<EOF || true
## StreakKit environment (injected by .claude/hooks/session-start.sh)

- git: branch \`${branch}\`, ${dirty} uncommitted file(s)
- JDK: ${jdk:-MISSING}
- Xcode: ${xcode}
- Booted iOS simulators: ${simulators} · attached Android devices: ${emulators}
- ktlint: $(have ktlint) · swiftformat: $(have swiftformat) · xcodegen: $(have xcodegen) · gh: $(have gh)

Build gates: \`./gradlew :shared:allTests\`, \`./gradlew :androidApp:assembleDebug\`,
and \`/ios:build\`. Prefer the \`/kmp:*\` commands over raw gradle so hooks and summaries fire.
EOF

# A hook that cannot produce JSON must not break the session, so fall back to a bare exit.
command -v python3 >/dev/null 2>&1 || exit 0

CONTEXT="$CONTEXT" python3 - <<'PY'
import json, os
print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "SessionStart",
        "additionalContext": os.environ["CONTEXT"],
    }
}))
PY
