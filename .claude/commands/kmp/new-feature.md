---
description: Plan and implement a feature across the shared module and both apps, shared-logic-first, with a test gate between each layer.
argument-hint: "<what the feature should do>"
disable-model-invocation: true
---

# Feature: $ARGUMENTS

Reference material, already in context:

@docs/01-architecture.md
@shared/src/commonMain/kotlin/dev/clement/streakkit/presentation/HabitsUiState.kt

## How to work on this

**Plan before editing.** If this feature touches more than one layer — and most do — start in
plan mode so the user can correct the shape before any code exists. Present the plan as the layer
order below, with the specific files you intend to touch.

**Then implement in this order, stopping at each gate:**

| # | Layer | Gate |
| --- | --- | --- |
| 1 | `shared/.../domain` — types and pure logic | `./gradlew :shared:allTests` |
| 2 | `shared/.../commonTest` — a test that fails before step 1 and passes after | same |
| 3 | `shared/.../presentation` — expose it on `HabitsUiState`/`HabitSummary`, pre-formatted | same |
| 4 | `androidApp` — Compose rendering | `./gradlew :androidApp:assembleDebug` |
| 5 | `iosApp` — SwiftUI rendering | `/ios:build` |
| 6 | Parity | `/kmp:parity-check` |

The order is the point. Writing the shared logic and its test first means each UI is a thin
rendering job with nothing left to decide — and it means a bug can only be in one place.

## Rules specific to this repo

- **No business logic in either app.** If you find yourself computing something in Compose or
  SwiftUI, it belongs in `summarize()` instead. Both apps must receive the same finished value.
- **Keep the Swift-facing surface primitive.** `String`, `Int`, `Bool`, `Float`, and lists of
  data classes. Exposing a `kotlinx-datetime` or coroutines type means the framework has to
  export it and Swift call sites get noticeably worse.
- **Persistence is a compatibility promise.** New fields on `Habit` need a default value, or
  stored JSON from a previous version stops decoding. `HabitRepositoryTest` has a test for that.
- **Do not add dependencies** without saying why in the plan. The version catalog is small on
  purpose; every addition is a thing the iOS framework has to link.

When all six gates pass, summarise what changed per layer in one line each, and stop. Do not
commit.
