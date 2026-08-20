# Roadmap

Milestones are sequential. **Do not start a milestone before the previous one's
exit criteria are met.** Each milestone ends in something installable that a family
member could actually use.

M0, M1, M3, M3a, M3b, M3c, M4 and M4a are all complete as of 2026-08-16 — the last three closed
in name only for a while, their checkboxes left unticked across the PRs that shipped them, until
this entry reconciled the file against the code (see each section's own "Exit" note for what was
verified and how). M2 is deliberately postponed so the offline core can be finished before
accounts and sync arrive; M3a, M3b, M3c and M4a were all taken ahead of their sequential position
for reasons argued in each one's own section below. Per the sequential rule at the top of this
file, **M5 is next** — US-20, US-21 and US-22 are merged (#59, then #60/#62 — #60 initially
merged into #59's now-dead head branch rather than `main` and had to be re-landed via #62,
2026-08-18); only US-23 (revoke) remains open. **M5a is specced (ADR-0039, 2026-08-18) and now
unblocked** — it runs alongside M5 on the same terms M3b and M3c ran alongside M4, and reuses
M5's optional-feature scaffolding (`:feature:health`, the no-op default binding, the per-member
toggle shape) now that it exists in `main`, rather than rebuilding it. Redesign-audit follow-up
work (below) continues alongside as well, on the same terms: it touches no table any milestone
reads.

M3b broke the "milestones are sequential" rule at the top of this file, and did so knowingly:
it was routines work that touched no table M4 reads, so running it beside M4 risked nothing
that sequencing would protect. Said out loud rather than left for someone to notice.

M3c (2026-08-15) does the same thing on the same terms, and is worth naming for a different
reason: it is the first milestone here driven by a cost the *development process* was paying
rather than by a gap in the product. Reinstalling to test wiped real training data every time.
That is not a feature request, which is exactly why it went unwritten for so long.

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
- [x] Log a set: weight, reps, RPE (RPE reached the UI later — set entry and the set editor
      both carry it, e.g. `ActiveSessionRoute.kt`'s `onRpeChanged`; corrected 2026-08-16, the
      parenthetical here had gone stale)
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

## M3c — Backup and restore

Stories: US-40, US-41, US-42. See `adr/0034-backup-is-a-file-you-own.md`.

Added 2026-08-15, and it is the same kind of arrival as M3a and M3b: not new scope invented by
the agent, but a cost the maintainer was paying every week. Uninstalling deletes the Room
database **and** DataStore; the app is reinstalled several times a week to test on device and
is also the real log for real training. Testing has been destroying training history.

**Taken ahead of M2 for the reason M3 and M3a were:** none of it needs an account, a backend or
a network. **Runs alongside M4** for the reason M3b did: M4 reads `sets` and draws charts, this
reads every member table once and writes a file. The one place they could collide — a restored
row changing a chart — is the point of the feature rather than a hazard, and is covered by the
exit criterion below.

Constitution §5 and US-11 already promised an export. US-11 **stays at M2 and is not
renumbered**: it is written around an account you can delete, which does not exist yet, and it
has no import in it. An export you cannot restore does not survive a reinstall.

- [x] ADR-0034, US-40/41/42, and the `data-model.md` and `tech-stack.md` notes written
      **before** any code
- [x] US-40: export the five member tables plus three DataStore keys as domain-shaped JSON
      under a versioned envelope, written through the Storage Access Framework. The catalog is
      excluded — it re-seeds from the APK with identical UUIDv5 ids, the same argument
      migrations v5 and v6 already make
- [x] US-41: import, replacing everything, inside one transaction. `ValidateBackup` is a pure
      function in `:core:domain` and runs to completion before a row is written, so a file that
      cannot be fully applied leaves the database untouched and names what is missing
- [x] US-41: import refused while a workout is running. `observeActive` drives the session
      screen and the guided flow's DataStore state points into `session_exercises`; a wipe
      mid-set is the one thing constitution §2.1 will not tolerate
- [x] US-42: a Settings screen, reached as a drill-down from Train's header rather than a
      fourth tab (US-36, ADR-0030), carrying export, import, the kg/lb toggle and the rest
      default — the last two closing controls US-05 and ADR-0008 have promised since M1 and
      never had
- [x] No new dependency. kotlinx.serialization is already in `:core:data` (`CatalogSeeder`),
      and SAF is `androidx.activity`'s `ActivityResultContracts`

**Exit: met 2026-08-16.** These six boxes had gone unticked across PRs #50/#51/#52 — landed, but
never reconciled against this file, against `CLAUDE.md`'s own definition of done. Ticked here
rather than in the commit that shipped the last of them, since that is when the gap was noticed,
not when it was introduced.

Log a real session, export it, uninstall, reinstall, import — and every workout, routine,
target and personal record is back, with the charts reading the restored history and the unit
preference intact. Verified **on device**, not only in the suite, on `Medium_Phone_API_36.1`:
logged a freestyle session (Barbell Bench Press, one bodyweight set), exported to
`gym-tracker-2026-08-16.json` via the SAF picker, confirmed the envelope holds `memberId`, unit,
rest default and all five tables, `adb uninstall`'d the app (the member id dies with the install,
exactly the failure mode this exit criterion exists to catch), reinstalled, confirmed Train home
read empty, imported the same file — the confirm dialog read the real counts ("Replace 0
workouts and 0 routines on this device with 1 and 0 from this file?") — and confirmed the
session reappeared on Progress with its exercise and set intact. `TwoTapSetLoggingTest` and
`OneTapSetLoggingTest` pass **unedited**: the full instrumented suite (13 tests, 0 failures) ran
twice from a cleared app on this same device.

---

## M4 — Progress and charts

Stories: US-16 … US-19, plus US-31, added 2026-08-09 out of the `Redesign.dc.html` audit
because it is the first place US-18's records are shown anywhere in the app.

- [x] Estimated 1RM (Epley), volume, and top-set trend per exercise
- [x] Weekly volume by body part (US-17). One block per week, one labelled bar per muscle,
      all bars on one scale. **Not a charting-library chart:** ADR-0019 leaves one accent on an
      achromatic ground, and the obvious rendering — a stacked column per week, one hue per
      muscle — needs twelve hues the palette does not have. Reached from History; the window
      is chosen on screen — see the selector line below
- [x] PR detection and history — closed 2026-08-14. ADR-0025 settled what a record is (heaviest
      load at a given rep count), and `PersonalRecordsOf` / `DetectPersonalRecord` landed in #29
      with 17 tests. US-31 was the first UI surface for it — a session's records shown on its
      finish summary.
    - [x] A standing per-exercise record list, closed 2026-08-14. `PersonalRecordsOf` already
          returned one `PersonalRecord` per rep count for one exercise; the per-exercise
          progress screen (US-16) just never read it. A "Personal records" section, between the
          trend chart and the log, absent rather than shown empty for a movement never done.
    - [x] The inline announcement on save, closed 2026-08-14. Both `SetEntryController` (two-tap)
          and `ActiveSessionViewModel.onLogNextSet` (one-tap) now run `DetectPersonalRecord`
          against the row actually written and surface the result as `SessionUiState.
          justSetRecord`, rendered as a filled banner above the (unchanged) rest countdown. Two
          deliberate simplifications versus the design frame, documented inline: an additive
          banner rather than swapping the countdown's fill, and no "beats X from Y" comparison
          line since the previous value isn't captured anywhere in the pipeline. Persists until
          the next set is logged, not tied to the rest cycle — simpler, and `RestController.skip`
          already bypasses the ViewModel entirely so there is no cycle-end signal to hook.
          `TwoTapSetLoggingTest` and `OneTapSetLoggingTest` pass unedited.
- [x] Empty and sparse-data states designed, not accidental (US-19)
- [x] Time range selector, closed 2026-08-14. `VolumeRange` (4 / 8 / 12 weeks, default 8) as a
      chip row on the weekly-volume screen — the same `FilterChip` pattern `TrendSeries`
      already uses on the per-exercise chart, not a new component. Scoped to weekly volume
      only: `WeeklyVolumeByBodyPart` already took an arbitrary `from`/`to` and only the
      ViewModel had the window hard-coded, where `ExerciseTrendOf`'s chart has no window at
      all today (it plots full history) — giving *that* one a range would be introducing
      windowing where none exists, a different and larger change, not exercised here
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
- [x] A "PR" badge on session rows. `PersonalRecordsAchievedIn` read the member's entire
      session history per row it was asked about — fine for `FinishSummaryScreen`'s one row,
      not for every visible row of a 200-session list. `SessionsWithRecords`, new, answers it
      in one O(sets) pass instead (US-38, ADR-0032). Closed 2026-08-14

### US-34 — Exercise log

Added 2026-08-11. See `user-stories.md`'s US-34 for the full story.

- [x] Session-by-session log below US-16's chart: one row per finished session with at
      least one set for the exercise, newest first, showing the session's best set, est.
      1RM, and its individual sets (`ExerciseLogOf`, new — mirrors `ExerciseTrendOf`'s
      three-read pattern so the two never disagree about which sessions counted).
      Closed 2026-08-11
- [x] A log row opens the source workout. `ExerciseLogEntry` gained `sessionId`
      (`ExerciseLogOf` already had `session.id` in scope building each row; it just was not
      kept), threaded through `ExerciseProgressRoute`/`Screen` to the existing `WorkoutDetail`
      destination — the same one `HistoryScreen`'s rows already open. Closed 2026-08-14
- [ ] **Still deferred:** a "tap any set to see its exercise's log" entry point elsewhere in
      the app (from a logged set on the session screen or workout detail, say). A different
      gap from the row-tap one above — not attempted here

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

## M4a — Rep, animated

Stories: US-43. See `adr/0035-rep-appears-inside-the-app.md`.

Added 2026-08-15, the same day as M3c and for a related reason: ADR-0026 (2026-08-09) named
the mascot, built the launcher icon, and explicitly left "does Rep appear inside the app" open
rather than decided by drift — "reopened properly rather than eroded one screen at a time." The
maintainer has now asked for that reopening.

**Taken ahead of M7 (where a mascot would otherwise file, as polish) for the reason M3b and M3c
both were: it touches no table, no domain type, no migration, and no data M4 reads.** It is
additive UI in `:core:designsystem` — one new composable, one new pure-Kotlin geometry model,
two colour tokens outside `ColorScheme` — plus four call sites that each render an optional
extra element. Sequencing protects nothing here that running it now would risk.

- [x] ADR-0035 and US-43 written **before** any code
- [x] `RepMascotGeometry`: the running pose, transcribed from the source SVG (viewBox
      `0 0 200 210`) as pure Kotlin, unit-tested — the loop closes, the two legs are exact
      mirror-phase copies of each other, the bob and band-tail rotation match the source
      `keyTimes`/`values`. `RepMascotGeometryTest`, 8 cases, all green
- [x] `RepMascot`: a `Canvas` composable driven by `rememberInfiniteTransition`, colours read
      from `MaterialTheme` and `LocalMascotBand` so light/dark needs no separate asset. Verified
      on device (not just in the suite) — an early build drew a stray second head circle because
      `drawCircle`'s default `center` ignores the ambient transform; fixed by passing
      `center = Offset.Zero` explicitly, confirmed gone on re-screenshot
- [x] `MascotBandLight` (`#9C7100`) / `MascotBandDark` (`#D19A00`), gated ≥3:1 against every
      surface Rep is drawn on by `MascotColorsTest`, never added to `ColorScheme`
- [x] Rep on Train home, the warm-up panel (running), exercise detail (beside the name, not the
      empty photo slot), and the guided screen's `RestHero`/`ExerciseSummary` states only.
      `MascotHome` (140dp) and `MascotInline` (88dp) sized and re-verified on device after the
      first pass at 104dp measured too wide for the warm-up panel's "Done" to stay on one line.
      **Superseded 2026-08-17 by the Turn 3 entry below:** the viewBox crop that fixes the
      overflow properly also changes what these two tokens measure, from width to height — this
      bullet's own "too wide" framing goes stale the moment that lands, on purpose.
- [x] Renders a static pose, not nothing, when `Settings.Global.ANIMATOR_DURATION_SCALE == 0`
- [x] `GymColorSchemeTest` passes **unedited** (mechanically refactored to share `WcagContrast`
      with the new `MascotColorsTest`, same assertions, still green)
- [x] `TwoTapSetLoggingTest`, `OneTapSetLoggingTest` and `GuidedFlowScreenTest` pass unedited —
      **reverified 2026-08-16.** The earlier note's "2 of 9 failing" was itself imprecise: those
      three files hold 5 `@Test` methods, not 9 (the full `app/src/androidTest/` suite is 13
      across 6 files). Ran the complete suite twice from a cleared app on
      `Medium_Phone_API_36.1(AVD)`: **13/13 passing, 0 failed, 0 skipped**, both times. The
      earlier kg/lb prefill failure did not reproduce; the inference that it was leftover state
      from manual testing rather than a real regression holds
- [x] No new dependency — confirmed: the file list for the three Rep commits
      (`a3385c3`, `b151705`, `7640648`) contains zero build files. The only config change is two
      justified `MagicNumber` exclusions in `config/detekt/detekt.yml`

**Exit:** Rep plays on all four surfaces in both light and dark mode, verified on device since
there is no screenshot-diff gate to lean on (`testing-strategy.md`); with system animations off,
he holds a pose instead of vanishing; the four unedited suites above stay green.

---

## M5 — Health Connect (optional)

Stories: US-20 … US-23. Read `specs/health-connect.md` first. Split into three PRs per
CLAUDE.md's ~400-line rule — the seam and the opt-in (US-20, US-21), the read (US-22), and
revoke (US-23) — since none of the three needs the others' code to land first.

- [x] `:feature:health` behind the `HealthMetricsSource` interface. PR A, 2026-08-18
- [x] Availability check, **collapsed to two live branches, not three** (ADR-0038,
      corrected from this line's own first draft): `HealthConnectClient.getSdkStatus()`'s
      "not available" and "update required" both map to one `Unavailable` —
      `health-connect.md`'s own text calls for exactly one silent branch, and a fourth status
      surfaced as an "update now" prompt would be the nag that document forbids. PR A,
      2026-08-18
- [x] Granular permission request, one at a time with its own on-screen reason first; app
      fully functional if denied or unavailable. PR A, 2026-08-18
- [x] Read heart rate, active calories, and exercise sessions for the session window. PR B,
      2026-08-18. Each of the three permissions gates its own read independently — a partial
      grant reads what it can and leaves the rest null, never a refused read overall
      (`health-connect.md`'s "partial permissions" case). Exercise, when granted, narrows the
      window the other two run over to the actually recorded session
- [x] Aggregate on-device; persist only avg HR, max HR, active kcal on the session. PR B,
      2026-08-18 — the four columns already existed on `sessions` since v1 (`SessionRepository.
      saveMetrics`, a Room partial-entity update). Rendered on `FinishSummaryScreen` and
      `WorkoutDetailScreen`, absent entirely unless a read actually ran, "not recorded" per
      field that was read for and found nothing (never zero, constitution §2.4)
- [x] Per-member toggle, default **off**, and — caught while building the Settings screen,
      recorded in ADR-0038 rather than silently — **independent of the availability check
      above**, not folded into it: a status that meant either "device incapable" or "toggle
      off" would leave Settings with no signal to decide whether to show the very control
      that turns the toggle on. PR A, 2026-08-18
- [x] Full UI suite passes with the no-op binding, `-Pgymtracker.optionalFeatures=off`
      (`specs/testing-strategy.md` §1), enforced by `HealthSettingsTest`. PR A, 2026-08-18
- [ ] Turning the toggle off stops reads and offers to delete previously imported metrics
      (US-23, PR C — not started). Missing from this list until now; added rather than left
      implicit the way M3c's and M4a's boxes went unticked across their own PRs

**Exit:** installing on a device with no Health Connect at all produces zero
crashes, zero empty holes, and no prompts.

PR A: all four gates plus `verifyDomainHasNoAndroidDeps` green. The full instrumented suite ran
twice on `Medium_Phone_API_36.1(AVD)` — default bindings (22 tests, 0 failed, `HealthSettingsTest`
skipped as designed) and `-Pgymtracker.optionalFeatures=off` (20 tests, 0 failed, 0 skipped,
`HealthSettingsTest` running and passing this time). `TwoTapSetLoggingTest`, `OneTapSetLoggingTest`
and `GuidedFlowScreenTest` pass unedited in both. Verified live on device, not only in the
suite: with Health Connect present and the toggle off, Settings shows the "Health Connect" row
with no permission card; turning it on walks all three permissions in order, each with its own
reason shown first, confirmed by logcat that the real
`com.google.android.healthconnect.controller` permission activity actually launches for each;
denying every permission leaves "No permissions were granted, so nothing is read." and the rest
of the app (Train home, a workout) untouched; toggling off mid-walk clears the pending card and
the message immediately. The no-Health-Connect case is covered by `HealthSettingsTest` rather
than by hand — this emulator image has Health Connect built in, so there was no device on hand
without it.

PR B: all four gates plus `verifyDomainHasNoAndroidDeps` green; the full instrumented suite ran
twice on `Medium_Phone_API_36.1(AVD)` (22 tests default / 20 tests optional-features-off, 0
failed either way); `TwoTapSetLoggingTest`, `OneTapSetLoggingTest` and `GuidedFlowScreenTest`
pass unedited in both.

**A real crash, found only on device, not by the unit suite.** With the toggle on and all three
permissions granted, finishing a workout crashed the app outright — `FATAL EXCEPTION: main`,
`HealthConnectException` wrapped as `IllegalStateException`, uncaught inside a `viewModelScope`
coroutine. Two causes, both fixed before this box was ticked: `HealthConnectMetricsSource.
metricsFor()` had no error handling at all, so any real SDK failure propagated and crashed the
process rather than degrading to "nothing recorded" the way an enhancement layer must
(constitution §3) — fixed with a fault-injection test driving the fix first. And the platform
itself names the actual cause: Android 14+ refuses every Health Connect read unless the app
declares a manifest handler for `VIEW_PERMISSION_USAGE`/`HEALTH_PERMISSIONS` — a requirement
`health-connect.md` never named — fixed by adding `HealthPermissionsRationaleActivity`.
Re-verified live end to end after both fixes: toggle on, permissions granted, finish a workout —
no crash (confirmed against logcat, not just the UI staying up), and both the finish summary and
the workout detail read "Heart rate not recorded · Calories not recorded" — a real Health
Connect read that found no samples, which is the reason it renders at all rather than being
absent: `SessionEntity`'s own read path only produces a non-null `SessionMetrics` when
`metrics_source` is set, and a genuinely never-attempted read (the pre-fix crash's actual
behavior, confirmed by pulling the on-device SQLite file and finding every metrics column null
including `metrics_source`) renders nothing, exactly as it did before this session's read ever
ran.

---

## M5a — Live heart rate from a paired band

Stories: US-46 … US-49. Read `specs/adr/0039-a-live-band-is-not-health-connect.md`
and `specs/health-connect.md`'s amended device-access rule first. This is not
Health Connect and not US-22's read: it is a live, transient value from a direct
Bluetooth connection to the band, never persisted.

Runs alongside M5 on the terms `roadmap.md` already set for M3b and M3c alongside
M4: it touches no table M5 reads. Its code was blocked on #59 and #60 merging so it
could reuse M5's optional-feature scaffolding (`:feature:health`, the no-op default
binding pattern, the per-member toggle shape) instead of building it twice — both
are now in `main` (see the section header above), so M5a's implementation can
start. Split into two PRs for the same reason M5 itself split into three: PR A is
the pairing infrastructure (US-46) below; PR B wires it into a visible reading
(US-47, US-48) and verifies US-49.

- [x] `LiveHeartRateSource` port in `:core:domain`, independent of
      `HealthMetricsSource`. PR A, 2026-08-18. A second port,
      `HeartRateBandScanner`, was added alongside it — `:feature:settings` cannot
      depend on `:feature:health`'s connection machinery just to render a device
      list, so scanning-for-candidates and holding-a-live-connection stayed two
      separate ports from the start, the same way `HealthMetricsSource` and
      `LiveHeartRateSource` are two ports rather than one
- [x] Availability check: no Bluetooth adapter / below API 31 / **not available**.
      PR A, 2026-08-18. `HeartRateBandScanner.availability()` is deliberately
      independent of the per-member toggle (mirrors ADR-0038's `HealthStatus`
      split) — a design correction made while wiring Settings, not before:
      `LiveHeartRateSource`'s own `Unavailable` conflates "device incapable" with
      "toggle off," which is fine for the connection itself but would have left
      Settings unable to decide whether to show its own toggle
- [x] Granular permission request (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`); app
      fully functional if denied or unavailable. PR A, 2026-08-18. One at a time,
      each with its own reason, via a plain `ActivityResultContracts.
      RequestPermission()` launcher — no SDK-specific contract needed, unlike
      Health Connect's
- [x] Scan, pair, and hold a Bluetooth Heart Rate Profile (0x180D/0x2A37) connection
      to the chosen device. PR A, 2026-08-18. `HeartRateBandGateway` is the seam
      (mirrors `HealthConnectGateway`'s shape exactly); `BleHeartRateSource`'s
      state machine (Searching/Beating/Lost, plus the staleness watchdog below) is
      fully unit-tested against a fake gateway using virtual time, no device or
      Robolectric needed for the logic itself — only the real
      `AndroidHeartRateBandGateway` touches actual Bluetooth APIs and needs
      on-device verification
- [x] Live BPM visible from every screen while a reading exists; absent entirely
      otherwise. PR B, 2026-08-18. `LiveHeartRateChip` sits in
      `GymTrackerNavHost`'s `Scaffold(topBar = …)` — the one slot every
      destination shares, unlike the bottom bar which is conditional per screen —
      so it renders the same whether the member is mid-workout, browsing the
      catalog, or in Settings, exactly what "not just the session screen" asks
      for. Deliberately not gated on `hasActiveSession`: the connection itself
      (`BleHeartRateSource`) is driven only by the preference toggle, so a member
      can check their heart rate without starting a workout, the same way they
      would glance at the band itself
- [x] Searching / Beating / Lost are distinct, honestly-labelled states; a stale
      reading is never shown as current. PR B, 2026-08-18. Rendered as three
      distinct `Text`/`NumeralText` branches — never a shared "unknown" state
      that could paper over the difference. `LiveHeartRateChipTest` pins the
      absence rule at the instrumented level: zero nodes containing "bpm",
      "Heart rate: searching…", or "Heart rate: lost" anywhere, under the no-op
      binding — mirrors `HealthSettingsTest`'s exact shape
- [x] Per-member toggle, default **off**; nothing is ever persisted. PR A,
      2026-08-18. The chosen device address is the one thing that *does* survive
      the toggle turning off, by design (`HeartRateBandPreference`'s class doc):
      the same convention system Bluetooth pairing uses, so turning it back on
      does not force re-scanning for a band already chosen. Distinct from US-23's
      revoke, which offers to delete previously *imported* metrics — there is
      nothing here to delete, since nothing is ever written to Room
- [x] Full UI suite passes with the no-op binding; `TwoTapSetLoggingTest` unedited.
      PR A, 2026-08-18, re-verified PR B, 2026-08-18 — full instrumented suite ran
      twice more on `Medium_Phone_API_36.1(AVD)` after the chip landed: default
      bindings (22 tests, 0 failed, `HealthSettingsTest` and `LiveHeartRateChipTest`
      both skipped as designed) and `-Pgymtracker.optionalFeatures=off` (22 tests,
      0 failed, both running and passing). One flake surfaced and was chased down
      before trusting it: `OneTapSetLoggingTest` failed once on a full-suite run
      with a `SQLiteConstraintException`, passed in isolation, then passed again
      on a full clean re-run — pre-existing cross-test contamination in the shared
      Room instance, unrelated to this change (confirmed, not assumed)

**Exit — partially reached.** Verified: installing with `-Pgymtracker.optionalFeatures=off`
(the no-Bluetooth/below-API-31 case's mechanical proxy, since CI's own emulator has
a working adapter) produces zero crashes, zero empty holes, no prompts, and the
full suite stays green; turning the toggle off drops the connection immediately
(unit-tested in `BleHeartRateSourceTest`). **Not verified: pairing a real Fitbit
Charge 6 and confirming live BPM tracks the band's own display.** That needs actual
hardware — nothing in this session had one paired to the test device — and is the
one thing left before this box is honestly closed. `AndroidHeartRateBandGateway`
(the only class touching real Bluetooth APIs) is the one piece of PR A/B with no
test coverage of any kind for exactly this reason.

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

**Four more ADR-0019 compliance gaps closed 2026-08-12 (PR A of the follow-up audit).**
`FilterChip` (Browse's body-part/equipment rows, the exercise progress series picker) and
`AssistChip` (exercise detail's equipment tag) never passed `shape`, so all three still read
Material's `CornerFull` token regardless of `GymShapes` — the exact trap `Shape.kt` documents,
now closed by passing `shape = MaterialTheme.shapes.large` at each call site the same way
`PrimaryActionButton` and the outlined buttons already did. The three bundled-photo call sites
(Browse's thumbnail, exercise detail's hero, workout detail's thumbnail) rendered in full
colour; the Modernist design system's `.grayscale` treatment (`filter: grayscale(1)
contrast(1.08)`) was never applied anywhere, against its own "do not tint or colorize imagery"
rule. A new `GymPhoto` composable in `:core:designsystem` wraps `AsyncImage` with that filter as
one affine `ColorMatrix` (pinned by `GymPhotoTest`), and all three call sites now go through it
so a future one cannot forget the treatment the way these three did. Nine `ColorScheme` slots —
`errorContainer`, `onErrorContainer`, `tertiaryContainer`, `onTertiaryContainer`,
`inverseSurface`, `inverseOnSurface`, `inversePrimary`, `surfaceTint`, `scrim` — were never
passed to `lightColorScheme()`/`darkColorScheme()`, so each still inherited Material's own
baseline (violet-tinted) default; `GymColorSchemeTest` now gates all nine the same way it
already gated `outlineVariant`. And the five raw `dp` literals ADR-0011's "feature code never
names a raw sp" rule has a `dp` counterpart for — `WeeklyVolumeScreen`'s bar height,
`ExerciseProgressScreen`'s chart height, `ExerciseDetailScreen`'s photo height, and
`BrowseScreen`'s row height and FAB clearance — are now named `GymDimens` tokens, pinned by
`GymDimensTest`. All four gates green; 11/11 instrumented tests pass unedited.

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

**Set entry prefers history over a target, 2026-08-13 (US-37, ADR-0031, PR C of the follow-up
audit).** US-30 (ADR-0027) had set entry prefer a routine's target over the member's last real
set; the design's section 2b asks for the opposite, and the maintainer chose it once both
readings were in front of them — a target can go stale, history never can, by construction. One
pure function, `ResolveSetPrefill` (`:core:domain`), now carries the merge rule that
`SetEntryController.open` and `ActiveSessionViewModel`'s one-tap prefill both used to inline
separately, in opposite orders, which is exactly how the two had room to disagree without either
noticing. Reps float to a floor of 12 when neither history nor a target has an opinion; weight
never floors, since an invented load is worse than an empty field. **Sets was drafted as a
universal floor of 3 first, exactly as the design's literal text reads, and that broke
`TwoTapSetLoggingTest` on-device** — confirming a set for a brand-new exercise logged three rows
instead of one, because the sheet's own default had silently changed under a test that confirms
without checking the sheet on purpose. The floor is narrower than the brief because of that
finding: `target?.sets ?: 3` only once a target exists at all; with no target, sets stays
ADR-0009's original 1. The set-entry sheet says when a number is history's: "Prefilled from Tue
4 Aug — 100 lb × 8" (the existing rest-panel date convention, not the frame's literal "last
Tuesday" wording). The one-tap log button (US-35) picks up the same weight/reps precedence but
keeps its existing "absent with no history and no target" gate unchanged. Verified live on
device; all four gates green plus `verifyDomainHasNoAndroidDeps` and 11/11 instrumented tests
including `TwoTapSetLoggingTest` and `OneTapSetLoggingTest` **unedited** — confirmed by an
actual on-device failure and fix, not asserted from the unit suite alone.

**Progress rows earn a hierarchy, and the deferred PR badge, 2026-08-14 (US-38, ADR-0032, PR D
of the follow-up audit).** US-33 shipped Progress but named one gap explicitly and left it: *"a
'PR' badge on session rows… needs a purpose-built O(sets) read"* — `PersonalRecordsAchievedIn`
re-reads a member's entire lifting history per set, fine for one finish-summary row, not for 200
visible rows at once. `SessionsWithRecords` (`:core:domain`) answers it in one pass instead:
every loaded set read once, grouped by (exercise, reps), walked chronologically to find where
each group's running best was first strictly beaten — `DetectPersonalRecord`'s own rule
(ADR-0025), computed once rather than per set. Alongside it, the row itself moves off `ListItem`
onto a plain ruled two-line `Row` (ADR-0029's ruled-sheet precedent): the routine name and date
share one line at two weights, the four-metric summary drops to a second, smaller, muted line —
answering audit finding 06's "reads as a bug" complaint through hierarchy, without touching the
duration/volume computation itself, which was already honest. No new `Typography` role added —
all fifteen `Typography` slots were already spoken for by ADR-0029, so the row reuses `titleSmall`
and `bodySmall` rather than overloading a role every other screen depends on for a 1–2sp
difference. New tests: `SessionsWithRecordsTest` (JUnit 5, the merge rule in isolation — first
appearance is not a record, ties are not records, a different rep count is a separate track,
bodyweight sets never count) and a `WorkoutHistoryTest` case pinning the two-session badge
behaviour end to end. Verified live on device; all four gates green plus
`verifyDomainHasNoAndroidDeps` and the full instrumented suite.

**The guided exercise screen joins the ruled sheet, 2026-08-14 (US-39, ADR-0033, PR E of the
follow-up audit).** A gap, not a regression: `GuidedExerciseScreen` (ADR-0017's guided flow,
US-05a) predates the redesign, `Redesign.dc.html` draws no frame for it, ADR-0029 explicitly
scoped it out, and this section never listed it — nobody had looked at it since the redesign
shipped, until a member reported still seeing "Go", a bare rep field and a "Finish set" button
on Material defaults. `Redesign.dc.html`'s `1b` — the one-exercise-at-a-time direction ADR-0029
rejected for the *main* session screen — is the closest source material for a screen that
genuinely is one exercise at a time, and every size in it is re-expressed through a `Typography`
role ADR-0029 already shipped rather than a new one: the resting state's countdown, movement name
and one combined `"135 lb × 12 · 61.2 kg · set 3 of 3"` line (`displayLarge`/`headlineSmall`/
`titleLarge`) all sit on the same full-bleed `primary`/`onPrimary` `Surface` shape `RestPanel`
already ships; the mid-set state's weight×reps hero reads the typed rep count, not the target
(`headlineMedium`, `RestPanel`'s own `UpNext` call-site shape); set progress reuses the session
header's `SegmentBar`, promoted to take a plain `total`/`done` pair instead of a
`SessionProgress`. The rep count gained the app's `StepperField` (+/− buttons) — the one
behavioural change, covered by three new `GuidedFlowTest` cases written first, and the reason
this needed a failing test rather than only a visual diff; ADR-0017's editable-before-commit
guarantee is proved unchanged by its own two tests staying unedited. `"Finish set"` became
`"Log set {n}"` — no test asserts the old string anywhere, and unlike ADR-0029's refused `ADJUST`
rename, both labels name the same operation. `displayMedium`, protected by ADR-0029 specifically
because this screen read it, is now read nowhere in the app; `GymTypographyTest`'s test naming
that reason was renamed rather than left with a false premise. `GuidedSetupDialog` stays
unchanged and is now the one guided-mode surface still on Material defaults — logged as a named
follow-up, not a silent gap. New instrumented coverage: `GuidedFlowScreenTest`, the first UI test
to reach guided mode at all. Verified live on device; all four gates green plus
`TwoTapSetLoggingTest`/`OneTapSetLoggingTest` unedited.

**Guided mode's start dialog defaults to 12 reps × 3 sets, weight still from history,
2026-08-15 (US-05a).** Requested directly: a predictable walkthrough length to start
from and adjust, rather than reps quietly tracking whatever the last session happened
to be. Weight is untouched — it still prefills from the last time the exercise was
done, same as the two-tap sheet. **The two-tap sheet's own Sets field was
deliberately left at ADR-0009's floor of 1 with no target** — raising it there would
let confirming without editing fabricate a shared `performed_at` across several sets,
the exact regression ADR-0031 found and reverted on-device the first time this was
tried. Guided mode carries no such risk: each set it writes gets its own real
timestamp regardless of the target count, so a fixed 3-set default costs nothing
there specifically. Two new `GuidedFlowTest` cases (with history, and with none at
all); `GuidedFlowScreenTest` simplified, since the dialog no longer needs a manual
bump past a 1-set default to reach the resting state it tests. All four gates green.

**Turn 3 — three clocks, one accent, closed 2026-08-17 (US-44, ADR-0036).**
`Redesign.dc.html` synced a third turn on 2026-08-16, the first unlanded material since M3b's
follow-up audit closed. It diagnosed three separate clocks, each wrong for a different reason:
the warm-up row asking `Done` to fit in −14dp beside a 104sp countdown and an 88dp `RepMascot`
box that draws a 32dp-wide figure; the rest countdown wearing the accent fill ADR-0029 meant for
the log button; and "per-set time" conflating a derived, retroactive number (the set-to-set
interval) with one that needs a schema change and doesn't exist yet (time-under-load). Shipped
exactly the doc's own recommended scope — frames `3a` + `3c` + `3g`, plus the
`RepMascotGeometry` viewBox crop `3a`'s finding depends on. `+30s` and an audio cue, both drawn
in `3c`'s frames, stayed out a second time, confirmed with the maintainer before writing
ADR-0036 — see that ADR and the entries below under "needs the maintainer's call," both
unchanged. `3f` (a display-only stopwatch on the guided screen) and time-under-load itself stay
out of scope, undesigned. A real bug surfaced only on device, not by any test: the warm-up
panel's new `Done` button was missing `shape = MaterialTheme.shapes.large`, so it rendered as
`OutlinedButton`'s default `CornerFull` pill instead of `GymShapes`'s square corner — exactly the
trap `Shape.kt`'s own class doc warns about. Verified live on device, light and dark: the ink/red
swap at the final ten seconds, the progress bar, and a past workout (including one predating
this change) showing correct retroactive intervals matching the active session's own numbers
exactly. All four gates green plus `verifyDomainHasNoAndroidDeps`. **Rebased 2026-08-17 onto
US-29's own countdown progress bar (PR #57), landed in parallel** — `RestTimerStore.restTotal`
is now pinned at `RestTimer.start()` rather than a live `defaultRest` read, closing the
mid-rest-desync limitation this entry originally accepted; see ADR-0036's amendment section.

**Switching between exercises, 2026-08-17 (US-45, ADR-0037).** Reported live, mid-workout, right
after Turn 3 landed: log a set on a later exercise because an earlier one's machine is taken, and
the earlier exercise vanishes from the session screen for good — no row, no button, nothing to
tap, only destructive ways back (delete every set on the later exercise, or remove it, US-02c).
Traced to `ActiveSessionViewModel`'s `currentRow`, a pure function of "the highest-position
exercise with a logged set" since ADR-0029 first wrote it — a gap in the original design, not a
regression from Turn 3. `ActiveSessionViewModel` gains a sticky, explicit `selectedExerciseId`
that wins over the derived default when set; `SessionPlan`'s list drops its `position >
currentRow.position` filter so every other exercise is reachable, in either direction, and
tapping one now selects it as the fully open exercise (own set list, target, one-tap log button)
rather than firing the `Add set` sheet blind. The two-tap and one-tap paths for a member who
never switches exercises are unchanged, and `TwoTapSetLoggingTest`/`OneTapSetLoggingTest` pass
unedited. Explicitly not this story: swapping a movement for a *substitute* exercise, which stays
where it already was, below, needing its own design.

**In progress:** nothing, as of 2026-08-17. The one entry this heading used to carry — "Finish
as a summary rather than a confirm dialog" (US-31) — shipped in `87e975c` (PR #35) and is
already ticked `[x]` in M4 above; the heading itself had gone stale rather than the work.

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
