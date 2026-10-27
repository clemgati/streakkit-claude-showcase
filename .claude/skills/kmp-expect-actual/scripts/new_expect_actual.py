#!/usr/bin/env python3
"""Generate a matched expect/actual triple for the StreakKit shared module.

The mechanical part of adding platform-specific behaviour — three files, one package, identical
signatures — is exactly the part that drifts when done by hand, and a drifted signature fails on
whichever platform you did not build. This writes all three at once.

    python3 .claude/skills/kmp-expect-actual/scripts/new_expect_actual.py \
        --name platformClipboard --kind fun --returns String

    python3 ... --name platformLocale --kind val --returns String --package platform

Nothing is overwritten: existing files are reported and skipped.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
BASE = ROOT / "shared" / "src"
PACKAGE_ROOT = "dev/clement/streakkit"

TARGETS = {
    "commonMain": ("expect", ""),
    "androidMain": ("actual", ".android"),
    "iosMain": ("actual", ".ios"),
}

BODIES = {
    "androidMain": {
        "fun": "{\n    TODO(\"Android implementation\")\n}",
        "val": " = TODO(\"Android implementation\")",
    },
    "iosMain": {
        "fun": "{\n    TODO(\"iOS implementation\")\n}",
        "val": " = TODO(\"iOS implementation\")",
    },
}


def declaration(modifier: str, kind: str, name: str, returns: str, source_set: str) -> str:
    if kind == "fun":
        signature = f"{modifier} fun {name}(): {returns}"
    else:
        signature = f"{modifier} {kind} {name}: {returns}"

    if modifier == "expect":
        return signature
    return signature + BODIES[source_set][kind]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True, help="declaration name, e.g. platformClipboard")
    parser.add_argument("--kind", default="fun", choices=["fun", "val"])
    parser.add_argument("--returns", required=True, help="return type, e.g. String")
    parser.add_argument("--package", default="platform",
                        help="sub-package under dev.clement.streakkit (default: platform)")
    parser.add_argument("--visibility", default="internal", choices=["internal", "public"],
                        help="internal keeps it out of the generated Objective-C header")
    args = parser.parse_args()

    file_stem = args.name[0].upper() + args.name[1:]
    package = f"dev.clement.streakkit.{args.package}"
    visibility = "" if args.visibility == "public" else "internal "

    written, skipped = [], []

    for source_set, (modifier, suffix) in TARGETS.items():
        directory = BASE / source_set / "kotlin" / PACKAGE_ROOT / args.package
        path = directory / f"{file_stem}{suffix}.kt"

        if path.exists():
            skipped.append(path)
            continue

        directory.mkdir(parents=True, exist_ok=True)

        header = ""
        if source_set == "commonMain":
            header = (
                f"/**\n"
                f" * TODO: say what this returns and why it must differ per platform.\n"
                f" *\n"
                f" * Both actuals must keep this exact signature and visibility; see\n"
                f" * .claude/skills/kmp-expect-actual/SKILL.md.\n"
                f" */\n"
            )
        elif source_set == "androidMain":
            header = "// Android actual. May use the Android SDK; must not assume an Activity.\n"
        else:
            header = "// iOS actual. Compiled for iosX64, iosArm64 and iosSimulatorArm64 alike.\n"

        path.write_text(
            f"package {package}\n\n"
            f"{header}"
            f"{visibility}{declaration(modifier, args.kind, args.name, args.returns, source_set)}\n",
            encoding="utf-8",
        )
        written.append(path)

    for path in written:
        print(f"created  {path.relative_to(ROOT)}")
    for path in skipped:
        print(f"exists   {path.relative_to(ROOT)}  (left alone)", file=sys.stderr)

    if written:
        print("\nNext: implement both actuals, then verify with")
        print("  mcp__streakkit__expect_actual_matrix")
        print("  ./gradlew :shared:allTests      # compiles Kotlin/Native too — this is the gate")

    return 0 if written else 1


if __name__ == "__main__":
    raise SystemExit(main())
