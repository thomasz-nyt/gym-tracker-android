# Roadmap

Milestones are sequential. **Do not start a milestone before the previous one's
exit criteria are met.** Each milestone ends in something installable that a family
member could actually use.

Current milestone: **M4**. M0, M1, M3, M3a and M3b are complete; M2 is deliberately postponed
so the offline core can be finished before accounts and sync arrive, and M3a and M3b were both
taken ahead of M4 for the same reason.

M3b broke the "milestones are sequential" rule at the top of this file, and did so knowingly:
it was routines work that touched no table M4 reads, so running it beside M4 risked nothing
that sequencing would protect. Said out loud rather than left for someone to notice.

What is left from the `Redesign.dc.html` audit is **not** all in M4, and is listed at the end
of this file so it does not get lost between milestones.

---

## M0 — Skeleton

Goal: an empty app that builds, lints, and tests in CI.

- [x] Gradle multi-module skeleton per `tech-stack.md`
- [x] Version catalog, convention plugins
- [x] Hilt wired, one blank Compose screen
- [x] ktlint + detekt configured
- [x] JUnit 5 + Turbine + MockK wired; one trivial passing test in `:core:domain`
- [x] CI check that `:core:domain` has no Android dependency
- [x] GitHub Actions: build, lint, unit test, gitleaks secret scan
- [x] `specs/adr/0000-template.md` and ADR-0001 recording the native-Android decision

**Exit:** green CI on a PR. No feature code.

---

## M1 — The core loop (local only)

Goal: log a workout end-to-end with no account and no network. This is the
milestone that decides whether the app is good.

Stories: US-01 … US-06b

- [x] Room schema: `sessions`, `exercises`, `session_exercises`, `sets`
- [x] Seed the exercise catalog from bundled JSON (free-exercise-db, public domain)
- [x] Start a session (US-01). Ending it is US-06.
- [x] Add an exercise to a session
- [x] Log a set: weight, reps (RPE in the domain; not yet in the UI)
- [x] Prefill from the last time this exercise was performed
- [x] Edit and delete a set (US-04). One row per set, so any one is reachable (ADR-0022)
- [x] Rest timer between sets
- [x] End a session, and the session history list (US-06)
- [x] Delete a past workout, with undo (US-06a, ADR-0012)
- [x] Unit preference (kg / lb), stored per user, converted at the edge only. Both units are shown (ADR-0008)
- [x] Add several exercises in one visit to the catalog (US-02a)
- [x] Newest exercise first in the active session (US-02b)
- [x] Remove an exercise from the session, with undo (US-02c)
- [x] Guided flow through one exercise (US-05a, ADR-0017)
- [x] Workout detail from history (US-06b)

The last five were added 2026-08-02 from a real session on the gym floor, the same
way US-06a and ADR-0011 arrived. They are ergonomics on the core loop, not new
scope: none of them adds a table or a migration. A sixth idea from that session —
sensor-based rep counting — is **not** here on purpose; it is deferred in
`adr/0018-sensor-assisted-rep-counting.md` because constitution §2.4 forbids
logging an inferred value.

US-02a was written against the in-session search and had to be rebuilt when M3
made browsing a destination of its own (ADR-0013). The complaint it answers
outlived its first implementation: picking three exercises should not be three
round trips, whether the picker is an overlay or a screen.

**Exit:** two-tap set logging measured and asserted in an instrumented test. You
personally log three real workouts on your own device without wanting to fix
anything mid-set.

---

## M2 — Accounts, household, sync

Stories: US-07 … US-11, plus US-15 which moved here from M3.

**Postponed until after M3** (2026-08-01). Nothing in M1 or M3 needs it, and the household
does not need accounts to start using the app.

- [ ] Supabase project, migrations in `supabase/migrations/`
- [ ] Auth: sign up, sign in, sign out
- [ ] `households` + `profiles`; invite a member by code
- [ ] RLS policies on every table + pgTAP tests proving cross-household reads fail
- [ ] Sync engine: local-first, WorkManager, last-write-wins per row with
      `updated_at`, conflict cases documented
- [ ] Offline queue survives app kill
- [ ] Data export (JSON) and account deletion
- [ ] Stock exercise media mirrored into Supabase Storage, never hotlinked (ADR-0014)
- [ ] Family-recorded clips for a household (US-15, moved from M3)

**Exit:** two devices, two family members, same household, log offline, reconnect,
converge. A pgTAP suite proves isolation.

---

## M3 — Exercise catalog

Stories: US-12 … US-14. US-15 moved to M2.

**Taken before M2** (maintainer decision, 2026-08-01), to keep the offline core moving
without an account. Everything in this milestone works with no network and no backend,
which is what makes the reordering possible at all.

Renamed from "Exercise catalog and media". The media half assumed GIFs the seed data
does not contain and a Storage bucket M2 has not built — see ADR-0014.

- [x] Catalog browse and filter by body part and equipment, combined
- [x] Search matches name and hand-authored aliases (ADR-0015)
- [x] Exercise detail: body-part tags, numbered instructions, bundled photo where one exists
- [x] YouTube **search** link-out, labelled as a search (external browser, no SDK, no account)
- [x] `Equipment.UNSPECIFIED`, so the filter stops calling unrecorded equipment "other"
- [x] Navigation Compose replaces state-derived routing (ADR-0013). History and the workout
      detail reached from it (US-06b) are destinations now too, each with its own
      `HistoryViewModel` (ADR-0024) — the last two of the six screens this checkbox covers.
      Bottom navigation (Train, Exercises, History) ties the three top-level places together,
      hidden while a workout is running. What is left out of the graph is left on purpose: the
      guided flow (US-05a) stays outside it for the reason ADR-0017 gives, and set entry, the
      set editor and the stale-session prompt stay dialogs per ADR-0013 itself.

**Exit:** in airplane mode, standing at an unfamiliar machine, you can narrow the catalog
by body part and equipment and confirm the machine from its photo or its numbered steps.
Every detail screen renders honestly — the five exercises the catalog has no instructions
for say so, rather than showing an empty panel.

*(The previous exit criterion — "every exercise has either a GIF, a clip, or text" — was
already satisfied by M1's seed data, since 868 of 873 ship instructions. It tested the
catalog, not this milestone.)*

Deferred to M2, where the backend they need exists:

- Stock media mirrored into Supabase Storage, and any GIF or video playback (ADR-0014)
- US-15, family-recorded clips for a household

---

## M3a — Routines, and the warm-up that is not one

Stories: US-28, US-29.

Added 2026-08-08 out of the `Redesign.dc.html` audit, whose finding 01 — "a session has
no plan" — is the one the rest of that redesign leans on. Taken before M4 for the reason
M3 was taken before M2: none of it needs an account, a backend, or a network.

- [x] Warm-up timer that records nothing (US-28, ADR-0021)
- [x] `routines` + `routine_items`, additive migration (v7), no change to `sessions`,
      `session_exercises` or `sets` (US-29, ADR-0020)
- [x] Create, rename, delete a routine; add, remove and reorder its movements
- [x] Start a routine — copies its items into `session_exercises`, after which it is an
      ordinary session and every M1 story keeps working on it unchanged
- [x] Each movement renders its "last time" values, labelled as history; movements with no
      history show no numbers (the US-13 absence pattern). Read through `LastPerformanceOf`
      rather than `PrefillFromLastSet` — same row, but a prefill is a number about to be
      typed and this is one about to be read, so it keeps kilograms and carries the date

The warm-up timer is in this milestone because it came out of the same audit, not because
it is part of a routine — ADR-0021 is explicit that it is neither a routine step nor a
session step, and the routine editor does not offer to add one.

**Exit: met 2026-08-08.** Tuesday is Upper A. Starting it puts its movements on the screen
in order, each showing what was actually lifted last time.

One caveat on the second half of that criterion, recorded rather than glossed: **`TwoTapSetLoggingTest`
was edited** — but not by this milestone, and not because the plan cost the core loop. It had
been failing on CI on every PR since #19, where the bottom navigation bar pushed "Add set"
below the fold on CI's 320x640 emulator; the test tapped a node that was in the tree but
clipped off screen. The edit adds `performScrollTo()`. The property the criterion is really
about is intact: neither routines nor the warm-up added an interaction to the two-tap path,
and both tests still perform exactly two `performClick` calls. See
`specs/testing-strategy.md` § "Two traps the instrumented suite has already fallen into".

---

## M3b — Targets in a routine

Story: US-30. See `adr/0027-routines-store-targets.md`.

Added 2026-08-09, and it is the same kind of arrival as M3a: not new scope invented by the
agent, but a limitation the maintainer hit while using the thing that shipped. ADR-0020 named
this exact loss two days earlier and set the condition for revisiting it; the condition was met.

**Runs alongside M4 rather than blocking it.** M4 is charts and reads only `sets`; this is
routines and touches `routine_items` and `session_exercises`. They do not overlap, and the one
place they could — a target leaking into a chart or a PR — is forbidden by the story and by
ADR-0027 rather than managed by sequencing.

- [x] ADR-0027 and US-30 written, and `data-model.md` updated, **before** any code
- [x] Migration v8: three nullable `target_*` columns on `routine_items` **and** on
      `session_exercises`. Additive; `sessions` and `sets` untouched. Closed 2026-08-09
- [x] `RoutineItem` and `SessionExercise` carry a target; a use case sets and clears one
      (`SetRoutineItemTarget`). Closed 2026-08-09
- [x] `StartSessionFromRoutine` copies targets across with the movements, so the session holds
      its own snapshot and there is still nothing to join back to the routine. Closed 2026-08-09
- [x] The routine editor can enter, edit and clear a target. The structural test in
      `RoutineEditorViewModelTest` that asserted no target field exists is replaced by
      `a movement with no target carries none, same absence pattern as lastTime` plus six more
      covering entry, partial fields, independence between movements, re-editing, clearing and
      rejecting an out-of-range value — a test of the new invariant, not a deletion.
      `TargetEditorController` (new, `feature/routines`) owns the form; `TargetEditorDialog`
      is the UI. Closed 2026-08-12
- [x] A target prefills set entry; with none, US-03's prefill from history is unchanged.
      `SetEntryController.open` merges `SessionExercise.target` with `PrefillFromLastSet`
      per field (a target's sets/reps/load are each independently optional, US-30), and
      `ActiveSessionViewModel`'s one-tap `nextLoggableSet` (ADR-0029, US-35) picks up the same
      merge — the two prefill surfaces were flagged in that class's own doc as needing to land
      together rather than drift apart, and this is that. Closed 2026-08-12
- [x] Targets render labelled as targets, beside history rather than merged into it. The
      session screen's `SessionMovements.kt`/`RestPanel.kt` already did this from ADR-0029
      (`Target 3 × 8 · 105 lb`, never reconciled with `Last Tue`); the routine editor gained
      the same line (`MovementListItem`), muted rather than accent so it never
      out-competes what was actually lifted. Closed 2026-08-12

**Exit:** a routine created on the sofa arrives at the gym carrying its numbers, and no chart,
no volume figure and no personal record has moved as a result. `TwoTapSetLoggingTest` passes
**unedited** — verified in the full instrumented suite alongside `OneTapSetLoggingTest` and
`CorrectingASetTest`, run twice from a cleared app. M3b closed 2026-08-12.

A load typed into the target editor is converted through the member's unit preference, the same
`UnitConverter` round trip `Add set`'s own weight field uses — caught on a device, not in a
test: the field had no unit label and stored whatever was typed as raw kilograms regardless of
the member reading pounds. `Load (lb)`/`Load (kg)` now labels it, and
`RoutineEditorViewModelTest` pins the round trip (135 lb typed → 61.23 kg stored → 135 lb shown
again on reopen).

### US-32 — A session remembers the routine it was started from

Story: US-32. See `adr/0028-a-session-remembers-its-routine.md`. Added 2026-08-09, the same
day as M3b, once History's "Sun 9 Aug, 13:53" turned out to be a real gap and not just a
missing string: nothing recorded *which* routine a session came from, so nothing could ever
say so.

- [x] ADR-0028 and US-32 written, and `data-model.md` updated, **before** any code
- [x] Migration v8 → v9: `sessions.routine_name` and `sessions.routine_id`, both nullable.
      Additive; `sets`, `session_exercises` and `routine_items` untouched — this migration
      is scoped to exactly what ADR-0028 claims and nothing US-30 already added.
      Closed 2026-08-10
- [x] `WorkoutSession` carries a `RoutineOrigin?`; `StartSessionFromRoutine` writes it once,
      at start, and nothing reads it back through a repository. Closed 2026-08-10
- [x] History and the finish summary lead with the routine's name, falling back to
      `Freestyle`. Closed 2026-08-10, verified live on device: created a routine, started it,
      logged a set, finished, and confirmed both the finish summary and the History row show
      the routine's name — not the bare date the audit's finding 01 complained about. Also
      fixed `SessionSummary.exerciseCount`, which counted every appearance a routine copied
      in whether or not it was ever touched — a routine-started session could read "3
      exercises" for one actually performed. It now counts only appearances with at least one
      set, and `HistoryScreen`'s and `FinishSummaryScreen`'s bodyweight-count segments, which
      had drifted apart, were brought back in sync
- [x] The four enforcement mechanisms ADR-0028 names are tests, not comments: the id's type,
      the structural test replacing `StartSessionFromRoutineTest`'s current tripwire, a
      schema test that `sessions` has no foreign key to `routines` (its intended DAO-level
      form — reflecting over `@Query` annotations — turned out to be unworkable:
      `androidx.room.Query`'s retention is `CLASS`, not `RUNTIME`, confirmed against the
      compiled `room-common` jar, so the check reads the CREATE TABLE SQL Room actually built
      instead), and confirmation that nothing in US-30's target pipeline changed. Closed
      2026-08-10

**Exit:** a session started from a routine shows that routine's name in History and the
finish summary, a session started without one shows `Freestyle`, and renaming or deleting a
routine afterward changes neither. `TwoTapSetLoggingTest` passes **unedited**.

---

## M4 — Progress and charts

Stories: US-16 … US-19, plus US-31, added 2026-08-09 out of the `Redesign.dc.html` audit
because it is the first place US-18's records are shown anywhere in the app.

- [x] Estimated 1RM (Epley), volume, and top-set trend per exercise
- [x] Weekly volume by body part (US-17). One block per week, one labelled bar per muscle,
      all bars on one scale. **Not a charting-library chart:** ADR-0019 leaves one accent on an
      achromatic ground, and the obvious rendering — a stacked column per week, one hue per
      muscle — needs twelve hues the palette does not have. Reached from History; the window is
      a fixed eight weeks until the selector below is built
- [ ] PR detection and history — **unblocked 2026-08-09**, in progress. ADR-0025 settled what a
      record is (heaviest load at a given rep count), and `PersonalRecordsOf` /
      `DetectPersonalRecord` landed in #29 with 17 tests. US-31 is the first UI surface for it —
      a session's records shown on its finish summary. Still missing: the inline announcement on
      save, and a standing per-exercise record list
- [x] Empty and sparse-data states designed, not accidental (US-19)
- [ ] Time range selector. Split from the line above, which it had been sharing: the states are
      done and the selector is not, so one checkbox could not tell the truth about both
- [x] Finish as a summary, not a confirm dialog (US-31). The confirm dialog itself is
      unchanged — only what happens after confirming, which is where the summary and any
      records set that session are shown. Shipped in `87e975c` (PR #35); this box was left
      unchecked in that commit, against `CLAUDE.md`'s own definition of done. Ticked here
      2026-08-09 rather than silently, since the gap sat on `main` for a while

**US-18 was answered on 2026-08-09** after three sessions deferred it — see ADR-0025. A record
is the heaviest load ever lifted **at a given rep count**, so every record is a set that
actually happened and a 100x8 sets one without having to beat a 105x1. The estimated-1RM
reading was rejected: it would announce a record for a weight nobody has lifted.

### US-33 — Progress replaces History

Added 2026-08-10. See `user-stories.md`'s US-33 for the full story, including the deliberate
call to rename ahead of the range selector below.

- [x] Tab and screen title renamed History → Progress; "Past workouts" demoted to a section
      heading above the unchanged session list. Closed 2026-08-10
- [x] Top section: est. 1RM + 8-week delta for the exercise most recently actually trained
      (`MostRecentlyTrainedExercise`, new — an appearance a routine copied in but never
      performed is skipped in favour of one that was). No lift switcher; tapping opens the
      existing per-exercise trend screen (US-16) unchanged. Closed 2026-08-10
- [x] "Weekly volume by muscle" restyled as a labelled row in the top section, replacing the
      bare `TextButton`; same destination. Closed 2026-08-10
- [ ] **Deferred, not built:** a "PR" badge on session rows. `PersonalRecordsAchievedIn` reads
      the member's entire session history per row it is asked about — fine for
      `FinishSummaryScreen`'s one row, not for every visible row of a 200-session list. Needs
      a purpose-built O(sets) read, not a call to the existing use case per row

### US-34 — Exercise log

Added 2026-08-11. See `user-stories.md`'s US-34 for the full story.

- [x] Session-by-session log below US-16's chart: one row per finished session with at
      least one set for the exercise, newest first, showing the session's best set, est.
      1RM, and its individual sets (`ExerciseLogOf`, new — mirrors `ExerciseTrendOf`'s
      three-read pattern so the two never disagree about which sessions counted).
      Closed 2026-08-11
- [ ] **Deferred, not built:** rows are not tappable — neither opening the source workout
      nor a "tap any set to see its exercise's log" entry point elsewhere in the app.
      Reached exactly as US-16 already is: catalog, or Progress's top section

**Exit:** charts render correctly with 1 session, 3 sessions, and 200 sessions.
Progression math is unit-tested against a hand-computed fixture table.

### `SessionProgress` — groundwork, not yet wired anywhere

Added 2026-08-11. `core/domain/session/SessionProgress.kt`: a pure function of a session and
its movements answering how many are done, which is current, and which are still to come.

This is the revisit `adr/0023-the-rest-period-earns-its-space.md` named in advance — "of N"
and "then X" were refused there because a freestyle session's `position` records the order
exercises were *added*, not a plan, and ADR-0023 said to revisit "when ADR-0020 lands." US-32
landed a session's routine provenance, so a session copied from a routine now has an order
that *is* a plan; `orderIsAPlan` on this type is exactly that distinction, structurally, so a
future screen cannot show "Exercise 3 of 6" for a freestyle session by forgetting to check.

- [x] `SessionProgress` and `sessionProgressOf`, JUnit 5 table tests, no UI. Closed 2026-08-11
- [x] Wired into the session screen: `adr/0029-the-session-screen-is-a-ruled-sheet.md`
      settles the copy (`orderIsAPlan == false` reads "Also in this workout", no ordinal
      claim) and rebuilds `feature/logging/.../session/` as a ruled sheet with the segment
      bar, "n of m done", and US-35's one-tap log button added beside `Add set`.
      `TwoTapSetLoggingTest` passes unedited. Closed 2026-08-12

---

## M5 — Health Connect (optional)

Stories: US-20 … US-23. Read `specs/health-connect.md` first.

- [ ] `:feature:health` behind the `HealthMetricsSource` interface
- [ ] Availability check: SDK available / update required / **not available**
- [ ] Granular permission request; app fully functional if denied or unavailable
- [ ] Read heart rate, active calories, and exercise sessions for the session window
- [ ] Aggregate on-device; persist only avg HR, max HR, active kcal on the session
- [ ] Per-member toggle, default **off**
- [ ] Full UI suite passes with the no-op binding

**Exit:** installing on a device with no Health Connect at all produces zero
crashes, zero empty holes, and no prompts.

---

## M6 — AI coaching

Stories: US-24 … US-27

- [ ] Supabase Edge Function calling the Anthropic API; key server-side only
- [ ] Context builder: last 8 weeks of that member's sets + the catalog
- [ ] Structured JSON response rendered as real UI, not a chat blob
- [ ] Guardrails per constitution §6, with tests: no body-composition language, no
      medical claims, ≤10% weekly load increase, no train-through-pain advice
- [ ] Output labelled as AI-generated
- [ ] Per-user rate limit; graceful degradation when the function is down
- [ ] Prompt + response persisted for traceability

**Exit:** the guardrail test suite passes, including adversarial prompts designed
to elicit weight-loss and body-image advice.

---

## M7 — Polish and household rollout

- [ ] Onboarding for a non-technical family member
- [ ] Accessibility pass: TalkBack, 48dp targets, contrast, large-font layouts.
      The app-wide type scale was raised early, at M1, because the maintainer was
      logging real workouts and could not read the set list on a gym floor
      (ADR-0011). This pass still owns the audit, including the 200% font-scale
      layouts — it now just starts from a legible default.
- [ ] Crash reporting (self-hosted or none — no third-party analytics, per §3)
- [ ] Internal distribution track

---

## M8 — iOS

Not started until Android has been in real household use for a month. Port
`:core:domain` logic to Swift (or extract it to KMP — decide via ADR at that point),
new SwiftUI layer, HealthKit, and the watchOS companion with `HKWorkoutSession`.

---

## What is left from the `Redesign.dc.html` audit

Tracked here rather than in a milestone, because these do not all belong to one. Written
2026-08-08, when M3a closed and the audit stopped being the thing currently being built.

**Shipped:** the visual system (ADR-0019), the rest panel and one-tap log (ADR-0023), bottom
navigation (ADR-0024), the warm-up timer (US-28), routines (US-29).

ADR-0019 shipped in `6b2671d` with a handful of compliance gaps that survived review: three
`.clip(RoundedCornerShape(...))` calls rounding catalog and workout-detail photos against the
"every radius is 0" rule; `StepperField`'s step buttons and `DrillDownTopBar`'s back button
reading `CornerFull` unfixed (the exact trap `Shape.kt` documents); dividers at Material's 1dp
hairline rather than a thickness that survives gym lighting; button labels centred rather than
flush left; `outlineVariant`, `background`, `secondary` and the extreme `surfaceContainer*` roles
un-gated by `GymColorSchemeTest`; and "numbers carry weight 800" (ADR-0019's own text) never
applied anywhere. Closed 2026-08-09, plus a `NumeralText` component (bolds digit runs via
`AnnotatedString` spans, decoupled from button-label uppercasing which cannot be done safely —
see `GymButtons.kt`'s `ButtonLabel` doc comment — without risking `TwoTapSetLoggingTest`'s
case-sensitive `onNodeWithText` matches). The nav-bar selected-item pill was **not** closed at
the time: confirmed via the compiled `material3-api.jar` that `NavigationBarItem` exposed no
`shape` parameter and its indicator token (`ShapeKeyTokens.CornerFull`) resolved to a hardcoded
`CircleShape`, never one of `Shapes`'s five roles, and fixing it meant reimplementing
`NavigationBarItem` from primitives — a custom widget, out of bounds per the redesign brief's
own constraints as they read then. **Closed 2026-08-13 by ADR-0030**, once the brief's own text
was read again and found to authorise exactly that exception in as many words ("Material 3 +
Compose components only, with one deliberate exception: the bottom bar"). See that entry below.

**Destructive actions off the row (ADR-0019), closed 2026-08-10.** `Delete routine` sat on the
Routines list row beside `Start`, styled as a plain `TextButton` despite a comment claiming it
was already outlined — and wrapped the row onto a second line to fit (redesign audit finding
04). It now lives in the routine editor as an `OutlinedButton`, below `Add exercise` and never
sharing that surface; `Start` is the row's one filled, constructive action and `Edit` stays
quiet. `RoutinesViewModel.onDeleteRoutine` moved to `RoutineEditorViewModel` with it — see
`RoutineEditorViewModelTest`'s two new cases, which replace the coverage
`RoutinesViewModelTest` used to carry. `HistoryScreen`'s `Delete` was **not** relocated the same
way: moving it to `WorkoutDetailScreen` would lose US-06a's five-second undo window, which lives
in the per-destination `HistoryViewModel` instance and does not survive a navigation pop. It was
restyled from a filled-looking `TextButton` to an outlined one instead, and stays on the row —
a deliberate, documented deviation from the redesign brief's "moves off the row entirely"
framing for this one case. New instrumented coverage:
`RoutineDeletionTest` (the list never renders "Delete routine"; deleting from the editor returns
to a list without the routine). Verified live on device at every step.

**Two ADR-0019 compliance gaps closed 2026-08-11, once the design bundle itself became
available and confirmed both values exactly.** `outlineVariant` (#C6C4C3, ~1.3:1 against the
ground) was gated only by inequality against Material's lavender, not by legibility —
`GymColorSchemeTest` now asserts a minimum contrast ratio too, and the value moves to ink at
40% opacity (#9F9D9D light / #6A6968 dark), the exact figure the design specifies. And
`GymDimens.PrimaryAction` moves from ADR-0016's original 64dp to the 72dp the design's own
constraints named; `GymDimensTest` now pins the value instead of only floor-checking it against
`MinTouchTarget`, so the two can't quietly drift apart again. Also added: an ExtraBold/Medium
weight hierarchy on `titleLarge`/`titleSmall` and the body roles — `titleMedium` deliberately
excluded, since it is the role `LoggedSets` and `RestPanel`'s mixed word/number lines render,
and `NumeralText`'s digit-span contrast depends on the line's own base weight not already
matching it. Verified live on device.

**The nav-bar pill closed, and Routines got its own entry point, 2026-08-13 (US-36, ADR-0030,
PR B of the follow-up audit).** Three top-level destinations now, not four — Train, Exercises,
Progress — with a hand-built `GymNavigationBar` (`:core:designsystem`) replacing
`NavigationBar` entirely rather than restyling it, per the brief's own explicit exception.
Routines moved from a tab to a drill-down, reached by one outlined button in Train's header,
present on every Train state; it gained a `DrillDownTopBar` the same way `RoutineEditor` already
had one, since it lost the bottom bar it used to exit through. Train home, with no workout
running, now names the routine due next — the one least recently performed, or never performed
at all — and offers `Start <name>` beside the unchanged `Freestyle` action; a new domain class,
`NextRoutineToTrain`, is the first reader of `RoutineOrigin.id` (ADR-0028), used only to match a
finished session back to a routine's identity, never rendered — exactly the sanctioned future
use that ADR's own text names. With no routines at all, the screen is byte-for-byte what it said
before this story, so `TabNavigationTest`'s `"Start workout"` signal and every existing
instrumented test kept passing unedited. New coverage:
`TabNavigationTest.routinesIsAPushFromTrainsHeaderButtonNotATab` (a push, not a tab-switch — Back
returns to Train). `RoutineDeletionTest` needed no code change at all: its own `"Routines"`
click target happens to match the new header button's label. Verified live on device; all four
gates green plus `verifyDomainHasNoAndroidDeps` and 12/12 instrumented tests.

**In progress:**

- **Finish as a summary rather than a confirm dialog (US-31, at M4).** "Showing the work is a
  better check than asking *are you sure*." The confirm dialog itself is kept — the maintainer
  chose the lower-risk reading of that sentence over removing it outright — and what comes
  after confirming is replaced with a summary, which is also the first place a PR (US-18) is
  shown anywhere in the app.

**Designed, not built, and needing a user story first:**

- **Swap a movement when the machine is taken.** The audit calls this the most common reason
  a plan breaks, and it now has something to break: a swap should change today's session
  without touching the routine it came from. Suggestions come from the same body part, ranked
  by what has actually been used. The design doc leaves one question open — whether a swap
  made three times should offer to update the routine.
- **Supersets.** The design doc scopes them as *a pair, not a group* — two adjacent movements,
  one rest taken after B, logged as rounds. Three or more would need a different model. Audit
  finding 07 stands, and nothing has been drawn for it.

**Needs the maintainer's call before it can be written:**

- **+30s on the rest timer.** ADR-0016 deferred it explicitly as a US-05 amendment; it is on
  the redesign's screens but has never been decided.
- **An audio cue at 0:10 and 0:00.** For earbuds with the phone in a pocket. US-05 promises a
  notification only, so this is an amendment rather than a bug.

**Deliberately not designed, and staying that way:**

- The two 5k runs. Constitution §1 puts outdoor training permanently out of scope; that is a
  constitution amendment, not a screen, and ADR-0021 is the precedent for how such a request
  gets answered without one.
- Household and multi-member, which is M2 — the routines model had to settle first, and now
  has.
