# After delivery

Everything in [03-development-workflow.md](03-development-workflow.md) assumes a human is
driving. This document is the other half: what runs when nobody is at the keyboard.

Nothing here is live yet — the repository has no remote. The workflows are committed and inert,
and this document is the checklist for turning them on.

## Turning it on

```bash
gh repo create streakkit-claude-showcase --private --source=. --remote=origin --push

# Install the GitHub App, add the secret and the workflow files in one step:
claude      # then: /install-github-app
```

Or manually: install the [Claude GitHub App](https://github.com/apps/claude), then add **one** of

| Secret | For |
| --- | --- |
| `ANTHROPIC_API_KEY` | A Claude API key from the Console |
| `CLAUDE_CODE_OAUTH_TOKEN` | A subscription. Generate with `claude setup-token` |

If you use the OAuth token, swap the `anthropic_api_key:` line in each workflow for
`claude_code_oauth_token: ${{ secrets.CLAUDE_CODE_OAUTH_TOKEN }}` — the line is commented in
`claude.yml` ready to uncomment.

> **Who can trigger a run.** The action rejects actors without write access, and rejects bots
> unless listed in `allowed_bots`. That check also applies to scheduled runs, which GitHub
> attributes to whoever last edited the cron.

## What runs, and when

### `ci.yml` — every push and PR

Four jobs: shared tests on **macOS** (because `:shared:allTests` includes the Kotlin/Native
target — running only the JVM half on Linux would skip exactly the bugs this repo cares about),
the Android APK, an iOS simulator build, and formatting.

The shared job also runs a **parity gate**: it drives `.claude/mcp/streakkit_mcp.py` over stdio
from a plain Python snippet and fails the build on a missing `actual`, warning on one-sided UI
fields. The same code that answers `mcp__streakkit__expect_actual_matrix` in a session is the
code that gates the branch — the analysis is not something only an interactive session can do.

### `claude.yml` — `@claude` mentions

Interactive mode: no `prompt` input, so it waits to be mentioned in an issue or PR comment. The
repository is checked out first, so `CLAUDE.md`, the skills, and the `.mcp.json` catalogue all
apply exactly as they do locally. Capped with `--max-turns 20` and a tool allow-list.

```
@claude the streak resets at midnight for users in UTC+13 — is that the yesterday-anchor rule?
@claude add a weekly goal field to Habit, shared logic and both apps
```

### `claude-code-review.yml` — every non-draft PR

Automation mode. The prompt names five defect classes, in priority order, and then — just as
importantly — names what to stay silent about: formatting, naming, idiomatic Compose/SwiftUI
differences, and "looks good" comments. **A reviewer that comments on everything gets muted
within a week**, at which point it is worse than nothing.

The top-priority class is this repo's characteristic defect: business logic that escaped into a
view. It compiles, it passes tests, and it is the beginning of every parity bug.

### `claude-issue-triage.yml` — every new issue

Decides the layer, applies labels, and posts one comment. The useful trick: when a report is
about a streak number and includes dates, it runs the **streak oracle** before assuming a bug.
Most "my streak is wrong" reports are the yesterday-anchor invariant working correctly, and the
oracle settles that in seconds rather than a round trip with the reporter.

Labels: `shared`, `android`, `ios`, `build`, `bug`, `parity`, `needs-repro`, `good-first-issue`.
`parity` is the one to watch — it means the shared module is not doing its job.

### `nightly-parity.yml` — weekdays at 06:00 UTC

The check nobody remembers to run. Parity decays *between* releases: a fix lands on iOS, the
Android equivalent is "tomorrow", and tomorrow does not come.

Two-stage by design. The deterministic MCP report runs first and is passed into the prompt, so
the model spends its turn on judgement rather than rediscovery. Then it checks
`gh issue list --label parity --state open` before opening anything — an audit that files a
duplicate every night is an audit people disable. Most nights it prints "Parity holds" and exits.

`workflow_dispatch` is enabled so you can try it without waiting for the cron.

## Scheduled work from your own machine

Workflows need a remote. For local recurring work there is `/loop`:

```
/loop 30m /kmp:parity-check      # run it on an interval
/loop /kmp:test                  # no interval — Claude paces itself
```

Useful while a long refactor is in flight and you want drift caught within the session rather
than at 06:00 the next day.

## Releases

```
/ship:release-notes v0.1.0
```

Reads the commits since the ref and writes **Added / Fixed / Internal** in terms of user-visible
effects rather than commit subjects. It is told to flag any change that landed on only one
platform — on this codebase that is itself a signal, because a shared-module change should affect
both, and one that does not may be a parity bug rather than deliberate scope.

It stops at the draft. Tagging and publishing stay manual; `gh release` is in `permissions.ask`.

## Incident loop

When a user reports a wrong number:

1. **`/ship:triage <issue>`** — layer, oracle check, labels, suspected file.
2. If the oracle disagrees with the app, it is a real bug: `shared-test-writer` writes the
   failing case *first* and confirms it fails against current code. A regression test that never
   failed proves nothing.
3. Fix in `commonMain`. Both apps inherit it; neither needs a change.
4. `/kmp:parity-check` before merging, `/ship:release-notes` after.

Step 3 is the payoff for the architecture. A streak bug is one fix, in one place, shipped to both
platforms — and the test that proves it runs on both targets.

## Cost control

- `--max-turns` on every workflow.
- `timeout-minutes` on every job.
- `concurrency` with `cancel-in-progress` in `ci.yml`.
- Smaller models where the task is small (`--model claude-sonnet-5` on triage, Haiku on the
  `rm` safety hook).
- A short `CLAUDE.md` — it is read on every run, local and remote alike.
- `/usage` locally; the analytics dashboard for an organisation.
