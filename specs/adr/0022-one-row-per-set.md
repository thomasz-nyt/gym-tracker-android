# ADR-0022: One row per set, so any single set can be corrected

- **Status:** accepted
- **Date:** 2026-08-07
- **Deciders:** maintainer (chose it), agent (raised the conflict)
- **Narrows:** ADR-0009's display-grouping consequence. ADR-0009's actual decision — the
  **Sets** field that writes N rows at once — is untouched and still stands.

## Context

US-04 says: *"I can edit weight, reps, or RPE of any set in the current session"* and *"I can
delete a set, with undo available for 5 seconds."*

The session screen cannot currently express "any set". `LoggedSets` renders `SetGroup`, which
collapses consecutive identical sets into one line — three sets of 12 become `3 × 12`. ADR-0009
chose that deliberately, and `SetGroup`'s own doc comment anticipated this exact moment:

> Display only. The rows underneath stay separate, so US-04 can still edit or delete any single
> set.

That is true of the data and false of the screen. A `3 × 12` line has no set id behind it, so
there is nothing for a tap to mean. Three sets, three ids, one tap target.

The constraint that matters: constitution §2 — the two-tap logging path is sacred. Whatever
this does must not add a tap to logging, only to correcting, which is rare by comparison.

## Options considered

1. **One row per set — chosen.** `SET 1 / SET 2 / SET 3`, each its own target. It is also what
   the redesign draws (`Redesign.dc.html`, frame *1a Edit logged set*), so the design and the
   story agree. Costs vertical space, which ADR-0011 and ADR-0016 have already twice decided is
   an acceptable price on this screen.
2. **Tap a group to expand it, then tap a set.** Keeps ADR-0009's grouping intact and the list
   short. Rejected: it makes the correction path two taps for no benefit to the frequent path,
   and an expanded group is a third visual state on a screen read one-handed.
3. **Edit the whole group at once.** Shortest for "I loaded the bar wrong for the entire block",
   but there is then no way to fix one set — which is the literal text of US-04. Rejected as not
   implementing the story.

## Decision

Wherever logged sets are shown, show **one row per `ExerciseSet`**, carrying its id, and make
that row the tap target for editing and deleting.

- This applies to the active session and to the past-session detail (US-06b), because US-04's
  third criterion allows correcting a past session's set too.
- The **Sets** field is unaffected. Logging `3 × 12` still writes three rows in one confirmation
  (ADR-0009); they now simply *read back* as three lines rather than one.
- Logging gains no taps. `TwoTapSetLoggingTest` must not need editing — if it does, that is the
  signal this went wrong.
- Set index stays the label (`SET 1`, `SET 2`), so the row names the thing it edits.

## Consequences

- US-04 becomes expressible: every set is reachable, which it was not before.
- The set list is taller. On a `3 × 12` block it is three lines where it was one. ADR-0011 and
  ADR-0016 both already accepted density loss on this screen for legibility, and this is the
  same trade for correctability.
- **`SetGroup` may end up with no callers.** It is a pure display type, and this ADR removes the
  only places that render it. If nothing uses it once US-04 lands, it should be deleted rather
  than kept "in case" — the ADR-0009 behaviour it described lives in `LogSets`, not in it. Do
  not leave it as dead code with a stale doc comment claiming it enables US-04.
- **Revisit if** a member logs high-set blocks often enough that the session screen becomes a
  scroll — at which point option 2 returns, with grouping as a collapsed default rather than the
  only rendering.
