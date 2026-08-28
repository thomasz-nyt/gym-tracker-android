# ADR-0011: A gym-readable type scale, ahead of the M7 accessibility pass

- **Status:** accepted
- **Date:** 2026-08-01
- **Deciders:** maintainer (requested), agent (scoped)

## Context

The maintainer's words, after carrying the app onto a gym floor: *"some UI improvements,
need bigger font to show in a outdoor or gym environments."*

Material 3's default type scale puts `bodySmall` at 12sp. That is what the completed-sets
list currently uses — the one line you read mid-set, at arm's length, standing up, often
with a phone on a bench under a skylight or a window. 12sp is the wrong size for it.

The tension is with `roadmap.md`, which files **large-font layouts under M7** ("Accessibility
pass: TalkBack, 48dp targets, contrast, large-font layouts"), and constitution §7's rule that
milestones are sequential.

That entry and this change are not the same work. M7 is an *audit*: TalkBack labels, contrast
ratios, touch targets, and proving the layouts survive the OS font-size setting cranked to
200%. This is picking a legible default type scale for a screen the maintainer is using now.
Deferring it would mean logging real workouts against text sized for a desk. M7 still has to
run, and it still has to audit these layouts.

Note this is a **readability** decision, not an outdoor-activity one. Constitution §1 puts
outdoor training permanently out of scope, and nothing here changes that; a gym with big
windows is still indoor, equipment-based strength training.

## Options considered

1. **Raise the Material 3 type scale app-wide in `:core:designsystem`.** One `Typography`
   object, every screen inherits, no new state, no settings surface, nothing to forget to
   apply on the next screen.
2. **A "gym mode" toggle, defaulting on.** Rejected for now: a DataStore preference, a
   settings surface to reach it, ViewModel state and tests, to make reversible a change
   nobody has yet asked to reverse. The OS font-size setting is already the system-wide
   version of this control, and it works today. Revisit if a household member wants
   *smaller* text than this default.
3. **Enlarge only the logged-set rows and the rest countdown.** The smallest diff, and it
   was tempting. Rejected because it leaves every future screen inheriting 12sp and makes
   the next screen's author repeat the decision — exactly the drift a design system exists
   to prevent.

## Decision

Option 1. `:core:designsystem` gains a `GymTypography` that overrides the Material 3 scale,
and `GymTrackerTheme` passes it to `MaterialTheme`.

| Role | M3 default | Here | Used for |
|---|---|---|---|
| `bodySmall` | 12sp | 16sp | secondary detail |
| `bodyMedium` | 14sp | 18sp | supporting lines |
| `bodyLarge` | 16sp | 20sp | body copy |
| `labelLarge` | 14sp | 18sp | button text |
| `titleSmall` | 14sp | 20sp | list headlines |
| `titleMedium` | 16sp | 22sp | screen sub-headings |
| `titleLarge` | 22sp | 28sp | screen headings |

Line heights rise with them so the text does not crowd itself.

Two rules that come with it:

- **Feature code never hard-codes an `sp` value.** Everything reads a role off
  `MaterialTheme.typography`, so M7 tunes one file rather than grepping for numbers.
- **The logged-set line is a `titleMedium`, not a `bodySmall`.** It is the primary content
  of that screen — the thing you came back to the phone to read — and its role should say so.
  Sizing it by role rather than by an ad-hoc `fontSize` is what keeps rule one honest.

Sizes stay in `sp`, so the OS font-size setting still multiplies them. A member who has
already set large system text gets larger text still. That is the correct behaviour and is
not a bug to cap.

## Consequences

- Layouts must tolerate text roughly 1.3× taller than before. Every list is already a
  `LazyColumn` and every row already wraps, so the risk is concentrated in fixed-height
  rows and side-by-side fields — the Sets / Reps / RPE row in set entry is the one to watch.
- Fewer items fit on screen. For the completed-sets list that is an acceptable trade: sets
  you can read beat sets you can count.
- M7's accessibility pass is unchanged in scope and now starts from a better default. It
  still owns TalkBack, contrast, 48dp targets, and the 200%-font-scale audit.
- **Revisit if** a screen truncates at a large system font scale, or if a household member
  asks for denser text — at which point option 2 becomes the answer, with this scale as its
  "on" value.

## Amendment (2026-08-28): ten roles, each with its own line ceiling

This ADR's revisit clause fired. A screen *did* truncate at a large system font scale — not
one screen, four: a picker row grown to three lines, a rest panel where `55 lb × 12 · 25 kg ·
set 2 of 3` was set at the same weight as the exercise name above it, a session load line
(`Bodyweight × 12`) that wraps at 320dp × 130% font scale, and dot-joined history sentences
that leave an orphan tail (`of 3`, `3 bodyweight`) on its own line. None of it was a font
problem. It was four missing rules, applied nowhere:

1. Metadata was set as display type (this ADR's own `titleMedium`, 22sp at Compose's default
   weight with digit runs bolted to 800 by `NumeralText` — not the 24sp/800 the redesign audit
   first suspected, but the same category of error).
2. Nothing declared `maxLines`, so Compose wrapped forever instead of truncating in place.
3. Dot-joined strings in a narrow column break at any point, with no control over where.
4. Words were sized as if they were digits — `Bodyweight` at a 44sp numeral role has no width
   budget at 320dp × 1.3 at any gutter.

Read this amendment together with [ADR-0008's Turn 4 amendment](0008-show-both-units.md), which
withdraws the secondary-unit conversion from every surface this table covers except Progress
and history — the conversion is what turned the rest panel's load line into a wrapping
sentence in the first place.

### The table (`Redesign.dc.html`, Turn 4, frame `4f`)

The ceiling belongs to the **role**, not the call site: `GymTextRole` (`:core:designsystem`)
pairs a `TextStyle` with its `maxLines` and overflow, and a `GymText(text, role, …)` composable
reads both, so a new screen names a role and cannot forget the ceiling.

| Role | sp | Line height | Weight | maxLines | Allowed for |
| --- | --- | --- | --- | --- | --- |
| `display.timer` | 88 | 0.86 | 800 | 1 | Rest and warm-up countdown. Nowhere else. Tabular figures. |
| `numeral.lg` | 34 | 1.00 | 800 | 1 | Target load, rep count, est. 1RM. Digits only. |
| `numeral.md` | 24 | 1.00 | 800 | 1 | Stepper value. |
| `title.lg` | 22 | 1.08 | 800 | 2 | Screen titles, exercise name in rest / up next, dialog question. |
| `title.md` | 17 | 1.20 | 800 | 2 | List row names, history titles, log-button value line. |
| `word.unit` | 20 | 1.00 | 800 | 1 | A word standing where a numeral would (`Bodyweight`), and unit suffixes (`lb`, `kg`). |
| `body` | 15 | 1.35 | 500 | 2 | Card names, the little prose that survives. |
| `meta` | 13 | 1.30 | 600 | 1 | Set counts, dates, targets, secondary units, `of 1:00`. |
| `label.caps` | 12 | 1.10 | 700, +0.12em | 1 | `REST`, `THEN`, `UP NEXT`, section labels, stat rows, secondary buttons. |
| `tag.caps` | 11 | 1.10 | 800, +0.06em | 1 | `ADDED` tag, kickers inside filled buttons. The floor — nothing smaller ships. |

Rules that go with the table:

- **Digits get numeral roles, words get word roles.** A load line is a baseline `Row` of
  separate `Text`s — `34sp` for `55`, `20sp` for `lb`, `20sp` for `×`, `34sp` for `12` — not
  one formatted string. This is what makes `100 lb × 12` and `Bodyweight × 12` both fit on one
  line with no autosizing and no measuring. It needs the number and the unit apart, which is
  why `WeightFormatter.WeightDisplay` gains `number` / `unit` / `isBodyweight` fields alongside
  its existing `primary` string (`:core:domain`, additive).
- Gutter is **20dp on the six migrated screens**, via a new `GymDimens.CompactScreenPadding`
  (20dp) used only by them. `GymDimens.ScreenPadding` (24dp) is unchanged — it is read by
  fourteen files, most of them untouched by this pass (Settings, both Progress screens,
  ExerciseDetail, the routine editor, WorkoutDetail, SetSheets, GuidedExerciseScreen's own
  non-migrated frames), and lowering it app-wide would silently reflow every one of them. See
  "An additive scale, not a value change" below — the same reasoning applies to the gutter as
  to the type roles themselves.

### An additive scale, not a value change

`GymTextRole` is a **new, parallel scale**, not a rewrite of `GymTypography`'s existing
`Typography` slots. Nothing in `MaterialTheme.typography` changes value. This matters because
those slots are shared: `titleMedium` alone is read by fourteen files, `bodySmall` by twelve,
`titleLarge` by twelve — the great majority of them untouched by this pass (Settings, both
Progress screens, the routine editor, Routines, WorkoutDetail, SetSheets, and
`GuidedExerciseScreen`'s own non-migrated frames). Repointing a shared slot's *value* to a
Turn-4 number would reflow every one of those screens as a side effect of a pass scoped to six.

The two slots this table might have been expected to touch — ADR-0029's `displayLarge` (104sp,
"the rest/warm-up countdown") and `headlineMedium` (44sp, "the rest banner's weight readout") —
turn out, on a full-repo grep, to be read by exactly two files: `RestPanel.kt`, which this pass
migrates, and `GuidedExerciseScreen.kt`, which it does not (see "Frame `4b` is
`GuidedExerciseScreen.kt`, not `RestPanel.kt`" below — that screen keeps its own rest countdown,
at the old size, unmigrated). So both stay exactly where ADR-0029 pinned them: `RestPanel.kt`'s
countdown and load readout move to the new `display.timer` (88sp) and `numeral.lg` (34sp) roles
instead, and `GuidedExerciseScreen.kt` keeps reading `displayLarge`/`headlineMedium` at their
original 104sp/44sp, exactly as before. `GymTypographyTest`'s "the design's exact pixel values"
assertions are untouched.

The same holds for the 16sp content floor and `titleMedium`'s ExtraBold exemption:
`GymTypographyTest`'s "nothing the app renders is smaller than sixteen sp" test and its
`titleMedium stays off the ExtraBold hierarchy` test both keep asserting against the *existing*
roles, which are unchanged and still read everywhere they were before. The new scale simply
isn't subject to either — it has its own floor (`tag.caps` at 11sp, which `4f`'s own text
calls "the floor — nothing smaller ships" for it), the same way `labelMedium`/`labelSmall`
(13sp/12sp) already sit under the 16sp floor as wayfinding labels rather than content, a
distinction `Type.kt`'s own class doc already draws. `title.md`'s weight-800 base means
`NumeralText`'s digit-bolding would draw nothing extra on that specific role — but `title.md`
is a role the split baseline row replaces `NumeralText` with, not one `NumeralText` is used on,
so nothing regresses; `NumeralText` keeps working exactly as before on every existing role that
still uses it (the completed-set rows, the rest comparison line).

### Frame `4b` is `GuidedExerciseScreen.kt`, not `RestPanel.kt`

Section 2 of the redesign prompt files the `Target 12` string under "Rest panel," and frame
`4b`'s own title is "Rest panel + set entry." Both read as `RestPanel.kt`. They are not: `4b`'s
mockup vocabulary — the `Then` eyebrow, a `Reps` stepper with a `Target 12` hint beneath it, and
a `Log set 2` / `Stop` button pair with no adjacent `SKIP REST` or `Add set` — matches
`GuidedExerciseScreen.kt`'s `RunningRestBanner` and `GuidedControls` composables exactly, and
matches nothing in `RestPanel.kt`. `RestPanel.kt`'s own vocabulary (`SKIP REST`, `Up next`,
`LOG SET N — DON'T WAIT`, `Add set`) appears instead in frame `4c`, alongside `SessionScaffold`'s
header (`FREESTYLE`, `4 min · 2 of 5 done`, `FINISH`) — `4c` is the composite main session
screen with `RestPanel.kt`'s resting state nested inside it, and `4b` is the separate
one-exercise-at-a-time guided flow (ADR-0033) reached by tapping "Start exercise." Both screens
implement their own version of a rest countdown, independently, and the redesign fixes both —
under the frame that actually depicts each one.

### Three points this amendment settles explicitly rather than leaving implicit

**Milestone tension, argued the same way this ADR already argued it once.** `roadmap.md` files
large-font layouts under M7, which is unstarted; current work is M4b. This ADR already pulled a
readability fix ahead of M7 once, on the grounds that M7 is an audit of layouts that must
already survive large text, not the reason they first become legible — "M7 still has to run,
and it still has to audit these layouts." The same argument applies here: fixing four
reproducible wraps is not the M7 audit, and does not substitute for it.

**Frames vs. this repo's own tripwires.** `Redesign.dc.html`'s Turn 4 frames uppercase every
button label (`ADD SET`, `STOP`, `LOG SET 4`). `GymButtons.kt`'s `ButtonLabel` deliberately does
not uppercase visually — a `Text`'s drawn string and its semantics string are the same value,
and `TwoTapSetLoggingTest` / `CorrectingASetTest` match `"Add set"` and `"Save set"` literally.
`WarmUpPanelScreenTest` and `SessionMovements.kt`'s `Start exercise`/`Remove` carry the same
constraint for `Start warm-up` and `Done`. This amendment keeps those three labels in sentence
case; the uppercase treatment applies only to genuinely new `label.caps`/`tag.caps` text (the
log-button kicker, section labels, `Set N` badges) that no test matches today.

**No manufactured totals — but only one of the two frames needs the guard.** Frame `4c` shows
`Set 4 of 5`, rendered by `RestPanel.kt`'s `UpNext`. A freestyle session has no set total there
— `MovementTarget.sets` is only populated by copying a routine item at session start (ADR-0027)
— and `UpNext` already documents why it omits "of N": *"the app does not know how many sets are
intended."* This amendment reads `"SET $index OF $total"` when `target.sets` is non-null and
`"SET $index"` otherwise — display only, no data added, no comparison manufactured
(constitution §2.4). Frame `4b`'s `Set 2 of 3`, by contrast, is `GuidedExerciseScreen.kt`'s
`GuidedRunning.targetSets: Int` — non-nullable, always populated (defaulting to 3 when a
movement carries no target, per that flow's own existing rule) — so it needs no guard at all;
`"Set ${setsDone + 1} of $targetSets"` is already backed by real data today.

### Deferred, not implemented

The frames also show elements this pass does not add, because they are new behaviour and this
is a type-and-layout pass only: a running count of catalog results (`412 movements`), a
session-length footer on the rest panel and the session screen, a warm-up preset duration
(`2:00` — the warm-up is an uncapped stopwatch, ADR-0021), muscle-group chips under weekly
volume, and a "sessions this week" / "repeat last routine" section on Train home. Each needs its
own user story or ADR before it lands, per this repo's working method.
