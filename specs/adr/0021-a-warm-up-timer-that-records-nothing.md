# ADR-0021: A warm-up timer that records nothing

- **Status:** accepted
- **Date:** 2026-08-07
- **Deciders:** maintainer (confirmed the §1 reading), agent (raised it)
- **Relates to:** constitution §1, ADR-0020

## Context

The redesign draws warm-up and cool-down as steps inside a routine — "Warm-up · treadmill
8 min", "Cool-down · full-body stretch 12 min" — rendered as blocks that count time and
carry no weight fields.

Constitution §1 scopes the app to **indoor, equipment-based strength training only** and is
blunt about the mechanism that would let it drift:

> If a feature request would introduce an "activity type" abstraction, the answer is no.

A routine step that counts up and has no load is a second kind of thing a session can
contain. That is an activity type, whatever it is called, and §1 refuses it. Agents may not
amend the constitution (`CLAUDE.md`), so this needed the maintainer.

## Options considered

1. **Warm-up as a routine step**, persisted alongside lifts. What the mocks draw. Requires
   amending §1. Rejected — it buys an 8-minute row at the cost of the rule that keeps the
   app from becoming a general fitness tracker.
2. **Drop it entirely.** Safe, and leaves the maintainer's warm-up with nowhere to go.
3. **A timer that records nothing — chosen.** A stopwatch startable from anywhere in a
   session. It is not a routine step, has no row, no `session_exercises` entry, and no
   contribution to volume, duration or history.

## Decision

Option 3. The app gains a **countdown/stopwatch that persists nothing.**

- It is **not** a session step. `routines`, `routine_items`, `session_exercises` and `sets`
  are untouched by it, so no activity-type abstraction enters the data model — which is
  what §1 actually forbids.
- Its running end-time lives in DataStore beside the rest timer's (ADR-0005, ADR-0010).
  Device-local, never synced, discarded when it finishes.
- It never appears in history, never counts toward session duration, and never appears in
  a summary. Nothing is logged, so §2.4 has nothing to be dishonest about.
- The routine editor does **not** offer "add a warm-up". The timer is reachable from the
  session screen, not from the plan.

## Consequences

- The maintainer's 8 minutes has somewhere to go without the app learning what a treadmill
  is.
- §1 is unamended and the "no activity type" rule keeps its teeth. This ADR is the record
  of *why* a timer is not one, so the next request of this shape has a precedent to argue
  against rather than an unexplained exception.
- The trade is real: because nothing is recorded, the app cannot tell you that you warmed
  up, and a routine cannot remind you to. That is the price of §1 and it is being paid
  deliberately.
- **Revisit if** the maintainer wants warm-ups to appear in history — at which point it is
  a constitution §1 amendment, not a screen, and this ADR is superseded rather than bent.
