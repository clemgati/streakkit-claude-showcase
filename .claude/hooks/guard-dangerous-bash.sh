#!/usr/bin/env bash
#
# PreToolUse(Bash) hook — refuses a short list of commands that destroy work irrecoverably.
#
# This is the *programmatic* half of the safety story. `permissions.deny` in settings.json is
# declarative and pattern-based; a hook can inspect the actual command text, explain itself, and
# suggest the safe alternative — which is what stops Claude from simply rephrasing and retrying.
#
# Note the division of labour: this hook does NOT try to be a general sandbox. It targets commands
# whose damage survives `git checkout .`, i.e. the ones a human could not undo either.
#
# Input  (stdin): {"tool_name":"Bash","tool_input":{"command":"..."},...}
# Output (stdout): PreToolUse permissionDecision JSON. Exit 0 always — a broken guard must not
#                  block the session; it fails open and says so.
set -uo pipefail

payload="$(cat)"
command -v python3 >/dev/null 2>&1 || exit 0

cmd="$(printf '%s' "$payload" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input",{}).get("command",""))' 2>/dev/null)" || exit 0
[ -n "$cmd" ] || exit 0

deny() {
  REASON="$1" python3 - <<'PY'
import json, os
print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "deny",
        "permissionDecisionReason": os.environ["REASON"],
    }
}))
PY
  exit 0
}

# Each pattern is an extended regex matched against the whole command string.
case_matches() { printf '%s' "$cmd" | grep -Eq "$1"; }

case_matches '\brm[[:space:]]+(-[[:alnum:]]+[[:space:]]+)*-?[rRf]*[[:space:]]*(/|~|\$HOME)([[:space:]]|$)' &&
  deny "Refusing a recursive delete rooted at /, ~ or \$HOME. If you meant a build directory, name it explicitly, e.g. 'rm -rf shared/build'."

case_matches '\bgit[[:space:]]+push\b.*(--force([^-]|$)|-f([[:space:]]|$))' &&
  deny "Refusing a force push. Use 'git push --force-with-lease' if you have verified the remote, and only after telling the user what will be overwritten."

case_matches '\bgit[[:space:]]+reset[[:space:]]+--hard' &&
  deny "Refusing 'git reset --hard' — it discards uncommitted work with no undo. Use 'git stash' first, or 'git restore <path>' for specific files."

case_matches '\bgit[[:space:]]+clean\b.*-[a-z]*[fd][a-z]*x' &&
  deny "Refusing 'git clean -fdx' — it deletes ignored files including local.properties and build caches you cannot regenerate without re-downloading. Delete specific paths instead."

case_matches 'rm[[:space:]]+-rf[[:space:]]+.*\.(konan|gradle)([[:space:]]|/|$)' &&
  deny "Refusing to delete the Kotlin/Native or Gradle home cache. That is a multi-GB re-download. Use './gradlew clean' or '--rerun-tasks' instead."

case_matches 'simctl[[:space:]]+erase[[:space:]]+all' &&
  deny "Refusing 'simctl erase all' — it wipes every simulator on this machine, not just StreakKit's. Erase one device by UDID instead."

case_matches 'sdkmanager\b.*--uninstall' &&
  deny "Refusing to uninstall Android SDK components; other projects on this machine depend on them."

# Nothing matched: stay silent and let the normal permission flow decide.
exit 0
