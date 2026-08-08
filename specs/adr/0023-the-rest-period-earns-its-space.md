# ADR-0023: The rest period earns its space

- **Status:** accepted
- **Date:** 2026-08-08
- **Deciders:** maintainer (chose the direction), agent (scoped it)
- **Extends:** ADR-0016's rest banner. ADR-0010's mechanism is untouched.

## Context

Three findings from the redesign audit are the same finding:

> **02.** 70% of the screen is empty mid-workout. One card at the top, one button at the
> bottom, nothing between. The most valuable ninety seconds in the app — the rest between
> sets — is rendered as blank ground.
>
> **03.** The rest timer counts, and says nothing. It reaches zero and disappears. It never
> tells you what the next set is.
>
> **05.** Yesterday is invisible. The prefill silently carries your last numbers, but never
> shows them as a number to beat.

ADR-0016 already moved the countdown from the smallest text on the screen to a banner, and
called it "a pure display of ADR-0010's stored end time". That fixed how it *looks*. It did
not give it anything to say.

The rest is when the phone is actually in your hand. It is the one moment in the workout with
attention to spare, and the app currently spends it on a number that counts down.

**The constraint that shapes this ADR is constitution §2.4 — honest data.** The redesign's
rest frame reads:

> Machine Bench Press · **Set 3 of 3** · then Seated Cable Rows

Neither of those is knowable today. "Of 3" is a *target*, and there is no target: ADR-0009
and ADR-0017 refused a prescription entity, and ADR-0020's routines — which will supply one —
are not built yet. "Then Seated Cable Rows" claims an order of intent; a freestyle session has
a `position` order, but that records the order exercises were *added*, not a plan to perform
them that way. Rendering either would be the app inventing a number nobody entered, which is
the one thing §2.4 forbids outright.

## Options considered

1. **Wait for routines, do nothing now.** Coherent, and leaves the most-glanced screen in the
   app blank for however long ADR-0020 takes. Rejected: findings 02, 03 and 05 are true today
   and none of them actually needs a plan.
2. **Show the redesign's frame as drawn**, deriving "of 3" from the last session's set count
   and "then X" from `position`. Rejected outright — that is a guess rendered as a fact, and a
   member reading "set 3 of 3" would have no way to tell the app made it up.
3. **Ship what is true today, and name what waits — chosen.** The rest carries the next set's
   real numbers and what was actually lifted last time. The two claims that need a plan are
   left out until there is one.

## Decision

The rest becomes a panel rather than a strip, and it is where the next set gets logged.

**What it shows.** The countdown at `displayLarge`, Skip beside it, and beneath it *Up next*:
the exercise the rest follows, the next set's number, and the weight and reps it will be
prefilled with, in both units (ADR-0008). Below that, what the member actually lifted last
time, labelled as history — `Last Tue · 95 lb × 8` — because a number to beat is only useful
if it is a number that happened.

**What it does.** A primary action logs that prefilled set from the rest panel directly, and a
quiet *Adjust* opens the existing entry sheet when a number needs changing. Logging from rest
is **one tap**, under US-03's ceiling rather than at it. The sheet stops being the only road.

**What it deliberately does not claim.**

- **No "of N".** The app does not know how many sets you intend. It shows `Set 3`, not
  `Set 3 of 3`. When ADR-0020's routines exist, the routine supplies the target and the "of N"
  becomes true — at which point it can be added, and not before.
- **No "then X".** Session `position` is the order exercises were added, not a plan.
- **Nothing when there is no history.** A first-ever set of a movement shows the movement and
  no comparison, the US-13 pattern — absence rendered as absence.

**What is unchanged.** ADR-0010's stored end time is still the only source of truth, so nothing
here needs restoring after a kill. Rest still gates nothing: every "Add set" stays live while
it counts down (US-05). "Add set" keeps its place and its behaviour, so
`TwoTapSetLoggingTest` must not need editing.

**Explicitly not in this ADR**, because both amend US-05 and that is the maintainer's call:
±30 s adjustment (which ADR-0016 already deferred once) and an audio cue at 0:10 and 0:00.

## Consequences

- The ninety seconds becomes the primary logging surface, and the common case — same weight,
  same reps, next set — costs one tap without opening anything.
- The rest panel is taller than the banner it replaces, on a screen ADR-0011 and ADR-0016 have
  already twice made less dense. This is the third such trade and the last one that is free;
  the next feature wanting vertical space on this screen has to take it from something.
- Two of the redesign's rest frames cannot be built as drawn until routines land. That is
  recorded here so the difference reads as a decision rather than an omission.
- **Revisit when ADR-0020 lands**: "of N" and "then X" become derivable from the routine, and
  this ADR's refusal of them expires with the reason for it.
