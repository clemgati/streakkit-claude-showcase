---
description: Triage a GitHub issue against this codebase — reproduce it in the shared module if possible, locate the layer, and propose labels.
argument-hint: "<issue-number>"
allowed-tools: Bash(gh issue view*), Bash(gh issue list*), mcp__streakkit, Read, Grep, Glob
disable-model-invocation: true
---

## Issue #$ARGUMENTS

```!
gh issue view "$ARGUMENTS" --json number,title,body,labels,comments \
  --template '{{.title}}

{{.body}}

Labels: {{range .labels}}{{.name}} {{end}}
Comments: {{len .comments}}' 2>&1 | head -60 || true
```

## Similar open issues

```!
gh issue list --state open --limit 15 --json number,title \
  --template '{{range .}}#{{.number}} {{.title}}
{{end}}' 2>&1 | head -20 || true
```

## Your task

Produce a triage note. Do not edit code, do not comment on the issue, do not apply labels — the
user decides whether to act on this.

1. **Which layer?** Decide between: shared domain, shared presentation, Android UI, iOS UI,
   build/tooling. The deciding question is whether the report mentions one platform or both.
   A bug on both platforms is a `commonMain` bug and should be reproduced there.
2. **Reproduce in the cheapest place.** If it is a streak or stats complaint, call
   `mcp__streakkit__streak_oracle` with the dates from the report. That either confirms the
   expected value in seconds or shows the reporter's expectation was wrong — both outcomes end
   the triage.
3. **Point at code.** Name the file and function you believe is responsible, and say how
   confident you are. "Probably `summarize()` — the report shows a rounding difference" is a
   useful answer; "needs investigation" is not.
4. **Propose labels** from: `shared`, `android`, `ios`, `build`, `bug`, `parity`, `needs-repro`,
   `good-first-issue`. Use `parity` whenever the two platforms disagree — those are the ones
   worth prioritising, because they mean the shared module is not doing its job.
5. **Duplicate?** Say which of the open issues above it duplicates, if any.

Finish with a single suggested next action, and the exact `gh` command the user could run to
apply your labels — so they can paste it if they agree, and ignore it if they do not.
