# ADR-0009: Logging several sets at once

- **Status:** accepted
- **Date:** 2026-07-26
- **Deciders:** maintainer (requested), agent (scoped)

## Context

Set entry logs one set per confirmation. The maintainer's words: *"we missed the 'set'
during input, we should have both reps and sets for an exercise, like 3 sets, 12 reps
for ab crunch"*. That is how people talk about a workout, and on a machine like an ab
crunch nobody wants to open the sheet three times.

The tension is with `data-model.md`'s first line: **"Log sets, not workouts."** Every
chart, PR and coaching input derives from individual `sets` rows, and constitution §1
warns that any added abstraction taxes the core loop.

There is a second tension, with constitution §2: "Never fabricate, estimate, or
interpolate a logged value." Three sets logged in one action did not happen at one
instant.

## Options considered

1. **A sets count on entry that writes N rows.** The schema does not change — three
   sets are still three rows, each with its own `set_index`, each independently
   editable in US-04. "3 × 12" is an input shorthand, not a stored concept.
2. **A `sets` column on a single row.** Rejected outright: it destroys the ability to
   record 135×8, 135×6, 125×8 — the normal shape of a working set — and every M4
   chart would have to guess what the reps meant.
3. **Prescriptions: target sets and reps set up front, ticked off as you go.** This is
   what "3 sets of 12" means in a programme. Rejected for M1: it is a workout-template
   concept, there is no story for it, and it is exactly the kind of abstraction
   constitution §1 says to refuse by default. If the maintainer wants planning rather
   than faster logging, that is a new story, not this ADR.
4. **Leave it alone; open the sheet three times.** Rejected: constitution §2 makes the
   speed of the core loop the point of the app.

## Decision

Option 1. Set entry gains a **Sets** field, defaulting to **1**.

- Confirming with sets = N writes N rows through the same `LogSet` path, so every
  validation and the kilogram conversion apply identically to each.
- All N share one `performed_at`, which is **the time they were recorded, not a guess
  at when each was performed**. The app does not know the individual times and does not
  invent them. Logging set by set — still the default, still two taps — records the
  real times.
- The completed-sets list groups consecutive identical sets for reading: `3 × 12` rather
  than three near-identical lines. The grouping is display only; the rows underneath are
  separate.

## Consequences

- "Log sets, not workouts" holds where it matters: the schema is unchanged, and US-04
  can still edit or delete any single set of the three.
- The two-tap path is untouched. Sets defaults to 1, so a member who confirms a
  prefilled set still does it in two taps, and `TwoTapSetLoggingTest` still passes
  without typing anything.
- Bulk logging trades timestamp fidelity for speed, and the member chooses which they
  want by how they log. M4's per-set trends are unaffected, since they read weight and
  reps rather than intervals; a future rest-interval analysis would only be meaningful
  for set-by-set logs.
- **Revisit if** the maintainer actually wants prescriptions — targets set before the
  workout and ticked off during it. That is option 3, a different feature, and it needs
  its own story rather than an extra field here.
