# ADR-0047: The rest band is ink at all times — amending ADR-0036's outlined step-back

- **Status:** accepted
- **Date:** 2026-08-30
- **Deciders:** maintainer, agent
- **Relates to:** ADR-0036 (rest is ink and red is the thing you tap — amended by this ADR),
  ADR-0029, ADR-0046, US-54, `Redesign.dc.html` Turn 5 file `03-session-screen.md` §4

## Context

File `03`'s rest-band section is three sentences, and the third one changes a premise ADR-0036
was built on:

> Ink, not accent — `#2b2827` ground, 2px rules top and bottom, 56dp, directly under the header.
> The accent belongs to the primary action. The final ten seconds may flip the numeral to
> accent (ADR-0029 as applied in turn 3); nothing else about it changes.

Today, `RestCountdownBanner` is a full-height `Surface` whose **container and content colour
both flip** to accent-filled for the final ten seconds (`RestingBody`'s `urgent` branch), and
`RestingBody` steps `PrimaryActionButton`'s `outlined` parameter to `true` for exactly those same
seconds specifically so the countdown block and the log button are never both accent-filled at
once — the entire reason ADR-0036 needed that parameter.

File `03`'s "nothing else about it changes" describes a different mechanism: the band's `#2b2827`
ground is permanent, through the final ten seconds and all of it — only the **numeral's own text
colour** flips to accent, never the surface behind it. Under that reading, the log button never
has anything to step back *from*: the band is never filled, so "exactly one filled element"
(ADR-0029) holds without the swap ADR-0036 introduced to enforce it. Confirmed with the
maintainer this is the intended reading, not an omission: `RestingBody`'s
`outlined = remaining <= FINAL_STRETCH` is removed, and `PrimaryActionButton` stays filled
through the whole rest.

**Where `SKIP REST` goes wasn't written down anywhere in file `03`** — not the layout table
(`label.caps + 28sp numeral + meta`, no button), not "what gets deleted," not the gate table.
Confirmed with the maintainer: it moves into the same secondary row sub-piece 3 (US-54) already
gave `Add set`/`Add exercise` — `label.caps × 2`, matching that row's own shape exactly, with
`SKIP REST` beside `Add set` while resting the same way `Add set` sits beside `Add exercise`
mid-set. `LOG SET` stays the screen's one primary action, full width, below the secondary row.

**28sp has no existing role.** This app's ten type roles (ADR-0011, `00-gate.md` §2) are
`numeral.lg` (34sp) and `numeral.md` (24sp) — nothing at 28sp. Per this codebase's own
established practice (`GymDimens`' "a new value gets a new token or reuses the nearest existing
one, never a private literal" — the same reasoning `WarmUpRowHeight` aliasing to `MinTouchTarget`
already applied to `dp`), the countdown reads `numeral.md` (24sp): the closer of the two, and the
smaller fits a 56dp band more comfortably than the larger would.

## Options considered

1. **Keep ADR-0036's full-surface flip, ignore file `03`'s "nothing else about it changes."**
   Rejected: reads past the sentence file `03` states plainly, and keeps the log button's
   `outlined` step-back solving a conflict ("two filled elements") the new band no longer creates.
2. **Give the countdown a genuinely new 28sp role.** Rejected: the frame's literal pixel value is
   not itself the constraint that matters here — the ten-role scale's own discipline is (a small
   number of shared sizes, not one per call site), and 24sp reads perfectly well at this size.
3. **Leave `SKIP REST` as a separate full-width row beneath the compact band.** Rejected: doesn't
   match `label.caps × 2` as tightly as the confirmed placement, and treats the resting state's
   secondary row as a different shape from the mid-set one sub-piece 3 already built, for no
   reason the design or the maintainer gave.

## Decision

`RestCountdownBanner` becomes a 56dp row: `label.caps` "REST", `numeral.md` (24sp) countdown —
its colour, not the row's container, flips to accent for the final ten seconds — and a muted
meta "of {total}". Ink (`inverseSurface`) container at all times, 2px structural rules top and
bottom. `SKIP REST` moves into the resting state's secondary row, `label.caps`, beside the
restyled `Add set` (sub-piece 3) — both above the still-full-width, always-filled `LOG SET`
primary. `PrimaryActionButton`'s `outlined` parameter and `RestingBody`'s `urgent`-driven
step-back are removed as dead code once nothing calls them with `true` anymore.

## Consequences

**Easier:** the resting and mid-set states now share the exact same secondary-row shape
(`label.caps × 2` above one full-width primary), which is one pattern to maintain instead of two.
The rest band no longer needs its own accent-vs-ink branch for the *container* — only the
numeral's colour is state-dependent, a smaller surface for a bug to hide in.

**Harder:** the final-ten-seconds moment is quieter than before — a colour change on one number
rather than the whole block flashing red. This is the design's own call, not an accident; if it
reads as too subtle in practice, that is `Redesign.dc.html`'s frame to revisit, not a bug in this
implementation.

**Committed to:** `PrimaryActionButton`'s `outlined` parameter is deleted, not deprecated — no
other call site used it (grep-verified before removal). A future rest-state redesign that wants a
"both filled at once" moment back would need to reintroduce the mechanism, not just flip a flag
ADR-0036 already built and this ADR removes.

**Revisit if:** on-device use shows the final-ten-seconds cue is too easy to miss without the old
full-block flash — the fix would be a louder numeral treatment (motion, size) within the ink
band, not reverting to a second filled surface competing with the log button.
