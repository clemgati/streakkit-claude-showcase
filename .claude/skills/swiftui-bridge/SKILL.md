---
name: swiftui-bridge
description: How SwiftUI consumes the shared Kotlin module in StreakKit — the Flow-to-Observable bridge, Kotlin-to-Swift type mapping, and the rules that keep the Swift call sites readable. Use when writing or reviewing anything under iosApp/, or when a Kotlin change breaks the Swift build.
when_to_use: Editing SwiftUI views or HabitsViewModel, or fixing a Swift build error that originates in a Kotlin API change.
paths: "iosApp/**/*.swift,iosApp/project.yml,shared/src/commonMain/**/presentation/*.kt"
allowed-tools: Read, Grep, Glob, Write, Edit, Bash(xcodebuild*), Bash(xcrun simctl*), Bash(xcodegen*)
---

# The Swift side of the bridge

There is exactly one file where Kotlin meets Swift: `iosApp/StreakKit/Model/HabitsViewModel.swift`.
Everything above it is ordinary SwiftUI; everything below it is ordinary Kotlin. Keep it that way
— the moment a view calls into `Shared` directly, the next Kotlin rename breaks five files
instead of one.

## How state flows

```
HabitsStore.state : StateFlow<HabitsUiState>     (commonMain)
        │  observeState(onEach:) → StoreSubscription
        ▼
HabitsViewModel.state : HabitsUiState            (@Observable)
        ▼
SwiftUI views
```

Swift cannot observe a Kotlin `Flow`. `observeState` is the shim: it collects on
`Dispatchers.Main` and hands each value to a closure, returning a `StoreSubscription` that
`deinit` cancels. If you add another shared flow, give it the same shape rather than exposing the
flow itself.

## Kotlin → Swift type mapping

| Kotlin | Swift | Watch out |
| --- | --- | --- |
| `Int` | `Int32` | Needs `Int(...)` before SwiftUI string interpolation |
| `Float` | `Float` | `ProgressView(value:)` wants `Double` |
| `String?` | `String?` | Fine |
| `List<T>` | `[T]` | Fine |
| `data class` | a class | **Reference type in Swift.** No value semantics, no synthesised `==` |
| `object Foo` | `Foo.shared` | `StreakKit.shared.createStore()` |
| default arguments | *lost* | Objective-C gets the full initialiser only |
| sealed class / generics | awkward | Do not export; flatten in `commonMain` first |

`HabitSummary` is made `Identifiable` by a one-line Swift extension because it already carries a
stable `id`. `DayMark` has no id, so `ForEach` keys on `\.isoDate`.

## Rules

- **No logic in views.** Streaks, percentages and labels arrive pre-computed on `HabitSummary`.
  If a view needs to compute something, add it to `summarize()` in `commonMain` so Compose gets
  the same value. This is the single rule that keeps the two apps in parity.
- **Keep the shared public API primitive.** Exposing a `kotlinx-datetime` or coroutines type
  forces `shared/build.gradle.kts` to `export()` that library and makes every Swift call site
  worse. `DayMark.isoDate` is a `String` for exactly this reason.
- **Swift 5 language mode is deliberate.** Kotlin/Native's generated Objective-C types are not
  `Sendable`, so Swift 6 strict concurrency would need `@unchecked` wrappers through the whole
  bridge. `SWIFT_VERSION` is pinned in `iosApp/project.yml` with that note.
- **Edit `project.yml`, not the `.xcodeproj`.** Both are committed — the YAML is what you review,
  the generated project is what CI builds. After changing the YAML run `cd iosApp && xcodegen
  generate` and commit both.

## When the Swift build breaks after a Kotlin change

The Swift error names the **old** symbol, because the generated header is stale or the API moved.
Work in this order:

1. `./gradlew :shared:assemble` — if Gradle failed, the Swift error is a symptom, not the cause.
2. If Gradle is green, the API genuinely changed: fix the Swift call site. Do not revert the
   Kotlin to make Swift compile.
3. `No such module 'Shared'` means the framework was not produced at all — check the
   `Compile Kotlin framework` build phase and that `ENABLE_USER_SCRIPT_SANDBOXING` is still `NO`.

Then `/ios:run` and look at the screenshot. A bridge bug compiles perfectly and renders nothing.
