# ADR-0019: A finished exercise is a timestamp the next set erases

- **Status:** accepted
- **Date:** 2026-08-04
- **Deciders:** maintainer (requested), agent (scoped)

## Context

Testing PR #14 on the gym floor: mid-workout, the active session's list mixes exercises
still being performed with ones already finished, and every card carries the same filled
orange "Add set" (ADR-0016). The maintainer asked for finished exercises to be marked,
moved to the bottom, and de-emphasised.

Nothing in the model knows an exercise is finished. `session_exercises` is
`id, session_id, exercise_id, position` (ADR-0004); the only completion moment anywhere is
the guided flow reaching its last planned set (US-05a). Constitution §2.4 forbids the app
inventing the state: "honest data" means it cannot *guess* that you are done with squats
when you were coming back for a fifth set.

The screen this lands on derives everything from the database — that is what makes
"reopen and you are back in your session" survive a process kill (US-01) — so whatever
"finished" is, it must live there too.

## Options considered

1. **An explicit member action, persisted as a nullable `finished_at` on
   `session_exercises`, cleared by any set logged after it.** A timestamp rather than a
   boolean: same cost, and it also says *when*, which is what lets the finished group sort
   meaningfully. The clearing rule makes the mark impossible to display dishonestly — a
   card can never say "done" about an exercise whose newest set came after the mark.
   Chosen.
2. **Derive "finished" from the guided flow only.** No schema change, but the ad-hoc
   two-tap path — the majority path, the sacred one — could never finish anything.
   Rejected.
3. **A heuristic** (has sets, and something newer exists above it). No tap, but the app
   would be asserting intent it does not know. Rejected on constitution §2.4.
4. **Sticky mark** (persisted, but a later set does not clear it). One rule fewer, but it
   lets the screen contradict the data it sits on. Rejected.
5. **In-memory only.** No migration, but the mark dies with the process on the one screen
   built around surviving that. Rejected.

## Decision

`session_exercises` gains a nullable `finished_at`. It is written only by an explicit
member act — the card's toggle, or completing a guided walkthrough's last set — and is
cleared by logging any set against the exercise afterwards. Display partitions the active
list in-progress-above-finished, each group newest-first by its own clock.

## Consequences

- One more Room migration (additive, nullable — existing rows read as in progress), and
  the column syncs like the rest of the row at M2.
- The invariant "a displayed 'done' is never older than the newest set" holds by
  construction, not by discipline: the clearing lives next to the write in the domain
  layer, so every path that logs a set — manual, multi-set, guided — gets it for free.
- Marking done is cheap to get wrong: the next set silently takes it back, and the toggle
  reverses a mis-tap. Nothing downstream (history, US-06b, prefill) reads `finished_at`,
  so the mark can never change what a past workout shows or what a set prefills.
- The guided flow's summary now stamps the mark, which couples US-05a to US-02d exactly
  as far as one `finish` call — leaving early stamps nothing.
- **Revisit if** M2's sync design wants per-column conflict rules (a cleared mark racing a
  set logged on another device), or if the household finds the auto-clear surprising — at
  which point the sticky variant returns as a settings discussion, not a redesign.
