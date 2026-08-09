# ADR-0027: Routines store targets — superseding ADR-0020's central bargain

- **Status:** accepted
- **Date:** 2026-08-09
- **Deciders:** maintainer (chose it, knowing what ADR-0020 gave up), agent (scoped it)
- **Supersedes:** ADR-0020 §Decision, on targets only. ADR-0020's routine *model* — a named,
  ordered list, copied one-way into a session — survives intact and is what makes this
  affordable.
- **Relates to:** ADR-0009 and ADR-0017 (which rejected a prescription entity), constitution
  §2.4 (honest data) and §2.1 (the two-tap path)

## Context

ADR-0020 chose option 3, "a routine is a saved *shape*, not a prescription", and was explicit
about the single thing that choice could not do:

> This is a genuine loss for one real case — **planning a progression in advance** ("next
> Tuesday I want 105"). Option 3 cannot express it; option 2 can. The maintainer should choose
> knowing that.

and it named the condition for coming back:

> **Revisit if** the maintainer plans progressions in advance often enough that deriving from
> history stops matching intent.

That condition has been met. On 2026-08-09 the maintainer asked for sets, reps and weight stored
per movement and editable, so that a routine arrives at the gym already carrying its numbers.
They were offered the narrower "single next-session target" that ADR-0020 suggested as the way
back, and **declined it in favour of the full version**, with the trade-off stated. So this is
ADR-0020's option 2, adopted deliberately and two days later.

### What ADR-0020 was actually protecting, and what it costs to give up

The objection was never minimalism. It was constitution §2.4 — a stored target is a number the
app shows next to your real numbers that **you did not lift**.

ADR-0020 did not answer that with a rendering convention. It answered it *structurally*, and
`StartSessionFromRoutine`'s own documentation is the clearest statement of it:

> With no link back to the plan, no screen can ever render "planned versus actual", so an
> authored number can never end up beside a lifted one.

That is a guarantee enforced by the absence of a foreign key. **This ADR removes it**, so it
owes a replacement, and a weaker one: a set of rules that every future screen has to honour.
That is the real cost here, and it is the same class of trade ADR-0019 made when it replaced
"red means destructive" with a layout invariant. Like that one, it is only as good as its tests.

## Decision

**A routine item may carry a target: sets, reps and load.** Each is independently nullable —
"3 sets of 8, load unrecorded" is a real plan, and so is "bench, no numbers", which is exactly
what every routine has today.

### The one-way copy stays, and targets travel through it

The obvious implementation — a `routine_id` on the session, read through at set entry — is
**rejected**. It reintroduces precisely the join ADR-0020 deleted, and with it every
planned-versus-actual screen that join makes possible.

Instead, **`session_exercises` gains the same three nullable target columns**, and
`StartSessionFromRoutine` copies the values across with the exercise. The session keeps its own
snapshot of what was planned; there is still no foreign key back to the routine, and still
nothing to join on. Two consequences fall out, both wanted:

- Editing the routine next week does not silently rewrite what last Tuesday's workout was
  planned to be.
- Editing today's session still never edits the routine — ADR-0020's rule, unchanged.

Migration **v7 → v8**, additive, three nullable columns on each of `routine_items` and
`session_exercises`. No change to `sessions` or `sets`.

### `sets` never gains a target column, and never will

A logged set records what happened. The target lives on the *appearance* of the exercise, not on
any set. This is the line that keeps §2.4 answerable: whatever a screen displays, the `sets`
table is still nothing but performed work.

**Every derived number keeps reading `sets` alone.** Volume (`WeeklyVolumeByBodyPart`), the
trend (`ExerciseTrendOf`), Epley, and personal records (`PersonalRecordsOf`, ADR-0025) must
never read a target. A planned 105 that was never lifted must not become a PR.

### A target prefills set entry; it does not promise anything

When an appearance carries a target, "Add set" prefills from it. When it does not, US-03's
existing prefill from the last performed set applies, unchanged.

This is ADR-0017's rule, quoted verbatim because it was written for exactly this:

> **The target is a prefill, never a promise.**

A prefilled number becomes real only when the member confirms it, and what is written is what
they confirmed. Lifting your plan and logging it is honest — you did it. **The two-tap path is
unaffected**: a prefill is a prefill whatever its source, and `TwoTapSetLoggingTest` must not
need editing. If it does, that is the signal this went wrong (ADR-0017's own test, and the
condition ADR-0020 set too).

### Targets are always labelled, and never merged with history

The rule that replaces the structural guarantee, and the one every future screen owes:

> A target is rendered as a target, always visibly distinct from a performed number, and never
> substituted for one. Where both exist they are shown as two things — "Target 3x8 · 105 kg"
> and "Last Tue · 100 kg x 8" — never reconciled into one figure.

Absent targets show nothing rather than a zero or a dash-as-number, which is the US-13 absence
pattern the routine editor already follows for movements with no history.

### The tripwire test is replaced, not deleted

`RoutineEditorViewModelTest` asserts structurally that `MovementRow` has no target field, so
that a prescription could not ship by accident. That test has done its job and now blocks the
decision above, so it is **replaced by a test of the new invariant** — that a target reaches the
screen *labelled as a target* and never as history. Deleting it and adding nothing is the one
outcome this ADR does not permit.

## Options considered

1. **Keep ADR-0020 as it stands.** Zero churn, keeps the structural §2.4 guarantee. Rejected by
   the maintainer: it is the limitation they hit.
2. **A single next-session target**, cleared once performed. Much smaller, and ADR-0020's own
   suggested route back. Offered and **declined** — a plan that erases itself is not the saved
   plan they asked for.
3. **Full stored targets, joined back to the routine at set entry.** Simplest to build and the
   worst of the three: it rebuilds the planned-versus-actual join.
4. **Full stored targets, copied into the session — chosen.** Costs a wider migration; keeps
   the one-way copy, which is the part of ADR-0020 worth keeping.

## Consequences

- A routine arrives at the gym carrying its numbers, which is what was asked for.
- **§2.4 now rests on a convention plus its tests, where it used to rest on a missing foreign
  key.** That is strictly weaker. The labelling rule above is load-bearing, not decoration.
- Two tables change and one migration is written; `sessions` and `sets` are untouched, so every
  M1 story keeps working.
- The finish summary and any future planned-versus-actual screen are now *expressible*. Neither
  is authorised here. **Anything that renders a target beside a lifted number needs to come back
  to this ADR** and show how the labelling rule is met.
- **Revisit if** a household member ever reads a target as something they lifted. That is the
  signal the convention was not enough, and the answer would be to make targets visually
  unmistakable rather than to reinstate the missing join.

## This is more than one PR

Per `CLAUDE.md`'s ~400-line rule, the work splits:

1. **This ADR and the user story** (US-30), plus the data model note. No code.
2. **Schema and domain**: migration v7→v8, `RoutineItem`/`SessionExercise` gain targets, a use
   case to set and clear one, `StartSessionFromRoutine` copies them. Tests.
3. **The routine editor UI**: entering and editing targets, replacing the tripwire test.
4. **The session**: targets prefill set entry, and render labelled beside history.
