---
description: Build, install, launch the Compose app on a running emulator or device and capture a screenshot.
allowed-tools: Bash(./gradlew :androidApp:assembleDebug*), Bash(adb*), Read
---

## Attached devices

```!
adb devices -l || true
```

## Your task

**If the list above is empty, stop.** Tell the user to start an emulator and do not start one
yourself — booting the wrong AVD costs several minutes and a lot of memory. Suggest
`emulator -list-avds`, then `emulator -avd <name>`.

Otherwise run these yourself, one at a time, stopping at the first failure:

```
./gradlew :androidApp:assembleDebug --console=plain
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n dev.clement.streakkit.android/.MainActivity
adb exec-out screencap -p > /tmp/streakkit-android.png
adb logcat -d -s AndroidRuntime:E
```

Give the app a few seconds to draw before the screenshot, then **read
`/tmp/streakkit-android.png`** and report what is on screen.

Then compare it against `/tmp/streakkit-ios.png` if `/ios:run` has produced one. The streak
numbers, the completion percentages and the week strip must be **identical** — both come from the
same shared `HabitSummary`. Any difference is a parity bug, not a styling choice; run
`/kmp:parity-check`.

Empty output from `adb logcat -d -s AndroidRuntime:E` means the app did not crash. It does not
mean the app is correct — that is what the screenshot is for.

> The install and launch deliberately happen as tool calls rather than in the `!` block above.
> A block that calls `adb install` with no device attached exits non-zero, and a non-zero exit
> silently aborts the whole command — which is exactly the failure this command is supposed to
> report. See the `!` block rules in `docs/02-claude-code-feature-map.md`.
