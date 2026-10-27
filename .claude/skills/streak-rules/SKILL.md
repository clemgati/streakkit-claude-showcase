---
name: streak-rules
description: The invariants governing StreakKit's streak, longest-streak and completion-rate arithmetic, and how to change them safely. Use whenever editing StreakCalculator, HabitsUiState.summarize, or any test that asserts a streak number, and whenever a user reports that a streak looks wrong.
when_to_use: Editing shared domain logic, diagnosing a wrong streak, or reviewing a change to date handling.
paths: "shared/src/**/*.kt,androidApp/src/**/HabitCard.kt,iosApp/StreakKit/Views/HabitCardView.swift"
allowed-tools: Read, Grep, Glob, Bash(python3 *), mcp__streakkit
---

# Streak rules

Streak arithmetic is the only real logic in this app, and every reported bug in it so far has been
one of two things: a misread invariant, or a date boundary. This skill exists so neither costs a
session.

## The four invariants

These are load-bearing. They are stated in the KDoc on `StreakCalculator` and asserted in
`StreakCalculatorTest`; if you change one, change all three places in the same commit.

1. **A current streak may be anchored on today *or* yesterday.** A user who has not checked in
   yet today has not broken anything — the day is not over. This is the invariant people get
   wrong most often, and the one that makes "my streak reset overnight" bug reports appear.
2. **Nothing older than yesterday can anchor a current streak.** A two-day-old check-in means the
   streak is broken, full stop.
3. **`longestStreak` is historical and ignores recency.** A five-day run last month beats a
   two-day run in progress.
4. **Nothing reads a clock.** Every function takes `today` as a parameter. This is what makes the
   whole module testable, and it is why `HabitsStore` has an injectable `today: () -> LocalDate`.

## Before changing any of them

Check the answer against an independent implementation first:

```
mcp__streakkit__streak_oracle with the real check-in dates and today
```

The oracle is a separate Python implementation of these same invariants
(`.claude/mcp/streakkit_mcp.py`). The decision table:

| Oracle vs. Kotlin | Oracle vs. the test | What is wrong |
| --- | --- | --- |
| agree | agree | The UI, not the logic. Look at the view. |
| disagree | agree | `StreakCalculator` — a real bug. |
| agree | disagree | The test's expectation. Fix the test. |
| disagree | disagree | You are changing an invariant. Update the KDoc too. |

## Edge cases that have bitten before

See [reference.md](reference.md) for the full table with expected values. The short version:
timezone boundaries, a check-in dated in the future, an empty set, and `windowDays = 0`.

## When you change the rules

1. Update the KDoc on `StreakCalculator` **first** — the prose is the specification.
2. Update `.claude/mcp/streakkit_mcp.py`'s `streak_oracle` to match. If you skip this, the oracle
   silently becomes a liar and the table above stops working.
3. Add the new case to `StreakCalculatorTest`, where the test name states the rule in words.
4. Run `./gradlew :shared:allTests` — it runs on both JVM and Kotlin/Native, and date arithmetic
   is exactly the kind of thing that differs between them.
