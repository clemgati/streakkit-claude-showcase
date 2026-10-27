# Streak edge cases — expected values

Loaded only when [SKILL.md](SKILL.md) is already in play. That is the point of splitting it out:
the invariants are short enough to always carry, this table is not.

`today = 2026-10-26` (a Monday) throughout. "d-N" means N days before today.

## currentStreak

| Check-ins | Expected | Invariant | Why people get it wrong |
| --- | --- | --- | --- |
| `{}` | 0 | — | |
| `{d-0}` | 1 | 1 | |
| `{d-1}` | 1 | 1 | Reads as "broken" to anyone who assumes the streak must include today |
| `{d-0, d-1, d-2, d-3}` | 4 | 1 | |
| `{d-1, d-2, d-3}` | 3 | 1 | |
| `{d-2, d-3, d-4, d-5}` | 0 | 2 | A long run that ended two days ago is still 0 |
| `{d-0, d-1, d-3, d-4, d-5}` | 2 | — | Counting stops at the gap; it does not skip it |
| `{d-0, d-1, tomorrow}` | 2 | — | A future date must not extend a current streak |

## longestStreak

| Check-ins | Expected | Note |
| --- | --- | --- |
| `{}` | 0 | |
| `{d-9}` | 1 | A single day is a streak of one, not zero |
| `{d-0, d-1} ∪ {d-20..d-24}` | 5 | Historical run wins over the live one (invariant 3) |

## completionRate

| Check-ins | Window | Expected | Note |
| --- | --- | --- | --- |
| `{d-0, d-1, d-2}` ∪ `{d-40, d-41}` | 30 | `3/30` | Days outside the window are excluded, not clamped |
| `{d-0 … d-6}` | 7 | `1.0` | |
| anything | 0 | throws | `require(windowDays > 0)` — a zero window is a caller bug, not an empty result |

Note the window is **not** adjusted for `createdOn`. A habit created yesterday shows a low
completion rate by design; it is "share of the last N days", not "share of days since you
started". If that ever changes, it changes in `StreakCalculator`, the oracle, and the KDoc
together.

## Dates and timezones

- `systemToday()` resolves in the device's own timezone. Two users in different zones can
  legitimately see different streaks at the same instant; that is correct behaviour.
- Stored check-ins are ISO-8601 **dates**, never instants, and are serialised with an explicit
  `IsoLocalDateSerializer` so the storage format cannot shift under a dependency bump.
- The shared module never converts between date and instant. If a feature needs a time of day,
  that is a new field, not a reinterpretation of `checkIns`.

## Cross-checking

```
mcp__streakkit__streak_oracle
  check_ins: ["2026-10-26", "2026-10-25", "2026-10-24", "2026-10-20"]
  today:     "2026-10-26"
→ currentStreak 3, longestStreak 3, completionRate 0.133333
```

The same input through `StreakCalculator` must produce the same three numbers. When it does not,
[SKILL.md](SKILL.md) has the decision table for which side is wrong.
