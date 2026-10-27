# Context engineering

A KMP repo is a hostile environment for a context window. A single Gradle failure is a thousand
lines, an `xcodebuild` log is worse, and understanding one feature means reading it in three
places — shared module, Compose, SwiftUI. Everything below is a response to that.

## 1. Keep output small at the source

The cheapest token is the one never produced.

```json
"env": { "GRADLE_OPTS": "-Dorg.gradle.console=plain -Dorg.gradle.welcome=never" }
```

Gradle's rich console redraws progress bars continuously; captured as text, that is hundreds of
lines of nothing. `--console=plain` is also passed explicitly in every command and workflow.

The same instinct elsewhere: `/kmp:test` and `/android:build` pipe through `tail`, and the MCP
server's `run_gradle_task` returns the last 60 lines rather than the whole log — the verdict and
the failures are always at the end. (Filtering with `grep` is tempting but its `$` anchors break
`!` blocks; see the rules in docs/02.)

## 2. Load context by scope, not by default

| Mechanism | Effect |
| --- | --- |
| `shared/CLAUDE.md`, `androidApp/CLAUDE.md`, `iosApp/CLAUDE.md` | Platform rules load only when you touch that platform |
| `paths:` on skills | `compose-screen` never loads in an iOS session |
| `SessionStart` hook | ~100 tokens of environment state, replacing several failed tool calls |

A root `CLAUDE.md` containing everything would carry Swift bridging rules into every Gradle
session. Splitting by directory means a session's starting context reflects the work.

## 3. Progressive disclosure inside a skill

`streak-rules/SKILL.md` holds the four invariants — short, and worth having whenever shared code
is in play. The full edge-case table with expected values lives in `reference.md`, read only when
the skill is already engaged and the detail is actually wanted.

Same idea in the bundled script: `new_expect_actual.py` does deterministic mechanical work that
would otherwise be generated token by token, and three correct files cost one tool call.

## 4. Delegate wide reads to subagents

The ratio that makes a subagent worthwhile is **large input, small output**.

| Task | Reads | Returns |
| --- | --- | --- |
| Parity audit | 6 files + 2 MCP tools | A table and a verdict |
| UI review | Every changed view | A ranked list of findings |

Run inline, that reading lands in your context and stays there for the rest of the session. Run
in `kmp-parity-auditor`, it lands in the subagent's context and the answer is what comes back.

The `SubagentStop` hook logs each run to `.claude/logs/subagents.log`, so a surprising verdict can
be traced to which agent said it without re-running the audit.

## 5. Survive compaction deliberately

Compaction preserves meaning and loses detail. Exactly which details matter is predictable, so
both are handled:

**Automatic — `PreCompact` hook.** `pre-compact.sh` writes `handoff.md` with branch,
status, recent commits, and diff stat. Mechanical state, cheap to recover from the filesystem,
expensive to recover from a summary. It runs whether or not you were paying attention.

**Deliberate — `/dev:checkpoint`.** Writes the half a hook cannot:

| Section | Why it is there |
| --- | --- |
| Goal | One sentence. The thing a summary blurs first. |
| Done | Only what passed a gate, with the gate named. |
| In flight | The specific file and the specific next edit. |
| **Decided** | Choices and *why*. The diff survives compaction; the reasoning does not. |
| Open questions | What you were about to ask. |

The "Decided" section is the one that earns the command. A diff shows that `today` is a
constructor parameter; it does not show that this was to keep `commonTest` deterministic across
both targets, so a later session "simplifies" it back.

**The loop:**

```
…long work…
/dev:checkpoint
/compact                 # or let auto-compact happen
# or for a clean break:
/clear
@handoff.md
```

`autoCompactThreshold` (see `.claude/settings.local.json.example`) lowers the trigger point,
worth doing here because one Gradle failure can consume a large fraction of the window.

## 6. Prefer structured answers to raw files

`mcp__streakkit__expect_actual_matrix` returns a table. The alternative — grep `expect` across
`commonMain`, grep `actual` across two platform sets, cross-reference by hand — is a dozen tool
calls and a few hundred lines of file excerpts to reach the same three rows.

That is the test for whether a local MCP server is worth writing: does it collapse a recurring
multi-call investigation into one structured answer? If yes, it pays for itself every session.
If it is a thin wrapper around a shell command, it does not.

## 7. Hooks that reduce context rather than add to it

`format-edited-file.sh` runs ktlint/swiftformat on each edited file immediately. Without it,
formatting drift surfaces later as a lint failure, which costs a turn to read and a turn to fix —
and the fix arrives as a diff Claude did not write, which then needs reconciling. Formatting at
the moment of writing means the model's view of the file and the file on disk never diverge.

Note the inverse case: there is deliberately **no** test run on `Stop`. A `Stop` hook that runs a
multi-minute gate on every turn fills the window with build output nobody asked for.

## Summary

| Problem | Mechanism |
| --- | --- |
| Verbose tool output | `console=plain`, `tail`, `grep`, bounded MCP responses |
| Irrelevant rules loaded | Module `CLAUDE.md`, `paths:`-scoped skills |
| Detail lost to compaction | `PreCompact` hook + `/dev:checkpoint` |
| Wide reads polluting the session | Subagents |
| Repeated investigations | Local MCP server returning structured answers |
| Drift between model and disk | `PostToolUse` formatting |
