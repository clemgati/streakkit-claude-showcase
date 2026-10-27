#!/usr/bin/env bash
#
# statusLine command — the line under the prompt.
#
# Input (stdin): the session JSON, including model, workspace, cost and context usage.
# Output: a single line of text (ANSI colour allowed).
#
# What is worth putting here on a KMP repo: the branch, how dirty the tree is, and — the useful
# one — whether a simulator/emulator is actually attached, because "run it" failing for that
# reason is the most common false alarm in a mobile session.
set -uo pipefail

payload="$(cat)"
command -v python3 >/dev/null 2>&1 || { echo "StreakKit"; exit 0; }

PAYLOAD="$payload" python3 - <<'PY'
import json, os, subprocess

try:
    data = json.loads(os.environ["PAYLOAD"])
except Exception:
    data = {}


def run(*args):
    try:
        return subprocess.run(args, capture_output=True, text=True, timeout=2).stdout.strip()
    except Exception:
        return ""


model = (data.get("model") or {}).get("display_name") or "claude"
cwd = (data.get("workspace") or {}).get("current_dir") or os.getcwd()

branch = run("git", "-C", cwd, "rev-parse", "--abbrev-ref", "HEAD") or "—"
dirty = len([line for line in run("git", "-C", cwd, "status", "--porcelain").splitlines() if line])

ios = "ios✓" if "Booted" in run("xcrun", "simctl", "list", "devices", "booted") else "ios·"
android = "and✓" if "\tdevice" in run("adb", "devices") else "and·"

dirty_part = f" +{dirty}" if dirty else ""
print(f"\033[2m🔥 StreakKit\033[0m \033[36m{branch}\033[0m{dirty_part} \033[2m|\033[0m {ios} {android} \033[2m| {model}\033[0m")
PY
