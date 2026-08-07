# ADR-0020: Routines — the object the app is missing

- **Status:** accepted — option 3. The maintainer chose it on 2026-08-07 knowing it
  reopens a question this repo had already answered twice, and knowing what it gives up.
- **Date:** 2026-08-07
- **Deciders:** maintainer (chose option 3), agent (scoped the options)
- **Relates to:** ADR-0009, ADR-0017, `data-model.md` §"What is deliberately not a table"

## Context

Finding 01 of the redesign audit is the one everything else leans on:

> Start workout creates an empty container. Everything after it is search. There is no
> object in the data model for "Upper A", so the six movements you do every Tuesday are
> re-assembled by hand every Tuesday.

The complaint is real and is not a UI problem. `sessions` has no name, no shape, and no
relationship to any previous session. Nothing in M1–M3 records that Tuesday is Upper A.

**The reason this is an ADR and not a user story is that the repo has already rejected
the thing it needs, twice, and the second rejection is five days old.**

`data-model.md` §"What is deliberately not a table":

> Giving it a row would make it a **prescription entity** — which ADR-0009 rejected and
> ADR-0017 keeps rejecting.

ADR-0017 (accepted 2026-08-02, merged in PR #14):

> **The target is the sets×reps already typed into set entry.** There is no plan entity,
> no template, and nothing that outlives the workout.

A routine is, precisely, a plan entity that outlives the workout. Adopting the redesign as
drawn means superseding that sentence. That is allowed — ADRs supersede ADRs — but it
should be done with the eyes open, because ADR-0009 and ADR-0017 did not reject
prescriptions out of minimalism. They rejected them because of constitution §2.4:

> **Honest data.** Never fabricate, estimate, or interpolate a logged value. If a metric
> is unavailable, show it as unavailable.

A stored target is a number the app shows next to your real numbers that **you did not
lift**. ADR-0017's phrase for it: "the target is a prefill, never a promise."

## Options considered

1. **Reject routines; keep ADR-0017 as it stands.** Honest, zero churn, and leaves finding
   01 unanswered — the maintainer keeps rebuilding Tuesday by hand every Tuesday. The
   redesign's entire premise ("put the plan on the screen") collapses; roughly two thirds
   of the mocks have nothing to render. Rejected unless the maintainer wants to stop here.

2. **Full prescription entity, as drawn.** New `routines` and `routine_items` tables
   carrying name, order, and per-movement targets (sets × reps × load). Everything in the
   mocks renders directly. Cost: it supersedes ADR-0017's core sentence, and it puts
   authored numbers on the same screens as logged ones, which is the exact adjacency §2.4
   was written about. Mitigable with rendering rules, but the entity itself is the risk.

3. **A routine is a saved *shape*, not a prescription — recommended.** A routine stores a
   **name and an ordered list of exercises**, and nothing else. No targets, no loads, no
   rep counts. The numbers the mocks show beside each movement ("3×8 · 100 lb") are read
   from history through the existing `PrefillFromLastSet` — they are what you *did* last
   time, not what you are *told* to do.

   This answers finding 01 in full (the object exists; Tuesday is Upper A; starting it
   copies the list into a session) while conceding nothing on §2.4: every number on screen
   remains a number someone actually lifted, labelled as such. ADR-0017 is **narrowed
   rather than superseded** — "no prescription entity" survives intact; only "nothing
   outlives the workout" is amended, and it is amended to cover a list of names, which is
   not a value and cannot be dishonest.

## Decision

**Option 3.** A routine is a name plus an ordered list of catalog exercises. Targets are
derived from history, never stored.

```
routines(id PK, user_id, name, position, created_at, updated_at, sync_state)

routine_items(id PK, routine_id FK→routines ON DELETE CASCADE,
              exercise_id FK→exercises, position,
              updated_at, sync_state)
```

- Starting a routine **copies its items into `session_exercises`** in order. The session is
  then an ordinary session — editing today never edits the routine, and every existing
  story (US-02a/b/c, US-03, US-04, US-05a) keeps working unchanged on it.
- Each row renders "last time" values from `PrefillFromLastSet`, labelled as history
  (`Last Tue · 100 lb × 8`), never as a target. Where there is no history the row shows
  the movement and no numbers — the US-13 pattern, honest about absence.
- The two-tap path is untouched. `TwoTapSetLoggingTest` must not need editing; if it does,
  that is the signal this went wrong (ADR-0017's own test).
- A routine is device-local until M2, like everything else (`sync_state` present but
  unused, matching `sessions`).

### Where this diverges from the mocks, and what it costs

The routine editor mock lets you **tap a target to change it** (`3×8 · 100 lb` as an
editable field). Under option 3 there is no stored target to edit, so that control does not
exist. The editor becomes: name, ordered list, add, remove, reorder.

This is a genuine loss for one real case — **planning a progression in advance** ("next
Tuesday I want 105"). Option 3 cannot express it; option 2 can. The maintainer should
choose knowing that. If planned progression turns out to matter, it returns as its own user
story about a *single* next-session target, which is a much smaller thing to get right than
a general prescription model.

## Consequences

- Finding 01 is answered, and Train/Routines/first-run/finish-summary all become
  renderable without a prescription concept.
- ADR-0017 needs one sentence amended, not withdrawn; the guided flow keeps working and
  gains a plan to walk through.
- `data-model.md` gains two tables and its "deliberately not a table" section gains a
  paragraph explaining why a routine is a shape and a target still is not.
- Room migration: two new tables, additive only, no change to `sessions`, `sets`, or
  `session_exercises`.
- **Revisit if** the maintainer plans progressions in advance often enough that deriving
  from history stops matching intent.
