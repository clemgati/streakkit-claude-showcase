# Claude Code feature map

Every Claude Code capability this repository demonstrates, where it lives, and how to try it.
Each entry is wired to something the app actually needs — nothing here is a toy that exists only
to be a toy.

Verified against Claude Code **2.1.311**.

---

## 1. Project memory — `CLAUDE.md`

| File | Loaded when |
| --- | --- |
| `CLAUDE.md` | Always |
| `shared/CLAUDE.md` | Working in the shared module |
| `androidApp/CLAUDE.md` | Working in the Android app |
| `iosApp/CLAUDE.md` | Working in the iOS app |

**Why split it.** A single root file would carry Compose conventions into every iOS session and
Swift bridging rules into every Gradle session. Directory-scoped files load when you touch that
directory, so each session pays only for the context it uses.

**What belongs in them:** the things that are expensive to rediscover and invisible in the code —
that AGP 9 forbids the Kotlin Android plugin, that `withHostTestBuilder` is what makes JVM tests
run at all, that the Swift-facing API must stay primitive. Not a description of what the code
does; Claude can read the code.

**Try it:** `/init` regenerates a root file from scratch if you want to compare. `#` at the start
of a prompt appends a line to memory without opening an editor.

---

## 2. Slash commands — `.claude/commands/`

Namespaced by directory, so `.claude/commands/kmp/test.md` is `/kmp:test`.

| Command | Does | Feature it demonstrates |
| --- | --- | --- |
| `/kmp:test` | Runs `:shared:allTests`, explains failures | `` !`bash` `` pre-execution, `model`, `effort` |
| `/kmp:parity-check` | Cross-platform drift audit | MCP resource reference with `@server:uri` |
| `/kmp:new-feature <desc>` | Shared-logic-first feature workflow | `$ARGUMENTS`, `@file` refs, `disable-model-invocation` |
| `/android:build`, `/ios:build` | Build + explain the failure | `allowed-tools`, `model`/`effort` |
| `/android:run`, `/ios:run` | Install, launch, screenshot | Multi-command `` ```! `` blocks, image reading |
| `/ship:release-notes [ref]` | Release notes from git history | `$ARGUMENTS` in prose, one-command `!` blocks |
| `/ship:triage <issue>` | Triage a GitHub issue | `$ARGUMENTS` in a `!` block, `gh` + MCP |
| `/dev:checkpoint` | Write a handoff note | See [context engineering](#8-context-engineering) |

**Mechanics worth copying:**

```markdown
---
description: What it does — Claude uses this to decide when to auto-invoke
argument-hint: "[simulator name]"
allowed-tools: Bash(xcodebuild*), Read, Grep   # pre-approved for this turn only
model: sonnet                                   # cheap model for a cheap job
effort: low
disable-model-invocation: true                  # user-only; never auto-triggered
---

```!
./gradlew :shared:allTests --console=plain | tail -40 || true
```
```

The `` ! `` block runs **before** Claude sees the file, so its output is already in context. That
is the difference between a command and a saved prompt: `/kmp:test` arrives with the test results
already in hand instead of spending a turn fetching them.

`disable-model-invocation: true` on `/kmp:new-feature` and `/ship:triage` is deliberate — both
are expensive and both should be a human's decision.

### Rules for `!` blocks, learned the hard way

A `!` block runs **before** Claude sees the command, and **any non-zero exit silently aborts the
whole command**. There is no error message: `claude -p "/your-command"` returns an empty result,
zero turns, and exit status 0. Every command in this repo shipped broken for exactly this reason,
and it took a dozen throwaway probe commands to localise.

What breaks a block, all established by bisection:

| Construct | Works? | Why |
| --- | --- | --- |
| `$1`, `$2` | no | Fails whether or not an argument is supplied |
| `${1:-default}` | no | `$1` is substituted *inside* the braces, producing invalid bash |
| `X="value"` on its own line | no | A bare assignment is a subcommand the permission layer cannot classify |
| `$(cmd)` inside a pipeline | no | Commands inside `$()` are checked separately, and the compound shape matches no rule |
| `$ARGUMENTS` | yes | Substituted as plain text |
| `$(cmd)` on its own, e.g. `echo "$(git describe)"` | yes | |
| pipelines, `|| true`, `if/then/fi` | yes | |

**The rule that falls out: one simple command per block, ending in `|| true`, using `$ARGUMENTS`
and never `$1`.** Anything more elaborate belongs in the prose, where you ask Claude to run it as
an ordinary tool call.

`/ship:release-notes` shows the shape — two one-line blocks give the last tag and the recent
history, and the prose tells Claude to run `git diff --stat <ref>..HEAD` itself when it needs
more.

**How to tell you have hit this:** `turns=0` with an empty result.

```bash
claude -p "/your-command" --output-format json \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["num_turns"])'
```

Zero means the expansion aborted. An *unknown* command behaves differently — one turn, and a
reply saying it does not exist — so `turns=0` specifically means "recognised, then failed to
expand".

---

## 3. Skills — `.claude/skills/`

Skills differ from commands in that **Claude loads them itself**, when the description matches
what it is doing.

| Skill | Auto-loads for | Shows |
| --- | --- | --- |
| `streak-rules` | `shared/src/**`, both habit-card files | Progressive disclosure: `SKILL.md` + `reference.md` |
| `kmp-expect-actual` | `shared/src/**`, `build.gradle.kts` | A bundled executable script |
| `compose-screen` | `androidApp/src/**` | Path-scoped loading |
| `swiftui-bridge` | `iosApp/**`, shared presentation | Path-scoped loading |

**Progressive disclosure** is the idea worth stealing. `streak-rules/SKILL.md` carries the four
invariants — short, always worth having. The full edge-case table with expected values lives in
`reference.md`, which is only read when the skill is already engaged. The alternative, one big
file, means every session that touches the shared module pays for a table it will not use.

**Bundled scripts.** `kmp-expect-actual/scripts/new_expect_actual.py` writes a matched
`expect`/`actual` triple across three source sets. Deterministic mechanical work belongs in a
script the skill invokes, not in tokens the model spends:

```bash
python3 .claude/skills/kmp-expect-actual/scripts/new_expect_actual.py \
  --name platformClipboard --kind fun --returns String
```

**`paths:` scoping** means `compose-screen` never loads during an iOS session. Combined with
module-scoped `CLAUDE.md` files, a session's starting context reflects what you are working on.

> Skill frontmatter also supports a `hooks:` key for registering session-scoped hooks on
> invocation. It is not used here — the published shape was ambiguous at the time of writing, and
> a reference repository should not ship configuration its author is guessing at. Check
> `code.claude.com/docs/en/skills` before adding one.

---

## 4. Subagents — `.claude/agents/`

| Agent | Tools | Model | Role |
| --- | --- | --- | --- |
| `kmp-parity-auditor` | Read, Grep, Glob, Bash, `mcp__streakkit` | sonnet | Finds cross-platform drift. Reports; never fixes. |
| `shared-test-writer` | Read, Grep, Glob, Edit, Write, Bash | sonnet | Writes tests only. `permissionMode: acceptEdits`. |
| `mobile-ui-reviewer` | Read-only + build commands | sonnet | Compose/SwiftUI review. Cannot edit. |

**Why these three.** Each has a job whose context is large and whose output is small — read six
files, answer in ten lines. That ratio is what a subagent is for: the reading happens in its
context window, not yours.

**Tool restriction is the design, not a safety blanket.** `mobile-ui-reviewer` has no `Edit`
because a reviewer that can edit will quietly fix things instead of telling you what is wrong.
`shared-test-writer` is told in its own prompt that it writes tests *only* and must stop if a
test cannot pass without a production change.

**Proactive delegation** comes from description wording: "Use proactively after any change to the
shared module" is what makes Claude reach for the auditor without being asked.

**Try it:** `@"kmp-parity-auditor (agent)" audit the habit card` forces a specific agent;
`/agents` manages them interactively.

---

## 5. Hooks — `.claude/hooks/` + `.claude/settings.json`

Nine hooks spanning the session lifecycle. Every one of them **fails open**: if `python3` is
missing, or a formatter is not installed, the hook exits 0 silently rather than breaking the
session. That rule is what makes committed hooks safe to inherit.

| Event | Script | What it does and why |
| --- | --- | --- |
| `SessionStart` | `session-start.sh` | Injects branch, JDK/Xcode versions, booted simulators, and which dev tools exist. ~100 tokens that prevent three failed tool calls. |
| `PreToolUse(Bash)` | `guard-dangerous-bash.sh` | Denies `rm -rf /`, force push, `reset --hard`, `clean -fdx`, cache deletion, `simctl erase all`. |
| `PreToolUse(Bash)` | *(prompt hook)* | A `type: "prompt"` hook on `rm` only, running Haiku, judging deletes the regex cannot classify. |
| `PreToolUse(Edit\|Write)` | `protect-secrets.sh` | Blocks edits to `local.properties`/keystores, and blocks writing anything that pattern-matches a credential. |
| `PostToolUse(Edit\|Write)` | `format-edited-file.sh` | ktlint/swiftformat on the file just written. |
| `SubagentStop` | `subagent-log.sh` | One line per finished subagent to `.claude/logs/`. |
| `PreCompact` | `pre-compact.sh` | Snapshots git state to `.claude/handoff.md` before summarisation. |
| `Stop` | `stop-summary.sh` | "N uncommitted files in: shared androidApp". |
| `Notification` | `notify.sh` | macOS banner + OSC 777 terminal sequence. `async: true`. |
| `SessionEnd` | `session-end.sh` | Appends to a session log. |

### The two guard hooks, and why both exist

`permissions.deny` in settings is **policy**: declarative, pattern-matched, fast. The guard hook
is **explanation**: it sees the actual command text and answers with a reason and an alternative —

> *Refusing `git reset --hard` — it discards uncommitted work with no undo. Use `git stash`
> first, or `git restore <path>` for specific files.*

A bare denial invites a rephrase-and-retry loop. A denial that names the safe alternative ends
the exchange in one turn. `protect-secrets.sh` does the thing rules genuinely cannot: it inspects
the *content* being written, which is the only moment a leaked key is catchable before it reaches
git.

### The prompt hook

```json
{
  "type": "prompt",
  "if": "Bash(rm *)",
  "model": "claude-haiku-4-5-20251001",
  "prompt": "… ${tool_input.command} … reply with permissionDecision JSON"
}
```

A model as a hook handler. Scoped to `rm` with `if:`, so it costs nothing on other commands, and
pinned to a small model because the question is simple. The regex guard catches known-bad shapes;
this catches novel ones. Layered, cheap, and each layer does what it is good at.

### What is deliberately *not* hooked

No test run on `Stop`. A `Stop` hook can block (exit 2) and force another turn, and on a repo
where the gate takes minutes that turns every session into a build loop. Tests run from
`/kmp:test`, where a human chose to wait.

**Disable everything:** `"disableAllHooks": true`, or delete the block from `settings.json`.
Inspect with `/hooks`.

---

## 6. Notifications

`notify.sh` fires on the `Notification` event, which is raised when Claude Code needs attention —
a permission prompt, or an idle session. Two delivery paths:

1. **OS banner** — `terminal-notifier` if present, else `osascript`. The AppleScript is built
   through `json.dumps` so a quote in the message cannot break it.
2. **`terminalSequence`** — an OSC 777 escape Claude Code writes to the terminal itself. tmux and
   iTerm2 turn it into a bell or a window badge, and it needs no binary at all.

Worth having on this repo specifically: Gradle syncs and Xcode builds are long enough that you
will tab away, and an unanswered permission prompt stalls the entire run.

The hook is `async: true` — a notification must never delay the thing it is announcing.

---

## 7. MCP servers

Full detail: **[`.claude/mcp/README.md`](../.claude/mcp/README.md)**.

Four servers in `.mcp.json` (committed), of which **one** is enabled by `settings.json`. That
split is the point: a committed `.mcp.json` is a *catalogue*, not a grant.

| Server | Transport | Needs |
| --- | --- | --- |
| `streakkit` | stdio, `python3` | nothing — enabled by default |
| `github` | HTTP | `GITHUB_PERSONAL_ACCESS_TOKEN` |
| `filesystem` | stdio, `npx` | Node |
| `context7` | HTTP | network |

**The local server** (`.claude/mcp/streakkit_mcp.py`, standard library only) demonstrates all
three MCP primitives:

- **Tools** — `expect_actual_matrix`, `ui_parity_report`, `streak_oracle`, `project_stats`,
  `run_gradle_task` (allow-listed).
- **Resources** — `@streakkit:streakkit://parity-matrix`, regenerated on every read.
- **Prompts** — `/mcp__streakkit__add-shared-field`, `/mcp__streakkit__investigate-parity-drift`,
  which appear in the slash-command menu.

**What makes a server worth writing.** Not "wrap an API" — this one answers questions that would
otherwise cost a dozen Grep calls *every session*: which actuals are missing, which UI fields
each app renders, and what a streak should be according to an implementation that is not the one
under test. The oracle is the sharpest of the three: two implementations of the same written
invariants disagreeing localises a bug immediately.

The same analysis runs in CI as a plain CLI invocation (`.github/workflows/ci.yml`), so the
parity gate is not something only an interactive session can check.

---

## 8. Context engineering

Four mechanisms, in increasing order of deliberateness:

1. **Scoped loading.** Module `CLAUDE.md` files and `paths:`-scoped skills mean a session starts
   with what it needs and not the rest.
2. **`PreCompact` hook.** `pre-compact.sh` writes git state, recent commits and diff stat to
   `.claude/handoff.md` before summarisation. Those details are cheap to recover from the
   filesystem and expensive to recover from a summary.
3. **`/dev:checkpoint`.** Writes the half a hook cannot: goal, what is done and which gate proved
   it, what is half-finished and the exact next edit, and — the valuable section — *decisions and
   why*. The diff survives compaction; the reasoning does not.
4. **Subagent delegation.** A parity audit reads six files to produce ten lines. Run in a
   subagent, the reading stays in its context.

**Practical loop for long work:**

```
…work…
/dev:checkpoint        # write down intent and rationale
/compact               # or let it happen; the hook has you covered either way
# or, for a clean break:
/clear
@.claude/handoff.md    # start the next session from the note
```

`autoCompactThreshold` (see `settings.local.json.example`) lowers the trigger point, which is
worth doing on a repo where a single Gradle failure can be a thousand lines. The `GRADLE_OPTS`
env setting in `settings.json` is the same idea applied earlier: `-Dorg.gradle.console=plain`
strips the progress-bar redraws that otherwise fill context with nothing.

---

## 9. Permissions and safety

Full detail: **[`06-permissions-and-safety.md`](06-permissions-and-safety.md)**.

- `permissions.allow` — the read-only and gate commands, pre-approved.
- `permissions.ask` — anything outward-facing: `git commit`, `git push`, `gh pr`, editing CI.
- `permissions.deny` — `local.properties`, keystores, `.env`, `~/.ssh`, `~/.aws`, `sudo`.
- `PreToolUse` guards — the programmatic layer described above.
- `settings.local.json.example` — shows both postures: locking out
  `--dangerously-skip-permissions` entirely, or loosening for a trusted machine.

The mode ladder, least to most autonomous: `plan` → `default` → `acceptEdits` → `auto` →
`bypassPermissions`. `--safe-mode` disables all customisation for debugging a broken config;
`--restricted` is for shared machines.

---

## 10. Automation

### Local, headless

| Script | What |
| --- | --- |
| `.claude/scripts/headless-review.sh` | `claude -p` review of a diff. `--permission-mode plan` (cannot edit), `--output-format json`, `--max-turns`. Exits 0/1/2 so it works as a gate. |
| `.claude/scripts/install-git-hooks.sh` | Installs it as `pre-push`. Opt-in, and exit 2 ("could not run") never blocks. |

### GitHub Actions

| Workflow | Trigger | Mode |
| --- | --- | --- |
| `ci.yml` | push, PR | Plain CI — tests on both targets, both app builds, lint, **plus the MCP parity gate** |
| `claude.yml` | `@claude` mention | Interactive |
| `claude-code-review.yml` | PR opened/updated | Automation — inline comments, told what to ignore |
| `claude-issue-triage.yml` | issue opened | Automation — labels + layer assessment |
| `nightly-parity.yml` | weekday cron | Automation — opens an issue only on new drift |

**The pattern that makes these useful:** each prompt says what *not* to report. A reviewer that
comments on everything gets muted within a week. `claude-code-review.yml` lists five defect
classes and then explicitly rules out formatting, naming, and idiomatic Compose/SwiftUI
differences — and says to post nothing when there is nothing.

`nightly-parity.yml` runs the cheap deterministic check first and passes its output into the
prompt, so the model spends its turn on judgement rather than rediscovery. It checks for an
existing open `parity` issue before opening one, because an audit that opens a duplicate every
night is an audit people turn off.

---

## 11. Status line

`.claude/hooks/statusline.sh` shows branch, uncommitted count, **whether a simulator and an
emulator are actually attached**, and the model. The device indicators are the point: "run it"
failing because nothing is booted is the most common false alarm in a mobile session, and the
status line answers it before you ask.

---

## 12. Built-ins worth knowing on this repo

| Thing | Use here |
| --- | --- |
| Plan mode (`shift+tab`) | Anything touching more than one layer. `/kmp:new-feature` assumes it. |
| `/code-review` | Your working diff, before pushing |
| `/security-review` | Before publishing — this repo reads a network API and writes device storage |
| `/compact`, `/clear`, `/rewind` | See [context engineering](#8-context-engineering) |
| `/mcp` | Connection status; enable a catalogued server for one session |
| `/hooks`, `/agents`, `/permissions`, `/config` | Inspect what is configured |
| `/doctor` | What Claude Code rejected in your settings |
| `/usage` | What all of this is costing |

---

## Where to start

Clone, run `claude`, then:

```
/kmp:test                     # the gate, with failures explained
/kmp:parity-check             # the local MCP server's live analysis
/android:run  ·  /ios:run     # build, launch, screenshot, and actually look
/kmp:new-feature add a weekly goal to each habit
```

The fourth one is the real demo: plan mode, the shared-logic-first order, the test gate, both
apps, and a parity check — the whole surface, on one small feature.
