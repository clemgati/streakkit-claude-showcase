# iosApp/ — SwiftUI

Loaded in addition to the root `CLAUDE.md` when you work in this directory.
Full conventions, including the Kotlin→Swift type table: `.claude/skills/swiftui-bridge/SKILL.md`
(auto-loads for files here).

## Shape

```
project.yml              XcodeGen source of truth. Edit this, not the .xcodeproj.
StreakKit.xcodeproj      Generated AND committed, so the repo builds without XcodeGen.
StreakKit/
  StreakKitApp.swift     @main
  Model/HabitsViewModel.swift   The ONLY file that imports Shared and talks to Kotlin.
  Views/                 Pure SwiftUI. No Shared imports beyond the state types they render.
```

## Rules

- **One bridge file.** `HabitsViewModel` wraps `HabitsStore.observeState(onEach:)` in
  `@Observable`. Views never call the store directly; if a view needs a new intent, add a method
  to the view model.
- Nothing is computed here. Streaks, percentages and labels arrive finished on `HabitSummary`.
- Kotlin `Int` arrives as `Int32` — wrap in `Int(...)` for interpolation. `Float` needs
  `Double(...)` for `ProgressView`.
- Kotlin `data class`es are **reference** types in Swift: no value semantics, no synthesised `==`.
- `SWIFT_VERSION` is pinned to 5.0 deliberately — Kotlin/Native's generated Objective-C types are
  not `Sendable`, and Swift 6 strict concurrency would need `@unchecked` wrappers throughout the
  bridge. Do not "fix" this without reading the note in `project.yml`.
- `ENABLE_USER_SCRIPT_SANDBOXING: NO` is required, or Gradle cannot run in the build phase.

## Gates

`/ios:build`, then `/ios:run` and look at the screenshot. After any change to how habit data is
rendered, `/kmp:parity-check`.

If Swift fails to compile after a Kotlin change, run `./gradlew :shared:assemble` first — a
Gradle failure surfaces as an unrelated-looking Swift error, usually `No such module 'Shared'`.
