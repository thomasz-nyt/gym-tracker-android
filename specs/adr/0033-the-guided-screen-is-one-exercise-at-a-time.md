# ADR-0033: The guided screen is one exercise, sized for the bench

- **Status:** accepted
- **Date:** 2026-08-14
- **Deciders:** maintainer (reported the gap), agent (scoped)

## Context

A member reported that a screen they use while training still looks like the pre-redesign app:
"it still shows the Go, set and reps, a large button with Finish set... the design should use
the same larger area and font to show the name, weight, reps, sets info in a same red
background area."

That screen is `GuidedExerciseScreen.kt` (ADR-0017's guided exercise flow, US-05a) — **not** the
main active-session screen, which ADR-0029 already rebuilt. Guided mode is a separate, fully
reachable screen entered via "Start exercise" on the current-movement row; while active it
replaces the session screen entirely (`ActiveSessionRoute.kt`'s `LoggingScreen`: `if (running !=
null) GuidedRoute(...) else SessionScreen(...)`). It predates the redesign. `Redesign.dc.html`
draws no frame for it, ADR-0029 explicitly scoped it out ("`GuidedExerciseScreen`'s rep counter
also reads it and is out of this ADR's scope"), and `roadmap.md`'s "what's left from the audit"
section never listed it — nobody had looked at it since the redesign shipped. This is a genuine
gap surfaced by use, not a regression and not previously-tracked work.

The design bundle has no frame drawn for a screen shaped like this one, but it does have
**option `1b`** — a whole alternative direction for the *main* session screen ("one exercise at a
time... sized to be read from the bench with the phone flat... sets are dots, not rows"), which
ADR-0029 rejected in favour of `1a` for that screen. 1b's stated purpose is a materially closer
match to what the guided screen is *for* — one movement, full focus — than to the multi-exercise
session screen it lost to there. This ADR borrows 1b's `Focus mid-set` / `Focus resting` frames
as the closest available source material for guided mode specifically, without reopening
ADR-0029's 1a decision for the main screen, which is untouched by this ADR.

## Options considered

1. **Leave it alone.** Rejected: it is the screen the member is actually complaining about, and
   its Material-default styling is now the most visible pre-redesign surface left in the app.
2. **Invent a new visual language for this screen from scratch.** Rejected: nothing in the
   design bundle backs it, and it would mean picking sizes and colours with no source of truth —
   exactly what `Type.kt`'s "fill unused roles rather than add a parallel token system" rule
   exists to prevent.
3. **Borrow 1b's recipe, expressed through roles and components ADR-0029 already shipped.**
   Chosen. 1b was drawn for a one-exercise, full-focus screen; guided mode is that screen. Every
   pixel value in 1b's frames is re-expressed through an existing `Typography` role or an
   existing composable (`RestPanel.kt`'s hero `Surface`, the session header's `SegmentBar`)
   rather than adding new ones, the same discipline ADR-0029 held to.

## Decision

Rebuild `GuidedExerciseScreen.kt`'s three states — mid-set, resting, complete — on the shipped
design system, reading `1b Focus mid-set` and `1b Focus resting` for shape and drawing every
size from a role ADR-0029 already created. Adopt `StepperField` for the rep count, the one
behavioural change bundled in. `GuidedSetupDialog` (the "Start {exercise}" modal) stays out of
scope. Release `displayMedium` from the exception ADR-0029 carved out for this screen.

### Layout, top to bottom

Root drops `Alignment.CenterHorizontally` and the root `ScreenPadding` — every other rebuilt
screen is flush-left, and the resting hero needs to be full-bleed, which a root padding would
prevent. Each state below applies its own padding.

**Mid-set, not resting.** Eyebrow `"Set {n} of {total}"`, `labelSmall`, accent — the same shape
as the session screen's `"Exercise {n} of {total}"`, and honest here in a way it deliberately is
not on the session screen: `UpNextSet` has no total to render, but `GuidedRunning.targetSets`
genuinely exists. Movement name, `headlineSmall`. A structural rule (2px, solid `onSurface`,
`SessionScaffold.kt`'s existing inline shape). Then the hero: weight × **the typed rep count**
(not the target) through `NumeralText` at `headlineMedium` — the exact role and call-site shape
`RestPanel`'s `UpNext` already uses for the same kind of readout — with the secondary unit at
`titleMedium`/`onSurfaceVariant`. Reading the typed count rather than the target is the literal
fix for what the member described: the number on screen is the number `Log set N` is about to
write. `"Go"` is deleted outright — it was a placeholder for a slot the eyebrow and hero now
fill.

**Resting.** A full-bleed `Surface(color = primary, contentColor = onPrimary)`, the identical
shape `RestPanel.kt`'s `PersonalRecordBanner` and `RestCountdownBanner` already ship: eyebrow
`"Rest"`, the countdown at `displayLarge` (same semantics `contentDescription` pattern as the
session screen's banner), a rule, eyebrow `"Then"`, movement name at `headlineSmall`, and **one
line** — `"135 lb × 12  ·  61.2 kg  ·  set 3 of 3"` — at `titleLarge`. That combined line is the
member's request rendered literally: name, weight, reps and set count together, on the red
ground. It is a plain `Text`, not `NumeralText`: `titleLarge`'s base weight is already
ExtraBold, so `NumeralText`'s digit-bolding span would draw nothing — the identical reasoning
`Type.kt` already documents for why `titleMedium` stays off the ExtraBold hierarchy. No skip-rest
control is drawn here: `Log set N` is already live throughout the countdown (ADR-0023's rule,
held structurally on this screen exactly as on the session screen), so there is no action a skip
button would unblock.

**Shared controls**, drawn under either head:

- Set-progress dots reuse the session header's `SegmentBar`, promoted from `private fun
  SegmentBar(progress: SessionProgress, ...)` to `internal fun SegmentBar(total: Int, done: Int,
  ...)` so it can be called with `targetSets`/`setsDone` here without a `SessionProgress` to
  wrap them in. Same three-weight colour rule ADR-0029 already settled: done segments solid
  `primary`, the current one `primary` at 55% alpha, upcoming ones `onSurface` at 20% alpha —
  the same avoidance of the design's third red (`#EC3013`), which ADR-0029 already refused by
  name.
- `StepperField(label = "Reps", ...)` — see "The rep count" below.
- The action row: `PrimaryActionButton("Log set {n}")`, single-string overload (the hero above
  already shows the numbers; the two-line eyebrow/detail overload the session screen uses would
  repeat them), beside an outlined `Stop` sized to `GymDimens.PrimaryAction` — replacing the
  session-adjacent screen's loose `TextButton`.

**Complete.** Same treatment: eyebrow `"Done"`, name at `headlineSmall`, a rule,
`NumeralText(headlineMedium)` for the sets/reps summary, `NumeralText(bodySmall,
onSurfaceVariant)` for the volume/time meta, the same `SegmentBar` fully filled, then the
existing `"Next: {name}"` / `"Back to workout"` / `"Stop here"` actions, unchanged in string and
behaviour. `"Back to workout"` is promoted from a `TextButton` to the primary action when there
is no next exercise — with nothing left to start, leaving is the screen's most frequent action,
which is exactly what `PrimaryActionButton`'s own doc reserves the role for.

### `"Finish set"` becomes `"Log set {n}"`

No test in the repo asserts the literal string `"Finish set"` (checked against
`app/src/androidTest` and `feature/logging`'s test sources), so this is not the `Add
set`/`ADJUST` hazard ADR-0029 refused: that rename was rejected because `ADJUST` would have named
a *different* control than the one `TwoTapSetLoggingTest` matches. Here the two labels name the
same operation — write one set through `LogSets`, start the rest — and `Log set {n}` is strictly
more precise: it names the number about to be written, and it uses the same verb the session
screen's one-tap button already uses for the same action.

### The rep count: `StepperField`, not a restyled text box

`GuidedController` gains `fun stepReps(direction: Int)`, sharing the same fallback `finishSet`
already uses (`typedReps.value ?: plan.targetReps.toString()`) and the same
`String.stepWholeNumber` every other numeric input in the app already shares (`SetSteppers.kt`,
floors at 1). `GuidedActions` gains `onRepsStepped`, wired through `GuidedRoute`.

This is the one behavioural change in this ADR, and it is bundled deliberately rather than left
for a follow-up: guided mode is the single strongest case for ADR-0016's own stepper rationale —
the edit is almost always ±1 to ±3 reps from the target, mid-set, one-handed — and shipping the
visual rebuild with the last numeric input in the app that has no `+`/`−` would be a smaller,
harder-to-justify PR than doing both together. `StepperField`'s own `OutlinedTextField` stays
live underneath, so ADR-0017's "the target is a prefill, never a promise" guarantee is preserved
structurally, not just by intent — and its own tests (`an edited rep count is what gets logged,
not the target`, `the rep field resets to the target after each set`) are unedited proof of that.

### `displayMedium` is released

ADR-0029 left `displayMedium` at Material's default specifically because this screen read it.
Once the countdown here moves to `displayLarge` (matching the session screen's banner),
`displayMedium` is read nowhere in the app. It stays at Material's default with Archivo wired —
the same treatment `displaySmall`/`headlineLarge` already get — rather than being repurposed for
something that does not yet exist, so a future accidental use does not silently fall back to the
system font.

### What this ADR does not touch

`GuidedSetupDialog` (the "Start {exercise}" modal) is unchanged. It is not the running state the
member described, `1b` has no frame for it, and ADR-0017 deliberately kept it separate from
`SetEntryDialog` specifically so the two-tap path's literal string matches stay out of reach of
changes to guided mode. The result is a visible inconsistency after this ADR — the running screen
has steppers, the setup dialog still has three bare `OutlinedTextField`s — logged here as a named
follow-up (three `StepperField`s in the same dialog shape) rather than a silent gap.

### Deliberately not adopted from the `1b` frames

- **The grayscale hero photo.** ADR-0029 chose no photo for the screen this one replaces while
  active; adding one here would make a photo appear and disappear as guided mode toggles, and
  not every exercise in the catalog has one (ADR-0014).
- **The header's prev/next exercise arrows.** Guided mode already advances through `nextUp` at
  the summary, by ADR-0017's design; arrows would imply free navigation between exercises no
  action backs — the same "a control that visibly does nothing is worse than an absent one"
  reasoning ADR-0029 already applied to the design's `+30s`.
- **`#EC3013`**, the frame's third red. Already refused by ADR-0029 by name; the set dots read
  the same three weights the segment bar already uses.
- **The frame's trailing `+` cell** after the last set dot. It implies logging beyond the
  target, which guided mode has no action for — the flow completes at `targetSets`.
- **152sp / 76sp / 34px / 32px.** All four read through already-shipped roles (`displayLarge`
  104sp, `headlineMedium` 44sp, `titleLarge` 28sp, `headlineSmall` 27sp). ADR-0011 forbids
  feature code naming a raw `sp`, and every `Typography` slot ADR-0029 did not already claim is
  deliberately left alone rather than opening a sixteenth role for a few pixels' difference.
- **The frame's fixed 84dp secondary button.** `Stop` uses `GymDimens.PrimaryAction` (72dp), a
  token that already exists and is already the height of the button beside it.
- **Filling the entire screen red while resting.** Only the countdown block goes full-bleed; the
  steppers and action row stay on the light ground beneath it. Inverting every control's colours
  onto the accent for a state that lasts under a minute would be a wide surface of ad-hoc colour
  overrides outside `GymColorSchemeTest`'s gated `primary`/`onPrimary` pair, the same trade
  `RestPanel` already made and documented for the session screen's rest banner. Of everything in
  this list, this is the one most likely to be revisited after a look on device.

## Consequences

- The guided screen goes from Material defaults and centred text to the same ruled, flush-left,
  role-driven system the session screen already has. A member moving between "Add set" and
  "Start exercise" now sees one design language, not two.
- Zero new `Typography` roles, zero new `GymDimens` tokens, zero new palette entries. Every size
  in this ADR was already spoken for by ADR-0029.
- `displayMedium`'s protected-because-read-elsewhere status ends; `GymTypographyTest`'s test
  naming that reason needs renaming in the same change, or its premise silently goes stale.
- `SegmentBar`'s signature changes from `(progress: SessionProgress)` to `(total: Int, done:
  Int)` — a pure refactor with one existing call site (the session header) plus this screen's
  new one.
- The rep count gains a real behavioural change (steppers), which is why this ADR needs a
  failing test first and not just a visual diff — `GuidedController.stepReps` is new surface
  area, unlike everything else in this ADR.
- `GuidedSetupDialog` is now the one guided-mode surface still on Material defaults — a visible,
  logged inconsistency and the natural next story, not silently left inconsistent.
- No UI test currently reaches guided mode at all; this ADR's PR adds the first one
  (`GuidedFlowScreenTest`), which is also how a screen like this stays noticed next time.
- Revisit the "no red behind the controls" call above if the maintainer, looking at this on
  device, wants the resting state to read more like `1b`'s fully inverted screen.
