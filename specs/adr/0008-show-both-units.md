# ADR-0008: Show both pounds and kilograms

- **Status:** accepted
- **Date:** 2026-07-26
- **Deciders:** maintainer

## Context

`roadmap.md` M1 has "Unit preference (kg / lb), stored per user, converted at the edge
only", and `data-model.md` § Units says convert in the presentation layer. Both read as
*one* unit at a time: pick kg or lb, see that.

The household is in the US but reads kilograms comfortably — plates and machines are
labelled in pounds, while most training material, and this app's own storage, are in
kilograms. Picking one means mentally converting the other every time.

This supersedes nothing in ADR-0006: storage is unchanged, and the conversion is still
the single `UnitConverter`. What changes is how many of its outputs reach the screen.

## Options considered

1. **A preference that also picks the entry unit, with the other unit shown alongside.**
   One number to type, both to read. The preference stops being "which unit do I use"
   and becomes "which unit do I think in".
2. **Show both, ask which to type in every time.** Rejected: US-03 allows two taps to
   log a set, and a unit picker in that path spends one of them.
3. **A toggle that switches the whole app between units.** What the roadmap implies.
   Rejected by the request: switching back and forth to read the other number is the
   friction being complained about.
4. **Store the entered unit per set and show that.** Rejected in ADR-0006 already, for
   the same reasons.

## Decision

- A member has a **primary unit**, which is the unit they type in and the one shown
  first. It defaults to **pounds**, because the household is in the US.
- Every displayed weight also shows the other unit, secondary and clearly subordinate:
  `135 lb · 61.2 kg`.
- Both numbers come from `UnitConverter` on the stored kilograms, each rounded to one
  decimal place as ADR-0006 specifies. Neither is derived from the other on screen.
- Trailing `.0` is dropped, so it reads `135 lb`, not `135.0 lb`.
- A set with no weight shows "Bodyweight", not `0 lb` — absent is not zero
  (constitution §2).
- The preference lives in DataStore per ADR-0005: it describes this install, and at M2
  it moves to `profiles.unit_preference` with the rest of the member's identity.

`data-model.md`'s Postgres default changes from `'kg'` to `'lb'` to match, so a member
created server-side at M2 gets the same default as one created on device today.

## Consequences

- Answers one of the open M1 questions: the unit preference lives in DataStore at M1.
- Reading is never ambiguous, and no one has to convert in their head mid-set.
- Two numbers per weight is more to render. The secondary is styled down, and the
  two-tap path in US-03 is unaffected because entry is still a single field in one unit.
- Charts at M4 will need a decision of their own: an axis cannot carry both labels.
  Expect them to use the primary unit only.
- **Revisit if** a household member finds the secondary number noisy — the natural
  escape hatch is a "show both" toggle defaulting to on, which is a settings change,
  not a data change.

## Amendment (2026-08-28): kilograms leave the session surfaces

The revisit clause above fired for a reason this ADR didn't anticipate: the secondary number
wasn't just noisy, it was structural. The rest panel's `55 lb × 12 · 25 kg · set 2 of 3` line
wrapped at 320dp because it was carrying three facts — a load, a conversion, and a set
position — welded into one sentence at display weight. See
[ADR-0011's Turn 4 amendment](0011-gym-readable-type-scale.md) for the full type-and-layout
diagnosis; this amendment covers only the unit-policy half of that fix.

**Kilograms appear only on Progress and in history rows.** They leave the rest panel, the set
display, the stepper sheet and the primary action button entirely. The unit is already
established by the screen a member is looking at — a set of `55 lb × 12` on the session screen
does not need `25 kg` beside it for the number to be legible — and the conversion was the
extra length that produced the wrap in the first place.

Where kg still appears (Progress, history rows), it stays subordinate exactly as this ADR
originally specified, restated at the new type scale: `meta` role, ~0.4× the size of the
numeral it follows, weight 600, muted. The ~0.5× figure some drafts of the redesign prompt
carried does not match `4f`'s table (`numeral.lg` 34sp against `meta` 13sp is closer to 0.38×);
this ADR's own number is the one that governs.

This narrows, but does not reverse, the original decision: a member still never has to convert
in their head, because both numbers are still available — just on the screen built for reading
them (Progress, history) rather than on every screen that shows a weight. `WeightFormatter`
still computes and returns both (`WeightDisplay.secondary` is unchanged); what changes is which
call sites choose to render it.

**Consequence for `UnitConverter` call sites removed by this amendment:** none. The formatter's
public contract is unchanged — see `WeightDisplay`'s additive `number`/`unit`/`isBodyweight`
fields in ADR-0011's amendment, which exist for the split baseline row, not for this change.
Only the composables that used to read `.secondary` on the session/rest/stepper surfaces stop
doing so.
