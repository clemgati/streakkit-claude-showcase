# Permissions, safety, and the danger flags

Four independent layers, each doing what the others cannot. They are listed in the order Claude
Code consults them.

```
1. permissions.deny      declarative, absolute          settings.json
2. PreToolUse hooks      sees the actual command/content  .claude/hooks/
3. permissions.ask       outward-facing actions           settings.json
4. permission mode       how much is asked at all         --permission-mode / shift+tab
```

## 1. Deny rules — policy

```json
"deny": [
  "Read(./local.properties)", "Read(./.env)", "Read(./.env.*)",
  "Read(./**/*.keystore)", "Read(./**/*.jks)",
  "Read(./**/google-services.json)", "Read(./**/GoogleService-Info.plist)",
  "Read(~/.ssh/**)", "Read(~/.aws/**)", "Read(~/.gnupg/**)", "Read(~/.claude/**)",
  "Bash(sudo *)"
]
```

Two things about this syntax that are easy to get wrong:

- **A `Read` deny also blocks `Edit` and `Write` on the same path**, and it extends to file
  commands Claude Code recognises in Bash — `cat`, `head`, `sed`, `tee`. There is no need for a
  matching `Edit` rule, and `Write(path)` rules are *never* matched at all: path rules are only
  checked against `Read(...)` and `Edit(...)`.
- **A single leading `/` is not an absolute path.** It anchors to the settings source. Use `//`
  for filesystem-absolute and `~/` for home. `Read(/secrets/**)` in user settings means
  `~/.claude/secrets/**`, which is almost certainly not what you meant.

`Read(~/.claude/**)` is here so a session cannot read your other projects' transcripts, which are
not this repository's business.

## 2. PreToolUse hooks — explanation, and content inspection

Rules are patterns. Hooks see the real thing, and can answer with a reason.

**`guard-dangerous-bash.sh`** denies `rm -rf /`, `rm -rf ~`, force push, `git reset --hard`,
`git clean -fdx`, deleting `~/.konan` or `~/.gradle`, `simctl erase all`, and
`sdkmanager --uninstall`. The selection criterion is narrow and worth stating: **damage that
survives `git checkout .`** — things a human could not undo either. It is not a sandbox.

Every denial names the safe alternative. That is not politeness; a bare refusal invites a
rephrase-and-retry loop, while "use `git stash` first" ends the exchange in one turn.

**`protect-secrets.sh`** does the thing rules cannot: it reads the *content* of an `Edit`/`Write`
and refuses anything matching an Anthropic key, a GitHub token, an AWS key id, a PEM private key
block, or a `password = "…"`-shaped literal. This is the only moment a leaked credential is
catchable before it reaches git.

**The prompt hook** (`type: "prompt"`, `if: "Bash(rm *)"`, Haiku) classifies deletes the regex
cannot: is this a build artifact or tracked source? Scoped to `rm` so it costs nothing elsewhere.

All three **fail open**. A hook that breaks the session when `python3` is missing is a hook people
delete, and a deleted hook protects nothing.

## 3. Ask rules — the outward-facing line

```json
"ask": [
  "Bash(git commit*)", "Bash(git push*)",
  "Bash(gh pr*)", "Bash(gh issue*)", "Bash(gh release*)",
  "Edit(/.github/workflows/**)",
  "Bash(xcrun simctl erase*)"
]
```

The principle: **anything another person will see, or anything that changes what runs
automatically.** Editing a workflow is in this list because a workflow edit changes what executes
without supervision later — a strictly larger blast radius than any single command.

Note that `allow` does not beat `ask`: a local "yes, don't ask again" cannot override a project
`ask` rule. That is what makes these lines meaningful in a shared file.

### An `ask` rule is a `deny` rule in CI

`ask` outranks `allow` wherever each is defined. That is what makes a shared `ask` rule
meaningful for humans — a teammate's local "yes, don't ask again" cannot override it.

The consequence is easy to miss: **in a non-interactive run there is nobody to answer, so every
`ask` resolves to a refusal.** The GitHub Action loads this repository's `.claude/settings.json`
(its `settingSources` are `user, project, local`), so the committed rules apply to it too.

This repo shipped that bug. `Bash(gh issue*)` was in `ask`, and the issue-triage workflow — whose
entire job is to label and comment on an issue — reported **success** while doing nothing. The
agent reasoned correctly, ran the oracle, reached the right verdict, then hit:

```
Claude requested permissions to use Bash, but you haven't granted it yet.
```

It took three rounds to find, because a denied tool call inside the agent does not fail the job,
and the run log does not include turn-by-turn tool use. The triage workflow now uploads the
action's execution log as an artifact for exactly this reason.

The fix was to take `gh issue*` out of `ask`: filing and labelling issues is reversible, it is
what the automation exists to do, and `--allowedTools` already bounds it. `gh pr*` and
`gh release*` stay — those are public and awkward to undo.

**The rule of thumb:** put something in `ask` only if you would also be willing for automation to
be unable to do it. For anything else, rely on `deny` plus a narrow `--allowedTools`.

## 4. Permission modes — the ladder

| Mode | Behaviour | Use it for |
| --- | --- | --- |
| `plan` | Reads and explores; cannot edit | Reviews, audits, understanding. `headless-review.sh` uses it. |
| `default` (Manual) | Asks on first use of each tool | The default here. |
| `acceptEdits` | Auto-accepts file edits in the working dir | A long mechanical refactor you are watching. `shared-test-writer` uses it. |
| `auto` | A classifier reviews actions instead of you | A trusted repo with good rules. |
| `bypassPermissions` | Skips prompts | Disposable containers only. |

```bash
claude --permission-mode plan          # read-only session
claude --dangerously-skip-permissions  # == bypassPermissions
```

### On `--dangerously-skip-permissions`

The name is accurate. It is appropriate in a disposable container with no credentials and no
remote write access; it is not appropriate on a laptop that has your SSH keys, because
`permissions.deny` is one of the things it skips.

Two things to know:

- It does **not** disable hooks. `guard-dangerous-bash.sh` and `protect-secrets.sh` still run,
  which is a good reason to put genuinely dangerous things in a hook rather than only in a rule.
- You can forbid it outright:

  ```json
  { "permissions": { "disableBypassPermissionsMode": "disable" } }
  ```

  Set it in **user or managed** settings (`settings.local.json.example` shows it). Once set, no
  flag re-enables it. It is deliberately left off in this repo's shared settings so you can
  experiment with the flag; turn it on for real work.

There is also `"disableAutoMode": "disable"` for the same treatment of `auto`.

## Debugging a broken configuration

| Flag / command | Does |
| --- | --- |
| `--safe-mode` | Starts with **all** customisation off — CLAUDE.md, skills, plugins, hooks, MCP, commands, agents. The first thing to try when the repo behaves strangely. |
| `--restricted` | Does not read the machine's user/project settings. For evaluation harnesses on shared machines. |
| `claude doctor` | Lists entries Claude Code **rejected** — a typo'd permission rule or an unknown hook event is skipped silently otherwise. |
| `/status` | Which settings files actually loaded. |
| `--debug=mcp` | Why a server did not connect. |
| `"disableAllHooks": true` | Hooks off without deleting them. |

A config that looks right but does not apply is usually one of: a higher-precedence file setting
the same key, a project file setting a key only user settings may set (`defaultMode: auto` and
`bypassPermissions` are the two), or the folder not being trusted yet — `allow` rules and `env`
wait for trust, while `deny` and `ask` apply immediately.

## Secrets in this repository

There are none, by construction: the only network call is a keyless public endpoint, and the only
machine-specific file is `local.properties` (gitignored, deny-listed, and hook-protected).

If you add something that needs a credential:

1. Read it from an environment variable; never commit it.
2. Add the variable name to this document so the team knows it is required.
3. For `.mcp.json`, use `${VAR}` expansion — see the `github` server entry.
4. For GitHub Actions, use repository secrets.
5. Leave `protect-secrets.sh` enabled. It is the last line, and it has no false-negative cost.

## Why the committed `.mcp.json` lists four servers but enables one

`.mcp.json` is a **catalogue**. `settings.json` grants:

```json
"enabledMcpjsonServers": ["streakkit"]
```

A server you did not opt into cannot read your repository or spend your tokens. The local Python
server is enabled because it has no credentials, no network, and no install step; `github`,
`filesystem` and `context7` require a deliberate opt-in via `settings.local.json` or `/mcp`.

The same instinct applies *inside* the local server: `run_gradle_task` accepts only an
allow-listed task name, never an arbitrary string. An MCP server runs with your full user
privileges — one that takes a shell string has handed out a shell.
