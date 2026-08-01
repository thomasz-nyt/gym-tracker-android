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
