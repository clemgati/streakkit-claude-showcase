#!/usr/bin/env bash
#
# PreCompact hook — writes a handoff note to disk before the conversation is summarised.
#
# Compaction preserves meaning but not detail: exact file paths, the branch you were on, which
# gate last passed. Those are cheap to recover from the filesystem and expensive to recover from
# a summary, so this hook snapshots them to .claude/handoff.md, which survives compaction, /clear,
# and the end of the session.
#
# Pair it with `/dev:checkpoint`, which writes the same file on demand.
set -uo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$PROJECT_DIR" || exit 0

payload="$(cat)"
trigger="manual"
if command -v python3 >/dev/null 2>&1; then
  trigger="$(printf '%s' "$payload" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("trigger") or d.get("hook_event_name","compact"))' 2>/dev/null || echo compact)"
fi

OUT=".claude/handoff.md"
mkdir -p .claude

{
  echo "# Handoff note"
  echo
  echo "Written by \`.claude/hooks/pre-compact.sh\` at $(date -u '+%Y-%m-%d %H:%M UTC') (trigger: ${trigger})."
  echo "This file is regenerated; do not hand-edit it."
  echo
  echo "## Git"
  echo '```'
  git status --short --branch 2>/dev/null | head -40
  echo '```'
  echo
  echo "### Recent commits"
  echo '```'
  git log --oneline -10 2>/dev/null
  echo '```'
  echo
  echo "### Uncommitted diff stat"
  echo '```'
  git diff --stat 2>/dev/null | tail -20
  echo '```'
  echo
  echo "## Build gates"
  echo
  echo "- Shared tests: \`./gradlew :shared:allTests\`"
  echo "- Android: \`./gradlew :androidApp:assembleDebug\`"
  echo "- iOS: \`/ios:build\`"
  echo
  echo "Re-run the gates before trusting anything above; this note records state, not success."
} > "$OUT" 2>/dev/null || exit 0

command -v python3 >/dev/null 2>&1 || exit 0
python3 -c 'import json; print(json.dumps({"systemMessage": "Context handoff saved to .claude/handoff.md — read it after compaction instead of re-exploring the tree."}))'
