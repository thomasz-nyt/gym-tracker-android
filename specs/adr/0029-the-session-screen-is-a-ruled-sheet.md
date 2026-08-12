# ADR-0029: The session screen is a ruled sheet, not a card stack

- **Status:** accepted
- **Date:** 2026-08-11
- **Deciders:** maintainer (supplied the design bundle), agent (scoped)

## Context

ADR-0019 landed the palette, the shape scale and the type family. The session screen still
does not look like the redesign, and the reason is not paint. `SessionExercises` wraps each
movement in a `Card` on `surfaceContainer*`, separated by 12dp of `Arrangement.spacedBy` —
`#F3F2F2` against roughly `#DCDBDA`, about 6% apart, which reads as mud rather than structure.
`GymDivider` exists and the session screen never calls it. And `SessionExercises` gives each
movement card its own `PrimaryActionButton`, with `ActiveSession` adding one more for "Add
exercise" — four filled red blocks on one screen, against `PrimaryActionButton`'s own KDoc:
"There is at most one of these per screen."

This was written against the actual `Redesign.dc.html` design bundle (`gym-tracker-app-ui-
redesign/project/`, frames `1a Session mid-set` and `1a Session resting`), not a prose
description of it — every size, weight and colour below is read directly from the frame
markup, not inferred.

`SessionProgress` (`core/domain/session/SessionProgress.kt`) already exists as unwired
groundwork, added specifically so this ADR would have a domain type to read rather than
deriving "n of m done" ad hoc in the ViewModel. `orderIsAPlan` was built into it for exactly
the copy question this ADR has to settle.

## Decision

Rebuild the session screen as a ruled sheet: flush-left rows on the bare ground, separated by
2px rules, with a full-bleed 2px rule under the header. No `Card`, no `surfaceContainer*` fill
anywhere on this screen. Exactly one filled accent element on screen at a time: the log button.

### Layout, top to bottom

- **Header.** Routine name uppercased for display only (`session.routine?.name ?: "Freestyle"`
  — the underlying string is untouched; History and the finish summary keep the name as typed),
  22sp/ExtraBold. Meta line below it: `"{elapsed} · {done} of {total} done"`, 16sp/Medium,
  muted. `FINISH` as an outlined button, top-right, unchanged in behaviour from today.
- **Segment bar**, one 6dp-tall segment per movement in `SessionProgress`, 3dp gaps, **only
  when `orderIsAPlan` is true** — see "Freestyle sessions" below for why it is absent
  otherwise. Three states: done, current, upcoming (see "Segment bar colour" below).
- **Structural rule**: 2px, solid `onSurface` (not `outlineVariant` — that token is the
  *lighter* of the two weights this ADR draws with; see ADR-0019's `outlineVariant` follow-up
  landed alongside this one).
- **Current movement.** Eyebrow `"Exercise {n} of {total}"`, 12sp/Bold, uppercase, accent.
  Movement name, 27sp/ExtraBold, -0.02em tracking. Meta: `"Target {sets} × {reps} · {rest}s
  rest"` when the movement has a target (US-30), otherwise nothing — the US-13 absence
  pattern, not a placeholder.
- **Set rows**, one per logged or upcoming set, each separated by a 2px `outlineVariant` rule
  (the row-weight rule). A done row shows its weight/reps through `NumeralText` and a
  checkmark in accent; the next set to log is dimmed to 55% opacity and labelled `NEXT`
  instead of a set number.
- **"Still to come"**, 12sp/Bold, uppercase, muted — deliberately *not* accent-coloured, unlike
  the current movement's eyebrow, because the redesign audit's finding 07 flagged exactly this
  kind of label reading as a link when it is not one. Each remaining movement is one ruled row:
  index, name, target. A warm-up/cool-down block (§6, not yet built) would render here too,
  with a letter index and a duration instead of a target — the row shape already anticipates
  it, the row content does not yet exist to fill it.
- **Bottom action bar**, 2px gap, never scrolls off: the log button (flex, 72dp) and the button
  that opens the stepper sheet (84dp wide, outlined) beside it.

### The log button is additive, not a replacement — the two-tap tripwire

`TwoTapSetLoggingTest` opens the sheet with `onNodeWithText("Add set")` and confirms with
`onNodeWithText("Save set")`, asserting exactly two `performClick` calls. The roadmap names it
three times as the signal a redesign attempt went wrong, and it must pass **unedited**.

The design's one-tap log button is additive, the same shape as US-05a's precedent: *"Starting
an exercise is an additional action. 'Add set' keeps its place and its behaviour, and the
two-tap path of US-03 is unchanged."* So: the current movement keeps the exact `Add set` button
US-03 built — same callback, same sheet, same "Save set" confirm — beside the new log button.

**The design labels this second button `ADJUST`. The implementation keeps `Add set`.** That is
a deliberate deviation from the design bundle, not an oversight: `TwoTapSetLoggingTest` matches
`onNodeWithText("Add set")` literally, and a `Text` composable's drawn string and its semantics
string are the same value — there is no way to draw "ADJUST" while keeping "Add set" as what
the test (and TalkBack) reads, the identical constraint `GymButtons.kt`'s `ButtonLabel` already
documents for why button labels are not visually uppercased. Renaming the label without
renaming what it does would be exactly the failure mode CLAUDE.md's process warns about: if
this button's own name has to change for the redesign to read right, that is a real product
question — is the mental model now "adjust a value" rather than "add a set"? — worth its own
line in a future user story, not a label swapped quietly inside this one. The log button beside
it is the addition: one tap, writes the prefilled set directly, no sheet. See US-35.

### Rest banner

Full-bleed accent surface. Eyebrow `"Rest"`, giant countdown at 104sp/ExtraBold (new
`displayLarge` role — see below), and `SKIP REST` full-width beneath it.

**The design's `+30s` is deliberately not drawn here.** `RestTimer.extend()` does not exist
until the timer-amendment change, and a control that renders but does nothing when pressed is
worse than one that is absent: the member cannot tell a dead button from a broken one, and the
only feedback either way is nothing happening. The same reasoning drops the design's audio-cue
label (`CUE AT 0:10 & 0:00`), which would promise a sound the phone does not yet make. Both
arrive with the use case that backs them. The layout is a `SKIP REST` at full width until then,
which is a complete state in its own right rather than a gap waiting to be filled.

Below the banner: `"Up next"` (accent eyebrow), movement name (27sp/ExtraBold — same role as
the current-movement name above), meta line, a rule, then the weight readout at 44sp/ExtraBold
(new `headlineMedium` role) through `NumeralText`, and the comparison line. The log button
stays live through all of this (ADR-0023 already got this right) with copy that changes to
`"LOG SET {n} — DON'T WAIT"` while resting, so an early set never needs the timer skipped
first.

### New type roles

Five specific pixel values in the design frames have no existing `Type.kt` role. Rather than
add a parallel token system, they fill Material's unused `Typography` slots — `Typography` has
fifteen named roles and this app was using nine of them before this ADR:

| Role | Size | Weight | Tracking | Used for |
|---|---|---|---|---|
| `displayLarge` | 104sp | ExtraBold | -0.05em | Rest/warm-up countdown |
| `headlineMedium` | 44sp | ExtraBold | -0.03em | Rest banner's weight readout |
| `headlineSmall` | 27sp | ExtraBold | -0.02em | Movement name (current + up next) |
| `labelMedium` | 13sp | Bold | — | `SET 1` / `NEXT` row labels |
| `labelSmall` | 12sp | Bold | 0.12em | Section eyebrows (uppercased at the call site) |

`displayLarge` replaces `RestPanel`'s prior use of `displayMedium` for the countdown — a stale
mismatch: `Type.kt`'s own comment already said *"`displayLarge` is the rest countdown... the
first of these that will actually be used,"* but the code wired `displayMedium` instead.
`displayMedium` is untouched at Material's default size, because `GuidedExerciseScreen`'s rep
counter also reads it and is out of this ADR's scope (ADR-0017, a different feature).

**`labelSmall`/`labelMedium` sit below ADR-0011's 16sp content floor, deliberately.**
`GymTypographyTest`'s "nothing the app renders is smaller than sixteen sp" test covers the
roles a member reads as primary content under load — the set line, button text. Eyebrows and
row-index labels are wayfinding, not content the floor was written to protect, and Material's
own defaults (`labelSmall` = 11sp) already treat this as a distinct class of text. These two
roles are intentionally left out of that test's `roles` map rather than silently exempted; the
exemption is documented on the roles themselves in `Type.kt`.

**The design's 15sp meta text (`"24 min · 2 of 6 done"`, target lines, comparison lines) is
rendered at `bodySmall`'s existing 16sp instead**, not a new role. One pixel below ADR-0011's
floor for a role this app already had was a smaller, more defensible deviation than adding a
sixth new role for a difference nobody would notice and the floor exists specifically to
prevent.

Every other size in the frames — the routine name, the sheet titles, the set-row numerals —
already has a role: `titleLarge` (28sp, ExtraBold, close enough to the design's 26sp headers
to reuse rather than add a role for 2px) and `titleMedium` (22sp, deliberately *not* ExtraBold
— see PR1's `Type.kt` comment on why `NumeralText`'s contrast depends on that).

### Segment bar colour

The design uses a third red (`#EC3013`) for the current-in-progress segment, distinct from the
accent's `#AE1800`/`#FF563C`. Introducing a new named hex would reopen ADR-0019's "one accent"
system for a single decorative detail and add a token nothing else reads. Instead: done
segments render at `colorScheme.primary` (solid), the current segment at `primary` with 55%
alpha, and upcoming segments at `onSurface` with 20% alpha. Three visibly distinct states, zero
new palette entries, nothing new for `GymColorSchemeTest` to gate.

### Freestyle sessions: no segment bar, no "then X"

`SessionProgress.orderIsAPlan` is false whenever a session was not started from a routine
(`session.routine == null`) — a freestyle session's `position` is add-order, not a plan.
ADR-0023 already refused an order-implying claim ("then Seated Cable Rows") for exactly this
case. This ADR keeps that refusal and extends it to the segment bar: a segment bar *is* an
order-implying claim, rendered as a bar instead of a sentence, so it renders only when
`orderIsAPlan` is true. A freestyle session shows the eyebrow without the ordinal
("`Exercise`" rather than "`Exercise 3 of 6`") and the "Up next" meta line without the "then"
clause, both driven by the same flag.

### What this ADR does not touch

`SetSheets.kt`'s `SetEntrySheet` and `SetEditSheet` are functionally already what US-03 and
US-04 require — prefilled steppers, "Save set" / "Save changes" / "Delete set" outlined and
never beside a save, five-second undo. They are not rebuilt to the design's exact two-part
button layout (bold label left, muted detail right) in this ADR; that is a smaller, separable
polish pass, not a structural gap the way the movement list was.

## Consequences

- The session screen goes from four filled buttons to one. Every screenshot in this ADR's PR
  should show that at a glance.
- Five new `Typography` roles exist that nothing outside this screen uses yet — reasonable,
  since nothing else in the app currently needs a 104sp countdown or a 27sp movement name, but
  worth knowing about before adding a sixth "just this once" role elsewhere.
- The rest banner is one control short of the design (`+30s`) and carries no audio-cue label,
  both waiting on `RestTimer.extend()` and the cue itself. Whoever builds those adds the
  controls in the same change, rather than finding them already drawn and merely unwired.
- `SessionMovements.kt` and `SessionScaffold.kt` change shape substantially; `SessionPreviews.kt`
  needs its fixtures updated to match or it stops compiling.
- Revisit if Material3 ever exposes more `Typography` slots, or if a sixth screen needs a
  pixel value close enough to one of the five new roles above to reuse rather than add a
  seventh.
