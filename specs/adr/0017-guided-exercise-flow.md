# ADR-0017: A guided flow through one exercise

- **Status:** accepted
- **Date:** 2026-08-02
- **Deciders:** maintainer (requested), agent (scoped)

## Context

The maintainer's words, from the gym floor: *"we should have a button to start each
exercise, after each rep, show a count down rest timer… for each exercise, we should show the
name of the exercise, weights, current reps, and a timer, a button to finish current rep, then
start the rest timer, when all the reps are done, show a summary of the exercise, and the next
one if on the queue."*

Read against `data-model.md`, "rep" here means **set**: a set is one bout of N reps, rest goes
between sets, and `ExerciseSet.reps` is the count inside one. Confirmed with the maintainer
before this ADR was written.

**ADR-0009 rejected this.** Its option 3, verbatim:

> **Prescriptions: target sets and reps set up front, ticked off as you go.** This is what
> "3 sets of 12" means in a programme. Rejected for M1: it is a workout-template concept,
> there is no story for it, and it is exactly the kind of abstraction constitution §1 says to
> refuse by default. If the maintainer wants planning rather than faster logging, that is a
> new story, not this ADR.

Its Consequences section names the revisit condition — *"Revisit if the maintainer actually
wants prescriptions… That is option 3, a different feature, and it needs its own story"* — and
that condition has now been met. This ADR is that revisit. The story is **US-05a**.

The constraint that decides the shape is constitution §1: *"If a feature request would
introduce an 'activity type' abstraction, the answer is no."* ADR-0009 was right that a
workout-template entity would cross that line. The question is whether the useful part of the
idea can be had without one.

It can, because the target already exists. US-03 and ADR-0009 already put a **Sets** field in
set entry — "3 sets of 12" is typed today, used once to write three rows, and then thrown
away. Guiding you through those three sets does not need a new concept; it needs the app to
stop discarding one it already has.

## Options considered

1. **A prescription entity: planned sets and reps stored per session-exercise, ticked off as
   completed.** What ADR-0009 rejected, and still the wrong answer. It needs a migration, it
   makes "planned" and "performed" two states of every row, and it is one short step from
   workout templates — the activity-type abstraction §1 refuses.
2. **Guided mode as a transient lens over the sets you are already logging.** The target is
   the sets×reps typed at the start, held for the duration of the exercise and discarded when
   it ends. Nothing is "planned" in the database; sets are written as they happen, exactly as
   they are today.
3. **No target at all: "Finish set" → rest → repeat until you tap Done.** Simplest, and it
   needs no target anywhere. Rejected: it cannot say "Set 2 of 3", cannot show a completion
   summary, and cannot advance to the next exercise — which is most of what was asked for.
4. **Replace the active-session screen with guided mode.** Rejected outright: it would put a
   mode ahead of "Add set" and regress the two-tap loop, which constitution §2.1 calls a bug
   *"regardless of what it adds"*.

## Decision

Option 2. Guided mode is an **opt-in lens over the existing set-logging path**, entered per
exercise from a "Start exercise" button, and it introduces no new persisted domain concept.

- **The target is the sets×reps already typed into set entry** (US-03, ADR-0009), prefilled
  from `PrefillFromLastSet` as usual. There is no plan entity, no template, and nothing that
  outlives the workout.
- **Each "Finish set" writes one real set through the existing `LogSet`**, then starts the
  existing rest through `RestController.startAfterSet()`. Guided mode owns no writing and no
  timing of its own.
- **The rep count is editable before each set is finished.** The target is a prefill, never a
  promise — see Consequences.
- **The "next in the queue" is derived, not stored:** the next exercise in `position` order
  with no logged sets. Nothing records that an exercise was "skipped" or "planned".
- **The in-flight target lives in DataStore, not Room**, per ADR-0005's boundary: it describes
  this device and this install only, and it will never sync. It shares the store the rest
  timer already uses (ADR-0010).
- **The two-tap path is untouched.** "Add set" keeps its position and its label; "Start
  exercise" is an additional action on a secondary line. `TwoTapSetLoggingTest` should not
  need editing, and if it does, that is the signal that this went wrong.

## Consequences

- **This improves constitution §2.4 compliance rather than straining it.** ADR-0009 writes N
  sets sharing one `performed_at`, and says so plainly: *"the time they were recorded, not a
  guess at when each was performed. The app does not know the individual times and does not
  invent them."* Guided mode logs each set as you finish it, so each carries a real timestamp.
  The honest-data problem ADR-0009 documented is the one this closes.
- **The editable rep count is load-bearing, not polish.** If the target is 12 and you manage
  9, writing 12 would fabricate a logged value — precisely what §2.4 forbids. The guided
  screen must let the count be corrected before the set is committed, and a test asserts the
  edited value is what lands.
- Killing the app mid-exercise loses nothing that matters: every finished set is already
  committed, and the target is in DataStore. Backing out returns to the normal session screen
  with those sets present. Guided mode is a way of looking at the session, never a separate
  place the data lives.
- The rest default drops to 60 seconds in the same milestone (US-05), which is what makes a
  guided run feel continuous rather than stalled.
- `SessionUiState` is assembled by nested `combine`, which tops out at five flows. Guided state
  made that group the sixth, so it is now nested one level deeper again — a `Triple` of unit,
  set entry and rest, then history, removal and guided around it. **That is the second grouping
  record on this screen and it is the signal, not the fix.** `ActiveSessionViewModel` now drives
  the session, the search, history, the workout detail, set entry, removal and this; the next
  thing added should split the ViewModel rather than add a third tuple.
- **It stays outside the navigation graph, and that is a decision rather than an oversight.**
  This ADR was written before M3 added one (ADR-0013), so the question was re-asked once it
  existed. ADR-0013's condition for adopting navigation at all is that the start destination
  is still derived from the database, because that is what makes "reopen and you are back in
  your session" survive the process being killed. The guided flow needs exactly the same
  property one level down: the plan is in DataStore and the sets are in Room, so reopening
  mid-exercise should land back mid-exercise. A destination on the back stack would not do
  that — a killed process restores to the graph's start, and the stored plan would be left
  describing an exercise the member is no longer looking at. **Revisit** if the back stack
  ever becomes restorable, at which point the argument reverses.
- **What this does not become.** No workout templates, no programmes, no cross-session plans,
  no "planned vs actual" reporting. Each of those is a new story and would need to answer §1
  again, from a worse position than this one — because each of them is the abstraction, where
  this is a lens. **Revisit if** a request arrives to save a target for next time: that is the
  prescription entity from option 1, and it should reopen ADR-0009 rather than extend this.
