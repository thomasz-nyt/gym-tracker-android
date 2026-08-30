# ADR-0045: The warm-up becomes a full-screen step, drawn the same way "Finish" already is

- **Status:** accepted
- **Date:** 2026-08-29
- **Deciders:** maintainer, agent
- **Relates to:** ADR-0021 (a warm-up timer that records nothing — unchanged by this ADR),
  ADR-0013 (state-driven navigation, not a saved back stack), ADR-0035 (`RepMascot`), US-28,
  `specs/roadmap.md` §"What is left from the `Redesign.dc.html` audit"

## Context

`Redesign.dc.html` Turn 5, file `02-warmup-step.md`: the warm-up panel is currently drawn
*inline*, inside the session screen's own column (`ActiveSession` calls
`WarmUpPanel(warmUp)` directly beneath `SessionTopBar`) — while running, it clips the current
exercise name and costs a fixed band of height the rest of the screen has to make room for. The
file's fix is to make it "a full-screen step you pass through once."

**Point 4 of that file — recording the elapsed time on the session and showing it in the header
— is explicitly out of scope, confirmed with the maintainer.** ADR-0021 is titled "a warm-up
timer that records nothing," was decided with the maintainer specifically because constitution
§1 forbids an "activity type" abstraction, and its own Consequences section names this exact
change as its trigger to be revisited: "if the maintainer wants warm-ups to appear in history —
at which point it is a constitution §1 amendment, not a screen." Nothing in this ADR reopens
that; `WarmUpViewModel`, `WarmUpTimer`, and the `sessions` table are untouched.

**"Starting a session with a warm-up goes to a warm-up route first" does not describe how this
app's warm-up actually works, and needed reconciling before anything could be built.**
ADR-0021 rejected "warm-up as a routine step" outright (option 1); there is no property of a
session or a routine that means "this one includes a warm-up." US-28 instead describes the
warm-up as "reachable from the session" and "startable from anywhere in a session" — an
on-demand stopwatch a member may or may not ever trigger, not a mandatory pre-session gate. So
"the route" this file means is better read as: whatever currently happens when a member taps
"Start warm-up" should become a full-screen experience instead of an inline one — not a new gate
every session start passes through.

**A real `NavHost` destination was considered and rejected.** `GymTrackerNavHost`'s own doc
comment (ADR-0013) is explicit that this app derives what a screen shows from Room rather than a
saved back stack, specifically so a killed-and-reopened app lands back where it actually was.
`LoggingScreen` already has a precedent for "one state fully replaces another, full-screen,
inside the same route" — `SessionUiState.finish` swaps the entire screen for
`FinishSummaryScreen` without a nav-graph entry of its own. The warm-up's running state follows
the same idiom: a full-screen composable selected by `warmUp.elapsed != null`, not a pushed
route. This is also what makes "no state where both are visible" true by construction — they are
two branches of one `if`, never composed together — without needing back-stack management to
enforce it.

**"STEP 1 OF 2 · WARM-UP" is not shown as written.** Numbering it "1 of 2" promises a cool-down
step this app does not have — `claude-code-prompt.md`'s own file lists cool-down under
"designed, not built, and needing a user story first." Displaying a step count for a step that
doesn't exist is exactly the kind of UI claim `specs/constitution.md` §2.4 exists to forbid,
applied here on the same reasoning ADR-0021 already used for "nothing is logged, so §2.4 has
nothing to be dishonest about" — a false count would give it something to be dishonest about.
The kicker reads `WARM-UP` alone.

**"Counting up. No target." replaces "Warm-up · not recorded" as the visible meta line, per the
file's own copy — the disclosure moves to `contentDescription`, not off the screen entirely.**
Turn 3 had put ADR-0021's "not recorded" wording on screen, ahead of only living in
accessibility text as ADR-0021 originally specified. Turn 5's frame doesn't carry that phrase
visibly at all. Since nothing about the recording decision changed, the honesty requirement
itself (§2.4) is still met by an accessible disclosure — this reverts to ADR-0021's original bar
rather than inventing new copy to keep a visible disclaimer the current design doesn't draw.

**`SKIP` and `DONE — START LIFTING` call the same underlying action.** ADR-0021: stopping the
timer discards it regardless of why — there is no data-model distinction between "finished
warming up" and "changed my mind," so both buttons call `warmUp.onStop()` and leave the step;
they differ only in framing and position (`SKIP` in the header, available immediately; `DONE —
START LIFTING` as the deliberate primary action). This keeps US-28's "it never blocks logging a
set" true — `SKIP` is reachable the instant the step renders, not gated behind any state.

## Options considered

1. **A `NavHost` destination (`composable<WarmUpRoute>`), pushed and popped.** Rejected: fights
   ADR-0013's explicit state-from-Room philosophy, and needs its own back-stack-survives-kill
   handling that the state-swap approach gets for free from the same mechanism
   `SessionUiState.finish` already uses.
2. **Show "STEP 1 OF 2" anyway, since the frame draws it.** Rejected: promises a feature (a
   cool-down step) this build does not have, for a screen with an accessibility-honesty pattern
   already recorded in this exact codebase (ADR-0021).
3. **Keep the visible "not recorded" wording alongside the new copy.** Rejected: the frame's own
   copy is "Counting up. No target."; grafting old wording onto new copy neither file asked for
   isn't matching the design, it's inventing a third version. The accessibility-only disclosure
   ADR-0021 originally specified is the honest floor, and this change doesn't lower it.

## Decision

The warm-up's running state becomes a dedicated full-screen composable (`WarmUpStep`), selected
by `warmUp.elapsed != null` inside `ActiveSession` in place of the rest of the session content —
the same full-screen-swap idiom `SessionUiState.finish` already establishes, not a new `NavHost`
route. The idle "Start warm-up" trigger is unchanged in place and behavior; tapping it starts the
timer and the screen swap follows from the same state change. `RepMascot` gains a new size,
`GymDimens.MascotWarmUp` (150dp). The kicker reads `WARM-UP`, not a fabricated step count. The
visible meta line reads "Counting up. No target."; "not recorded" remains in the timer's
`contentDescription`. `SKIP` (header, right) and `DONE — START LIFTING` (primary, 64dp) both call
`warmUp.onStop()`.

## Consequences

**Easier:** the running warm-up can no longer clip session content, by construction — it isn't
composed alongside it. A future cool-down step, if ever built, has a real "step 2 of 2" to count
against instead of a placeholder needing removal first.

**Harder:** `WarmUpPanelScreenTest`'s assertions change — `"Done"` becomes `"DONE — START
LIFTING"`, and the test needs to confirm the swap replaces session content rather than merely
appearing within it. This is a deliberate copy and structure change, not a caught regression.

**Committed to:** any future feature reachable only from the running session screen (switching
exercises, US-45; the segment bar) is unreachable while the warm-up step is on screen — true
already under the inline panel too (the countdown was drawn above that content), but now
architecturally explicit rather than incidental.

**Revisit if:** a cool-down step is designed and built — at which point the dropped step-count
kicker should come back, accurately, and this ADR's reasoning for omitting it no longer applies.
