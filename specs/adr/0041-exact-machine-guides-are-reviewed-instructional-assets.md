# ADR-0041: Exact-machine guides are reviewed instructional assets

- **Status:** accepted
- **Date:** 2026-08-23
- **Deciders:** maintainer (exact machines, placement, rollout and validation), agent
- **Relates to:** ADR-0015 (stable catalog identity), ADR-0018 (no inferred rep logging),
  ADR-0035 (Rep's Canvas geometry and palette), US-13 (offline machine instructions)

## Context

REP already animates a generic running mascot. The source drawing set also contains seven
machine placards: leg press, leverage chest press, wide-grip lat pulldown, seated leg curl, leg
extension, leverage shoulder press and seated cable row. The maintainer wants these to read like
a careful private trainer demonstrating the household's actual gym machines, not like decorative
exercise GIFs.

That standard changes the risk. Seat position, pivot, foot or hand placement, range and the path
of the moving lever depend on a machine's exact manufacturer and model. A plausible generic
drawing can be confidently wrong. Constitution section 2.4 also rules out turning an
instructional animation into camera-, sensor- or animation-derived rep logging.

The original seven SVGs, exact machine identification, manuals, reference photos and designated
reviewer are not in the repository yet. This ADR defines the gate they must pass; it does not
authorise invented geometry or cues while those inputs are absent.

## Options considered

1. **Bundled reviewed guide data plus app-native Compose Canvas geometry.** Chosen. Stable
   exercise UUIDs select a guide; pure Kotlin geometry makes keyframes, bounds and contact points
   testable; Compose supplies theme and reduced-motion behavior; everything works offline.
2. **Render the supplied SMIL SVG directly in a WebView.** Rejected. It introduces a second UI
   and accessibility surface, gives animation state poor semantics, and leaves important
   geometry opaque to JVM tests.
3. **Add Lottie and convert the drawings.** Rejected. It adds a dependency and a conversion
   pipeline without solving exact-machine validation or making contact points testable.
4. **Match a guide by exercise name or aliases.** Rejected. Catalog names and variants can
   drift; a fuzzy match could show instructions for the wrong machine. Absence is safer.

## Decision

- M4b is a schema-neutral UI milestone allowed to run beside M2. It adds no Room table, sync
  payload, backup field or network dependency.
- A guide is selected only by an explicit stable `ExerciseId` in bundled `machine_guides.json`.
  Unknown ids and unreviewed variants return no guide and render no empty section.
- Each guide records the exact manufacturer and model, concise Setup / Move / Checkpoints cues,
  a manufacturer manual reference, reviewer identity and review date. Production data is added
  only after the manual and a human trainer or machine maintainer both approve it.
- Source SVGs and provenance stay under `docs/machine-guides/sources/`; the app does not play
  SMIL. Reviewed geometry is re-authored as pure Kotlin keyframes and drawn by a
  `RepMachineGuide` Canvas composable, reusing Rep's line-art language.
- Leg press is the pilot. The other six machines follow only after its real-machine session and
  review expose any corrections to the model or renderer.
- The guide appears on exercise detail and from a secondary `Form guide` action during guided
  setup. Opening and closing that guide preserves every setup value. It never appears in the
  active-set state and never adds a tap to one- or two-tap logging.
- Instructional motion has Play/Pause/Replay controls and a useful accessibility description.
  With system animations disabled, it shows labelled start and end poses plus movement
  direction; a single unexplained frozen frame is not an adequate reduced-motion alternative.
- Machine lines use the neutral ink roles. Rep's reviewed gold band remains illustration-only.
  Red remains reserved for an action or interactive state, not decorative motion arrows.
- The animation is advisory instruction only. It never counts, infers or writes a rep, never
  evaluates camera form, and never changes a set value.

## Consequences

Exactness and review become part of the asset format rather than comments beside drawing code.
Adding a machine takes more than adding an SVG, but an unverified variant fails closed. The
pilot cannot be completed until the source SVG, make/model, manual, reference photos and reviewer
are supplied. Each later machine is a small reviewable change instead of one large animation PR.
