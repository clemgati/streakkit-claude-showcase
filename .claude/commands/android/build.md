---
description: Build the Android debug APK and explain any failure.
allowed-tools: Bash(./gradlew :androidApp:assembleDebug*), Bash(./gradlew :shared:*), Read, Grep
model: sonnet
effort: low
---

```!
./gradlew :androidApp:assembleDebug --console=plain 2>&1 | grep -vE '^\s*$' | tail -30
```

## Your task

One-line verdict. If it failed, note that this repo sits on AGP 9 + Kotlin 2.4, where two
mistakes are easy to make and produce confusing errors:

1. **Applying `org.jetbrains.kotlin.android`.** AGP 9 has built-in Kotlin support and fails the
   build if that plugin is also applied. The version catalog deliberately does not contain it.
2. **Applying `com.android.library` to the shared module.** AGP 9 refuses it alongside the
   Kotlin Multiplatform plugin; `shared` uses `com.android.kotlin.multiplatform.library` with the
   `kotlin { androidLibrary { … } }` DSL instead.

If the error mentions a `compileSdk` below what a dependency requires, the fix is the
`compileSdk`/`targetSdk` values in `gradle/libs.versions.toml` plus
`sdkmanager "platforms;android-<N>"` — not pinning the dependency backwards.

Do not run `./gradlew clean` to make an error go away. It hides incremental-build bugs and costs
minutes; find the actual cause.
