# ADR-0037: Choosing which exercise is open

- **Status:** accepted
- **Date:** 2026-08-17
- **Deciders:** maintainer (reported the bug live, on the gym floor; chose the interaction
  model), agent (scoped and traced the cause)
- **Relates to:** ADR-0029 (the session screen is a ruled sheet — the "current row" rule this
  amends), ADR-0031 (set entry prefers history), US-02c (removing an exercise)

## Context

Reported directly, mid-workout: once a set is logged against a later exercise, an earlier
untouched one becomes unreachable for the rest of the session. Traced to the code, not just
observed on screen — `ActiveSessionViewModel.kt`'s `currentRow` is a pure function of the data:

```kotlin
val currentRow = rows.lastOrNull { it.sets.isNotEmpty() } ?: rows.firstOrNull()
```

"The highest-position exercise with a logged set." `SessionPlan`'s "Still to come" list
(`SessionMovements.kt`) only ever shows exercises with a **greater** position than that row, so
the moment set 1 lands on exercise 3, exercises 1 and 2 have no row, no button, and nothing to
tap anywhere on the screen. The domain layer never lost this information — `SessionProgress.of`
(`core/domain/.../SessionProgress.kt`) already returns untouched earlier movements in `current`/
`stillToCome` — the UI is what discards them, at exactly the filter ADR-0029 wrote:
`planOrder.filter { it.sessionExercise.position > currentRow.sessionExercise.position }`.

The only ways back today are destructive: delete every set on exercise 3 (undoing real work),
or remove it from the session (US-02c — abandoning it, not returning to it later). Neither is
"go back to exercise 1 once its machine frees up," which is, per the roadmap's own audit note,
"the most common reason a plan breaks."

This is a gap in ADR-0029's original design, not a regression — `currentRow`'s derivation
predates every change in the Turn 3 PR this ADR follows. It is being fixed now because it was
found live, the same way US-06a and ADR-0011 were.

**Not the same problem as the roadmap's "swap a movement" item.** That story is about
*substituting* one exercise for a different one when a machine is taken (leg press instead of
squat). This ADR is about *navigation* — reaching an exercise the session already includes,
in either direction. The two are complementary and can both exist; this one is scoped to
navigation alone.

## Options considered

**What tapping another exercise does**

1. **Selects it as the fully open exercise.** Chosen. The tapped row becomes the new current
   row exactly as if it had always been current — its own set list, target, `Start exercise`/
   `Remove`, and the one-tap `LOG SET` button. Consistent in both directions: reaching an
   earlier exercise and reaching a later one now work the same way, tap then log.
2. **Keep today's forward-only shortcut, extend it backward.** Tapping any row opens the `Add
   set` sheet directly, without changing which row is "current." Smaller diff, but the member
   still cannot glance at an earlier exercise's target or its already-logged sets without going
   through the sheet, and the sheet has no way to show that. Rejected — it fixes reachability
   but not usability, and the maintainer's own report was "switch between exercises," not
   "let me blind-fire a set at one."

**How long a selection lasts**

1. **Sticky until explicitly changed.** Chosen. The selection is a real piece of UI state, not
   a one-shot detour — it survives rest timers, adding other exercises, anything else happening
   on screen, until the member taps a different exercise. Matches the actual use case: pick
   exercise 1 back up once free, and stay there.
2. **Snap back to the derived default after the next set logged anywhere.** Rejected: a member
   who logs a set on exercise 3 out of habit (muscle memory, the one-tap button sitting right
   there) while genuinely working on exercise 1 would find the screen has silently moved out
   from under them.

## Decision

- `ActiveSessionViewModel` gains `selectedExerciseId: MutableStateFlow<SessionExerciseId?>`
  (starts `null`) and `fun selectExercise(id: SessionExerciseId)`, the same bare-field idiom
  `justSetRecord` already uses in this file — no new Controller class for one flag.
- `currentRow`'s derivation becomes selection-first, falling back to the existing rule:
  ```kotlin
  val currentRow =
      selected?.let { id -> rows.firstOrNull { it.sessionExercise.id == id } }
          ?: rows.lastOrNull { it.sets.isNotEmpty() }
          ?: rows.firstOrNull()
  ```
  A selection pointing at a since-removed exercise falls through to the default automatically —
  no separate cleanup path is load-bearing, though `onRemoveExercise` clears a matching
  selection as hygiene.
- Every downstream computation that already reads `currentRow` — history prefill, target,
  one-tap prefill, `nextLoggableSet` — follows the selection with no changes of its own. This is
  the same value it always read; only what feeds it changed.
- `SessionPlan`'s list drops the position filter: every other exercise is shown, in plan order,
  regardless of whether it is earlier or later, touched or not. A row already carrying sets
  shows `"{n} sets logged"` in place of its target line, so the list distinguishes "not started"
  from "in progress elsewhere" without a new visual language — reusing the phrase
  `CurrentMovement`'s own "N sets logged" line already established. The section eyebrow is
  renamed "Still to come" → "Other exercises", since the list is no longer only what's ahead.
- Tapping a row now calls the new `onSelectExercise`, not `onAddSet` — the sheet is still one
  tap away from the now-open exercise's own log bar, unchanged.

**What this does not touch.** Constitution §2.1's two-tap and US-35's one-tap ceilings are
unchanged for the path that never touches selection at all — a member who never switches
exercises sees identical behaviour to before this ADR, and `TwoTapSetLoggingTest`/
`OneTapSetLoggingTest` pass unedited. The segment bar stays non-interactive (it carries no
per-exercise identity — ADR-0033 already recorded that gap; wiring it up is separable). Guided
mode's own forward-only `nextUp` is untouched — ADR-0033 already refused prev/next arrows there
for a different reason ("no action backs them"), and this ADR's selection lives on the main
session screen only.

## Consequences

- `openSessionExerciseId` is no longer a pure function of `(session, sets)` — it now depends on
  UI-only state that does not persist across process death (the same non-guarantee `setEntry`/
  `setEdit`'s in-memory state already has). Reopening the app after being killed returns to the
  derived default, not the last selection — an acceptable gap the same way an open sheet does
  not survive a kill either.
- The "Still to come" copy and every test string that matched it needs updating to "Other
  exercises" wherever it appears literally (previews, any test asserting the old label).
- A future "swap for a substitute exercise" story can build on top of this: once an exercise is
  reachable and selectable, offering a substitute at that same tap is a smaller addition than it
  would have been against the old, one-directional list.
- **Revisit** if the segment bar ever gains per-exercise identity — at that point tapping a
  segment and tapping an "other exercises" row should probably do the same thing, and this ADR's
  `onSelectExercise` is the callback that segment tap would call.
