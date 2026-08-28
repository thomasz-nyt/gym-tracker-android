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
reads both, so a new screen names a role and cannot forget the ceiling. This is additive to,
not a replacement for, `MaterialTheme.typography` — see "Mapping onto the existing
`Typography` slots" below for why.

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
- Gutter is **20dp on every screen** (`GymDimens.ScreenPadding`, 24 → 20). 20dp buys 8dp of
  text column over 24dp without reading as tight.

### Where this table reverses rules this ADR and ADR-0029 already pinned

Three reversals, each a product decision restated in its pinning test rather than a silent
value change:

1. **The 16sp content floor this ADR names in its own "nothing the app renders is smaller than
   sixteen sp" test is withdrawn for `body` (15sp).** That floor is the reason this ADR exists
   — the completed-set line was a 12sp `bodySmall`. Its purpose now moves to role assignment
   instead of a single floor: what a member reads under load is a numeral or title role, and
   `body` is the prose that survives around it, one pixel under the old floor rather than at it.
2. **`title.md` carries weight 800, which puts `NumeralText`'s digit-bolding to sleep on that
   role.** This ADR's own class doc on `Type.kt`, and `GymTypographyTest`'s
   `titleMedium stays off the ExtraBold hierarchy` test, hold `titleMedium` at Compose's default
   weight *specifically* so `NumeralText`'s digit-only bolding still reads as contrast within a
   mixed word/number line. The redesign's split baseline row replaces that mechanism on load
   lines — digits and words are now separate `Text`s at separate roles, not one string with an
   embedded span — but `NumeralText` stays in use elsewhere in the app (set rows, the rest
   comparison line), so this is a role-scoped exception, not a repeal of the mechanism.
3. **ADR-0029's `displayLarge` (104sp, the rest/warm-up countdown) drops to `display.timer`
   at 88sp, and `headlineMedium` (44sp, the rest banner's readout) is retired in favour of
   `numeral.lg` at 34sp.** Both were pinned in `GymTypographyTest` as "the design's exact pixel
   values" — that test now pins the new values instead, with the same comment.

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

**No manufactured totals.** Frame `4c` shows `Set 4 of 5`; `4b` shows `Set 2 of 3`. A freestyle
session has no set total — `MovementTarget.sets` is only populated by copying a routine item at
session start (ADR-0027), and `RestPanel.kt`'s existing `UpNext` composable already documents
why it omits "of N": *"the app does not know how many sets are intended."* This amendment reads
`"SET $index OF $total"` when `target.sets` is non-null and `"SET $index"` otherwise — display
only, no data added, no comparison manufactured (constitution §2.4).

### Deferred, not implemented

The frames also show elements this pass does not add, because they are new behaviour and this
is a type-and-layout pass only: a running count of catalog results (`412 movements`), a
session-length footer on the rest panel and the session screen, a warm-up preset duration
(`2:00` — the warm-up is an uncapped stopwatch, ADR-0021), muscle-group chips under weekly
volume, and a "sessions this week" / "repeat last routine" section on Train home. Each needs its
own user story or ADR before it lands, per this repo's working method.
