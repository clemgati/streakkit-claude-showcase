---
description: Build the SwiftUI app for the iOS simulator, including the Kotlin framework, and explain any failure.
argument-hint: "[simulator name, default 'iPhone 17 Pro']"
allowed-tools: Bash(xcodebuild*), Bash(xcrun simctl list*), Bash(./gradlew :shared:*), Read, Grep
---

## Available simulators

```!
xcrun simctl list devices available | grep -E 'iPhone' | head -6
```

## Build

```!
cd iosApp && xcodebuild -project StreakKit.xcodeproj -scheme StreakKit -configuration Debug \
  -destination "platform=iOS Simulator,name=${1:-iPhone 17 Pro}" \
  -derivedDataPath build/DerivedData build 2>&1 | grep -E '(error|warning|BUILD)' | head -30
```

## Your task

Report the verdict in one line. If it failed, the cause is almost always one of these four, in
descending order of likelihood — check them in order rather than reading the whole log:

1. **The Kotlin framework did not rebuild.** The `Compile Kotlin framework` build phase runs
   `./gradlew :shared:embedAndSignAppleFrameworkForXcode`. If Gradle failed, the Swift error will
   look unrelated — usually `No such module 'Shared'`. Run `./gradlew :shared:assemble` directly
   to see the real error.
2. **A shared API changed and Swift was not updated.** Kotlin renames reach Swift through the
   generated Objective-C header, so the Swift error names the *old* symbol. Fix the Swift call
   site; do not revert the Kotlin.
3. **A new Kotlin type is not representable in Objective-C.** Default arguments, sealed classes,
   and generics with variance all bridge badly. The fix is in `commonMain`: expose something
   primitive instead.
4. **`ENABLE_USER_SCRIPT_SANDBOXING` got switched back on**, which blocks Gradle from running in
   the build phase. It is set to `NO` in `iosApp/project.yml` for exactly this reason.

If `project.yml` changed, regenerate first with `cd iosApp && xcodegen generate`, and commit both
the YAML and the regenerated `.xcodeproj`.
