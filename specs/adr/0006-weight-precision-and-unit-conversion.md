# ADR-0006: Weight precision and kg/lb conversion

- **Status:** accepted
- **Date:** 2026-07-26
- **Deciders:** agent, under standing instruction to proceed without blocking; **needs
  maintainer confirmation**

## Context

Three specs constrain weights and they do not fully agree:

- `data-model.md`: "canonical unit is ALWAYS kg", `weightKg: Double?`, and the Postgres
  column is `numeric(6,2)` — two decimal places.
- `data-model.md` § Units: one `UnitConverter` in `:core:domain` "with a
  rounding-behaviour test table", because "unit bugs in a lifting app are uniquely
  infuriating".
- US-03: "Weight accepts one decimal place".

US-03 does not say *which unit* the single decimal place applies to. That matters: a
member using pounds loads the bar in 2.5 lb steps, and 45 lb is 20.411658… kg. If the
one-decimal rule were applied to the stored kilograms, 45 lb would round to 20.4 kg and
read back as 44.98 lb — a number the member never typed and cannot put on a bar. Over an
edit-and-save cycle the displayed weight would drift.

This blocks US-03, and the maintainer is unavailable, so it is decided here and flagged
for confirmation rather than left to the implementation.

## Options considered

1. **One decimal place in the member's display unit; store kilograms at two.** Input is
   validated in the unit the member is actually typing. Conversion to kg keeps two
   decimals, matching `numeric(6,2)` exactly, so the local and remote schemas agree.
2. **One decimal place in kilograms always.** Simplest rule, and exactly what `weightKg`
   suggests. Rejected: it corrupts pound entry, which is the case the rule exists to
   protect.
3. **Store the unit the set was entered in alongside the value.** Lossless, and honest
   about provenance. Rejected for M1: it adds a column `data-model.md` does not have, and
   makes every chart and the M6 coaching context unit-aware. Option 1 is lossless enough
   to be indistinguishable in practice (see below).
4. **Store integer grams.** Exact, no floating point. Rejected: it contradicts
   `weightKg: Double?` and `numeric(6,2)` in the schema of record, for a precision nobody
   in a gym can use.

## Decision

- The member enters weight in their own unit, to **one decimal place**.
- `UnitConverter` converts to kilograms and rounds to **two decimal places** for storage.
- Display converts back and rounds to **one decimal place**.
- `1 kg = 2.20462262185 lb`.

Verified before adopting: every 2.5 lb increment from 2.5 lb to 500 lb survives
lb → kg(2dp) → lb(1dp) unchanged, and every 0.1 kg increment from 2.5 kg to 300 kg
survives kg → kg(2dp) → kg(1dp) unchanged. The round-trip table is a test, not a comment.

## Consequences

- A pound user always sees back exactly what they typed, across edits and app restarts.
- Two decimal places of kilograms is ~0.004 lb of resolution, so the rounding is invisible
  at any weight a person lifts.
- Local Room and remote Postgres agree on precision, so sync cannot introduce a diff that
  looks like an edit.
- `UnitConverter` is the only place a conversion happens (`data-model.md` § Units).
  Anything else doing arithmetic on a display weight is a bug.
- The stored kilogram value is canonical, so a member switching units mid-history sees
  their whole log re-expressed rather than mixed.
- **Revisit if** the maintainer disagrees with the display-unit reading of US-03, or if a
  household member wants pounds recorded verbatim for competition logging — that is
  option 3, and it is a schema change.
