# REP Gym Tracking

REP records indoor, equipment-based strength sessions for one household. Its language keeps
fast set logging distinct from optional instruction and enhancement layers.

## Language

**Member**:
One person in the household whose workouts, routines, and training history belong to them.
_Avoid_: Account, device, shared household log

**Routine**:
A Member's reusable ordered template of movements and optional Targets for future Workouts.
_Avoid_: Completed workout, training history

**Routine Update**:
A Member-confirmed change to a reusable Routine's exercise list or order for future Workouts.
_Avoid_: Automatic workout sync, performed values converted to targets

**Workout**:
One Member's training visit from Start to Finish, including the timed warm-up and rests.
_Avoid_: Routine, lifting-only duration

**Movement**:
One occurrence of a selected exercise within a Routine or Workout.
_Avoid_: Machine Variant, entire workout

**Target**:
Intended sets, reps, load, and/or rest for a Movement, distinct from what the Member actually performed.
_Avoid_: Logged result, completed set

**Logged Set**:
A record of one set the Member actually performed; explicit bulk entry creates several Logged Sets.
_Avoid_: Planned set, target

**Planned Workout**:
A Workout started from a Routine, with today's plan distinct from the reusable template.
_Avoid_: Guided mode, routine

**Focused Exercise View**:
A one-Movement presentation of the current Workout's plan and Logged Sets.
_Avoid_: Separate workout, independently configured walkthrough

**Movement Completion**:
A Movement meets its planned set count when that many actual Logged Sets have been recorded,
even if their reps or loads differ from the Target; without a planned count, the Member explicitly marks performed work Done.
_Avoid_: Every target achieved, one set started

**Ended Early**:
A Movement deliberately ended after some actual Logged Sets, before its planned set count was met.
_Avoid_: Plan met, missing sets treated as performed

**Skipped**:
A Movement deliberately left unperformed in today's Workout.
_Avoid_: Deleted from the Routine, completed

**Rest**:
Recovery time following a Logged Set, accompanied by one countdown that can remain visible while
the Member inspects or selects another Movement.
_Avoid_: Mandatory waiting screen, simultaneous per-movement timers

**Machine Guide**:
Advisory setup, movement, and checkpoint instruction for one reviewed Machine Variant. It is
eligible only when manufacturer documentation and a trainer or machine maintainer agree.
_Avoid_: Exercise GIF, generic machine animation, automatic rep counter

**Machine Variant**:
One exact manufacturer-and-model combination whose adjustment points, pivots, contact points,
and movement path are treated as distinct from similar-looking equipment.
_Avoid_: Machine type, generic machine

**Form Guide**:
A member-invoked view of a Machine Guide used before a set, separate from performing or logging
the active set.
_Avoid_: Active-set overlay, form scoring
