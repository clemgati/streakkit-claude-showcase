# Architecture

## The shape

```
┌──────────────────────────┐      ┌──────────────────────────┐
│ androidApp (Compose)     │      │ iosApp (SwiftUI)         │
│   collectAsStateWith…    │      │   HabitsViewModel        │
└────────────┬─────────────┘      └────────────┬─────────────┘
             │  HabitsUiState                  │  HabitsUiState
             └────────────────┬────────────────┘
                              ▼
            ┌──────────────────────────────────┐
            │ shared/commonMain                │
            │   presentation  HabitsStore      │
            │   data          HabitRepository  │
            │   remote        QuoteApi         │
            │   domain        StreakCalculator │
            └───────┬──────────────────┬───────┘
                    │ expect           │ expect
            ┌───────▼──────┐   ┌───────▼──────┐
            │ androidMain  │   │ iosMain      │
            │ SharedPrefs  │   │ NSUserDefaults│
            │ OkHttp       │   │ Darwin        │
            └──────────────┘   └──────────────┘
```

Both apps receive the **same** `HabitsUiState`, with every value already computed and formatted.
Neither app contains a line of business logic. That is the whole design, and everything below is
a consequence of it.

## Why presentation is shared, not just domain

The common advice is to share the domain and let each platform build its own view models. This
repo shares the presentation layer too, for one reason: **it is the layer where drift actually
happens.**

Nobody reimplements a streak algorithm twice. What they do is format a percentage with
`roundToInt()` on one platform and truncation on the other, or write "Streak broken" on Android
and "No streak" on iOS, or handle the offline branch in one app and forget it in the other. Those
are presentation decisions, and if they live in two places they will differ.

So `HabitSummary` carries `currentStreak: Int`, `completionRate30d: Float`, and
`week: List<DayMark>` with pre-rendered labels and ISO date strings. The views choose colours,
shapes and animations; they never choose a *value*.

`mcp__streakkit__ui_parity_report` exists to check the other half of that bargain: that both apps
actually render every field.

## Layers in `shared/commonMain`

| Layer | Contents | Constraints |
| --- | --- | --- |
| `domain` | `Habit`, `StreakCalculator` | Pure. No IO, no clock, no coroutines. |
| `data` | `KeyValueStore` (interface + `expect` factory), `HabitRepository` | Owns the `StateFlow<List<Habit>>`; writes through on every mutation. |
| `remote` | `Quote`, `QuoteApi`, `OfflineQuotes`, `expect createHttpClient()` | Failure degrades to a bundled quote, never an error state. |
| `presentation` | `HabitsStore`, `HabitsUiState`, `HabitSummary`, `DayMark` | The contract with both apps. Primitives only. |
| `platform` | `expect platformName`, `systemToday()` | The only place that reads a clock. |

Dependencies point downward only. `domain` imports nothing from above it, which is why
`StreakCalculator` is a plain object with no setup and its tests need no fixtures.

## Decisions worth explaining

### No SQLDelight, no DI framework, no Compose Multiplatform

Persistence is a hand-written `KeyValueStore` over `kotlinx.serialization`. Three reasons:

1. **`expect`/`actual` stays visible.** A codegen plugin would hide the one KMP mechanism this
   repo exists to demonstrate behind generated code.
2. **The build stays robust.** Every plugin is a version-compatibility risk, and this repo is
   pinned to a very current AGP/Kotlin pair already.
3. **The data is tiny.** A few dozen habits with a set of dates each. A database would be
   ceremony around a JSON blob.

Dependency injection is a single `object StreakKit` with one function. The graph is three objects
deep; a reader should be able to see all of it at once.

Compose Multiplatform is excluded because the brief was native SwiftUI, and because having two
genuinely different UI toolkits render one shared state is what makes the parity problem real.

### `KeyValueStore` is an interface, but its factory is `expect`

```kotlin
interface KeyValueStore { … }                       // commonMain — fakeable in tests
internal expect fun platformKeyValueStore(): KeyValueStore
```

An `expect class` cannot be implemented in `commonTest`, so the tests would have had to be
platform-specific — exactly the thing this module is organised to avoid. Splitting the interface
from the factory keeps `commonTest` able to run everywhere with `InMemoryKeyValueStore`.

The cost is `StreakKitAndroid.initialize(context)` in `Application.onCreate()`, because Android
cannot open storage without a context and `expect fun` takes no parameters. That is a genuine
wart, and it is documented where it bites rather than hidden.

### The Swift-facing API is deliberately primitive

`DayMark.isoDate` is a `String`, not a `LocalDate`. Day labels are pre-formatted. The reason is
mechanical: every non-primitive type on the public API must be `export()`ed from the framework in
`shared/build.gradle.kts`, and exported Kotlin types produce Swift call sites that look like
Objective-C. The module does export `kotlinx-datetime` and `kotlinx-coroutines` because
`HabitsStore.state` is a `StateFlow` and `Habit.checkIns` is a `Set<LocalDate>` — but nothing
SwiftUI touches needs either.

### Swift 5 language mode

Kotlin/Native's generated Objective-C classes are not `Sendable`. Under Swift 6 strict
concurrency, every value crossing the bridge needs an `@unchecked Sendable` wrapper or a
`MainActor` assumption, which would roughly double the size of `HabitsViewModel` and teach a
pattern that is noise rather than insight. `SWIFT_VERSION` is pinned to 5.0 in
`iosApp/project.yml` with that note attached. It is a real trade-off, not an oversight.

### XcodeGen, with the `.xcodeproj` committed

`project.yml` is 70 readable lines; a `.pbxproj` is thousands of generated ones that conflict on
every branch. But requiring XcodeGen to clone-and-build would be hostile, so both are committed:
the YAML is what you edit and review, the generated project is what `xcodebuild` and CI use.
Regenerate with `cd iosApp && xcodegen generate` and commit both.

## Testing strategy

`commonTest` runs **twice** — once on the JVM as `androidHostTest`, once on Kotlin/Native as
`iosSimulatorArm64Test`. `./gradlew :shared:allTests` runs both; 44 tests, 88 executions.

Everything needed to make that deterministic is injected:

| Dependency | Injected as | Why |
| --- | --- | --- |
| Current date | `today: () -> LocalDate` | No test depends on when it runs |
| Id generation | `newId: () -> String` | Assertions can name ids |
| Persistence | `KeyValueStore` | `InMemoryKeyValueStore`, with a `writeCount` |
| Coroutine scope | `CoroutineScope` | `UnconfinedTestDispatcher` — no test ever waits |
| Network | `QuoteApi` | A fake that can be told to fail |

A test that passes on one target and fails on the other is the most valuable signal this repo
produces: it means something in `commonMain` is not actually common.

### UI tests, and the gap that created them

`commonTest` cannot catch a control that renders correctly and cannot be tapped. That bug shipped
— the iOS week-strip cell drew a `Color.clear` fill, SwiftUI does not hit-test a clear fill, and
the tap target shrank to its 1pt border for *unchecked* days only. It passed every gate, because
every gate was a compile or a pure-logic test.

| Suite | Runs on | Needs a device |
| --- | --- | --- |
| `androidApp/src/test/.../WeekStripTest.kt` | JVM, via Robolectric | no |
| `iosApp/StreakKitUITests/WeekStripUITests.swift` | iOS Simulator, XCUITest | yes |

Both query the **same accessibility identifiers** — `week-day-<iso-date>`, with a value of
`checked` or `not checked` — which come from `DayMark.accessibilityId` and
`DayMark.accessibilityState` in the shared module. One definition, so a cross-platform
interaction test stays genuinely comparable rather than two tests that merely look alike.

They were confirmed to fail before they were trusted: reverting `.contentShape(Circle())` turns
two of the three iOS tests red with the diagnostic they carry. A regression test that has never
failed proves nothing.

## What is deliberately missing

No authentication, no sync, no widgets, no notification scheduling, no navigation beyond a single
screen and a sheet. Each would add surface without adding a new *kind* of problem, and the
repository's second job — demonstrating Claude Code on a cross-platform codebase — is better
served by a codebase small enough to read in one sitting.
