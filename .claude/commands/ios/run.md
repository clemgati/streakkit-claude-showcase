---
description: Build, install, launch the iOS app on a simulator and capture a screenshot so the change can be seen rather than assumed.
argument-hint: "[simulator name, default 'iPhone 17 Pro']"
allowed-tools: Bash(xcodebuild*), Bash(xcrun simctl*), Read
---

## Already-booted simulators

```!
xcrun simctl list devices booted || true
```

## Your task

Use **iPhone 17 Pro** unless `$ARGUMENTS` names a different simulator. Run these yourself, one at
a time, stopping at the first real failure:

```
xcrun simctl boot "iPhone 17 Pro"            # harmless if already booted
cd iosApp && xcodebuild -project StreakKit.xcodeproj -scheme StreakKit -configuration Debug \
  -destination "platform=iOS Simulator,name=iPhone 17 Pro" \
  -derivedDataPath build/DerivedData build
xcrun simctl install "iPhone 17 Pro" iosApp/build/DerivedData/Build/Products/Debug-iphonesimulator/StreakKit.app
xcrun simctl launch "iPhone 17 Pro" dev.clement.streakkit.ios
xcrun simctl io "iPhone 17 Pro" screenshot /tmp/streakkit-ios.png
```

Give it a few seconds to draw, then **read `/tmp/streakkit-ios.png`** and describe what is
actually on screen — not what the code implies should be. A screenshot is the only cheap way to
catch the failures that compile perfectly: a view rendering off-screen, a `ProgressView` that
never resolves, text truncated to "iOS…", a tap target too small to hit.

Check specifically:

- the quote card resolved to a quote rather than a spinner — still loading after a few seconds
  means the network call is hanging, not slow;
- the streak numbers and week strip match what `mcp__streakkit__streak_oracle` says for the
  seeded data;
- the weekly-goal line agrees with the ticks directly above it (invariant 5);
- nothing is clipped at either edge.

If something looks wrong, say what and why before proposing a fix.

> The build and install run as tool calls rather than in the `!` block above, so a failure is
> something you can see and report. A multi-minute build inside a `!` block blocks expansion
> silently, and any non-zero exit aborts the command with no output at all. See the `!` block
> rules in `docs/02-claude-code-feature-map.md`.
