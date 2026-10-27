#!/usr/bin/env bash
#
# SubagentStop hook — appends one line per finished subagent to .claude/logs/subagents.log.
#
# Subagents work in their own context, so from the main session you see only their final answer.
# When a parity audit or a review says something surprising, this log is how you find out which
# agent said it and when, without re-running the audit.
set -uo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
payload="$(cat)"
command -v python3 >/dev/null 2>&1 || exit 0

mkdir -p "$PROJECT_DIR/.claude/logs"

PAYLOAD="$payload" LOGFILE="$PROJECT_DIR/.claude/logs/subagents.log" python3 - <<'PY'
import json, os, datetime

try:
    event = json.loads(os.environ["PAYLOAD"])
except Exception:
    raise SystemExit(0)

stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
agent = event.get("agent_type") or "unknown"
agent_id = (event.get("agent_id") or "")[:8]
session = (event.get("session_id") or "")[:8]

with open(os.environ["LOGFILE"], "a", encoding="utf-8") as handle:
    handle.write(f"{stamp}\tsession={session}\tagent={agent}\tid={agent_id}\n")
PY
