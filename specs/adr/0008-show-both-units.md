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
