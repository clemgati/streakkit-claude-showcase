#!/usr/bin/env bash
#
# PreToolUse(Edit|Write) hook — blocks two different mistakes:
#
#   1. Editing a file that is machine-local or a credential (local.properties, keystores, .env).
#   2. Writing something that *looks* like a secret into a tracked source file.
#
# (1) overlaps with `permissions.deny` in settings.json, deliberately: deny rules are the policy,
# this hook is the explanation. (2) is the part rules cannot do — it inspects the content being
# written, which is the only place a leaked key is visible before it reaches git.
#
# Input (stdin): {"tool_name":"Edit"|"Write","tool_input":{"file_path":..,"content"|"new_string":..}}
set -uo pipefail

payload="$(cat)"
command -v python3 >/dev/null 2>&1 || exit 0

# The payload travels in an env var, not stdin: stdin is already taken by the heredoc below.
PAYLOAD="$payload" python3 - <<'PY'
import json, os, re, sys

try:
    event = json.loads(os.environ["PAYLOAD"])
except Exception:
    sys.exit(0)  # fail open: a malformed payload must never block a session

tool_input = event.get("tool_input", {}) or {}
path = tool_input.get("file_path") or ""
# Write uses `content`; Edit uses `new_string`.
body = tool_input.get("content") or tool_input.get("new_string") or ""

PROTECTED_NAMES = {
    "local.properties",          # absolute SDK path; machine-specific and gitignored
    "google-services.json",
    "GoogleService-Info.plist",
}
PROTECTED_SUFFIXES = (".keystore", ".jks", ".p12", ".mobileprovision", ".pem")

SECRET_PATTERNS = [
    (r"sk-ant-[A-Za-z0-9_\-]{10,}", "an Anthropic API key"),
    (r"ghp_[A-Za-z0-9]{20,}", "a GitHub personal access token"),
    (r"AKIA[0-9A-Z]{16}", "an AWS access key id"),
    (r"-----BEGIN [A-Z ]*PRIVATE KEY-----", "a private key block"),
    (r"(?i)\b(api[_-]?key|secret|password|auth[_-]?token)\s*[:=]\s*[\"'][^\"'\s]{12,}[\"']",
     "a hardcoded credential"),
]


def deny(reason):
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }
    }))
    sys.exit(0)


base = os.path.basename(path)
if base in PROTECTED_NAMES or base.startswith(".env") or path.endswith(PROTECTED_SUFFIXES):
    deny(
        f"'{base}' is machine-local or a credential and is not editable from a session. "
        "If a value needs to change, tell the user what to set and let them edit it."
    )

for pattern, label in SECRET_PATTERNS:
    if re.search(pattern, body):
        deny(
            f"This edit appears to put {label} into {base or 'a source file'}. "
            "Read it from an environment variable instead, and document the variable in "
            "docs/06-permissions-and-safety.md so the team knows it is required."
        )

sys.exit(0)
PY
