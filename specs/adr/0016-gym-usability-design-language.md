# ADR-0016: A gym-usability design language — color, emphasis, and touch ergonomics

- **Status:** accepted
- **Date:** 2026-08-02
- **Deciders:** maintainer (requested), agent (scoped)

## Context

The maintainer reviewed the app as a product designer and gym trainer and asked for it to be
easy to use *in the gym*: easy to tap, bright color, larger text and buttons. ADR-0011 already
raised the type scale for exactly this reason. What it did not touch is everything else:

- `Theme.kt` still ships the stock Material 3 purple; its own comment says real tokens arrive
  "with the first screens that need them". Those screens exist now.
- Visual emphasis is inverted from tap frequency. "Add set" — the most-tapped control in the
  app — is a small text button on the right edge of a row. Set entry is a keyboard-only dialog
  in the middle of the screen. The rest countdown, the most-*glanced* element, is the smallest
  text on the session screen. "Finish workout", tapped once per visit and unrecoverable, sits
  directly beside "Add exercise", tapped constantly.
- Between sets you have one hand, chalked fingers, and the phone at arm's length. The common
  edit is small — one plate up, one rep down — and it currently requires summoning a keyboard.

The tension with `roadmap.md` is the one ADR-0011 already resolved: M7 owns the accessibility
*audit* (TalkBack, measured contrast, 200% font-scale layouts), and it keeps all of it. This
ADR picks usable defaults for screens the maintainer is using today. The audit still runs, and
it now starts from a better place.

Two hard constraints frame everything below. Constitution §2: the two-tap set-logging path is
sacred, and `TwoTapSetLoggingTest` enforces it structurally — it is not edited by this work
and must stay green. Constitution §1: nothing here adds a feature; this is the same app with
its emphasis put where its usage is.

## Options considered

1. **One ADR, the pass done now, all screens.** The ADR-0011 precedent. One decision document,
   the core loop redesigned, catalog and history touched only in emphasis. Chosen.
2. **Defer to M7.** Keeps milestone sequence pure; means months of logging real workouts
   against a UI whose most-used control is its smallest. Rejected for the same reason
   ADR-0011 rejected it.
3. **Material You dynamic color** for the "bright" ask. Rejected: it derives *muted* tones
   from the wallpaper, varies per device, and hands the app's one identity decision to the OS.
4. **Dark-only "gym look".** Punchy, but it ignores the system setting ADR-0011 chose to
   respect for font size, and a sunlit gym is exactly where a dark screen washes out.

## Decision

One design language, stated here, implemented in `:core:designsystem`, consumed by role
everywhere else — the color-and-ergonomics counterpart to ADR-0011's type scale.

**Color.** One accent: high-visibility orange, the same hue in both schemes (light primary
`#F26200`, dark primary `#FF6D00`). `onPrimary` is near-black in both — dark-on-orange is how
high-vis actually works (the vest, the road sign); white-on-orange cannot reach AA contrast on
any orange that still reads as bright. The palette follows the system light/dark setting. Red
is reserved for destructive actions and errors, never for emphasis, so Delete can never be
confused with Save. Every foreground/background pair the app renders must meet WCAG AA
(≥ 4.5:1), asserted by a unit test in `:core:designsystem` — the palette is gated by the test,
not by eye.

**Emphasis.** Each screen has exactly one primary role — its most *frequent* action, not its
most important-sounding — rendered as a full-width filled orange button: Start workout (home),
Add set (each exercise card), Save set (entry sheet). Supporting actions are tonal and
full-width; rare actions are quiet text. "Finish workout" moves to the top of the screen, away
from the thumb zone, and gets a one-line confirmation — it is the one tap in the app with no
recovery path, since no story resurrects a finished session.

**Touch ergonomics.** 48dp stays the floor everywhere. Screen-level CTAs are full-width and
64dp tall, anchored at the bottom where the thumb already is. Stepper targets are 56dp.
Feature code never hard-codes these sizes: they are tokens in `:core:designsystem`, the dp
counterpart of ADR-0011's "never hard-code an sp" rule.

**Set entry.** The dialog becomes a bottom sheet — the thumb zone, not the screen center —
and weight, reps, and sets each get large +/− steppers: weight steps ±2.5 kg or ±5 lb in the
member's own unit read at press time, reps and sets step ±1 with a floor of 1 (US-03). Weight
steps down to blank, not to zero — a bodyweight set is an absence, not a value (constitution
§2). Tapping the number still opens the keyboard for big jumps. The two-tap path — open
prefilled, save — is structurally unchanged.

**Rest countdown.** A full-width banner above the bottom action bar: `displayLarge` digits
readable at arm's length, Skip beside them, gone at zero. It is a pure display of ADR-0010's
stored end time and never blocks logging (US-05). Deliberately *not* added: ±30s adjustment,
which would amend US-05 and is a spec conversation, not a styling one.

## Consequences

- Logging becomes one-handed: every frequent action is a large orange target in thumb reach,
  and the common between-sets edit is a tap, not a keyboard session.
- Screens get taller and less dense — cards, 64dp buttons, a countdown banner. Fewer rows fit;
  ADR-0011 already accepted that trade and this doubles down on it.
- The bottom sheet and steppers are more code than the dialog they replace, and stepper
  arithmetic (unit-aware increments, floors, blank-weight semantics) is new controller logic
  that needs its own tests before it is written.
- Every future screen must name its one primary action. That constraint is the point.
- The palette is now the app's identity and is test-gated: changing a color means making the
  contrast suite agree.
- M7's audit is unchanged in scope: TalkBack labels, measured contrast across the whole app,
  and the 200% font-scale layouts are still its work, now over better defaults.
- **Revisit if** a household member finds the UI too loud or too sparse — ADR-0011's "gym
  mode" toggle idea returns with this language as its "on" state — or if fixed stepper
  increments prove wrong for dumbbell progressions, at which point per-exercise increments
  become a user-story discussion rather than a bigger default.
