---
description: Run the shared KMP test suite on both JVM and Kotlin/Native and explain any failures.
argument-hint: "[optional --tests filter]"
allowed-tools: Bash(./gradlew :shared:allTests*), Bash(./gradlew :shared:test*), Read, Grep
model: sonnet
effort: low
---

## Current state

Changed files:
```!
git status --short -- shared/ | head -20
```

## Run the gate

```!
./gradlew :shared:allTests --console=plain $ARGUMENTS 2>&1 | tail -40
```

## Your task

The suite above runs **twice** — once as `androidHostTest` on the JVM and once as
`iosSimulatorArm64Test` on Kotlin/Native. That is deliberate: a test that passes on one and fails
on the other is the most valuable signal this repo produces, because it means the shared module is
not actually shared.

Report in this order:

1. **Verdict** — pass or fail, and on which target(s).
2. If it failed on **both** targets, the logic is wrong. Name the failing invariant from the
   KDoc on `StreakCalculator` and say which one the code broke.
3. If it failed on **one** target only, do not touch the test. Say so plainly and look for the
   cause in platform-divergent code: an `expect`/`actual` pair, a `Dispatchers` assumption, or
   date handling. `mcp__streakkit__expect_actual_matrix` lists the candidates.
4. If everything passed, say so in one line. Do not summarise passing tests.

Do not edit any file in this command. Diagnose only — the user will decide what to change.
