# shared/ — the Kotlin Multiplatform module

Loaded in addition to the root `CLAUDE.md` when you work in this directory.

## Source sets

| Set | Compiles for | Contains |
| --- | --- | --- |
| `commonMain` | everything | All logic. Domain, repository, Ktor client, presentation. |
| `androidMain` | Android | `actual`s only: SharedPreferences store, OkHttp engine, `Build.VERSION`. |
| `iosMain` | iosX64, iosArm64, iosSimulatorArm64 | `actual`s only: NSUserDefaults, Darwin engine, UIDevice. |
| `commonTest` | JVM **and** Kotlin/Native | Every test. Runs twice. |

The two `*Main` platform sets are deliberately tiny — three declarations each. If a platform set
starts growing, the logic that landed there probably belongs in `commonMain` behind an interface.

## Layers in commonMain

```
domain/        Habit, StreakCalculator. Pure. No IO, no clock, no coroutines.
data/          KeyValueStore (interface) + expect factory, HabitRepository (StateFlow owner).
remote/        Quote, QuoteApi, offline fallback, expect createHttpClient().
presentation/  HabitsStore (intents + StateFlow), HabitsUiState/HabitSummary/DayMark.
platform/      expect platformName, systemToday().
StreakKit.kt   The one factory both apps call.
```

Dependencies point downward only: `presentation → data/remote → domain`. Nothing in `domain`
imports anything above it, which is why `StreakCalculator` is trivially testable.

## Rules

- **`domain` stays pure.** No suspend functions, no `Flow`, no IO. If something in `domain` needs
  the current date, it takes it as a parameter.
- **`HabitsUiState` is the contract with both apps.** Every field on it must be renderable
  directly: pre-computed, pre-formatted, primitive. Adding a field means adding it to
  `summarize()`, then to *both* apps — `mcp__streakkit__ui_parity_report` checks the second half.
- **`expect` declarations stay `internal`** unless an app genuinely calls them. Public ones end up
  in the generated Objective-C header. There are currently three; see
  `.claude/skills/kmp-expect-actual/SKILL.md` before adding a fourth.
- **The repository writes through on every mutation.** `mutate()` updates the `StateFlow` and
  persists in one step; there is no explicit save and no dirty flag.
- **Corrupt stored JSON degrades to empty**, never throws. Losing demo data beats a crash loop,
  and `HabitRepositoryTest` asserts it.

## Testing

Run `./gradlew :shared:allTests` — never just the JVM half. Fixtures: `InMemoryKeyValueStore`,
injectable `newId`, injectable `today`, and `UnconfinedTestDispatcher` for `HabitsStore`. No test
in this module should ever need to wait for anything.

The `shared-test-writer` subagent knows these conventions; delegate to it rather than
re-explaining them.
