#!/usr/bin/env bash
#
# PostToolUse(Edit|Write) hook — formats the file that was just written, in place.
#
# Running the formatter here rather than at commit time means Claude never sees a diff between
# what it wrote and what is on disk, so it does not spend a turn "fixing" its own indentation.
#
# Every formatter is optional: if the binary is missing the hook exits quietly, so a fresh clone
# on a machine without ktlint/swiftformat still works. That is the rule for every hook in this
# repo — see docs/02-claude-code-feature-map.md.
#
# Input (stdin): {"tool_name":"Edit"|"Write","tool_input":{"file_path":"..."},...}
set -uo pipefail

payload="$(cat)"
command -v python3 >/dev/null 2>&1 || exit 0

file="$(printf '%s' "$payload" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input",{}).get("file_path",""))' 2>/dev/null)" || exit 0
[ -n "$file" ] && [ -f "$file" ] || exit 0

note() {
  MSG="$1" python3 -c 'import json,os; print(json.dumps({"systemMessage": os.environ["MSG"], "suppressOutput": True}))'
}

case "$file" in
  *.kt|*.kts)
    if command -v ktlint >/dev/null 2>&1; then
      ktlint --format --log-level=error "$file" >/dev/null 2>&1 || true
    else
      note "ktlint not installed — skipped formatting $(basename "$file"). brew install ktlint"
    fi
    ;;
  *.swift)
    if command -v swiftformat >/dev/null 2>&1; then
      swiftformat --quiet "$file" >/dev/null 2>&1 || true
    else
      note "swiftformat not installed — skipped formatting $(basename "$file"). brew install swiftformat"
    fi
    ;;
esac

exit 0
