---
name: compose-screen
description: Conventions for the Jetpack Compose app in StreakKit — how screens consume the shared store, state hoisting, previews, and what must never live in a composable. Use when adding or reviewing anything under androidApp/.
when_to_use: Writing or reviewing Compose UI in androidApp.
paths: "androidApp/src/**/*.kt"
allowed-tools: Read, Grep, Glob, Write, Edit, Bash(./gradlew :androidApp:*), Bash(adb*)
---

# Compose conventions in StreakKit

The Android app is a rendering layer and nothing more. Its counterpart is
`.Codex/skills/swiftui-bridge/SKILL.md`, and the two must stay symmetrical — when you change one,
read the other.

## How state arrives

```kotlin
val state by store.state.collectAsStateWithLifecycle()
```

`HabitsStore` comes from `StreakKit.createStore()` in `MainActivity`. The shared `StateFlow` is
collected directly — there is no Android ViewModel and no mapping layer, because every value on
`HabitsUiState` is already final.

## Rules

- **Nothing computed in a composable.** No streak arithmetic, no percentage formatting, no date
  handling. If you need a value that does not exist yet, add it to `summarize()` in `commonMain`.
  A `remember { }` that derives a user-visible number is a parity bug waiting to happen.
- **Stateless composables take data and lambdas.** `HabitCard(habit, onToggleToday, onToggleDay,
  onDelete)` — it receives a `HabitSummary` and emits intents. Only `HabitListScreen` holds the
  store; only it holds `isAddSheetVisible`.
- **`key = { it.id }` on every `items()`**. Without it, toggling one habit recomposes and
  reorders the rest.
- **`@Preview` on anything with non-trivial layout**, constructing `HabitSummary` by hand. The
  preview is the cheapest way to see a change, and it forces the composable to stay stateless.
- **Material 3 only**, with `MaterialTheme.colorScheme` / `.typography`. No hard-coded colours
  outside `theme/Theme.kt`, no `Color(0xFF…)` in a screen file.
- **Semantics for anything tappable that is not a `Button`.** The week-strip cells are
  `Modifier.clickable` boxes; they need a content description for TalkBack.

## Parity with SwiftUI

These must match the iOS implementation exactly, because both read the same `HabitSummary`:

| Thing | Where it is decided |
| --- | --- |
| Streak label wording | `streakLabel()` in both apps — identical strings |
| Week strip: 7 cells, today outlined, checked filled | `StreakCalculator.recentDays` supplies the days |
| Completion percentage rounding | `roundToInt()` on Android, `.rounded()` on iOS — same result |
| Card tint when checked in today | primary container / accent at low opacity |

After any change here, run `/kmp:parity-check`. It reads both implementations and reports
differences that compile fine on each side.

## Verify

```
./gradlew :androidApp:assembleDebug     # compile gate
/android:run                            # install, launch, screenshot — look at it
```

A Compose change that was not looked at has not been verified. Previews lie about scrolling,
insets and long text.
