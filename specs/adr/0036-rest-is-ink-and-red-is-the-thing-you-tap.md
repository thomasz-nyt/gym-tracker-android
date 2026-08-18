# ADR-0036: Rest is ink, and red is the thing you tap

- **Status:** accepted
- **Date:** 2026-08-17
- **Deciders:** maintainer (supplied Turn 3 of the design bundle, chose the scope), agent (scoped)
- **Relates to:** ADR-0029 (the session screen is a ruled sheet), ADR-0019 (the mono palette),
  ADR-0023 (the rest period earns its space), US-29 (the countdown progress bar and its pinned
  `RestTimerStore.restTotal`, PR #57 — see the amendment below)

## Context

`Redesign.dc.html`'s Turn 3 ("Three clocks, one accent") diagnosed the rest countdown ADR-0029
shipped: a full-bleed `primary`/`onPrimary` `Surface` for the entire rest period. Everywhere
else in the app a red fill means *tap this* — the log button, `START {exercise}`, the finish
button's own outlined restraint all read that way by contrast. During rest, the thing you tap is
`LOG SET n — DON'T WAIT`, and it currently sits below a red block that outweighs it roughly
eight to one on screen. ADR-0029's own rule — "exactly one filled accent element on screen at a
time" — was never actually true during rest; two red-filled things were never on screen
together only because the countdown block was the *only* filled thing, which is a coincidence of
scope, not the rule working.

Turn 3 also names the frame's `◔ CUE AT 0:10` label and `+30s` control. Both are already
deferred by ADR-0029 and `RestPanel.kt`'s own KDoc — `RestTimer.extend()` does not exist, and
neither does an audio cue — and `specs/roadmap.md` lists both under "needs the maintainer's
call". Confirmed again here: **both stay out.** The 0:10 colour flip below is kept, because it
is a real visual behaviour change, not a promise of one that does not exist.

Separately, Turn 3's derived-interval finding (frame `3g`) gives every set row a number that
today has no rendering slot: the ✓ checkmark the ADR-0029 set row currently draws. A trailing
figure and a redundant tick do not both belong in a 44dp-labelled row — see US-44.

## Options considered

**Rest countdown colour**

1. **Ink block, red earns the last ten seconds.** Chosen. Resting is `inverseSurface`/
   `inverseOnSurface` — a real, already-contrast-gated pair, not a new one — with the accent
   drawn only as the log button's fill. At `remaining ≤ 10s` the block and the log button swap:
   the block takes `primary`/`onPrimary`, the log button steps back to outlined. Exactly one
   filled accent element at every instant, including the swap itself, which is the mechanism
   that keeps ADR-0029's rule literally true through the whole rest cycle rather than by luck of
   scope.
2. **No field at all** (Turn 3's `3d`) — cheapest, but throws away the countdown block's role as
   "the thing you notice from across the room" (ADR-0023), which nothing else on the redesign
   replaces. Rejected.
3. **Keep the red, shrink it to a band** (`3e`) — smallest diff to ADR-0029's text, but forces
   the log button to outline for the *entire* rest period rather than the *urgent* last ten
   seconds, which is the opposite of what "don't wait" should look like. Rejected.
4. **Leave it filled, my as originally shipped.** Leaves the eight-to-one imbalance and the
   `+30s`/cue gaps unaddressed. Rejected — this is the thing being fixed.

**Set-row trailing slot**

1. **Interval, not a checkmark.** Chosen. A row already showing a real weight × reps number does
   not need a second symbol to confirm it happened; the interval is new information the
   checkmark never was.
2. **Both, side by side.** Rejected on width: `RepMascotGeometry`'s design measurements and
   `SessionMovements.kt`'s existing `RowLabelWidth` (44dp) leave no room for two trailing
   figures without wrapping, the exact overflow failure mode Turn 3's warm-up finding already
   diagnosed once this milestone.

## Decision

- `RestCountdownBanner` (`RestPanel.kt`) is two-state, switched on `remaining <= 10s`:

  | | calm (> 0:10) | final ten (≤ 0:10) |
  |---|---|---|
  | countdown block | `inverseSurface` / `inverseOnSurface` | `primary` / `onPrimary` |
  | log button | filled `primary` | outlined |

  Both pairs are colours `GymColorSchemeTest` already gates — no new palette entry, nothing new
  to add to that suite.
- A progress bar and an `of {total}` readout are added beside the countdown, requiring
  `RestTimerStore.defaultRest` threaded into `SessionUiState` as `restTotal` — the fix
  `RestPanel.kt`'s own KDoc already named and deferred. `restTotal` is a **live** read of the
  setting, combined the same way the rest tick already is, not a value captured when the current
  rest started — `RestTimer.start()` reads `defaultRest` once to compute an end instant but never
  stores the duration itself, and adding that storage is a bigger change than this fix needs.
  The one honest consequence: changing the setting in the middle of a running rest changes what
  `restTotal` reads for that same rest, which would visibly shift the "of {total}" figure and the
  progress bar's denominator. Visiting Settings mid-rest is already a narrow edge case, and a
  denominator that briefly disagrees with itself is a smaller cost than the storage this would
  take to close exactly — revisit if it turns out to bother anyone in practice. **Superseded by
  US-29 before this ADR shipped code — see the amendment below.**
- `+30s` and the `◔ CUE AT 0:10` label **stay out.** Confirmed with the maintainer a second time
  before this ADR: both need behaviour (`RestTimer.extend()`, a real sound) this change does not
  build. The 0:10 colour flip is kept — it costs nothing undelivered and is itself the cue.
- `PrimaryActionButton`'s two-line overload (`GymButtons.kt`) gains an `outlined: Boolean = false`
  parameter, which is the "countdown block built two ways" mechanism `RestPanel.kt:123-131`
  already says the deferred PR-banner swap needs. That swap itself stays out of scope here — with
  the countdown no longer accent-filled outside the final ten seconds, the "two filled surfaces
  on one rest cycle" problem the PR banner posed has mostly dissolved on its own.
- Set rows (`LoggedSets`, `SessionMovements.kt`) drop the `✓` in accent for the derived
  set-to-set interval, muted, at `bodySmall` (US-44). This is a rendering change only — no
  behaviour the ✓ carried is lost, since a logged row's presence in the list already means it
  was logged.

ADR-0019 is untouched: still one accent, still red, still nowhere in `ColorScheme` but `primary`/
`RedBright`. Nothing here adds a hue.

## Consequences

- The rest banner goes from "always the loudest thing on screen" to "the log button is the
  loudest thing on screen, except for the last ten seconds, when the countdown correctly is."
- `SessionUiState` gains a field (`restTotal`) sourced from a store the ViewModel already
  injects but, until now, never read into UI state.
- `PrimaryActionButton`'s outlined variant is now available to any future call site that needs
  a "de-emphasised but still primary" button — worth knowing about before a third bespoke
  outlined-button idiom appears somewhere else.
- The set row's ✓ is gone; nothing currently depends on its presence (no test asserts it), but
  any future work reaching for "how do I show a set is done" should reach for the row's own
  presence in the list, not a new symbol.
- **Revisit** if `RestTimer.extend()` and an audio cue are ever built — `+30s` and the cue label
  belong in this same countdown block, not bolted on separately, and this ADR's two-state
  colour split should absorb them rather than be reworked around them.

## Amendment, 2026-08-17 (US-29, PR #57)

This ADR's own Decision section, above, proposed `restTotal` as a **live** read of
`RestTimerStore.defaultRest`, naming as an accepted cost that changing the rest-length setting
mid-rest would visibly shift the progress bar's denominator out from under a countdown already
running. That was still true when this ADR was written; it stopped being true before this ADR's
code shipped. US-29's own countdown-progress-bar story, landed in parallel as PR #57, added
exactly the storage this ADR had judged not worth its cost: `RestTimerStore.restTotal`, a second
`Flow<Duration?>` pinned to whatever `defaultRest` read at the moment `RestTimer.start()` called
`setRest(endsAt, total)` — written atomically with `restEndsAt` so the two values can never
describe two different rests. `RestingBody`/`RestCountdownBanner` (`RestPanel.kt`) now read this
pinned `restTotal` rather than a live `defaultRest`, and the mid-rest-desync limitation this ADR
accepted above no longer applies: changing the setting in Settings while a rest is running has no
effect on that rest's own bar or readout, matching what US-42 already promises for the setting
itself.

One consequence follows from the pin rather than the live read: `restTotal` is `null`, not a
guessed value, for a rest that was already running when a member upgrades onto the migration that
adds this column — `RestTimerStore`'s storage is additive, so an in-flight rest genuinely predates
the field. `RestingBody` renders the countdown number regardless; only the bar and the `"of
{total}"` readout are skipped for that one rest, rather than either guessing a denominator
(constitution §2.4) or drawing one against the now-changed live setting the way the original
Decision text above would have.

Nothing else in this ADR's Decision changes: the two-state ink/red colour split, the log button's
`outlined` swap at the same ten-second threshold, and the deferred `+30s`/audio-cue scope are all
unaffected by which flow `restTotal` reads from.
