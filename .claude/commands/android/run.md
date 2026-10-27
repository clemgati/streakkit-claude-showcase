---
description: Build, install, launch the Compose app on a running emulator or device and capture a screenshot.
allowed-tools: Bash(./gradlew :androidApp:assembleDebug*), Bash(adb*), Read
---

## Devices

```!
adb devices -l
```

## Build, install, launch, capture

```!
set -e
./gradlew :androidApp:assembleDebug --console=plain 2>&1 | tail -3
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk 2>&1 | tail -2
adb shell am start -n dev.clement.streakkit.android/.MainActivity 2>&1 | tail -1
sleep 5
adb exec-out screencap -p > /tmp/streakkit-android.png
echo "screenshot: /tmp/streakkit-android.png"
adb logcat -d -s AndroidRuntime:E 2>/dev/null | tail -15
```

## Your task

If the device list above was empty, stop and tell the user to start an emulator — do not start
one yourself, because booting the wrong AVD wastes several minutes and a lot of memory. Suggest:
`emulator -list-avds` then `emulator -avd <name>`.

Otherwise, read `/tmp/streakkit-android.png` and report what is on screen. Then compare it against
the iOS screenshot if one exists from `/ios:run`: the streak numbers, the completion percentages
and the week strip must be **identical**, because both come from the same shared `HabitSummary`.
Any difference is a parity bug, not a styling choice — run `/kmp:parity-check`.

Note that `adb logcat -d -s AndroidRuntime:E` prints crashes only. Empty output there means the
app is alive, not that it is correct.
