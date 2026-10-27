#!/usr/bin/env python3
"""StreakKit's own MCP server: a local, dependency-free stdio server.

Why this exists
---------------
An MCP server earns its place when it knows something about *this* repository that a general
agent would otherwise rediscover with a dozen Grep calls every session. This one answers four
such questions:

  * which `expect` declarations are missing an `actual`, per platform;
  * whether both UIs actually render the same `HabitsUiState` fields;
  * what the streak numbers *should* be, computed independently of the Kotlin under test;
  * what the module sizes and build gates are.

It deliberately uses nothing but the Python standard library, so it runs on a machine with no
npm, no venv, and no install step — the repo stays clonable-and-go. Protocol is JSON-RPC 2.0
over newline-delimited stdin/stdout; diagnostics go to stderr, never stdout (stdout is the wire).

Exercised by: `/kmp:parity-check`, the `kmp-parity-auditor` subagent, and
`.github/workflows/nightly-parity.yml`.
"""

from __future__ import annotations

import datetime
import json
import os
import re
import subprocess
import sys
from pathlib import Path

SERVER_NAME = "streakkit"
SERVER_VERSION = "1.1.0"

# Must match StreakCalculator.WEEK_LENGTH. The oracle is only useful while it agrees with the
# documented invariants — see .claude/skills/streak-rules/SKILL.md.
WEEK_LENGTH = 7
FALLBACK_PROTOCOL = "2025-06-18"

# The server sits at <root>/.claude/mcp/, so the repo root is two levels up. An explicit
# STREAKKIT_ROOT wins, which is what the CI workflow sets.
ROOT = Path(os.environ.get("STREAKKIT_ROOT") or Path(__file__).resolve().parents[2])

SHARED_SRC = ROOT / "shared" / "src"
ANDROID_SRC = ROOT / "androidApp" / "src"
IOS_SRC = ROOT / "iosApp" / "StreakKit"

# Only these Gradle tasks may be run through the server. An MCP server is a privilege boundary
# like any other: it should expose the smallest surface that makes it useful.
ALLOWED_GRADLE_TASKS = {
    ":shared:allTests",
    ":shared:compileDebugKotlinAndroid",
    ":shared:assemble",
    ":androidApp:assembleDebug",
    "tasks",
    "projects",
}


def log(message: str) -> None:
    print(f"[streakkit-mcp] {message}", file=sys.stderr, flush=True)


# --------------------------------------------------------------------------------------------
# Repository analysis
# --------------------------------------------------------------------------------------------

EXPECT_RE = re.compile(
    r"^\s*(?:internal\s+|public\s+|private\s+)?expect\s+(class|object|fun|val|var|interface)\s+([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)
ACTUAL_RE = re.compile(
    r"^\s*(?:internal\s+|public\s+|private\s+)?actual\s+(?:\w+\s+)*?(class|object|fun|val|var|interface)\s+([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)


def _kotlin_files(base: Path) -> list[Path]:
    return sorted(base.rglob("*.kt")) if base.exists() else []


def _scan(source_set: str, pattern: re.Pattern) -> dict[str, str]:
    """Map declaration name -> 'kind @ relative/path' for one source set."""
    found: dict[str, str] = {}
    for path in _kotlin_files(SHARED_SRC / source_set):
        text = path.read_text(encoding="utf-8", errors="replace")
        for kind, name in pattern.findall(text):
            found[name] = f"{kind} @ {path.relative_to(ROOT)}"
    return found


def expect_actual_matrix() -> dict:
    expected = _scan("commonMain", EXPECT_RE)
    android = _scan("androidMain", ACTUAL_RE)
    ios = _scan("iosMain", ACTUAL_RE)

    rows = []
    for name in sorted(expected):
        rows.append({
            "declaration": name,
            "expect": expected[name],
            "android": android.get(name),
            "ios": ios.get(name),
            "complete": name in android and name in ios,
        })

    orphans = sorted((set(android) | set(ios)) - set(expected))

    return {
        "rows": rows,
        "missing": [r["declaration"] for r in rows if not r["complete"]],
        "orphan_actuals": orphans,
        "ok": all(r["complete"] for r in rows) and not orphans,
    }


# Fields of the shared UI state that each app is expected to render.
UI_STATE_FILE = SHARED_SRC / "commonMain/kotlin/dev/clement/streakkit/presentation/HabitsUiState.kt"
FIELD_RE = re.compile(r"^\s+val\s+([a-zA-Z][A-Za-z0-9]*)\s*:", re.MULTILINE)


def ui_parity_report() -> dict:
    """Which shared UI-state fields does each platform actually reference?

    A field rendered on Android but never mentioned in the Swift sources is the single most
    common way these two apps drift, and it is invisible to the compiler.
    """
    if not UI_STATE_FILE.exists():
        return {"error": f"missing {UI_STATE_FILE.relative_to(ROOT)}"}

    fields = sorted(set(FIELD_RE.findall(UI_STATE_FILE.read_text(encoding="utf-8"))))

    def corpus(base: Path, suffixes: tuple) -> str:
        if not base.exists():
            return ""
        chunks = []
        for suffix in suffixes:
            for path in base.rglob(f"*{suffix}"):
                chunks.append(path.read_text(encoding="utf-8", errors="replace"))
        return "\n".join(chunks)

    android_text = corpus(ANDROID_SRC, (".kt",))
    ios_text = corpus(IOS_SRC, (".swift",))

    rows = []
    for field in fields:
        # Match property *access* (`habit.weeklyGoalLabel`), not the bare word.
        #
        # Bare-word matching produced false positives in both directions: a named argument in a
        # Compose @Preview (`weeklyDone = 5`) counted as Android rendering the field, and a local
        # SwiftUI `@State var weeklyGoal` counted as iOS rendering it. Both languages read these
        # off the summary with a dot, so anchoring on that is both stricter and more accurate.
        #
        # Known limitation: a Kotlin `with(habit) { weeklyGoalLabel }` or a Swift destructuring
        # would be missed. Neither appears in this codebase, and the failure mode is a false
        # *drift* report, which gets investigated — not a false clean bill of health.
        access = re.compile(rf"\.{re.escape(field)}\b")
        on_android = bool(access.search(android_text))
        on_ios = bool(access.search(ios_text))
        rows.append({
            "field": field,
            "android": on_android,
            "ios": on_ios,
            "parity": on_android == on_ios,
        })

    return {
        "fields": rows,
        "android_only": [r["field"] for r in rows if r["android"] and not r["ios"]],
        "ios_only": [r["field"] for r in rows if r["ios"] and not r["android"]],
        "unused": [r["field"] for r in rows if not r["ios"] and not r["android"]],
        "ok": all(r["parity"] for r in rows),
    }


# --------------------------------------------------------------------------------------------
# Streak oracle — an independent implementation of the rules in StreakCalculator.kt
# --------------------------------------------------------------------------------------------

def _parse_dates(values) -> list[datetime.date]:
    return sorted({datetime.date.fromisoformat(v) for v in values})


def streak_oracle(check_ins, today: str, window_days: int = 30) -> dict:
    """Compute the streak numbers in Python, deliberately *not* reusing the Kotlin logic.

    Two implementations of the same written invariants disagreeing means one of them is wrong,
    and the disagreement is the bug report. Used by `/kmp:parity-check`.
    """
    days = set(_parse_dates(check_ins))
    now = datetime.date.fromisoformat(today)
    one = datetime.timedelta(days=1)

    # Invariant 1: today or yesterday may anchor a live streak.
    if now in days:
        cursor = now
    elif (now - one) in days:
        cursor = now - one
    else:
        cursor = None

    current = 0
    while cursor is not None and cursor in days:
        current += 1
        cursor -= one

    longest = 0
    run = 0
    previous = None
    for day in sorted(days):
        run = run + 1 if previous is not None and (day - previous) == one else 1
        previous = day
        longest = max(longest, run)

    def in_window(window_days_: int) -> int:
        start = now - datetime.timedelta(days=window_days_ - 1)
        return sum(1 for day in days if start <= day <= now)

    hits = in_window(window_days)

    return {
        "currentStreak": current,
        "longestStreak": longest,
        "completionRate": round(hits / window_days, 6),
        "windowDays": window_days,
        # Invariant 5: the same rolling seven days the week strip draws.
        "weeklyProgress": in_window(WEEK_LENGTH),
        "weekLength": WEEK_LENGTH,
        "today": today,
        "note": "Independent Python implementation. If Kotlin disagrees, one of them is wrong.",
    }


VERSION_SOURCES = {
    "android": (
        Path("androidApp/build.gradle.kts"),
        re.compile(r'versionName\s*=\s*"([^"]+)"'),
    ),
    "ios-project": (
        Path("iosApp/project.yml"),
        re.compile(r'CFBundleShortVersionString:\s*"?([^"\s#]+)"?'),
    ),
    # Generated by XcodeGen from project.yml, but committed — so it is a third place the version
    # lives, and it drifts the moment someone edits the YAML without regenerating.
    "ios-plist": (
        Path("iosApp/StreakKit/Info.plist"),
        re.compile(r"<key>CFBundleShortVersionString</key>\s*<string>([^<]+)</string>"),
    ),
}


def version_consistency(expected: str = None) -> dict:
    """Check that every place the app states its version agrees — and, optionally, with a tag.

    A release tag that silently disagrees with the shipped version strings is the kind of mistake
    nobody notices until a user reports a version that does not exist.
    """
    sources, problems = {}, []

    for name, (relative, pattern) in VERSION_SOURCES.items():
        path = ROOT / relative
        if not path.exists():
            sources[name] = {"path": str(relative), "version": None}
            problems.append(f"{relative} is missing")
            continue
        match = pattern.search(path.read_text(encoding="utf-8", errors="replace"))
        version = match.group(1).strip() if match else None
        sources[name] = {"path": str(relative), "version": version}
        if version is None:
            problems.append(f"no version found in {relative}")

    found = {s["version"] for s in sources.values() if s["version"]}
    if len(found) > 1:
        problems.append("sources disagree: " + ", ".join(
            f"{n}={s['version']}" for n, s in sorted(sources.items())
        ))

    if expected:
        expected = expected.lstrip("v")
        mismatched = {n: s["version"] for n, s in sources.items() if s["version"] != expected}
        if mismatched:
            problems.append(
                f"expected {expected!r} but " + ", ".join(
                    f"{n}={v!r}" for n, v in sorted(mismatched.items())
                )
            )

    return {
        "sources": sources,
        "version": found.pop() if len(found) == 1 else None,
        "expected": expected,
        "problems": problems,
        "ok": not problems,
    }


def project_stats() -> dict:
    def count(base: Path, suffix: str) -> dict:
        if not base.exists():
            return {"files": 0, "lines": 0}
        files = list(base.rglob(f"*{suffix}"))
        lines = sum(len(f.read_text(encoding="utf-8", errors="replace").splitlines()) for f in files)
        return {"files": len(files), "lines": lines}

    return {
        "shared_common": count(SHARED_SRC / "commonMain", ".kt"),
        "shared_android": count(SHARED_SRC / "androidMain", ".kt"),
        "shared_ios": count(SHARED_SRC / "iosMain", ".kt"),
        "shared_tests": count(SHARED_SRC / "commonTest", ".kt"),
        "android_app": count(ANDROID_SRC, ".kt"),
        "ios_app": count(IOS_SRC, ".swift"),
    }


def run_gradle(task: str, timeout: int = 900) -> dict:
    if task not in ALLOWED_GRADLE_TASKS:
        return {
            "error": f"'{task}' is not allow-listed.",
            "allowed": sorted(ALLOWED_GRADLE_TASKS),
        }
    try:
        proc = subprocess.run(
            ["./gradlew", task, "--console=plain"],
            cwd=str(ROOT), capture_output=True, text=True, timeout=timeout,
        )
    except subprocess.TimeoutExpired:
        return {"error": f"'{task}' timed out after {timeout}s"}

    output = (proc.stdout or "") + (proc.stderr or "")
    return {
        "task": task,
        "exitCode": proc.returncode,
        "succeeded": proc.returncode == 0,
        # Gradle is verbose; the tail is where the verdict and the failures are.
        "tail": "\n".join(output.splitlines()[-60:]),
    }


# --------------------------------------------------------------------------------------------
# MCP surface: tools, resources, prompts
# --------------------------------------------------------------------------------------------

TOOLS = [
    {
        "name": "expect_actual_matrix",
        "description": (
            "List every `expect` declaration in shared/commonMain with its Android and iOS "
            "`actual`, flagging any that are missing. Run this before claiming a KMP change is "
            "complete — a missing actual fails the build only for the platform you did not build."
        ),
        "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
    },
    {
        "name": "ui_parity_report",
        "description": (
            "Report which HabitsUiState/HabitSummary fields each app actually references. "
            "Catches the drift the compiler cannot see: a field rendered on Android and silently "
            "ignored in SwiftUI."
        ),
        "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
    },
    {
        "name": "streak_oracle",
        "description": (
            "Compute currentStreak/longestStreak/completionRate from check-in dates using an "
            "independent Python implementation of the documented invariants. Use it to decide "
            "whether StreakCalculator.kt or the test's expectation is the wrong one."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "check_ins": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "ISO-8601 dates, e.g. [\"2026-10-26\", \"2026-10-25\"]",
                },
                "today": {"type": "string", "description": "ISO-8601 date to treat as today"},
                "window_days": {"type": "integer", "default": 30},
            },
            "required": ["check_ins", "today"],
            "additionalProperties": False,
        },
    },
    {
        "name": "version_consistency",
        "description": (
            "Check that androidApp/build.gradle.kts, iosApp/project.yml and the generated "
            "Info.plist all state the same version, optionally matching an expected release tag. "
            "Run before tagging — a tag that disagrees with the shipped strings is invisible "
            "until a user reports a version that does not exist."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "expected": {
                    "type": "string",
                    "description": "Version to require, with or without a leading 'v'",
                },
            },
            "additionalProperties": False,
        },
    },
    {
        "name": "project_stats",
        "description": "File and line counts per module. Cheap way to see where the code actually lives.",
        "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
    },
    {
        "name": "run_gradle_task",
        "description": (
            "Run one allow-listed Gradle task and return its exit code plus the last 60 lines. "
            "Allow-listed: " + ", ".join(sorted(ALLOWED_GRADLE_TASKS)) + "."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {"task": {"type": "string", "enum": sorted(ALLOWED_GRADLE_TASKS)}},
            "required": ["task"],
            "additionalProperties": False,
        },
    },
]

RESOURCES = [
    {
        "uri": "streakkit://parity-matrix",
        "name": "KMP parity matrix",
        "description": "Live expect/actual and UI-field parity report, regenerated on every read.",
        "mimeType": "text/markdown",
    },
    {
        "uri": "streakkit://version-catalog",
        "name": "Gradle version catalog",
        "description": "gradle/libs.versions.toml — the single source of dependency versions.",
        "mimeType": "text/plain",
    },
    {
        "uri": "streakkit://architecture",
        "name": "Architecture notes",
        "description": "docs/01-architecture.md",
        "mimeType": "text/markdown",
    },
]

PROMPTS = [
    {
        "name": "add-shared-field",
        "description": "Checklist for threading a new field from the shared model through both UIs.",
        "arguments": [
            {"name": "field", "description": "Field name, e.g. reminderTime", "required": True},
            {"name": "kotlin_type", "description": "Kotlin type, e.g. String?", "required": True},
        ],
    },
    {
        "name": "investigate-parity-drift",
        "description": "Structured investigation when Android and iOS disagree about a value.",
        "arguments": [
            {"name": "symptom", "description": "What differs between the platforms", "required": True},
        ],
    },
]


def render_parity_markdown() -> str:
    matrix = expect_actual_matrix()
    ui = ui_parity_report()

    lines = ["# StreakKit parity matrix", "", "## expect / actual", ""]
    lines.append("| Declaration | commonMain | androidMain | iosMain | OK |")
    lines.append("| --- | --- | --- | --- | --- |")
    for row in matrix["rows"]:
        lines.append(
            f"| `{row['declaration']}` | {row['expect']} | {row['android'] or '**MISSING**'} "
            f"| {row['ios'] or '**MISSING**'} | {'✅' if row['complete'] else '❌'} |"
        )
    if matrix["orphan_actuals"]:
        lines += ["", f"Orphan actuals with no expect: {', '.join(matrix['orphan_actuals'])}"]

    lines += ["", "## Shared UI state field usage", "",
              "| Field | Compose | SwiftUI | Parity |", "| --- | --- | --- | --- |"]
    for row in ui.get("fields", []):
        lines.append(
            f"| `{row['field']}` | {'✅' if row['android'] else '—'} "
            f"| {'✅' if row['ios'] else '—'} | {'✅' if row['parity'] else '⚠️'} |"
        )

    verdict = "No drift detected." if matrix["ok"] and ui.get("ok") else "Drift detected — see ❌/⚠️ above."
    lines += ["", f"**Verdict:** {verdict}"]
    return "\n".join(lines)


def read_resource(uri: str) -> str:
    if uri == "streakkit://parity-matrix":
        return render_parity_markdown()
    if uri == "streakkit://version-catalog":
        path = ROOT / "gradle" / "libs.versions.toml"
    elif uri == "streakkit://architecture":
        path = ROOT / "docs" / "01-architecture.md"
    else:
        raise ValueError(f"unknown resource: {uri}")
    return path.read_text(encoding="utf-8") if path.exists() else f"(missing {path})"


def render_prompt(name: str, args: dict) -> dict:
    if name == "add-shared-field":
        field = args.get("field", "<field>")
        ktype = args.get("kotlin_type", "<Type>")
        text = f"""Add `{field}: {ktype}` to the shared model and render it on both platforms.

Work in this order and stop at the first failing gate:

1. `shared/.../domain/Habit.kt` — add the property. If it is persisted, give it a default so
   existing stored JSON still decodes; `HabitRepositoryTest` has a corrupt-payload test that
   must stay green.
2. `shared/.../presentation/HabitsUiState.kt` — expose it on `HabitSummary`, computed in
   `summarize()`. Keep the Swift-facing type primitive (String/Int/Bool/Float), not a
   kotlinx-datetime type.
3. `shared/src/commonTest` — a test that fails before the UI work and passes after.
4. `./gradlew :shared:allTests` — must be green before touching either app.
5. `androidApp/.../ui/HabitCard.kt` — render it.
6. `iosApp/StreakKit/Views/HabitCardView.swift` — render it the same way.
7. `mcp__streakkit__ui_parity_report` — confirm the new field shows ✅ on both platforms.

Do not add platform-specific formatting logic in either app; if formatting is needed, do it in
`summarize()` so both apps get the same string."""
    elif name == "investigate-parity-drift":
        symptom = args.get("symptom", "<symptom>")
        text = f"""Android and iOS disagree: {symptom}

Rule out causes in this order — cheapest first, and most drift is caught by step 2:

1. `mcp__streakkit__ui_parity_report` — is the field referenced by both apps at all?
2. `mcp__streakkit__streak_oracle` — with the real check-in dates, what *should* the number be?
   This tells you whether the shared logic is wrong or only one app's rendering is.
3. If the oracle agrees with one platform, the other platform is formatting or caching it
   differently — look at the view, not the shared module.
4. If the oracle disagrees with both, the bug is in `StreakCalculator.kt`; add the failing case
   to `StreakCalculatorTest` before fixing it.
5. `mcp__streakkit__expect_actual_matrix` — only if the value comes from an `expect` declaration
   (storage or platformName), since those are the only places the two platforms run different code.

Report which step found it."""
    else:
        raise ValueError(f"unknown prompt: {name}")

    return {
        "description": f"StreakKit · {name}",
        "messages": [{"role": "user", "content": {"type": "text", "text": text}}],
    }


def call_tool(name: str, args: dict):
    if name == "expect_actual_matrix":
        return expect_actual_matrix()
    if name == "ui_parity_report":
        return ui_parity_report()
    if name == "streak_oracle":
        return streak_oracle(
            args.get("check_ins", []),
            args["today"],
            int(args.get("window_days", 30)),
        )
    if name == "version_consistency":
        return version_consistency(args.get("expected"))
    if name == "project_stats":
        return project_stats()
    if name == "run_gradle_task":
        return run_gradle(args["task"])
    raise ValueError(f"unknown tool: {name}")


# --------------------------------------------------------------------------------------------
# JSON-RPC plumbing
# --------------------------------------------------------------------------------------------

def handle(request: dict):
    method = request.get("method")
    params = request.get("params") or {}

    if method == "initialize":
        # Echo the client's protocol version when it offers one; both sides then agree.
        version = params.get("protocolVersion") or FALLBACK_PROTOCOL
        return {
            "protocolVersion": version,
            "capabilities": {"tools": {}, "resources": {}, "prompts": {}},
            "serverInfo": {"name": SERVER_NAME, "version": SERVER_VERSION},
            "instructions": (
                "StreakKit repo intelligence. Call expect_actual_matrix and ui_parity_report "
                "before declaring a cross-platform change finished."
            ),
        }
    if method == "ping":
        return {}
    if method == "tools/list":
        return {"tools": TOOLS}
    if method == "tools/call":
        name = params.get("name", "")
        try:
            payload = call_tool(name, params.get("arguments") or {})
            is_error = isinstance(payload, dict) and "error" in payload
        except Exception as exc:  # surface tool failures as results, not transport errors
            payload, is_error = {"error": f"{type(exc).__name__}: {exc}"}, True
        return {
            "content": [{"type": "text", "text": json.dumps(payload, indent=2)}],
            "isError": is_error,
        }
    if method == "resources/list":
        return {"resources": RESOURCES}
    if method == "resources/read":
        uri = params.get("uri", "")
        mime = next((r["mimeType"] for r in RESOURCES if r["uri"] == uri), "text/plain")
        return {"contents": [{"uri": uri, "mimeType": mime, "text": read_resource(uri)}]}
    if method == "prompts/list":
        return {"prompts": PROMPTS}
    if method == "prompts/get":
        return render_prompt(params.get("name", ""), params.get("arguments") or {})

    raise LookupError(f"method not found: {method}")


def cli(argv: list) -> int:
    """A plain command-line face for the same analysis the MCP tools expose.

    Why both: an MCP tool is reachable from a session, but not from CI, a git hook, or an agent
    whose Bash allow-list is a prefix rule. Piping JSON-RPC into stdin needs a shell pipeline,
    and `printf ... | python3 ...` does not match a `Bash(python3 ...)` prefix rule — so the
    stdio-only version was effectively unusable from a locked-down agent. One subcommand is.

    Exit codes: 0 clean, 1 drift found (so `parity` works as a CI gate), 2 bad usage.
    """
    import argparse

    parser = argparse.ArgumentParser(
        prog="streakkit_mcp.py",
        description="StreakKit repo analysis. With no arguments, runs as an MCP stdio server.",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    oracle = sub.add_parser("oracle", help="streak numbers from an independent implementation")
    oracle.add_argument("--check-ins", required=True,
                        help="comma-separated ISO dates, e.g. 2026-10-25,2026-10-26")
    oracle.add_argument("--today", required=True, help="ISO date to treat as today")
    oracle.add_argument("--window-days", type=int, default=30)

    sub.add_parser("expect-actual", help="expect/actual coverage as JSON")
    sub.add_parser("ui-parity", help="shared UI-state field usage as JSON")
    sub.add_parser("stats", help="per-module file and line counts as JSON")

    versions = sub.add_parser("versions", help="version strings agree; exits 1 if not")
    versions.add_argument("--expect", help="require this version, with or without a leading 'v'")
    sub.add_parser("parity", help="full matrix as markdown; exits 1 when drift is found")

    args = parser.parse_args(argv)

    if args.command == "oracle":
        dates = [d.strip() for d in args.check_ins.split(",") if d.strip()]
        try:
            print(json.dumps(streak_oracle(dates, args.today, args.window_days), indent=2))
        except ValueError as exc:
            print(f"bad date: {exc}", file=sys.stderr)
            return 2
        return 0

    if args.command == "expect-actual":
        result = expect_actual_matrix()
        print(json.dumps(result, indent=2))
        return 0 if result["ok"] else 1

    if args.command == "ui-parity":
        result = ui_parity_report()
        print(json.dumps(result, indent=2))
        return 0 if result.get("ok") else 1

    if args.command == "stats":
        print(json.dumps(project_stats(), indent=2))
        return 0

    if args.command == "versions":
        result = version_consistency(args.expect)
        for name, source in sorted(result["sources"].items()):
            print(f"{name:12s} {source['version'] or '(not found)':10s} {source['path']}")
        if result["ok"]:
            print(f"\nOK — all sources agree on {result['version']}")
            return 0
        print("\nFAILED")
        for problem in result["problems"]:
            print(f"  - {problem}")
        return 1

    # parity
    print(render_parity_markdown())
    matrix, ui = expect_actual_matrix(), ui_parity_report()
    return 0 if (matrix["ok"] and ui.get("ok")) else 1


def main() -> None:
    if len(sys.argv) > 1:
        raise SystemExit(cli(sys.argv[1:]))

    log(f"serving {ROOT}")
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            request = json.loads(line)
        except json.JSONDecodeError as exc:
            log(f"bad JSON: {exc}")
            continue

        request_id = request.get("id")
        if request_id is None:
            continue  # a notification; nothing to answer

        try:
            result = handle(request)
            response = {"jsonrpc": "2.0", "id": request_id, "result": result}
        except LookupError as exc:
            response = {"jsonrpc": "2.0", "id": request_id,
                        "error": {"code": -32601, "message": str(exc)}}
        except Exception as exc:
            response = {"jsonrpc": "2.0", "id": request_id,
                        "error": {"code": -32603, "message": f"{type(exc).__name__}: {exc}"}}

        sys.stdout.write(json.dumps(response) + "\n")
        sys.stdout.flush()


if __name__ == "__main__":
    main()
