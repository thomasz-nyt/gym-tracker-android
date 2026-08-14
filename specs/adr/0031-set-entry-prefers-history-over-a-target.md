# ADR-0031: Set entry prefers history over a target

- **Status:** accepted
- **Date:** 2026-08-13
- **Deciders:** maintainer (chose the direction, having seen both readings), agent (scoped it)
- **Supersedes:** ADR-0027's §"A target prefills set entry; it does not promise anything" only.
  Every other part of ADR-0027 — the schema, the one-way copy, the no-join rule, and above all
  the labelling rule ("a target is rendered as a target, always visibly distinct from a
  performed number, and never substituted for one") — is unchanged and still binds.

## Context

ADR-0027 chose: *"When an appearance carries a target, 'Add set' prefills from it. When it does
not, US-03's existing prefill from the last performed set applies, unchanged."* Target, then
history.

`Redesign.dc.html`'s section 2b asks for the opposite, in order:

> 1. **What you last did on this exact movement** — its weight, reps and sets.
> 2. Else the **routine's target** for that slot.
> 3. Else **3 sets × 12 reps, weight blank.**
>
> Weight is **never** inherited from a different exercise. Sets and reps can fall back to 3 × 12
> safely; a leg-press number sitting in a lateral-raise field is worse than empty.

The two ADRs disagree about which number should be sitting in the box when the sheet opens, not
about whether a target is safe to plan with or how it renders. Reconciling them means picking
one precedence and saying why.

### Why history first is the better rule

A target is written once, when the routine is built, and can go stale: three weeks of steady
progress on a movement leaves the target three weeks behind the last real set. History is never
stale in that way — it is, by construction, the most recent thing the member actually did.
Prefilling from history first means the number in the box is always "what you did last time",
which is the number a member showing up to beat is actually trying to beat. A target still has
a job — it is what fills the box the first time a movement is ever attempted, before there is
any history to prefer — and it keeps that job under this ADR.

## Decision

**Precedence for weight and reps: last performed set on this exact movement, then the routine's
target, then nothing.** Reps additionally floor at 12 when neither source has an opinion; weight
never floors — an invented load is worse than an empty field, which is the same reasoning
ADR-0006 already applies to unit conversion at the boundary.

**Weight is never inherited across exercises.** Both sources — history and target — are already
scoped to the one movement being entered; this is not a new mechanism, it is naming the
invariant the existing code already had so a future change cannot break it by widening either
source's scope.

**Sets floors at 3 only once a target exists — with no target at all it stays ADR-0009's
original 1.** The brief's "3 sets × 12 reps, weight blank" reads as a universal floor, and the
first implementation of this ADR took it that way: `target?.sets ?: 3`, unconditionally. That
broke `TwoTapSetLoggingTest` on-device — confirming a set for a brand-new exercise logged three
identical rows instead of one, because the sheet's own default had silently changed out from
under a test that confirms without checking the sheet on purpose. `TwoTapSetLoggingTest` must
pass **unedited**, and a regression there is a signal the change went wrong, not a test to
patch — so the floor is narrower than the brief's literal text: `if (target == null) 1 else
target.sets ?: 3`. A target's own count still wins when it has one, and a target with no
explicit count still floors at 3 — both real wins over the old "always 1, whether or not a plan
exists" behaviour — but a completely new exercise, with nothing to floor *from*, keeps the
one-tap-safe default ADR-0009 chose. ADR-0009's own reasoning — *"how many sets you did or
planned is not a claim about today's count"* — is otherwise untouched; history is still never a
source for this field.

**One pure function carries the rule, called from both places it was previously duplicated.**
`ResolveSetPrefill` (`:core:domain/set`) takes a member's most recent [SetPrefill], a routine's
[MovementTarget]?, and the member's [WeightUnit], and returns the merged weight/reps/sets plus
whether the result came from history. `SetEntryController.open` and
`ActiveSessionViewModel`'s one-tap prefill both call it instead of each inlining the same
`target ?: history` merge — which is exactly how the two call sites could have drifted
unnoticed, and did, since neither reads the other today.

**The sheet says where the number came from.** When [ResolvedPrefill.fromHistory] is true, a
muted line under the steppers reads "Prefilled from last Tuesday — 100 lb × 8", so the number
reads as a target to beat rather than an unexplained default. Nothing is added when the number
came from a target — the target already renders labelled as one, elsewhere on the same sheet,
per ADR-0027's unchanged rule.

### The labelling rule is unaffected

ADR-0027's rule is about what is *displayed as a target* versus *displayed as history*, not
about which value becomes the *prefill*. This ADR changes only the second thing. A target still
renders as `Target 3 × 8 · 105 lb`, still never merged into a performed number's line, and
`SetEntryTargetPrefillTest`'s labelling assertions are untouched by this change — only its
*precedence* assertions move, deliberately, to match the new order.

## Options considered

1. **Keep ADR-0027's target-first order.** Rejected — the design's rationale (a target goes
   stale, history does not) is the stronger argument once stated, and it was the maintainer's
   own call to make once both readings were in front of them.
2. **History first, chosen.** Matches the design, and gives the member a live streak to beat
   rather than a plan written once and never revisited.

## Consequences

- `SetEntryController.open`'s `prefilled` flag and `ActiveSessionViewModel`'s
  `nextLoggableSet` computation both change their merge order; both are covered by
  `ResolveSetPrefillTest`'s table tests, so the two cannot silently diverge again.
- `SetEntryController.change`'s `sets` field now opens pre-populated with a target's own count,
  or floors at 3 for a target with none — but stays `"1"`, unchanged, with no target at all.
  `TwoTapSetLoggingTest` and `OneTapSetLoggingTest` pass **unedited**, confirmed on-device: the
  universal-floor version of this decision did not.
- **Revisit if** a member reports the opposite complaint — a target going unused because
  history always wins even on the first session after building a new plan. That is the
  situation option 1 would have protected and this one does not; the fix would be scoping
  history's precedence to *sessions since the target was last edited*, not reverting the order
  outright.
