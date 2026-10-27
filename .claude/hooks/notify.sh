#!/usr/bin/env bash
#
# Notification hook — raises a macOS banner when Claude Code wants attention (a permission
# prompt, or an idle session waiting on input).
#
# Worth wiring up on a KMP repo specifically: Gradle syncs and Xcode builds are long enough that
# you will tab away, and a permission prompt that nobody answers stalls the whole run.
#
# Two delivery paths are shown:
#   - an OS banner via terminal-notifier or osascript
#   - `terminalSequence`, which Claude Code writes to the terminal itself; tmux/iTerm2 turn that
#     into a bell or a window badge, and it needs no extra binary at all.
set -uo pipefail

payload="$(cat)"
command -v python3 >/dev/null 2>&1 || exit 0

message="$(printf '%s' "$payload" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("message","Claude Code needs your attention"))' 2>/dev/null)" || exit 0

if command -v terminal-notifier >/dev/null 2>&1; then
  terminal-notifier -title "Claude Code · StreakKit" -message "$message" -sound Ping >/dev/null 2>&1 || true
elif command -v osascript >/dev/null 2>&1; then
  # Build the AppleScript with JSON escaping so quotes in the message cannot break it.
  MSG="$message" python3 - <<'PY' | xargs -0 osascript -e 2>/dev/null || true
import json, os
msg = json.dumps(os.environ["MSG"])
print(f'display notification {msg} with title "Claude Code · StreakKit" sound name "Ping"', end="\0")
PY
fi

# Also emit an OSC 777 desktop-notification sequence for terminals that support it.
MSG="$message" python3 - <<'PY'
import json, os
print(json.dumps({
    "terminalSequence": f"\033]777;notify;StreakKit;{os.environ['MSG']}\007",
    "suppressOutput": True,
}))
PY
