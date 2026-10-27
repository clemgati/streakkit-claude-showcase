# androidApp/ — Jetpack Compose

Loaded in addition to the root `CLAUDE.md` when you work in this directory.
Full conventions: `.claude/skills/compose-screen/SKILL.md` (auto-loads for files here).

## Shape

```
StreakKitApplication.kt   Hands the app context to the shared module. Required before any store.
MainActivity.kt           Creates the HabitsStore, sets content.
ui/                       HabitListScreen (holds state) + stateless cards.
theme/Theme.kt            Material 3, dynamic colour on S+. The only place colours are defined.
```

## Rules

- `store.state.collectAsStateWithLifecycle()` — there is no Android ViewModel and no mapping
  layer, because every value in `HabitsUiState` is already final.
- Composables below `HabitListScreen` are stateless: they take a `HabitSummary` and emit lambdas.
- Nothing is computed here. No arithmetic, no formatting, no date handling. If a value does not
  exist yet, add it to `summarize()` in `commonMain` so SwiftUI gets it too.
- `key = { it.id }` on every `items()`.
- Colours come from `MaterialTheme.colorScheme`. No `Color(0xFF…)` outside `theme/`.

## Gates

`./gradlew :androidApp:assembleDebug`, then `/android:run` and actually look at the screenshot.
After any change to how habit data is rendered, `/kmp:parity-check`.
