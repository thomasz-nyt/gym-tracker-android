# ADR-0004: A `session_exercises` table between sessions and sets

- **Status:** accepted
- **Date:** 2026-07-26
- **Deciders:** maintainer

## Context

`data-model.md`'s core principle is "log sets, not workouts": a session is a thin
container and everything derives from the `sets` table. The M1 schema is therefore
`exercises`, `sessions`, `sets`.

Two US-02 acceptance criteria cannot be satisfied by that schema:

1. *"when I search the catalog and select an exercise, it is appended to the
   session"* — an exercise selected but not yet logged against has no row
   anywhere. It can only exist as ViewModel state, which dies on app kill. That
   reads as a bug against US-03's "the set is persisted locally before any UI
   transition; killing the app immediately after does not lose it."
2. *"the same exercise may appear twice in one session"* — `ExerciseSet.setIndex`
   is documented as "1-based within (session, exercise)", so two occurrences of
   the same exercise in one session are indistinguishable. They collapse into a
   single group, and their set indices collide.

US-03's two-tap requirement depends on the first: the instrumented test in
`testing-strategy.md` §2 opens the app with an active session and a prior set,
which means the session's exercises must survive a process restart.

## Options considered

1. **Add `session_exercises`** — rows of `(id, session_id, exercise_id, position)`;
   sets hang off a `session_exercise_id`. Represents an empty entry and a repeated
   entry honestly. Costs one table, and softens the "log sets, not workouts"
   principle from a schema rule to a philosophy about where the *value* lives.
2. **Keep sets-only and drop the two criteria.** No schema change, and the
   constitution's minimalism holds exactly. But selecting an exercise would have
   to open the set-entry sheet directly, and "same exercise twice" — a real
   pattern when someone returns to a machine later in a workout — becomes
   unsupported.
3. **Keep sets-only, exercise selection is UI state.** Cheapest. Rejected: an
   added-but-unlogged exercise vanishing on app kill is precisely the kind of
   quiet data loss constitution §2 and US-03 exist to prevent.

## Decision

Add `session_exercises`. A set belongs to a session-exercise, not directly to a
session and an exercise.

`ExerciseSet.setIndex` is redefined as **1-based within its `session_exercise`**,
which makes it well defined when an exercise appears twice in one session.

Sets do **not** carry a denormalised `exercise_id`. `data-model.md` previously
specified the index `sets(exercise_id, performed_at DESC)` to back the US-03
prefill and the M4 charts; that becomes an index on
`session_exercises(exercise_id)` plus `sets(session_exercise_id, performed_at DESC)`,
and the prefill query joins.

## Consequences

- The US-03 prefill ("most recent set of that exercise, any session") is now a
  join rather than a single-table lookup. At household scale — hundreds of
  sessions, a few thousand sets — this is not a measurable cost, and it avoids
  a denormalised `exercise_id` that nothing in SQLite would keep consistent with
  its session-exercise. If the two-tap assertion ever gets close to its budget,
  revisit with a measurement, not a guess.
- An empty `session_exercise` is now a representable state, so US-06's "a session
  with no sets is discarded rather than saved" needs to say what happens to a
  session that has session-exercises but no sets. It is still discarded; the
  session-exercise rows go with it via `ON DELETE CASCADE`.
- The M2 Postgres schema and its RLS policies gain a table. `sets` policies now
  reach the owning session through `session_exercises`, which is one more join in
  every policy — worth watching in the pgTAP suite.
- "Log sets, not workouts" still holds where it matters: no activity types, no
  workout templates, and every chart and PR still derives from `sets`.
- Revisit if a future story wants exercises ordered or grouped across sessions
  (supersets, circuits) — that is a different table, and it is out of scope by
  constitution §1 unless the maintainer says otherwise.
