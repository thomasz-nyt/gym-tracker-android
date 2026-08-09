# ADR-0026: The app is called REP, and it has a mascot

- **Status:** accepted for the name and the launcher icon. The in-app questions below are
  **proposed only** and are not built.
- **Date:** 2026-08-09
- **Deciders:** maintainer (chose the name, the icon and the mascot), agent (scoped it)
- **Relates to:** ADR-0019 (the mono palette), ADR-0014 (the catalog has no GIFs),
  constitution §7 (dependencies)

## Context

The app had no name beyond `Gym Tracker` in `strings.xml` and **no launcher icon at all** — no
`ic_launcher` resource, no `android:icon` in the manifest, so it shipped with the system
default. The maintainer produced a set of machine-placard drawings with Claude, built around a
mascot called Rep: a profile head with a nose bump, one eye, and a yellow sweatband whose tail
lags behind every movement.

The name is chosen for the same reason the drawings are: "rep" is both a repetition and a
friendly noun, and it is short enough to sit under a launcher icon without wrapping, which
`Gym Tracker` was not.

## The part that is not free: this adds a second colour

ADR-0019 is unambiguous about what the visual system is:

> One accent, red. ... The ground is achromatic ... every future screen picks the accent or the
> ground, and there is no third choice to argue about.

The mascot's sweatband is `#D19A00`, a gold. That is a third choice. It is being adopted
anyway, because the maintainer chose it and because the launcher icon is the one surface where
it costs nothing: it is not a screen, nothing is rendered next to it, and no contrast pair in
`GymColorSchemeTest` is affected. **The gold is confined to the icon.** Nothing inside the app
uses it, and this ADR does not authorise it to spread there.

If the mascot ever does appear inside the app, ADR-0019's "no third choice" has to be
reopened properly rather than eroded one screen at a time.

## Decision

- **The app is called REP.** `app_name` only; the Gradle module names, the `com.gymtracker`
  namespace and the `Theme.GymTracker` style are untouched, because renaming those is churn
  with a migration risk and no user-visible benefit.
- **The launcher icon is Rep's head**, as an adaptive icon: gold band and white linework on the
  drawings' `#1B2220` ink. Hand-traced into a `VectorDrawable` — the source SVG uses `<circle>`
  elements, CSS classes and a negative-origin viewBox, none of which a `VectorDrawable`
  understands.
- **Adaptive only, no PNG fallback.** `minSdk` is 26, which is the release adaptive icons
  arrived in, so rasterised fallbacks would be dead weight nothing could load.

## Proposed, and explicitly not decided here

The maintainer also asked whether the placards' **animated SVGs** could play against the
matching exercise during a workout. That is a much larger decision than the icon and it is
recorded here so it is not mistaken for something this ADR settled. What it runs into:

1. **`AnimatedVectorDrawable` cannot play these.** The drawings animate with SMIL
   (`<animate>`, `<animateTransform>`, `values`/`keySplines`), which Android's vector pipeline
   does not implement at all. Each drawing would have to be re-authored as an AVD, or played in
   a `WebView`, or converted to Lottie. All three are real work and the last two are a
   dependency, which constitution §7 gates behind an ADR of its own.
2. **Coverage is 7 of 873.** The drawings cover seven machines; the catalog has 873 exercises.
   Matching "when the name matches" means most exercises show nothing, so the absence rule from
   US-13 and ADR-0015 applies — and a feature that fires for under 1% of the catalog needs to
   justify its weight.
3. **ADR-0014 deferred exercise media to M2** on the grounds that the seed data has none and
   there is no Storage bucket. These drawings are different — they are authored, bundled, and
   need no backend — so they are not blocked by ADR-0014, but the milestone question stands:
   this is not M4 work.
4. **The second palette.** The placards use steel, pad, gold and stop-red — a whole system that
   is not this app's. Rendering them inside a screen is where the "gold is confined to the
   icon" line above would break.

None of that is a refusal; it is the list of things a future ADR has to answer. The cheapest
honest first step is probably **one** machine, re-authored as an AVD, on the exercise detail
screen rather than mid-set — the two-tap path (constitution §2.1) is the last place to put an
animation.

## Consequences

- The app has an identity on the home screen, which it did not before.
- `GymColorSchemeTest` is unaffected: the gold is not in the colour scheme.
- **Revisit** if the mascot is wanted anywhere inside the app — that reopens ADR-0019, not this.
