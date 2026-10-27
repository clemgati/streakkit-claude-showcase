# MCP servers in this repo

Four servers are declared in `.mcp.json`, which is committed, so everyone who clones the repo is
offered the same set. Only one of them is enabled by default.

| Server | Transport | Needs | Enabled by default |
| --- | --- | --- | --- |
| `streakkit` | stdio (`python3`) | Python 3 only | **yes** |
| `github` | HTTP | `GITHUB_PERSONAL_ACCESS_TOKEN` | no |
| `filesystem` | stdio (`npx`) | Node/npm | no |
| `context7` | HTTP | network | no |

`.claude/settings.json` sets `"enabledMcpjsonServers": ["streakkit"]`. That is the whole point of
the split: a committed `.mcp.json` is a *catalogue*, not a grant. A server you did not opt into is
a server that cannot read your repository or spend your tokens. To turn the rest on, copy
`.claude/settings.local.json.example` and extend the list — or run `/mcp` and enable them for the
session only.

## The local server: `streakkit`

`streakkit_mcp.py` is ~400 lines of standard-library Python. It is here to answer the questions
that would otherwise cost a dozen Grep calls at the start of every session.

**Tools**

| Tool | What it answers |
| --- | --- |
| `expect_actual_matrix` | Which `expect` declarations are missing an `actual`, and on which platform |
| `ui_parity_report` | Which shared UI-state fields each app actually renders — the drift the compiler cannot see |
| `streak_oracle` | What the streak numbers *should* be, computed independently of the Kotlin |
| `project_stats` | File and line counts per module |
| `run_gradle_task` | Runs one **allow-listed** Gradle task and returns the tail of its output |

**Resources** — `@streakkit:streakkit://parity-matrix` (regenerated on every read),
`streakkit://version-catalog`, `streakkit://architecture`.

**Prompts** — `/mcp__streakkit__add-shared-field` and `/mcp__streakkit__investigate-parity-drift`.
These show up in the slash-command menu like any other command.

### Why `streak_oracle` is not redundant with the tests

The Kotlin tests assert that `StreakCalculator` matches *what the test author believed*. The
oracle is a second implementation of the same written invariants, in a different language, by a
different route. When a streak looks wrong, the two together localise the bug immediately: if the
oracle agrees with the tests, the bug is in the UI; if it disagrees with both, the invariant
itself was misread.

### Allow-listing inside the server

`run_gradle_task` refuses anything outside `ALLOWED_GRADLE_TASKS`. An MCP server runs with your
full user privileges, so it is a trust boundary like any other — a server that takes an arbitrary
shell string is a server that has handed out a shell. The allow-list lives in the server rather
than in Claude Code's permission rules because the server is the thing that can be sure.

## Trying it

```bash
# Handshake + list the tools, without starting a session:
printf '%s\n' \
  '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{}}}' \
  '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' \
  | python3 .claude/mcp/streakkit_mcp.py

# Inside a session:
/mcp                                  # see connection status for all four servers
@streakkit:streakkit://parity-matrix  # pull the live matrix into context
```

If the server does not appear, run `claude --debug=mcp` and check stderr: the server logs the repo
root it resolved on startup, which is the thing that is usually wrong.
