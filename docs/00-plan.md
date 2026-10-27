# Build plan — StreakKit, a Claude Code showcase on Kotlin Multiplatform

**Status:** approved and executed (2026-10-26)
**Repo:** `streakkit-claude-showcase`

---

## 1. Purpose

Two deliverables in one repository:

1. **A small, real KMP app.** Shared Kotlin business logic, a native **Jetpack Compose** Android
   app, and a native **SwiftUI** iOS app. Deliberately modest in features — it exists to be a
   credible host for the second deliverable.
2. **A worked example of every significant Claude Code capability**, wired into that app's actual
   development and operation: slash commands, skills, hooks, notifications, MCP (local + external),
   subagents, permissions/safety modes, context engineering, and post-delivery automation.

The test of success is not "the app is impressive." It is: *a developer can clone this repo, run
`claude`, and within ten minutes see each Claude Code feature doing something genuinely useful on
a cross-platform codebase.*

## 2. Why a habit tracker

The app needs a domain with **non-trivial pure logic** (so there is something worth testing,
reviewing, and delegating to subagents) and **platform divergence** (so KMP `expect`/`actual` and
two native UIs are justified), while needing **no API keys and no flaky network** (so CI and tests
stay deterministic).

Streak calculation satisfies this. "What is the current streak?" has real edge cases — timezone
boundaries, today-not-yet-checked-in, gaps, longest-ever vs. current — which makes it the natural
anchor for the TDD, code-review, and parity-audit demos.

Scope is fixed at four capabilities: **list habits, create a habit, check in / undo for a day,
view streak stats.** Plus one optional network call (a daily quote) purely to exercise the Ktor
layer and the "offline fallback" branch. No auth, no sync, no database migrations.

## 3. Architecture

```
shared/              Kotlin Multiplatform — the only place business logic lives
  commonMain         domain (Habit, StreakCalculator), data (repository, serialization),
                     remote (Ktor quote client), presentation (HabitsStore + StateFlow)
                     expect: KeyValueStore, platformName, httpEngine
  androidMain        actual: SharedPreferences-backed store, OkHttp engine
  iosMain            actual: NSUserDefaults-backed store, Darwin engine
  commonTest         StreakCalculator + repository tests (pure, fast, deterministic)

androidApp/          Jetpack Compose. Collects shared StateFlow directly.
iosApp/              SwiftUI. Observable wrapper bridging shared Flow -> @Published.
```

**Deliberate choices:**

- **No SQLDelight, no Koin, no Compose Multiplatform.** Persistence is a hand-written
  `expect`/`actual` key-value store over `kotlinx.serialization`. This keeps the build robust and
  makes `expect`/`actual` the *visible* centerpiece of the KMP story rather than hiding it behind a
  codegen plugin. The iOS UI is genuinely SwiftUI, per requirement.
- **Presentation logic is shared** (`HabitsStore` exposes a `StateFlow<HabitsUiState>`), so the
  "did both platforms stay in parity?" question is real and auditable — which is exactly what the
  `kmp-parity-auditor` subagent checks.

Detail: [`01-architecture.md`](01-architecture.md).

## 4. Claude Code surface to build

Every item below is a real file in this repo, exercised against real code. The full map of
*what lives where and how to run it* is [`02-claude-code-feature-map.md`](02-claude-code-feature-map.md).

| Capability | Concretely, in this repo |
| --- | --- |
| **Project memory** | Root `CLAUDE.md` plus module-scoped `shared/CLAUDE.md`, `androidApp/CLAUDE.md`, `iosApp/CLAUDE.md`, so platform rules load only when that module is in play |
| **Slash commands** | `.claude/commands/` — namespaced (`/kmp:*`, `/android:*`, `/ios:*`, `/ship:*`), demonstrating `$ARGUMENTS`/`$1`, `` !`bash` `` pre-execution, `@file` references, `allowed-tools`, `argument-hint`, `disable-model-invocation`, and a `model`/`effort` override |
| **Skills** | `.claude/skills/` — four skills, all `paths:`-scoped, with progressive disclosure (`SKILL.md` + `reference.md`) and an executable generator in `scripts/` |
| **Hooks** | `.claude/hooks/` wired in `settings.json` across the lifecycle: `SessionStart` (inject env state), `PreToolUse` (a regex guard **and** a `type: "prompt"` model-judged guard, plus secret scanning on edits), `PostToolUse` (ktlint/swiftformat on edit), `SubagentStop`, `PreCompact` (persist a handoff note), `Stop` (summary), `Notification` (macOS banner), `SessionEnd` |
| **Notifications** | `Notification` hook → `osascript` banner; `Stop` hook → terminal bell + summary line; documented `terminalSequence` variant for tmux/iTerm |
| **Automation** | Headless `claude -p` in `.claude/scripts/` (diff review in `plan` mode with JSON output, installable as a pre-push hook) and five GitHub Actions workflows; `/loop` for local recurring checks |
| **MCP — local** | `.claude/mcp/streakkit_mcp.py` — a dependency-free Python stdio MCP server exposing **tools** (expect/actual matrix, UI-field parity report, streak oracle, project stats, allow-listed gradle task), **resources** (version catalog, live parity matrix, architecture), and **prompts** (`/mcp__streakkit__*`) |
| **MCP — external** | `.mcp.json` with the official GitHub MCP server (HTTP), a filesystem server (stdio/npx), plus the Context7 docs server — all project-scoped and git-committed, with env-var expansion for tokens |
| **Subagents** | `.claude/agents/` — `kmp-parity-auditor`, `shared-test-writer`, `mobile-ui-reviewer`, each tool-restricted, model-pinned, with proactive-delegation wording |
| **Context engineering** | `PreCompact` hook writing `.claude/handoff.md`, a `/dev:checkpoint` command, `autoCompactThreshold` guidance, and the "read-only scouting in a subagent" pattern documented in [`07-context-engineering.md`](07-context-engineering.md) |
| **Safety / danger modes** | `permissions.allow`/`ask`/`deny` tuned for a Gradle+Xcode repo, secrets denied by path, `PreToolUse` guard script, `disableBypassPermissionsMode`, and a documented ladder from `--permission-mode plan` → `acceptEdits` → `auto` → `bypassPermissions`, plus `--safe-mode` and `--restricted` for debugging |
| **Post-delivery** | `@claude` responder, PR review, issue triage, nightly parity audit, release-note generation — all as committed workflows, documented in [`04-post-delivery-operations.md`](04-post-delivery-operations.md) |
| **Plan mode / review** | Documented entry points: `/code-review`, `/security-review`, plan-mode-first workflow for feature work |

## 5. Build order

1. **Docs first.** This plan, then architecture, then the feature map. The repo should read as
   intentional, not reverse-engineered.
2. **Gradle + shared module.** Wrapper (Gradle 9.8.2), version catalog, `shared` with domain,
   data, remote, presentation, `expect`/`actual` per platform, and `commonTest` coverage of
   streak math. Gate: `./gradlew :shared:allTests` green.
3. **Android app.** Compose UI over the shared store. Gate: `./gradlew :androidApp:assembleDebug`.
4. **iOS app.** XcodeGen `project.yml` (generated `.xcodeproj` also committed so the repo builds
   without XcodeGen installed), SwiftUI views, `embedAndSignAppleFrameworkForXcode` integration.
   Gate: `xcodebuild -scheme StreakKit -destination 'generic/platform=iOS Simulator' build`.
5. **Claude Code surface.** Everything in §4. Gate: hook scripts pass `shellcheck`-level sanity and
   run standalone; the MCP server answers `initialize`/`tools/list` over stdio.
6. **CI + automation.** Five workflows, plus headless scripts.
7. **Verify and commit.** Full build of both platforms, then `git init` + one commit. No push.

## 6. Toolchain decisions (resolved against this machine, 2026-10-26)

| Component | Version | Note |
| --- | --- | --- |
| JDK | Temurin 21.0.11 | Already present |
| Gradle | 9.8.2 (wrapper) | Matches installed CLI; AGP 9.x requires Gradle 9 |
| Kotlin | 2.4.21 | Latest stable on Maven Central |
| AGP | 9.4.1 | Latest stable |
| compileSdk / targetSdk | 37 | Required by AndroidX/OkHttp at these versions; installed during the build |
| Xcode | 26.6, iOS 26.5 simulators | Already present |
| Ktor | 3.6.0 | `okhttp` on Android, `darwin` on iOS |
| kotlinx: coroutines / serialization / datetime | 1.11.0 / 1.12.0 / 0.8.0 | |
| Compose BOM | 2026.09.00 | Android-only Compose |
| Dev tools | ktlint 1.8.0, swiftformat 0.64.3, xcodegen 2.46.0, node 26.12.1 | Installed for hook/MCP demos |

**Graceful degradation is a hard requirement.** Every hook script and every `npx`-based MCP server
must no-op with a clear message when its binary is absent, so someone cloning this repo onto a bare
machine still gets a working session. Verified by running each hook with the tool removed from
`PATH`.

## 7. Non-goals

- Production-grade app features (auth, sync, widgets, notifications scheduling).
- A design system. Styling is plain Material 3 / plain SwiftUI.
- Pushing to GitHub. Workflows are committed but inert until the repo is published; the
  post-delivery doc states exactly what to do then.
- Teaching Kotlin or SwiftUI. The code is idiomatic but the commentary is about Claude Code.

## 8. Risks and how they were handled

| Risk | Handling |
| --- | --- |
| AGP 9 / Kotlin 2.4 DSL drift from older tutorials | Versions resolved live from Maven metadata; build iterated until green rather than assumed |
| Hand-written `.xcodeproj` is fragile | XcodeGen generates it from a reviewable `project.yml`; both are committed |
| MCP servers needing npm on a fresh machine | The *local* server is pure-stdlib Python 3; npx-based servers are additive and documented as optional |
| Demo hooks that annoy in real use | Every hook is cheap, non-blocking where possible, and the feature map lists exactly how to disable each one (`disableAllHooks`, or deleting a block) |
| Secret leakage through a demo repo | `permissions.deny` covers `.env*`, `local.properties`, keystores; a `PreToolUse` guard blocks reads/writes outside the project |

## 9. Deviations from the plan, as executed

Recorded rather than retconned — the reasons are the useful part.

| Planned | Built | Why |
| --- | --- | --- |
| `expect class KeyValueStore` | `interface KeyValueStore` + `expect fun platformKeyValueStore()` | An `expect class` cannot be implemented in `commonTest`, which would have forced the repository tests to be platform-specific — the exact thing the module exists to avoid. Cost: `StreakKitAndroid.initialize(context)` at app startup. |
| `com.android.library` on `shared` | `com.android.kotlin.multiplatform.library` | AGP 9 refuses the old plugin alongside the KMP plugin, and has built-in Kotlin support that conflicts with `org.jetbrains.kotlin.android`. Found at build time, not predicted. |
| compileSdk 36 | compileSdk 37 | AndroidX and OkHttp at current versions require compiling against 37. The SDK platform was installed rather than pinning dependencies backwards. |
| A skill with a `hooks:` frontmatter block | Omitted, documented | Two documentation sources disagreed on the YAML shape. A reference repository should not ship configuration its author is guessing at; the feature map says so and points at the docs. |
| A `context: fork` skill | Omitted | Three subagents already cover the forked-execution case with clearer intent. A skill that forks purely to demonstrate forking would have been the kind of toy this repo set out not to contain. |
| `UserPromptSubmit` hook | Omitted | The useful content — branch, toolchain, device state — is already injected once at `SessionStart` and shown continuously in the status line. Re-injecting it on every prompt is pure context cost. |

Everything else landed as planned, including the build order and all six gates.
