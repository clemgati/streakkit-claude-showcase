---
name: kmp-expect-actual
description: How to add or change an expect/actual declaration in the shared module without breaking one platform silently. Use when shared code needs platform-specific behaviour (storage, system APIs, HTTP engines, device info) or when a build fails with a missing actual.
when_to_use: Adding platform-specific behaviour to commonMain, or diagnosing a missing/unmatched actual.
paths: "shared/src/**/*.kt,shared/build.gradle.kts"
allowed-tools: Read, Grep, Glob, Write, Edit, Bash(python3 *), Bash(./gradlew :shared:*), mcp__streakkit
---

# expect / actual in StreakKit

The failure mode this skill exists to prevent: you add an `expect`, write the Android `actual`,
build Android, and ship. iOS does not compile — but nothing told you, because you never built it.
`./gradlew :shared:allTests` builds both. Run it.

## Decide whether you need expect/actual at all

Most "platform-specific" needs are not. In order of preference:

1. **An interface in `commonMain` + a platform implementation injected by the app.** This is how
   `KeyValueStore` works: the *interface* is common, so tests can fake it, and only the factory
   is `expect`. Prefer this — an `expect class` cannot be faked in `commonTest`.
2. **`expect fun` returning a common interface.** The pattern used by `platformKeyValueStore()`
   and `createHttpClient()`. Small surface, easy to match.
3. **`expect val`** for a constant that differs, like `platformName`.
4. **`expect class`** — last resort. It cannot be instantiated in common tests and its
   constructors must match exactly on every target.

## Adding one

```bash
python3 .claude/skills/kmp-expect-actual/scripts/new_expect_actual.py \
  --name platformClipboard --kind fun --returns String
```

The script writes all three files with matching signatures and the right package, because the
part that goes wrong by hand is the signature drifting between `androidMain` and `iosMain`.
Review what it generated; it is a starting point, not a finished implementation.

## Rules

- **Visibility must match.** An `internal expect` needs `internal actual` on every platform.
  Keep these `internal` unless an app genuinely calls them — everything public ends up in the
  generated Objective-C header and makes the Swift API worse.
- **Three Apple targets, one source set.** `iosX64`, `iosArm64` and `iosSimulatorArm64` all
  compile `iosMain`. You write the actual once; it must work on both device and simulator.
- **Android actuals may use the Android SDK; they may not assume an Activity.** There is no
  context in `commonMain`, which is why `StreakKitAndroid.initialize(context)` exists and is
  called from `Application.onCreate()`. If your actual needs a context, use that, and fail with a
  message that says what to call — not an NPE.
- **iOS actuals may use `platform.Foundation` / `platform.UIKit`.** Objective-C interop names
  look odd in Kotlin (`NSUserDefaults.standardUserDefaults`, `setObject(value, forKey = key)`);
  that is expected.

## Verify

```
mcp__streakkit__expect_actual_matrix
```

Every row must show ✅ for both platforms, and `orphan_actuals` must be empty — an `actual` with
no `expect` means a rename landed on one side only. Then `./gradlew :shared:allTests`, which is
the real gate because it compiles Kotlin/Native too.
