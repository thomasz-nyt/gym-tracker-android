# ADR-0046: The main session screen's own guided/freestyle contract — not a merge with `GuidedExerciseScreen.kt`

- **Status:** accepted
- **Date:** 2026-08-30
- **Deciders:** maintainer, agent
- **Relates to:** ADR-0033 (the guided screen is one exercise, sized for the bench — unchanged
  by this ADR), ADR-0029 (the session screen is a ruled sheet), ADR-0037 (choosing which exercise
  is open), US-45, `SessionProgress`, `MovementTarget`

## Context

`Redesign.dc.html` Turn 5, file `03-session-screen.md` — "the largest file in the pass" — draws
a contract table with two columns, "Guided (`plan != null`)" and "Freestyle (`plan == null`)",
differing in header tag, set kicker, log behaviour, section label, and how a plan overrun is
shown. Two things needed resolving before any of it could be built.

**Does "guided" here mean `GuidedExerciseScreen.kt`?** That screen already renders `"SET
${running.setsDone + 1} OF ${running.targetSets}"` — close enough to file `03`'s "`SET 2 OF 3`"
half that reading the file as "fold that screen back into the main one" is a real, live
possibility, not a stretch. But `GuidedExerciseScreen.kt` is a **separate, full-screen route**
(`LoggingScreen`: `if (running != null) GuidedRoute(...) else SessionScreen(...)`), and ADR-0033
put it there two weeks ago (2026-08-14), directly from the maintainer's own live report about
that exact screen's pre-redesign styling — and said so explicitly: "without reopening ADR-0029's
`1a` decision for the main screen, which is untouched by this ADR." Reversing that on the strength
of an older design document's frame vocabulary, without the maintainer's fresh sign-off, would be
exactly the kind of "trust the frame over the codebase's own recent, reasoned history" mistake
this repo's process is built to catch. **Confirmed with the maintainer: file `03`'s "guided" is
the main multi-exercise session screen's own concept — whether *this session* is backed by a
plan — independent of `GuidedExerciseScreen.kt`, which this ADR does not touch.** That screen,
ADR-0033, and its own `"SET N OF M"` stay exactly as they are.

**What "plan" already means here, read off the domain model rather than invented:**
`SessionProgress.orderIsAPlan` (`session.routine != null`) already exists and already gates
whether the segment bar shows. `SessionExercise.target: MovementTarget?` (`sets`, `reps`,
`weightKg`) already exists and already backs the "Target 3 × 8 · 105 lb" line under the open
exercise. File `03`'s `SetPlan(setsPlanned, targetReps, load)` is not a new concept — it is
`MovementTarget` under a different name, already wired to every session, not something this pass
introduces. `plan != null` for a given exercise, in this codebase's own terms, is
`row.sessionExercise.target?.sets != null`.

**`Remove` and file `05`.** File `03`'s own text says `Remove` "moves to long-press" — that
gesture is file `05`'s (set corrections, deferred past M2 — see US-52/roadmap). Confirmed with
the maintainer: `Remove` stays exactly where it is, a reachable control under the open
exercise's meta line, restyled only if a restyle is needed for this file's own rules. It is not
deleted, and does not wait on `05`.

**Scale.** The plausibly-touched files — `GuidedExerciseScreen.kt`, `SessionScaffold.kt`,
`SessionMovements.kt`, `RestPanel.kt`, `GuidedController.kt`, `ActiveSessionViewModel.kt` — total
over 3,200 lines. Per `CLAUDE.md`'s "propose a split first" rule, file `03` ships as a sequence
of commits on one branch rather than one pass, each scoped and checked in on before the next:

1. **This commit** — the kicker, section label, and header tag become plan-aware, reading
   `SessionProgress.orderIsAPlan` and each exercise's existing `target`. No change to what
   logging a set does.
2. Plan-overrun labelling (`SET 5 · EXTRA`) once a target's set count is exceeded.
3. Deleting `Add set`/`Add exercise`'s button chrome for the file's `label.caps` secondary-row
   treatment.
4. The rest band's redraw (ink, not accent, 56dp, under the header).

Each is its own commit against this ADR; later ones may earn their own ADR section or a follow-up
ADR if the reasoning is substantial enough on its own.

## Options considered

1. **Read "guided" as `GuidedExerciseScreen.kt`, reverse ADR-0033.** Rejected: reverses a
   two-week-old, maintainer-driven decision on the strength of a design document that predates
   the current codebase's own evolution past it — exactly the kind of frame-vs-build mismatch
   this Turn's `01` and `02` files both already turned out to have, applied here without the
   maintainer's fresh confirmation this time would be a guess, not a decision.
2. **Delete `Remove` now, restore it when file `05` ships.** Rejected: a real, if temporary,
   functionality loss the maintainer did not ask for, to satisfy a file that names its own
   dependency on work already deliberately deferred elsewhere in this Turn.
3. **One combined commit for all of file `03`.** Rejected outright by `CLAUDE.md`'s size rule
   before it was seriously considered; 3,200+ lines of touched surface is not a "propose a
   split" borderline case.

## Decision

File `03` targets the main session screen only. `plan` for an exercise is
`sessionExercise.target?.sets != null`; `GuidedExerciseScreen.kt` and ADR-0033 are untouched.
`Remove` stays reachable, unchanged in function. The file ships as an ordered sequence of
commits on one branch, each scoped narrowly enough to stay well under the 400-line guidance on
its own.

## Consequences

**Easier:** each sub-commit is independently reviewable and revertible; a mistake in the rest
band's redraw (piece 4) doesn't put the kicker relabelling (piece 1) at risk.

**Harder:** "one composable, two contracts," read literally against the design document, promised
a single artifact this ADR does not deliver — the main session screen and `GuidedExerciseScreen.kt`
remain two composables, not one, and any future work that *does* want to unify them needs its own
ADR arguing that on its own merits, not inherited from this one.

**Committed to:** future Turn 5 sub-pieces of file `03` cite this ADR for the plan-existence
definition (`target?.sets != null`) rather than re-deriving it, so the concept doesn't drift
between commits.

**Revisit if:** a future redesign turn explicitly proposes merging `GuidedExerciseScreen.kt` into
the main screen, with the maintainer's own sign-off on reopening ADR-0033 — at which point this
ADR's scope boundary is what gets superseded, not silently worked around.
