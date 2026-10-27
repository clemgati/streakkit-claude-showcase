# StreakKit

A habit tracker: shared Kotlin Multiplatform logic, a native Jetpack Compose app, and a native
SwiftUI app. The app is small on purpose — this repository is also a worked example of using
Codex on a cross-platform codebase, and the app exists to give that something real to act
on. See [docs/02-Codex-feature-map.md](docs/02-Codex-feature-map.md).

## Layout

```
shared/      Kotlin Multiplatform — ALL business logic. domain, data, remote, presentation.
androidApp/  Jetpack Compose. Renders shared state. No logic.
iosApp/      SwiftUI. Renders the same shared state. No logic.
.Codex/     Commands, skills, subagents, hooks, and a local MCP server.
docs/        Plan, architecture, and the Codex feature map.
```

## The one rule

**Business logic lives in `shared/src/commonMain` and nowhere else.** Streaks, percentages, labels
and date handling are computed once in `HabitsUiState.summarize()` and both apps render the
result. A number formatted inside a composable or a SwiftUI view is a parity bug even when it
currently produces the right answer, because there is now a second place for it to be wrong.

Everything else in this file follows from that.

## Build gates

Run these, in this order, and do not report a change as working until the relevant ones pass:

| Gate | Command |
| --- | --- |
| Shared logic (JVM **and** Kotlin/Native) | `./gradlew :shared:allTests` |
| Android | `./gradlew :androidApp:assembleDebug` |
| Compose UI (JVM, no emulator) | `./gradlew :androidApp:testDebugUnitTest` |
| iOS | `/ios:build` |
| iOS UI | `cd iosApp && xcodebuild test -scheme StreakKit -destination '…'` |
| Cross-platform parity | `/kmp:parity-check` |

`:shared:allTests` running on both targets is the point: a test that passes on one and fails on
the other means the shared module is not actually shared.

## Toolchain facts that are easy to get wrong

- **AGP 9 + Kotlin 2.4.** AGP has built-in Kotlin support, so `org.jetbrains.kotlin.android` must
  **not** be applied — the build fails if it is. The version catalog deliberately omits it.
- **The shared module uses `com.android.kotlin.multiplatform.library`**, not
  `com.android.library`, which AGP 9 refuses alongside the KMP plugin. Its Android config lives
  in `kotlin { androidLibrary { … } }`.
- **Host tests are opt-in** with that plugin — `withHostTestBuilder {}.configure {}` in
  `shared/build.gradle.kts`. Remove it and `commonTest` silently stops running on the JVM.
- **`compileSdk`/`targetSdk` live in `gradle/libs.versions.toml`.** If a dependency demands a
  newer one, install the platform; do not pin the dependency backwards.
- **Edit `iosApp/project.yml`, not the `.xcodeproj`.** Both are committed. After changing the
  YAML: `cd iosApp && xcodegen generate`, and commit both.

## Conventions

- Nothing in the domain reads a clock. `today` is always a parameter, defaulting to
  `systemToday()`. This is what makes the tests deterministic.
- The Swift-facing API stays primitive: `String`, `Int`, `Bool`, `Float`, lists of data classes.
  Exposing a `kotlinx-datetime` or coroutines type forces an `export()` in the framework and
  makes every Swift call site worse. `DayMark.isoDate` is a `String` for this reason.
- New persisted fields on `Habit` need a default value or previously stored JSON stops decoding.
  `HabitRepositoryTest` has a test for that.
- Tests are named as sentences stating the rule, in backticks.
- Dependencies are added only with a reason — every one is something the iOS framework links.

## Working here

- Start multi-layer work in plan mode, or with `/kmp:new-feature`, which enforces the
  shared-logic-first order.
- `mcp__streakkit__*` answers repo questions faster than grepping: expect/actual coverage, which
  UI fields each app renders, and an independent streak oracle.
- Do not commit unless asked. Do not push, tag, or open a PR without being asked.
- A UI change that has not been looked at has not been verified — `/android:run` and `/ios:run`
  take a screenshot for that reason.
