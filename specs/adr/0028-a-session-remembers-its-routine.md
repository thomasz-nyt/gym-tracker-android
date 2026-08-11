# ADR-0028: A session remembers the routine it was started from

- **Status:** accepted
- **Date:** 2026-08-09
- **Deciders:** maintainer (chose option 2, having been shown why option 1 falls short),
  agent (scoped the options)
- **Supersedes:** one sentence each in ADR-0020 and ADR-0027 — the claim that a session
  started from a routine carries "nothing to join on… no foreign key, no field on
  `sessions`." Neither ADR's *decision* is touched: ADR-0020's one-way copy and ADR-0027's
  target snapshot both survive intact and are what makes this affordable.
- **Relates to:** ADR-0020 (routines), ADR-0027 (routine targets), constitution §2.4
  (honest data), US-29, US-30

## Context

History currently reads "Sun 9 Aug, 13:53." The redesign asks it to read "Upper A · Tue 4
Aug," and the Routines screen to show "Done 14 times · last Tue 4 Aug · avg 52 min." Neither
is possible today, on purpose: `StartSessionFromRoutine`'s own KDoc is explicit that "from
that instant nothing connects the two: no foreign key, no field on `sessions` or
`session_exercises`, nothing to join on." That sentence is the thing this ADR changes.

### What ADR-0020 and ADR-0027 actually rejected, and what they didn't

ADR-0020 bought constitution §2.4 *structurally*: with no link back to the plan, no screen
could ever render "planned versus actual." ADR-0027 already spent that guarantee once, and
said so in its own Consequences:

> §2.4 now rests on a convention plus its tests, where it used to rest on a missing foreign
> key. That is strictly weaker.

ADR-0027 also considered and rejected a `routine_id` on the session — but rejected a
specific *use* of one, not the column in the abstract:

> The obvious implementation — a `routine_id` on the session, **read through at set
> entry** — is rejected. It reintroduces precisely the join ADR-0020 deleted.

"Read through at set entry" is a **live pointer**: a value fetched from `sessions` and used
to query `routines` while the session is still being logged, so a target or a name could be
resolved fresh from the routine at any moment during the workout. That is what would
reintroduce planned-versus-actual. What this ADR proposes is different in kind: a value
**written once, at start, and never read back through a repository** — dead provenance on a
row that, once the workout ends, never changes again. ADR-0027 never evaluated that design,
because the question it was answering was about §2.4 (an authored number beside a lifted
one), and a routine's name and id are not authored numbers. Pretending ADR-0027 already
blessed this would be dishonest about what it actually decided; this ADR has to make its own
case.

### Why a name alone cannot do the job

The obvious minimal fix is a name-only column: `sessions.routine_name TEXT NULL`, copied at
start, never joined on. It would fully serve "Upper A · Tue 4 Aug" in History and the finish
summary. It cannot serve the Routines screen's aggregates, and the reason is checkable in
the code, not a matter of taste:

- `CreateRoutine` enforces **no uniqueness on `name`.** Two routines can be called "Upper A."
- `RenameRoutine` and `DeleteRoutine` both exist. Rename "Upper A" to "Upper A2" and every
  past session grouped by name string silently moves out of the "Upper A" bucket. Delete
  "Upper A" and create a new routine with the same name, and the new one inherits the old
  one's count.

"Done 14 times · avg 52 min" computed by grouping on a mutable, non-unique string is a
number the app cannot stand behind — constitution §2.4 with a different mask on. A name
serves the *label* honestly forever, because a copied string cannot misrepresent what a
routine was called at the time. It cannot serve *identity across sessions*, which the
Routines screen and the finish summary's "compare to the last run of this routine" both
need.

## Options considered

1. **Name only.** `sessions.routine_name TEXT NULL`. Cheapest, serves History and the finish
   summary's headline. Cannot serve per-routine aggregates, ever, for the reason above —
   this is a permanent limitation, not a temporary one, since grouping by a name that can be
   renamed or duplicated is dishonest at any point in the future too.
2. **Name and a non-referential id, id inert until authorised — chosen.**
   `sessions.routine_name TEXT NULL` (read from day one) plus `sessions.routine_id TEXT NULL`
   (written from day one, **read by nothing** until a future story authorises the
   aggregates). Provenance cannot be backfilled — there is nothing to join on to reconstruct
   it after the fact — so writing the id now is the only way it exists later; not writing it
   costs every session logged in the gap, permanently.
3. **Name and id, joined freely.** Simplest code. Rejected: it reverses the bargain both
   prior ADRs argued for and reopens exactly the question they closed, for a feature (the
   aggregates) that is not built yet and does not need a live join to arrive later.

## Decision

**Option 2.** Two nullable columns on `sessions`, both written at
`StartSessionFromRoutine` time, added in migration **v8 → v9** — strictly after ADR-0027's
v7 → v8 (targets), never folded into it, because folding a `sessions` change into v8 would
contradict that ADR's explicit "no change to `sessions` or `sets`" in the same migration
that made the claim.

```
sessions(…, routine_name TEXT, routine_id TEXT, …)
```

Domain-side, the pair is a single value type, not two independent nullable fields:

```kotlin
data class RoutineOrigin(val id: String, val name: String)

data class WorkoutSession(
    …,
    val routine: RoutineOrigin? = null,
)
```

**`RoutineOrigin.id` is typed as a plain `String`, not `RoutineId`, on purpose.** Resolving
it back to a routine — `routines.find(RoutineId(origin.id))` — requires a deliberate,
greppable wrap that isn't there today. This is the same shape of guardrail
`core/domain/set`'s `SetValidation` and this ADR's own enforcement tests are: a rule that
survives review by being awkward to break rather than merely documented.

### The label and the provenance are never reconciled

**The name is what gets rendered. The id is never read back into a display.** Concretely:
the app must never re-resolve a session's displayed routine name by looking up
`routine_id`. Renaming a routine next week must not retitle what last Tuesday's session
says it was called — that would be `RoutineItem.target`'s snapshot rule again, applied one
level up. Deleting the routine must not blank or orphan the session's name either; the name
is a copy, and a copy does not depend on the thing it was copied from still existing.

### Enforcement — four mechanisms, none of them a comment

1. **The type.** `RoutineOrigin.id: String`, not `RoutineId`. `routines.find(origin.id)`
   does not compile.
2. **A structural test**, replacing `StartSessionFromRoutineTest`'s current tripwire (which
   forbids any field on `WorkoutSession`/`SessionExercise` containing "routine" — that
   assertion is retired here, in favour of positive tests of the new, narrower invariant):
   the session carries the routine's name; renaming the routine afterwards does not change a
   session already started; deleting the routine does not change it either;
   `RoutineOrigin.id` is a `String`; no domain use case accepts both a `RoutineOrigin` and a
   `RoutineRepository`.
3. **A DAO-level test:** no `@Query` in `SessionDao` or `RoutineDao` names both `sessions`
   and `routines`, and the exported schema JSON for v9 shows no foreign key from `sessions`
   to `routines`.
4. **Everything ADR-0027 already enforces keeps enforcing.** This ADR does not touch `sets`,
   `WeeklyVolumeByBodyPart`, `ExerciseTrendOf`, Epley, or `PersonalRecordsOf` — none of them
   gain a reason to read `routine_id`, and nothing here changes what they read.

### Sessions logged before this migration

They read **"Freestyle."** Provenance cannot be reconstructed retroactively — there was
never a foreign key to reconstruct it from, by ADR-0020's own design. The alternative is a
third state ("routine unknown") that would live forever for a handful of rows on upgrading
devices. "Freestyle" names an absence; it is not a fabricated number, so this is not the
constitution §2.4 failure mode ADR-0020 and ADR-0027 both guard against — it is the same
absence pattern US-13 already uses for a movement with no history.

## Consequences

- History and the finish summary can lead with a routine name. The Routines screen's
  per-routine aggregates ("Done 14 times," "avg 52 min," comparing a finish against the last
  run of the same routine) become *expressible* for the first time — **not authorised
  here.** Reading `routine_id` for anything beyond writing it at start is a future story's
  job, and it comes back to this ADR to show the read stays passive (never a join used to
  resolve a display value, never part of a query that also touches `sets` or `routines` at
  request time in a way that could surface as planned-versus-actual through the back door).
- Two migrations exist where the redesign brief implied one (v7→v8 for targets, v8→v9 for
  this). That is a feature of the sequencing, not an accident: each migration's diff matches
  exactly one ADR's stated scope, so `git blame` on a column always points at the ADR that
  added it.
- **Revisit if** a future story wants `routine_id` used as a live pointer — e.g., a "jump to
  this routine" link from a finished session, or a query that joins `sessions` to `routines`
  at read time. That is a different design than "dead provenance, written once," and needs
  its own accounting the way ADR-0027 asked any planned-versus-actual screen to come back to
  it.

## This is more than one PR

Per `CLAUDE.md`'s ~400-line rule:

1. **This ADR, US-32, and the `data-model.md` note.** No code. This PR.
2. **Schema and domain:** migration v8→v9, `RoutineOrigin`, `WorkoutSession.routine`,
   `StartSession`'s new defaulted parameter, `StartSessionFromRoutine` wiring, `SessionEntity`
   columns, the four enforcement mechanisms above as tests.
3. **History and the finish summary:** the routine name (or "Freestyle") replaces the bare
   date as the lead line.
