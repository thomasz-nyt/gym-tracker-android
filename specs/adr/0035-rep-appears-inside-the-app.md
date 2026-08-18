# ADR-0035: Rep appears inside the app, and ADR-0019's third colour is admitted on purpose

- **Status:** accepted
- **Date:** 2026-08-15
- **Deciders:** maintainer (asked for the mascot in-app, chose the gold-pair and Canvas
  options), agent (measured the contrast, scoped the call sites)
- **Relates to:** ADR-0019 (the mono palette), ADR-0026 (the app is called REP, launcher-only
  gold), ADR-0015 / US-13 (the catalog's absence rule)
- **Supersedes:** ADR-0026's "the gold is confined to the icon" line, for the narrow case this
  ADR authorises. ADR-0026's other decisions (name, launcher icon, adaptive-only) are untouched.

## Context

ADR-0026 named the mascot, drew the launcher icon, and deliberately left the in-app question
open: *"If the mascot ever does appear inside the app, ADR-0019's 'no third choice' has to be
reopened properly rather than eroded one screen at a time."* The maintainer has now asked for
that reopening: Rep, animated, on Train home, the warm-up panel, exercise detail, and the guided
exercise screen's rest/complete states.

Two things ADR-0026 flagged as unresolved are resolved here:

**The source cannot be played as-is.** The maintainer's drawings animate with SMIL
(`<animate attributeName="points">`, `<animateTransform>`, `keySplines`), which
`AnimatedVectorDrawable` does not implement. Re-authoring is required regardless of what this
ADR decides about colour.

**The gold fails WCAG's 3:1 non-text floor in light mode.** `#D19A00` measures 2.26:1 against
`GroundLight` (`#F3F2F2`) — it only ever read correctly because the launcher tile is `#1B2220`,
near-black. One value cannot serve both schemes; a decision belongs in this ADR, not silently in
a `Color.kt` diff.

Only the generic running Rep is in scope. The maintainer's placard set also includes seven
machine-specific animated diagrams (leg press, chest press, lat pulldown, seated leg curl, leg
extension, shoulder press, seated row); those are **not** part of this decision. They cover 7 of
873 catalog exercises, would need a name-matching design and ADR-0015's absence rule worked out
for the other 866, and are roughly 4x the SVG this ADR's scope re-authors. Left for a future
story if wanted.

## Options considered

**Colour**

1. **A gold pair, `MascotBandLight`/`MascotBandDark`, illustration-only.** Chosen. Mirrors how
   `Red`/`RedBright` already pair per scheme in `Color.kt`. Dark mode keeps the exact launcher
   gold; light mode darkens it to clear the floor. Scoped to the mascot's band alone — never
   text, a control, or app state — so `GymColorSchemeTest`'s claim that the *rendered UI* is
   achromatic-plus-red stays true; what changes is that one decorative illustration is now
   allowed a second hue.
2. **Recolour the band to the app's red.** Zero palette change, but the drawing stops matching
   the launcher icon, and the maintainer's brief for the source drawings calls the band tail
   "the whole identity — the only piece that changes between poses." Rejected.
3. **Monochrome Rep, no accent.** Safest against ADR-0019, but throws away the one piece of the
   drawing that carries motion legibility, for no measured problem the gold pair doesn't already
   solve. Rejected.

**Technique**

1. **Compose `Canvas` + `rememberInfiniteTransition`.** Chosen. Pose geometry becomes pure
   Kotlin, testable on the JVM without Robolectric or a device — the only option that lets this
   land test-first. Colours read `MaterialTheme`/`LocalMascotBand`, so light/dark is automatic.
   No new dependency.
2. **`AnimatedVectorDrawable`, re-authored from the SMIL source.** No dependency, fine on
   `minSdk` 26. Rejected: the polylines would need rewriting as morphable `pathData` with
   matching command counts, the result is opaque to a JVM test, and theming needs duplicated
   day/night drawables rather than one composable reading the color scheme.
3. **Add Lottie.** Closest to the source authoring model, but it is a new dependency gated by
   constitution §7 behind its own ADR, there is no direct SMIL-to-Lottie path, and the M3c work
   running alongside this one has "no new dependency" as a stated line item. Rejected.

**Placement**

Four call sites were asked for; each is evaluated against constitution §2.1 ("the core loop is
sacred — logging a set during rest between sets must take ≤2 taps") and US-13/ADR-0015's
absence rule.

1. **Train home**, in `NoSession`'s empty `weight(1f)` band. Off the logging path entirely —
   this screen is reached *before* a workout starts. Accepted.
2. **Warm-up panel**, running state. Inside an active session, but the warm-up is explicitly "a
   stopwatch, and nothing else" (`WarmUpPanel`'s own doc) — there is no tap to interfere with
   here, only a countdown being watched. Accepted.
3. **Exercise detail.** The photo slot (`MovementPhoto`) was considered and rejected: it is
   empty for 866 of 873 exercises specifically because US-13 promises "nothing in its place
   where one does not," and filling it with a mascot is the placeholder that story refused.
   Accepted instead as a small mark beside the exercise name — brand, not a stand-in for missing
   media. This is a drill-down, not the two-tap path.
4. **Guided exercise screen.** `MidSetHeader` and `GuidedControls` — the screens rendered while
   a set is actually being logged — are excluded; adding a moving illustration to the one screen
   built around "what you are lifting, which set you are on, and the one button that ends it"
   is the exact erosion ADR-0026 warned against. `RestHero` and `ExerciseSummary` are accepted:
   both are states where nothing is mid-tap.

## Decision

- **`MascotBandLight = #9C7100`, `MascotBandDark = #D19A00`.** Both measured ≥3.37:1 (light) /
  ≥4.63:1 (dark) against every surface Rep is drawn on — `background`, `surfaceContainerLowest`,
  `surfaceVariant` — clearing WCAG 1.4.11's 3:1 non-text floor with margin, not just at the
  worst case. Exposed as `LocalMascotBand`, a `CompositionLocal` provided by `GymTrackerTheme`
  alongside — not inside — `ColorScheme`. This is the mechanism that keeps the scoping honest:
  `GymColorSchemeTest` iterates `ColorScheme`'s roles, and the mascot band is not one of them,
  so the achromatic-plus-red claim the test makes about the rendered app stays true by
  construction, not by discipline.
- **On any accent (`primary`) surface, Rep draws monochrome in `onPrimary`.** Gold-on-red
  measures 1.86:1 (light) / 1.25:1 (dark) — nowhere near usable — so `RestHero`'s red hero is
  the one place the band is dropped rather than recoloured again.
- **The generic running Rep only**, re-authored as `RepMascotGeometry` (pure Kotlin pose data,
  unit-tested) driving a `Canvas`-based `RepMascot` composable. The seven machine placards are
  explicitly not built here.
- **Reduced motion.** When `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`, `RepMascot` renders
  the phase-0 pose statically and starts no `InfiniteTransition`. This also keeps the CI
  instrumented job usable — it runs with `disable-animations: true`, so a test that waited on an
  animation frame would hang rather than assert.
- **Placement:** Train home (`NoSession`), the warm-up panel (running state), exercise detail
  (a mark beside the name, not the photo slot), and the guided screen's `RestHero`
  (monochrome) and `ExerciseSummary` states. Not `MidSetHeader`, not `GuidedControls`.

## Consequences

- `GymColorSchemeTest` is unaffected — the mascot golds are never added to `ColorScheme`, so the
  test's iteration over rendered pairs and achromatic roles has nothing new to see. A new test,
  `MascotColorsTest`, gates the two golds directly against WCAG 1.4.11 the same way
  `GymColorSchemeTest` gates AA text.
- ADR-0019's "no third choice to argue about" is now true of every *interactive and textual*
  surface, and false of exactly one decorative illustration. Future screens still pick the
  accent or the ground — this does not reopen the palette for anything else, and a second
  request for a new hue elsewhere in the app is a new ADR, not a precedent this one sets.
- The app now contains its first Compose animation of any kind. `RepMascotGeometry` is pure
  Kotlin specifically so this does not also become the first piece of animation logic nothing
  can unit-test.
- US-13's absence rule is untouched: the exercise-detail photo slot still shows nothing for the
  866 exercises with no bundled image. Rep sits beside the name, never in the photo's place.
- **Revisit** if the seven machine placards are wanted later — that is a new ADR, not an
  extension of this one, given the name-matching and coverage questions ADR-0026 already raised
  and this one did not answer. **Revisit** also if a future screen wants the mascot band on an
  accent surface — the monochrome-on-`primary` rule above would need to change first.

## Amendment, 2026-08-17 (Turn 3, ADR-0036)

`RepMascotGeometry`'s viewBox (`0 0 200 210`) was transcribed unchanged from the source SVG, but
the figure's ink only ever occupied roughly the middle 39% of its width and 67% of its height —
`RepMascot`'s `Canvas` fits the whole viewBox, so `Modifier.size(GymDimens.MascotInline)` (88dp)
reserved a box for a figure drawn at 32dp wide. `Redesign.dc.html`'s Turn 3 measured this against
the warm-up panel specifically (finding 01) and prescribed the fix: crop the viewBox to the ink's
bounding box (`46 20 84 148` in source units, covering every animated pose's extent including the
bob) and size call sites by height, not by a fixed box. This is a correction inside this ADR's
own scope, not a new decision — same drawing, same placements, same colour rule — and it is
recorded here rather than in a new ADR for that reason. One consequence worth naming: Rep now
draws larger at every call site for the same token value, which Turn 3's own text calls out as
wanted ("it makes Rep bigger everywhere else it appears") rather than a side effect to correct
for. `GymDimens.MascotHome`/`MascotInline` are retuned once, on device, after the crop; see
`specs/roadmap.md`'s Turn 3 entry for what that retuning settled on.
