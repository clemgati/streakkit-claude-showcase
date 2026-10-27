---
description: Build, install, launch the iOS app on a simulator and capture a screenshot so the change can be seen rather than assumed.
argument-hint: "[simulator name, default 'iPhone 17 Pro']"
allowed-tools: Bash(xcodebuild*), Bash(xcrun simctl*), Read
---

```!
set -e
cd iosApp
xcrun simctl boot "iPhone 17 Pro" 2>/dev/null || true
xcrun simctl bootstatus "iPhone 17 Pro" -b >/dev/null 2>&1 || true
xcodebuild -project StreakKit.xcodeproj -scheme StreakKit -configuration Debug \
  -destination "platform=iOS Simulator,name=iPhone 17 Pro" \
  -derivedDataPath build/DerivedData build 2>&1 | tail -3
xcrun simctl install "iPhone 17 Pro" build/DerivedData/Build/Products/Debug-iphonesimulator/StreakKit.app
xcrun simctl launch "iPhone 17 Pro" dev.clement.streakkit.ios
sleep 4
xcrun simctl io "iPhone 17 Pro" screenshot /tmp/streakkit-ios.png
echo "screenshot: /tmp/streakkit-ios.png"
```

## Your task

The block uses iPhone 17 Pro. If `$ARGUMENTS` names another simulator, re-run for that one.

Read `/tmp/streakkit-ios.png` and describe what is actually on screen — not what the code implies
should be. A screenshot is the only cheap way to catch the failures that compile perfectly:
a view that renders off-screen, a `ProgressView` that never resolves, text truncated to "iOS…",
a tap target too small to hit.

Check specifically:
- the quote card resolved (it shows a quote, not a spinner) — if it is still loading after four
  seconds the network call is hanging, not slow;
- streak numbers and the week strip match what `mcp__streakkit__streak_oracle` would say for the
  seeded data;
- nothing is clipped at either edge.

If something looks wrong, say what and why before proposing a fix.
