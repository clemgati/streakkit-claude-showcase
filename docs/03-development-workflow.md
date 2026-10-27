# Development workflow

How a change actually gets made here, and which Claude Code feature carries each step. This is
the "during development" half; [04-post-delivery-operations.md](04-post-delivery-operations.md)
is the other.

## First run

```bash
git clone <repo> && cd streakkit-claude-showcase
printf 'sdk.dir=%s/Library/Android/sdk\n' "$HOME" > local.properties
./gradlew :shared:allTests          # downloads Kotlin/Native on first run; ~2 minutes
claude
```

**Accept the trust prompt the first time `claude` starts here.** Workspace trust gates more than
permissions: an untrusted folder contributes no slash commands, no skills, no subagents and no
hooks, and its `permissions.allow` rules are ignored. The symptom is not an error — `/kmp:test`
is simply an unknown command, and in `claude -p` it returns an empty result with zero turns and
no diagnostic. If the repo's tooling appears to do nothing, check trust first:

```bash
claude -p "hello" 2>&1 >/dev/null | head -2   # prints the "not been trusted" notice if untrusted
```

The `SessionStart` hook reports what is installed and what is missing. Nothing in `.claude/`
requires the optional tools — ktlint, swiftformat and xcodegen are all degrade-gracefully.

## The loop for a real change

### 1. Plan before editing

Anything touching more than one layer starts in plan mode (`shift+tab`) or with
`/kmp:new-feature <description>`. The reason is specific to this architecture: the expensive
mistake is deciding *where* a value gets computed, and that decision is nearly free to correct
in a plan and expensive to correct across three layers and two apps.

`/kmp:new-feature` pre-loads `docs/01-architecture.md` and `HabitsUiState.kt` with `@`, so the
plan is written against the actual contract rather than a guess at it.

### 2. Shared logic first, with a failing test

| Order | Layer | Gate |
| --- | --- | --- |
| 1 | `domain` — types and pure logic | `./gradlew :shared:allTests` |
| 2 | `commonTest` — a test that fails before 1 and passes after | same |
| 3 | `presentation` — expose it on `HabitSummary`, pre-formatted | same |
| 4 | `androidApp` | `./gradlew :androidApp:assembleDebug` |
| 5 | `iosApp` | `/ios:build` |
| 6 | parity | `/kmp:parity-check` |

The order is load-bearing. By the time you reach step 4, every value the UI needs already exists
and is already tested, so both UIs are a rendering job with nothing left to decide — which is
exactly why they cannot drift.

Delegate step 2 to `shared-test-writer`; it knows the fixtures and the naming convention.

### 3. Run `/kmp:test`, not `./gradlew`

Same command underneath, but the slash command arrives with the output already in context and
with instructions for interpreting it — in particular, that a failure on **one** target is a
different class of bug than a failure on both, and that the test is not the thing to change.

### 4. Look at both apps

`/android:run` and `/ios:run` build, install, launch and screenshot. Then *read the screenshot*.
A UI change that was not looked at has not been verified: previews and compilers both pass
cleanly on views that render off-screen, spin forever, or truncate to "iOS…".

Compare the two screenshots. Streak numbers, percentages and week strips must be identical —
they come from the same `HabitSummary`. A difference is a parity bug, not a styling choice.

### 5. Review before committing

```
/kmp:parity-check    # cross-platform drift
/code-review         # general review of the working diff
/security-review     # before publishing anything
```

`/kmp:parity-check` is the one specific to this repo. It combines the MCP server's mechanical
report with a side-by-side read of the two implementations, looking for the class of difference
that compiles fine on both platforms.

### 6. Commit

Claude does not commit unless asked — `git commit` and `git push` are in `permissions.ask`, and
the root `CLAUDE.md` says so explicitly. When you do ask, the `Stop` hook has already told you
how many files are uncommitted and in which modules.

Optional: `.claude/scripts/install-git-hooks.sh` adds a pre-push review that blocks only on
BLOCKER findings and never blocks when it cannot run.

## Working with context on long sessions

Gradle and Xcode produce enormous output; a single failed build can be a thousand lines. Three
habits:

- **`GRADLE_OPTS=-Dorg.gradle.console=plain`** is already set in `settings.json`. It removes the
  progress-bar redraws, which are pure context cost.
- **Delegate wide reads.** "Audit parity" is six files in, ten lines out — that belongs in
  `kmp-parity-auditor`, whose context is not yours.
- **`/dev:checkpoint` before you walk away.** The `PreCompact` hook saves git state
  automatically; the command saves intent and rationale, which is the part that cannot be
  recovered from the filesystem.

## When the build breaks

| Symptom | Likely cause |
| --- | --- |
| `No such module 'Shared'` in Swift | Gradle failed in the Xcode build phase. Run `./gradlew :shared:assemble` to see the real error. |
| Swift error naming a symbol you just renamed | Expected — fix the Swift call site, do not revert the Kotlin. |
| `org.jetbrains.kotlin.android` plugin error | AGP 9 has built-in Kotlin. Remove the plugin. |
| `com.android.library` incompatible with KMP | Use `com.android.kotlin.multiplatform.library`. |
| A dependency demands a newer `compileSdk` | Bump it in `libs.versions.toml` and `sdkmanager "platforms;android-<N>"`. |
| JVM tests stopped running | `withHostTestBuilder {}.configure {}` was removed from `shared/build.gradle.kts`. |
| Tests pass on JVM, fail on Native | The valuable one. Something in `commonMain` is not actually common. |

`/android:build` and `/ios:build` each carry their platform's version of this table, so the
diagnosis arrives with the failure.

## Conventions a reviewer will enforce

- No computation in a composable or a SwiftUI view.
- New persisted fields on `Habit` have defaults.
- Tests named as sentences stating the rule.
- Nothing in `domain` reads a clock.
- New dependencies need a reason — each one is something the iOS framework links.
