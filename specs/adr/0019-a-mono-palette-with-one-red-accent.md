# ADR-0019: A mono palette with one red accent — superseding ADR-0016's colour

- **Status:** accepted
- **Date:** 2026-08-07
- **Deciders:** maintainer (chose the direction), agent (scoped)
- **Supersedes:** ADR-0016 §Colour and §Shape. ADR-0016's emphasis and touch-ergonomics
  rules are untouched and still bind.

## Context

A redesign proposal (`Redesign.dc.html`, Claude Design project "Gym tracker app UI
redesign") re-does the app in a mono system: a near-black/off-white ground, square
corners, Archivo, and a single red accent. The maintainer chose to adopt it.

ADR-0016 is five days old and picked orange. It has to be superseded honestly, so the
record should be clear about **why**, because the proposal's stated reason does not hold:

> "Orange never reached AA in either direction."

That is not true of the palette in the repo. ADR-0016 hit exactly the wall the proposal
describes — white-on-orange cannot reach AA on any orange that still reads as bright — and
solved it by putting **near-black** on orange, which is how high-visibility actually works.
`GymColorSchemeTest` asserts every rendered pair at ≥ 4.5:1 in both schemes and passes
today. Orange is not being replaced because it failed a measurement.

It is being replaced because the maintainer prefers the mono system, and because one
accent used at full strength on an otherwise achromatic ground is a stronger identity than
a warm palette in which the accent competes with warm neutrals and warm surfaces. That is
an aesthetic decision, which is the maintainer's to make. Recording it as a contrast fix
would put a false claim in the repo and would mislead whoever revisits this at M7.

The constraint that does bind: constitution §2 (honest data, and the two-tap path is
sacred) and ADR-0016's own gate — **the palette is decided by the contrast test, not by
eye.** Whatever replaces it inherits that gate.

## Options considered

1. **Keep ADR-0016's orange, take only layout ideas.** Zero churn, keeps a passing test
   and the red-means-destructive rule. Rejected by the maintainer: the mono system is the
   point of the redesign, and orange-on-warm is what they want to move away from.
2. **Adopt the red accent, keep "red is reserved for destructive".** Incoherent — the
   accent *is* red, so the reservation cannot survive. Rejected.
3. **Adopt the red accent and replace the colour rule with a structural one.** The
   proposal's own answer: destructive actions never share a surface with a save. Chosen.
4. **Mono with a non-red accent** (keep red purely for destructive). Preserves ADR-0016's
   separation, but the maintainer picked the proposal as drawn, and the reds carry the
   identity. Rejected, but this is the fallback if the consequence below bites.

## Decision

Adopt the proposal's visual system in `:core:designsystem`, consumed by role everywhere
else. ADR-0011's type *sizes* and its "never hard-code an `sp`" rule are kept verbatim.

**Colour.** One accent, red. Light fill `#AE1800` with an `#F3F2F2` label; `#EC3013`
for rules, kickers and large emphasis; `#FF563C` as the dark-scheme fill. The ground is
achromatic — `#F3F2F2` / `#201E1D` — replacing ADR-0016's warm neutrals. `outlineVariant`
is set explicitly, closing the shipped bug below.

**Shape.** Every radius to 0, as one `Shapes()` object in the theme. Feature code never
names a corner size, the same way it never names an `sp`.

**Type.** Archivo replaces Roboto, **bundled as a font resource, not Downloadable Fonts** —
constitution §2 says the gym has no signal, and a typeface that arrives over the network is
a typeface that is absent exactly where the app is used. Numbers carry weight 800 so a load
reads at arm's length. ADR-0011's sizes do not change.

**The destructive rule.** ADR-0016 separated Delete from Save *by hue*. A mono palette
cannot spare a second hue, so the separation becomes **structural and stronger**:

> A destructive control never shares a surface with a save, and is never filled — it is
> outlined. Delete lives inside the editor that owns the thing being deleted.

This is a layout invariant, so it is asserted by Compose UI tests over the screens that
have both, not by the colour test.

**The gate is inherited.** `GymColorSchemeTest` is rewritten against the new palette, not
deleted. It keeps the AA assertion over every rendered pair, keeps asserting the accent is
present in both schemes, and gains an assertion that `outlineVariant` is neutral — the
token whose absence is finding 08.

## Consequences

- The identity is now one accent on an achromatic ground; every future screen picks the
  accent or the ground, and there is no third choice to argue about.
- **`error` and `primary` are both red now, and that is the real cost of this ADR.**
  ADR-0016 could say "the red control is the dangerous one". It cannot any more. The
  structural rule above is what replaces that guarantee, and it is weaker in one specific
  way: it depends on every future screen author honouring a layout invariant, where the old
  rule was enforced by the palette itself. The UI tests are load-bearing, not decoration.
  **Revisit with option 4 if a household member ever taps a destructive control expecting
  a save** — that is the signal the structural rule was not enough.
- Every radius-0 surface is a bigger tap target than the stadium it replaces at the same
  height, which is worth something on a gym floor and costs nothing.
- Archivo adds ~200 KB per weight to the APK. Four weights ship; anything beyond that
  needs a reason.
- ADR-0016's emphasis and ergonomics rules (one primary role per screen, 48dp floor, 64dp
  screen CTAs, 56dp steppers, dp as tokens) carry over untouched. This ADR changes what the
  app looks like, not where the taps are.
- M7's accessibility audit is unchanged in scope and now runs over a palette whose contrast
  gate has been re-derived rather than inherited.
