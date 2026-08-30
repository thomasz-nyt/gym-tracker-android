# User Stories

Acceptance criteria are written so that each bullet maps to at least one automated
test. If a criterion cannot be tested, rewrite it until it can.

Personas: **Adult** (you), **Partner** (casual user, low tolerance for friction),
**Teen** (may have an age-restricted account; may have no Health Connect at all).

---

## M1 — Core loop

### US-01 — Start a session
As a member, I start a gym session so my sets are grouped.
- Given the home screen, when I tap "Start workout", a session is created with
  `started_at = now` and I land on the active-session screen.
- Given an active session exists, when I reopen the app, I return to it rather
  than starting a new one.
- Only one active session per member at a time.
- If, on app open, the active session's last activity (its last set's
  `performed_at`, else `started_at`) is more than 4 hours old, I am prompted to
  finish it (`ended_at` = the last set's timestamp) or discard it if it has no
  sets. The app never invents an end time silently.

### US-02 — Add an exercise
- Given an active session, when I search the catalog and select an exercise, it is
  appended to the session — appended in `position`, which is the order I performed
  them. The active session *displays* them newest-first (US-02b); history and the
  workout detail read in `position` order.
- Recently used exercises appear first, before alphabetical results.
- With no history yet, a curated set of common gym movements appears above the
  alphabetical results, so a new member does not meet 873 exercises in alphabetical
  order. History outranks it as soon as there is any (ADR-0007).
- Starter exercises show a bundled photo of the movement. Exercises without one show
  no image rather than a placeholder; the rest of the catalog gets media at M3.
- The same exercise may appear twice in one session.

### US-02a — Add several exercises in one visit to the catalog
Added 2026-08-02 against the in-session search; rewritten 2026-08-03 against the
browse screen, which replaced that search at M3 (US-12, ADR-0013). Picking one
exercise used to end the visit, so a three-exercise workout was three round trips
— first through a search field, then through a navigation stack.
- Given I reached the catalog from an active session, when I choose an exercise it
  is added to the workout and **the catalog stays up**, with my query and filters
  intact.
- Each exercise I have added in this visit is marked on its row, with a count, so
  the second tap on one is visible.
- Choosing the same exercise twice in one visit adds it twice, per US-02.
- A button on the catalog returns me to the session and reports how many I added.
- Hardware back returns me to the session with everything I picked, not just the
  last one.
- The exercises arrive in the order I picked them, each with its own `position`,
  however many I chose.
- Reaching the catalog from home is unchanged: a tap opens the exercise's detail
  screen (US-13), and nothing is added to anything.

### US-02b — The newest exercise is first
Added 2026-08-02. The exercise I just added was the one furthest from my thumb, at
the bottom of a growing list.
- In the active session, exercises are listed **newest first**.
- The number shown against each exercise counts the exercises done so far, so the
  newest carries the highest one and the list never shows a gap after a removal
  (US-02c). Adding an exercise does not renumber the ones already there.
- Past workouts (US-06, US-06b) and a restored workout (US-06a) are unaffected: both
  read in `position` order, the order I performed them.

### US-02c — Remove an exercise from the session
Added 2026-08-02. Machines get taken and occupied, and exercises get added by
mistake; there was no way to take one back out.
- From the active session, I can remove an exercise. It disappears from the list,
  and any sets logged against it are removed with it.
- Undo is available for 5 seconds, as in US-04 and US-06a, and restores the exercise
  with its sets unchanged — same ids, same values, same position.
- Removing an exercise does not renumber the ones around it; the displayed list
  closes the gap (US-02b) while `position` stays as it was.
- Removing the exercise holding my most recent set of that movement changes what the
  next set of it prefills with (US-03). The prefill never resurrects a removed set.
- Removing the last exercise leaves an empty active session, not a discarded one.
  US-01 and US-06 still own ending or discarding it.

### US-03 — Log a set  ← the story that matters most
- Given an exercise in the active session, when I tap "Add set", weight and reps
  are **prefilled from my most recent set of that exercise** (any session).
- When no prior set exists, the fields are empty and focused.
- Confirming a set requires **no more than 2 taps** when the prefilled values are
  correct. Asserted by an instrumented test that counts interactions.
- Weight accepts one decimal place, in the member's own unit; the other unit is shown
  alongside (ADR-0006, ADR-0008). Reps are whole numbers ≥ 1.
- A **sets** count may be entered — "3 sets of 12" — which records that many identical
  sets, each as its own row (ADR-0009). It defaults to 1, so the two-tap path is
  unaffected.
- The set is persisted locally before any UI transition. Killing the app
  immediately after does not lose it.

### US-04 — Correct a mistake
- I can edit weight, reps, or RPE of any set in the current session.
- I can delete a set, with undo available for 5 seconds.
- Editing a past session's set is possible from history and recalculates PRs.

### US-05 — Rest timer
- After logging a set, a rest timer starts automatically at my configured
  default (60 seconds until changed in settings).
- The timer keeps running when the app is backgrounded and notifies at zero.
- If notification permission is denied, the timer still runs and displays
  in-app. The permission is requested once and never re-prompted.
- I can dismiss or skip it. It never blocks logging the next set.

### US-05a — Be walked through an exercise
Added 2026-08-02. See `adr/0017-guided-exercise-flow.md`, which revisits the
prescription option ADR-0009 rejected. "Rep" in the original request means *set*.
- Given an exercise in the active session, I can start it. **Weight is prefilled from
  my last session of it, as in US-03.** Reps and the sets count are **not** — they
  start at a fixed 12 reps × 3 sets, a walkthrough length picked once rather than
  drawn from the last time, and editable before confirming. **Changed 2026-08-14:**
  this deliberately diverges from US-03's history-driven reps prefill; see below.
- While an exercise is running I see its name, the weight in both units, which set I
  am on out of how many, the rep count for this set, and the timer.
- The rep count is **editable before I finish each set**. If I planned 12 and managed
  9, the set that is written says 9. The target is a prefill, never a promise.
- Finishing a set writes exactly one set, with **its own `performed_at`** — not a
  shared timestamp as in ADR-0009 — and starts the rest timer (US-05).
- When the last set is done I see a summary of that exercise: sets completed, reps,
  total volume, and elapsed time.
- If another exercise in the session has no sets logged against it, the summary
  offers it as the next one. Otherwise it offers to finish.
- Leaving guided mode at any point returns me to the session with every set logged so
  far intact. Killing the app does the same.
- Starting an exercise is an **additional** action. "Add set" keeps its place and its
  behaviour, and the two-tap path of US-03 is unchanged.
- **12×3 default, not history, and not the two-tap sheet.** The two-tap sheet's own
  Sets field stays at ADR-0009's floor of 1 with no target — raising it there would
  mean confirming without editing fabricates a shared `performed_at` across several
  sets, exactly the regression ADR-0031 found and reverted on-device. Guided mode has
  no such risk: each set it writes always gets its own real timestamp regardless of
  the target count (see above), so a fixed 3-set walkthrough costs nothing there.
- **Added 2026-08-14:** the screen's visual treatment is US-39's, not this story's — these
  acceptance criteria are the unchanged behavioural source of truth; US-39 only changes how it
  looks and adds `+`/`−` steppers to the rep count.

### US-06 — End a session and see history
- Ending a session sets `ended_at` and returns me to home.
- History lists sessions newest-first with date, duration, exercise count, and
  total volume.
- A session with no sets is discarded rather than saved.
- A session finished via the stale-session prompt (US-01) gets `ended_at` = its
  last set's `performed_at`.
- Total volume counts only sets with a recorded weight. Bodyweight sets are shown
  as a count alongside it, never folded in as zero (constitution §2).

### US-06a — Delete a past workout
Added 2026-08-01. Real workouts now share the device with sessions logged while
testing, and there was no way to remove one without clearing the other. See
`adr/0012-deleting-a-past-session.md`.
- From history, I can delete a past workout; it disappears from the list, and its
  exercises and sets are deleted with it.
- Undo is available for 5 seconds, as in US-04, and restores the workout with its
  exercises and sets unchanged — same ids, same values, same order.
- Only a finished workout can be deleted this way. The session I am currently in
  is not in history; ending or discarding that one is US-01 and US-06.
- Deleting the session holding my most recent set of an exercise changes what the
  next set of it prefills with (US-03). The prefill never resurrects a deleted set.

### US-06b — See what happened in a past workout
Added 2026-08-02. History gives totals for a workout but not its contents, so there
is no way to answer "what did I do on Tuesday, and at what weight".
- From history, I can open a past workout and see the exercises it contained, in the
  order I performed them.
- Each exercise shows its name, its equipment, its primary muscles, and its bundled
  photo where it has one — no placeholder where it does not, per US-02.
- Sets are shown **one row per set**, with weight in both units (ADR-0008) and
  per-exercise volume. *Amended 2026-08-07 by ADR-0022:* this criterion read "grouped as
  in ADR-0009 — `3 × 12` rather than three near-identical lines" until US-04 needed every
  set to be individually correctable here too, and a grouped line has no set id behind it
  to tap.
- A bodyweight set is shown as bodyweight and counted separately. It is never folded
  into volume as zero, per US-06.
- Session metrics that were never recorded are shown as "not recorded", never as
  zero (constitution §2.4). Until M5 that is every session.
- Instruction steps, GIFs, and filtering by body part or equipment are **not** part
  of this story. They are US-12 and US-13, at M3.

---

## M2 — Accounts, household, sync

### US-07 — Sign up and sign in
- Email + password. Session persists across app restarts.
- Sign-out clears local data for that member only.
- **Amended 2026-08-29 (ADR-0042): sign-in is optional, for the life of the app.** Every
  feature from M1 through M5a keeps working fully with no account; nothing gains a
  requirement to sign in. On a device's first-ever sign-in, its existing local rows are
  adopted into the newly-signed-in account (`data-model.md`'s own re-key UPDATE). A second
  sign-in on the same device — a different member on a shared household phone, or the same
  member signing in again after signing out — adopts nothing further: the device has already
  adopted once, and that member's session starts with an empty household view rather than
  inheriting whatever is already on the device.

### US-08 — Create or join a household
- The first member creates a household and gets an invite code.
- Another member joins with the code and becomes a member of that household.

### US-09 — Isolation
- A member can read and write only their own sets.
- A member can see other household members' *names and session summaries*, but
  **not** their per-set data unless that member has opted to share.
- Cross-household reads return zero rows. Proven at the database layer, not the UI.

### US-10 — Offline-first
- Every M1 action works with no network.
- Changes queue and sync when connectivity returns, surviving app kill.
- A sync status indicator distinguishes: synced / pending / error.
- **Amended 2026-08-29 (ADR-0043): the indicator is silent exactly when synced.** It shares
  `GymTrackerNavHost`'s `topBar` slot with `LiveHeartRateChip` rather than adding new
  permanent chrome — a muted pending count, or a tappable error row, when there is something
  to report; nothing when there is not. "Synced" is not itself announced there, since silence
  already means "nothing more urgent than usual" for the chip it shares the slot with. The
  full three-state detail this story's "distinguishes: synced / pending / error" asks for,
  including *when* it last synced, lives in Settings, where a member can go look for it.

### US-11 — Export and delete
- I can export all my data as JSON to a file.
- I can delete my account; all my rows are removed and this is verified by a test.
- **Note added 2026-08-29:** the export half is already shipped. US-40 (M3c, 2026-08-16)
  delivered exactly this — every member table as JSON through the Storage Access Framework —
  ahead of M2 because constitution §5 promised an export independent of accounts existing.
  What M2 still owes this story is account deletion: a server-side cascade removing every row
  belonging to the account, plus the local wipe, both proven by a test.

---

## M2a — Household media

### US-15 — Record our own gym's equipment
Moved to M2 from M3 on 2026-08-01: it is written in terms of a household, and there is
no household until M2 (ADR-0014). **Moved again 2026-08-29, from M2 to this new M2a**: M2's
own exit criterion — two devices converging, isolation proven — needs no media at all, and
this story needs the household and sync engine M2 builds, so it belongs after M2 exits
rather than inside it.
- I can record or pick a short clip and attach it to a catalog exercise for my
  household, overriding the stock media for us only.
- The clip is stored on the device and works offline; sharing it with the household
  goes through the same sync engine M2 built.

---

## M3 — Catalog

Revised 2026-08-01, when M3 was taken before M2. Two criteria assumed catalog fields
that do not exist and one assumed a backend that does not — see ADR-0014 and ADR-0015.

### US-12 — Browse and filter
- Filter the catalog by body part and by equipment; combine both. Clearing the
  filters returns the full catalog.
- Search matches on name **and on hand-authored aliases** (ADR-0015): "pulldown"
  finds Wide-Grip Lat Pulldown, "pec deck" finds Butterfly.
- Filters and the search query combine, rather than one replacing the other.
- Browsing is reachable from home, without starting a workout.
- The same screen is reached from an active session to add an exercise, where
  tapping a result still adds it directly. **US-02's path gains no taps.**
- Equipment the source never recorded reads as "Not specified", not as "Other"
  (constitution §2, ADR-0015).

### US-13 — See how a machine works
- The exercise detail screen lists primary and secondary muscles worked.
- It shows numbered instruction steps. Where the catalog records none — five
  exercises — it says so, rather than rendering an empty panel.
- It shows a bundled photo of the movement where one exists, and nothing in its
  place where one does not (ADR-0007, ADR-0014).
- Everything on the screen ships in the app, so it is **fully usable in airplane
  mode on first launch**, with nothing to cache first.
- GIF and video playback move to M2 with the Storage bucket they need (ADR-0014).

### US-14 — Link out to a video
- The detail screen offers a YouTube **search** for the exercise, opened in the
  external browser or app.
- It is labelled as a search. The catalog ships no curated links and the app does
  not imply it has vetted one (constitution §2, ADR-0015).
- No embedded player, no third-party SDK, no account (constitution §3).
- It is the only thing in M3 that needs the network. Offline, it is unavailable and
  nothing else on the screen is affected.

---

## M3a — Routines, and the warm-up that is not one

Added 2026-08-08, from the `Redesign.dc.html` audit. Taken before M4 for the same
reason M3 was taken before M2: none of it needs an account or a network.

### US-28 — Warm up without logging it
See `adr/0021-a-warm-up-timer-that-records-nothing.md`. Constitution §1 forbids an
"activity type" abstraction, so the eight minutes on the treadmill get a **timer and
no row**. The ADR says "countdown/stopwatch" and "end-time"; the mock at `2a` says
"counts up". It is a **stopwatch** — the "end-time" phrasing is mechanism carried
over from ADR-0010, where storing the end is what makes a count-*down* survive being
killed. For a count-up timer the instant worth storing is the **start**.

- From an active session I can start a warm-up. It counts up from zero and shows
  elapsed minutes and seconds.
- It has no weight field, no rep field, and no exercise attached to it.
- Killing the app and reopening shows the time that has **actually** elapsed since I
  started, not zero and not a paused value.
- Starting a warm-up while one is already running does not reset it.
- Stopping it clears it. Nothing is written: no `session_exercises` row, no `sets`
  row, and no change to the session.
- It never appears in history, never counts toward session duration, and never
  appears in a session summary. Nothing is logged, so §2.4 has nothing to be
  dishonest about.
- It never blocks logging a set. The two-tap path of US-03 is unchanged, and
  `TwoTapSetLoggingTest` needs no edit.
- It is reachable from the session, not from a plan: a routine (US-29) cannot
  contain one.

### US-29 — Routines
See `adr/0020-routines.md`. A routine is a **name and an ordered list of exercises**,
and nothing else. It answers audit finding 01 — "Tuesday is Upper A" — while conceding
nothing on constitution §2.4, because a list of names is not a value and cannot be
dishonest.

- I can create a routine, give it a name, and add catalog exercises to it. The same
  exercise may appear twice, as it may in a session (US-02).
- I can rename it, remove a movement, reorder the movements, and delete the routine.
  Deleting it removes its items and touches no session, past or present.
- Starting a routine creates a session and copies its movements into it **in order**.
  From that moment it is an ordinary session: US-02a/b/c, US-03, US-04 and US-05a all
  work on it unchanged, and editing today never edits the routine.
- Each movement shows what I **actually lifted** last time, read from history and
  labelled as history (`Last Tue · 100 lb × 8`) — never as a target. A movement with
  no history shows its name and no numbers, the US-13 absence pattern.
- **There is no target to edit.** The editor offers a name, an order, add, and remove.
  It has no sets, reps, or load field, and it does not offer to add a warm-up (US-28).
- Starting a workout without a routine still works exactly as it does today. A routine
  is an additional path, never a required one.
- The two-tap path is untouched: `TwoTapSetLoggingTest` must pass **unedited**. ADR-0017
  names that as the signal this went wrong if it does not.
- Routines are device-local until M2, like everything else.

**Deliberately excluded, from ADR-0020's own "where this diverges from the mocks":**
planning a progression in advance ("next Tuesday I want 105"). Option 3 cannot express
it. If it turns out to matter it returns as its own story about a *single* next-session
target, which is a much smaller thing to get right than a general prescription model.

**Superseded on 2026-08-09 by US-30.** It turned out to matter. The bullet above saying
"there is no target to edit" is no longer true; every other bullet still is.

**Amended on 2026-08-09 by US-32.** "Starting a routine creates a session… From that
moment it is an ordinary session" is no longer the whole story: the session now carries
the routine's name and id as dead provenance, written once at start. It is still true that
editing the session never edits the routine, and the session is still ordinary in every
other respect — nothing reads the provenance back into the workout while it is in
progress. See `adr/0028-a-session-remembers-its-routine.md`.

### US-30 — Targets in a routine
See `adr/0027-routines-store-targets.md`, which supersedes ADR-0020 on this point and
only this point. The maintainer was offered the narrower single-next-session target the
paragraph above proposes and **declined it**: a plan that erases itself is not the saved
plan they wanted.

- I can give a movement in a routine a **target**: sets, reps and load. Each is optional
  on its own — "3 × 8, load unrecorded" is a plan, and so is a movement with no numbers,
  which is what every routine has today.
- I can edit a target and clear it. Editing one movement's target changes no other
  movement, and no session.
- Starting the routine **copies the targets into the session** along with the movements.
  Editing the routine afterwards does not change a session already started, and editing
  the session still never edits the routine (US-29, unchanged).
- With a target present, "Add set" prefills from it. With none, it prefills from my last
  performed set exactly as it does today (US-03).
- **A target is always rendered as a target**, visibly distinct from a performed number,
  and never substituted for one. Where both exist, both are shown — `Target 3 × 8 · 105 kg`
  and `Last Tue · 100 kg × 8` — never reconciled into a single figure.
- A movement with no target shows no target, rather than zeroes or a dash (the US-13
  absence pattern).
- **No target is ever written to `sets`.** What is logged is what I confirmed.
- **No derived number reads a target.** Volume (US-17), the trend (US-16), Epley, and
  personal records (US-18) all read `sets` alone. A planned 105 I never lifted must
  never become a PR.
- The two-tap path is untouched: `TwoTapSetLoggingTest` must pass **unedited**. A prefill
  is a prefill whatever its source.

### US-32 — A session remembers the routine it was started from
See `adr/0028-a-session-remembers-its-routine.md`. History reads "Sun 9 Aug, 13:53" today;
a session started from a routine has no way to say "Upper A" instead. This gives it one,
without reopening the join ADR-0020 and ADR-0027 both declined.

- Starting a routine records the routine's **name** on the session, copied once at start.
- History and the finish summary lead with the routine's name — `Upper A · Tue 4 Aug` —
  falling back to `Freestyle` when the session was not started from a routine.
- Renaming or deleting the routine afterwards does not change what an already-started
  session says it was called. The name is a copy, made once, the same way US-30's targets
  are.
- A session also carries the routine's **id**, written at the same moment as the name, but
  **read by nothing yet.** No screen resolves it, no query joins on it, and no derived
  number depends on it existing. It exists so that a future story (a "done N times" count,
  a "last run of this routine" comparison) does not have to leave a gap for every session
  logged before that story is written — the id cannot be added retroactively, because
  there is nothing to reconstruct it from.
- Sessions logged before this shipped show `Freestyle`, honestly: there is no routine to
  recover, so the absence is shown as an absence (US-13's pattern), not guessed at.
- The two-tap path is untouched: `TwoTapSetLoggingTest` must pass **unedited**.

---

## M3c — Backup and restore

See `adr/0034-backup-is-a-file-you-own.md`. Uninstalling deletes the Room database **and**
DataStore. The maintainer reinstalls several times a week to test on device and logs real
training in the same app, so today testing destroys training history.

These stories do **not** replace US-11, which stays at M2 and keeps meaning what it says: a
data-rights export paired with account deletion. This is the round trip that survives a
reinstall, and it needs no account, no backend and no network.

### US-40 — Export everything I have logged to a file
- From Settings, I can export my data to a file. I choose where it goes through the system
  file picker, so putting it somewhere cloud-synced is my decision, not the app's.
- The file is JSON, human-readable, and contains every session, exercise appearance, set,
  routine and routine item I have logged.
- It also carries my **unit preference** and **rest default**, so a restore does not leave me
  re-picking kg or lb.
- It does **not** contain the exercise catalog. The catalog ships in the APK and re-seeds
  identically on reinstall, because its ids are UUIDv5 over a fixed source slug — the same
  argument migrations v5 and v6 already make when they wipe and re-seed it.
- It does **not** contain a running rest countdown, an in-flight guided exercise, or a
  warm-up in progress. Those describe this install (ADR-0005) and end when it does.
- The file names its own format version, when it was exported, and which app build wrote it.
- Export works with no network, like everything else in M1 (constitution §2.2).
- Exporting changes nothing. Running it twice in a row produces two files with identical
  contents, and my data is untouched either way.

### US-41 — Restore a file after a reinstall
- From Settings, I can import a file I exported earlier, and get back **exactly** what I had:
  every workout, every set, every routine, every target, every personal record, and the same
  unit preference and rest default.
- Charts and history read the restored data the same as if it had never left. A restored
  personal record is still a personal record, because it is still the set that was performed.
- **Import replaces everything.** Before it runs, I am told in real numbers what I am about
  to lose and what I am about to gain — "Replace 12 workouts and 3 routines on this device
  with 9 and 2 from this file?" — and nothing happens until I confirm.
- **Import is all-or-nothing.** If anything goes wrong partway through, I still have exactly
  what I had before I started. There is no state where both the old data and the new are gone.
- **A file that cannot be fully restored is refused, and the app says what is missing.** If it
  references an exercise this build no longer has, nothing is written and my current data is
  untouched — so the same file still restores cleanly on a build that has it.
- A file from a **newer** format version than this build understands is refused by name,
  rather than partially read. A file missing a field added after it was written still
  restores; the fields it does have are enough.
- **Import is refused while a workout is running**, and says so. Finishing or discarding the
  workout first is the fix. A session must never disappear out from under the screen logging
  it (constitution §2.1).
- The two-tap path is untouched: `TwoTapSetLoggingTest` and `OneTapSetLoggingTest` must pass
  **unedited**.

### US-42 — Settings, and the two preferences that were never settable
The app has no Settings screen. `UnitPreference` is read at nine call sites and set at none;
US-05 promises a rest default "until changed in settings" and ADR-0008 promises a unit
preference. Both controls are built here, alongside the home export and import needed anyway.

- There is a Settings screen, reached from Train's header — a drill-down, **not** a fourth
  tab. US-36 and ADR-0030 settled the bottom bar at three items; this does not reopen it.
- I can switch between **kg and lb**, and every screen that shows a weight follows
  immediately: the session screen, set entry, the trend chart, weekly volume, routine targets
  and history. Nothing stored changes — kilograms remain canonical, per `data-model.md`.
- I can change the **default rest** between sets, and the next rest I start uses it. A rest
  already running is not retimed underneath me.
- Export and Import live here (US-40, US-41), with Import visibly unavailable, and the reason
  given, while a workout is running.

---

## M4 — Progress

### US-16 — Per-exercise trend
- For a chosen exercise, a chart of estimated 1RM over time, with top-set weight
  and total volume as switchable series.
- Estimated 1RM uses Epley and is labelled as an estimate.

### US-17 — Volume by body part
- Weekly training volume grouped by primary muscle group, for a chosen range.

### US-18 — Personal records
See `adr/0025-what-counts-as-a-personal-record.md`. A PR is **the heaviest load ever
lifted for a given exercise at a given rep count** — so bench at 5 reps and bench at 8
reps keep separate records, and every record is a set that actually happened. The rule
was the maintainer's call on 2026-08-08, after three sessions deferred the story rather
than invent it.

- A PR is detected on save and shown inline at the moment it happens. **Closed 2026-08-14**:
  both the two-tap sheet (`SetEntryController`) and the one-tap log button
  (`ActiveSessionViewModel.onLogNextSet`) run `DetectPersonalRecord` against the row actually
  written, surfacing the result as `SessionUiState.justSetRecord` — a filled banner shown above
  the rest countdown until the next set is logged.
- A PR list per exercise with dates. **Closed 2026-08-14**: the per-exercise progress screen
  (US-16) gains a "Personal records" section between the chart and the log — one row per rep
  count, ascending, each with the date it was set. Absent, not shown empty, for a movement
  never performed — the same rule the chart and the log both already follow.
- The **first** time a rep count is performed is not a record: a record requires a
  previous load at the same (exercise, reps) to beat. "You have not done this before" is
  a fact, not an achievement, and celebrating it makes the first workout wall-to-wall
  banners.
- **Equalling a record is not beating it.** Strictly greater, so repeating the same
  working weight every week does not fire a banner every week.
- **Bodyweight sets set no records.** There is no load to compare, and reading a missing
  weight as zero would tie every bodyweight set for last place forever (constitution
  §2.4, and the rule `ExerciseTrendOf` already applies).
- Detection must not delay the set being committed or the entry sheet closing.
  Constitution §2.1 makes the two-tap loop sacred, and this runs on the save path.

### US-19 — Honest empty states
- With no data, charts show a clear "not enough data yet" state rather than an
  empty grid or a zero line.
- With a single data point, no trend line is drawn and no trend is claimed.

### US-31 — Finish as a summary
Added 2026-08-09, from the `Redesign.dc.html` audit's finding *"showing the work is a
better check than asking are you sure."* Today ending a workout shows only a yes/no
confirm. This replaces what happens **after** confirming with a summary of what was
logged — and is the first place a personal record (US-18) is shown anywhere in the app,
now that its detection logic exists.

- **The confirm dialog is unchanged.** Tapping "Finish workout" still asks "Finish this
  workout? [Keep going] [Finish workout]" exactly as it does today. Only what happens
  after confirming is new — this story does not touch the ask-first step, a deliberate
  choice over the audit's more literal reading, made by the maintainer on 2026-08-09.
- Confirming shows a summary: duration, exercise count, set count, and total volume —
  the same figures history already computes (`SessionSummary`), so the two can never
  disagree.
- Any personal record set during the session is shown, one line per record: the
  exercise, the rep count, and the load — using US-18's existing detection rule
  unchanged (ADR-0025). No new definition of a record is invented here.
- **When a rep count is beaten more than once in one session, only the best is shown.**
  100 kg then 105 kg for 5 reps in the same workout is one record, not two — the member
  cares about the number they left with, not each intermediate step.
- **A session with no records shows no PR section at all** — absence rather than a "no
  records this time" line, the same pattern US-13 already uses for a movement with no
  history.
- A session discarded for having no sets (US-06's existing `Discarded` case) shows no
  summary. There is nothing to summarize, and the screen returns to home exactly as it
  does today.
- **This did not close US-18** at the time it shipped. The inline announcement at the moment a
  record is set, and a standing per-exercise list of records with dates, both closed later
  (2026-08-14) — see US-18. The finish summary remains a third, additional place a record is
  shown, not a replacement for either.

### US-33 — Progress replaces History
Added 2026-08-10, from the `Redesign.dc.html` audit's section 5. History was already a
finished list (US-06); this gives the same screen a reason to open it beyond "what did I
do" — "am I getting stronger" — and renames the tab to say so.

`GymTrackerNavHost.kt`'s own comment on the `WeeklyVolume` destination named the trigger
for this rename in advance: "the tab becomes Progress and gains its charts when M4
lands," gated on PR detection (US-18) and a time range selector. US-18 shipped
2026-08-09 (ADR-0025); the range selector has not, and remains its own unchecked item in
`roadmap.md`. The maintainer chose to rename now rather than wait for it — this section
records that as a deliberate call, not an oversight.

- The tab's label changes from **History** to **Progress**, in the bottom bar and as the
  screen's own title. What was the screen's title, "Past workouts," becomes a section
  heading above the session list rather than disappearing — the list itself, its
  ordering, and US-06a's delete-and-undo are all unchanged.
- Above that list, a new top section leads with **one lift's estimated 1RM**: the
  current estimate, and the change over the last 8 weeks, using US-16's existing Epley
  estimate and `ExerciseTrendOf`. No new estimation rule is invented.
- **The lift is chosen without asking**, at open time: the exercise most recently
  actually trained, meaning the first appearance (by position) in the newest finished
  session that has at least one set logged against it. An appearance a routine copied in
  but the member never reached (US-29) is skipped in favour of one that was performed.
  If the newest session has nothing performed in it at all, the section says so (US-19)
  rather than reaching back through older sessions for something to show — the section
  answers "since you last trained," not "the last time you trained something."
- **There is no lift switcher on this tab in this pass.** Tapping the section opens the
  same per-exercise trend screen US-16 already has, for that same exercise, with its
  existing series toggle and chart. Featuring a *different* lift here is reached the way
  it always has been — Exercises → an exercise's detail → "See progress" — not a new
  control on Progress. Revisit if that turns out to matter enough to ask for.
- **"Weekly volume by muscle" (US-17) becomes a labelled row in this section**, styled
  as a row rather than a bare link, in place of the `TextButton` History carried. The
  destination it opens is unchanged.
- **A "PR" badge on session rows is explicitly deferred, not built here.** The audit
  asked for one on rows containing a personal record. The only existing way to ask "did
  this session set a record" is `PersonalRecordsAchievedIn`, which was built for
  `FinishSummaryScreen` — one session, evaluated once, right after it happened — and
  reads the member's *entire* session history to answer that for even one row: for
  a badge on every visible row of a 200-session list, that is O(rows × total history),
  not O(rows). Doing this honestly needs a purpose-built read — e.g., one pass over
  every set in time order, per (exercise, reps), marking the session that first reached
  each new maximum — which is a real algorithm with its own correctness questions ADR-0025
  had to answer once already, not a one-line addition to this story.
- The two-tap path is untouched: `TwoTapSetLoggingTest` must pass **unedited** — nothing
  here touches the session screen.

### US-34 — Exercise log
Added 2026-08-11, from the `Redesign.dc.html` audit's `Exercise log` frame. US-16's
per-exercise screen answers "am I getting stronger" with a chart; this adds the answer to
"what did I actually do" for that same movement, on the same screen.

- Below US-16's chart (or its absence state), a log: one row per finished session that has
  at least one set logged for this exercise, **newest first** — a log reads like history,
  the opposite direction from the chart, which reads left to right in time.
- Each row shows the session's date, its best set and estimated 1RM (reusing US-16's
  existing Epley estimate — no new estimation rule), and the individual sets performed
  that session (weight, reps), the same figures `WorkoutDetailScreen` already shows for a
  whole workout, scoped here to one exercise across every workout it appears in.
- Sets from an appearance a routine copied in but the member never reached are already
  excluded, because a session with nothing performed for this exercise contributes no row
  — the same rule `ExerciseTrendOf` applies to the chart, so the two can never disagree
  about which sessions counted.
- A bodyweight set shows its reps with no weight and contributes nothing to that row's
  best set or estimate, per constitution §2.4 — the rule US-16's chart already follows.
- **A row opens the workout it came from.** Added 2026-08-14: `ExerciseLogEntry` carries the
  session's id, and tapping a row navigates to the existing `WorkoutDetail` destination — the
  same one `HistoryScreen`'s own rows open.
- **Still deferred:** jumping from a logged set anywhere else in the app straight to this
  screen. The audit's frame implies it, but doing it well means deciding what a tap on a set
  means on every screen that already gives a set a tap target of its own
  (`WorkoutDetailScreen`'s opens the editor), which is a real design decision and not one
  this story invents by default. This screen remains otherwise reached exactly as US-16
  already reaches it — from the catalog, or Progress's top section.
- With nothing ever performed (US-16's `NoData` state), the log section is absent rather
  than shown empty — the same absence pattern the chart itself already uses.
- The two-tap path is untouched: `TwoTapSetLoggingTest` must pass **unedited** — nothing
  here touches the session screen.

### US-35 — Log the prefilled set in one tap
Added 2026-08-11. See `adr/0029-the-session-screen-is-a-ruled-sheet.md`, written against the
`Redesign.dc.html` design bundle's `1a Session mid-set` and `1a Session resting` frames. The
audit's finding — the session screen is a card stack when the design is a ruled sheet — leans
on this: the design's one filled control per screen is the log button, and it has to actually
log, not just open the sheet US-03 already has.

- The session screen's one primary action states exactly what it will log —
  `LOG SET 3 · 100 lb × 8` — and logging it is **one tap**, writing the set directly with no
  sheet in between.
- This is **additional**, not a replacement. `Add set` sits beside the log button, opens the
  same stepper sheet US-03 already built, and is for when a number needs to change before
  logging — not for every set. The two-tap path (`Add set` → prefilled sheet → `Save set`)
  keeps its behaviour and its label unchanged. The redesign's frames call this control
  `ADJUST`; the implementation keeps `Add set` because `TwoTapSetLoggingTest` matches that
  string literally — see ADR-0029's note on this for the reasoning, which is the same
  constraint `GymButtons.kt`'s `ButtonLabel` already documents for why labels are not
  visually uppercased.
- The prefill the log button writes is exactly what `Add set` would have opened showing — the
  two controls can never disagree about what the next set is, because both read the same
  prefill.
- During rest, the log button stays live and its label changes to
  `LOG SET 3 — DON'T WAIT`, so an early set never needs the rest timer skipped first
  (ADR-0023, unchanged).
- `TwoTapSetLoggingTest` must pass **unedited** — it exercises `Add set` → `Save set`, a path
  this story adds to, not alters.

---

### US-36 — Three tabs, and a routine on Train home
Added 2026-08-12. See `adr/0030-three-tabs-and-a-hand-built-bottom-bar.md`, written against
`Redesign.dc.html`'s section 2a. Closes the redesign audit's nav-bar-pill deviation and finding
01 ("a session has no plan") on Train home specifically.

- The bottom bar shows exactly three destinations — **Train**, **Exercises**, **Progress** —
  and its selected-item indicator is square, not a pill: `NavigationBarItem`'s own indicator
  token cannot be overridden (confirmed against the compiled `material3-api.jar`), so this is a
  hand-built bar from primitives, not a restyle.
- **Routines is reached one way only**: an outlined button labelled `Routines` in the Train
  header, present whether or not a workout is running, whether or not the member has any
  routines yet.
- **Train home, with no workout running, names the routine due next** — whichever the member has
  gone longest without doing, or has never done at all — and offers `Start <name>` beside the
  existing `Freestyle` action. With no routines at all, the screen is unchanged from today
  ("Start workout," no routine named).
- **Never fabricated.** There is no weekday or split model in the data, so "due next" means
  "least recently performed" — an honest read of history, not an invented schedule
  (constitution §2.4). The exact wording stays clear that it is a suggestion, not a plan the app
  is asserting exists.
- Starting from Train home behaves exactly as starting the same routine from the Routines screen
  already does (`StartSessionFromRoutine`, US-29): if a workout is already running, nothing is
  copied into it, and the running workout's own state does not change to reflect the tap that
  was ignored.
- `TwoTapSetLoggingTest` and `OneTapSetLoggingTest` must pass **unedited** — neither exercises
  the no-session state this story changes.

---

### US-37 — Set entry prefers history over a target
Added 2026-08-13. See `adr/0031-set-entry-prefers-history-over-a-target.md`, written against
`Redesign.dc.html`'s section 2b. Supersedes only US-30's target-first prefill order — every
other part of US-30 (a target is always labelled, never merged with history) is unchanged.

- **Precedence for weight and reps: the last set actually performed on this exact movement,
  then the routine's target for it, then nothing.** Reps additionally fall back to 12 when
  neither source has a number; weight never falls back — an invented load is worse than an
  empty field.
- **Weight is never inherited from a different exercise.** Both sources are already scoped to
  the one movement being entered.
- **Sets falls back to 3 once a target exists to floor from; with no target at all it stays
  ADR-0009's original 1.** A universal floor was tried first and broke
  `TwoTapSetLoggingTest` on-device — confirming a set for a brand-new exercise logged three
  rows instead of one. Never a claim about today's count either way, from history or otherwise
  — ADR-0009's rule is unchanged, only narrower than the design's literal text.
- When the prefill came from history, a muted line under the steppers says so — "Prefilled from
  last Tuesday — 100 lb × 8" — so the number reads as a target to beat. Nothing is added when
  the prefill came from a target; the target already renders labelled as one.
- One function, `ResolveSetPrefill` (`:core:domain`), carries the rule; both call sites
  (`SetEntryController.open`, `ActiveSessionViewModel`'s one-tap prefill) use it rather than
  each inlining their own merge.
- **A target is still always rendered as a target, and never substituted for one** — US-30's
  labelling rule is unchanged; only which value fills the box changes.
- `TwoTapSetLoggingTest` and `OneTapSetLoggingTest` must pass **unedited** — confirmed on-device,
  not just asserted here.

---

### US-38 — Progress rows earn a hierarchy
Added 2026-08-14. See `adr/0032-progress-rows-earn-a-hierarchy.md`, written against
`Redesign.dc.html`'s section 5 and audit finding 06. Closes the one bullet US-33 explicitly
deferred: *"a 'PR' badge on session rows… needs a purpose-built O(sets) read."*

- **A session row is two lines, not three.** Line one: the routine name (or `Freestyle`),
  weight ExtraBold, with `· Tue 4 Aug` appended in a lighter weight — one line, two weights, not
  a separate date row. Line two: the existing duration/exercises/sets/volume summary, muted and
  smaller. One hierarchy, not four numbers at equal weight.
- **A session that set a personal record carries a `PR` badge** — outlined, never filled, in the
  accent colour. Answered by `SessionsWithRecords` (`:core:domain`), a purpose-built read: every
  loaded set the member has ever logged, read once, grouped by (exercise, reps), and walked in
  chronological order to find where each group's running best was first strictly beaten — the
  same rule `DetectPersonalRecord` already defines (ADR-0025), computed as one pass rather than
  one query per set.
- **The duration/volume computation is unchanged.** `"3m · 34 sets"` is what bulk set entry
  (ADR-0009) against a real timestamp window actually produces — the number is honest, and this
  story's smaller, muted metric line is the fix for how it reads, not a change to how it is
  computed.
- The row itself moves off `ListItem` onto a plain ruled `Row`, matching ADR-0029's ruled-sheet
  precedent — a `GymDivider` beneath each row rather than `ListItem`'s implicit surface.

### US-39 — The guided exercise screen is one exercise, sized for the bench
Added 2026-08-14. See `adr/0033-the-guided-screen-is-one-exercise-at-a-time.md`, written against
`Redesign.dc.html`'s `1b Focus mid-set` and `1b Focus resting` frames — the direction ADR-0029
rejected for the *multi*-exercise session screen, and the only frames in the bundle drawn for a
screen that holds one exercise at a time, which is what guided mode (US-05a) already is. Reported
by a member still seeing "Go", a plain rep field and a "Finish set" button on Material defaults.

- Resting, the countdown fills a full-width accent block, and the movement, its load, its rep
  count and which set it is read as **one line inside that block** — not as separate lines on a
  bare ground.
- Mid-set, the load and rep count are the screen's largest element, and the number shown is the
  one that will be written — it tracks the field, not the target.
- The rep count keeps its `+`/`−` steppers and stays typeable; US-05a's "editable before I finish
  each set" is unchanged and its test (`GuidedFlowTest`) is unedited.
- The primary action says `Log set {n}`, naming the set it will write — the same verb the session
  screen's one-tap button uses for the same operation.
- Set progress is one bar per target set, in the accent at the three weights the session header's
  own segment bar already uses. No third accent colour is introduced.
- Nothing on the screen is centred; every label is flush left.
- The start dialog (`GuidedSetupDialog`) is unchanged, and `TwoTapSetLoggingTest` /
  `OneTapSetLoggingTest` must pass **unedited** — nothing here touches the session screen or the
  two-tap path.

### US-44 — How long I spent between sets
Added 2026-08-17, out of `Redesign.dc.html` Turn 3 (frame `3g`). Two different numbers both read
as "per-set time": time-under-load needs a start event only guided mode has and a column `sets`
does not have; the set-to-set interval is the difference between two `performed_at` values
already in the table, needs no schema change, and is correct retroactively on every session ever
logged. This story builds only the second number. See `adr/0036-rest-is-ink-and-red-is-the-thing-
you-tap.md` for why it replaces the set row's checkmark rather than sitting beside it.

- Each logged set shows the time since the previous set **in the same session** — intervals span
  movements on purpose, since the walk to the next machine is what explains a long session, not
  just the reps themselves.
- The first set of a session shows no interval; there is nothing before it to measure from.
- An exercise's header shows the average interval across its own logged sets.
- A session logged before this shipped shows correct intervals the first time it is opened —
  the figure is a read over `performed_at`, not something written at log time.
- Sets logged within the same few seconds of each other (ADR-0009 bulk entry) show no interval
  rather than `+0:00` — a near-zero gap from typing several sets at once is not information
  about the workout, and rendering it invites the same "reads as a bug" complaint the redesign
  audit already made once about the history summary line.
- Constitution §2.4: an interval is never estimated or interpolated — absent, not guessed, when
  there is no earlier set to measure from.

### US-45 — Switch back to an exercise the machine took away
Added 2026-08-17, reported live during testing. See `adr/0037-choosing-which-exercise-is-open.md`.
Once a set is logged against a later exercise, an earlier untouched one had no row, no button,
and nothing to tap anywhere on the session screen — the only ways back were destructive (delete
every set on the later exercise, or remove it, US-02c). Not the same story as the roadmap's
undesigned "swap a movement for a substitute exercise" — this is navigation between exercises the
session already includes, in either direction.

- Every exercise already in the session is reachable and tappable from the session screen at all
  times, regardless of its position in the plan and regardless of whether it already has sets
  logged.
- Tapping an exercise other than the currently open one makes it the open exercise: its own set
  list, target, `Start exercise`/`Remove`, and the one-tap `LOG SET` button — the same full
  experience today's current exercise gets, not a shortcut into a sheet.
- The exercise switched away from loses nothing — its logged sets stay exactly as they were, and
  it is reachable again the same way, by tapping it.
- The choice is sticky: it stays open until the member taps a different exercise, not until the
  next set logged anywhere snaps the screen back to a derived default.
- US-03's two-tap ceiling and US-35's one-tap log button are unchanged for a member who never
  switches exercises — `TwoTapSetLoggingTest` and `OneTapSetLoggingTest` pass unedited.

### US-52 — One inset, one spacing vocabulary
Added 2026-08-29, from the `Redesign.dc.html` audit's Turn 5, file `01-insets-and-spacing.md`.
See `adr/0044-a-legal-spacing-vocabulary-and-one-inset-consumer.md`. Foundational and
presentation-only — the other Turn 5 files (`02`, `03`, `04`, `06`) read this story's spacing
vocabulary; file `05` (set corrections) is deferred until M2's sync engine has landed in every
DAO call site, since it is the one Turn 5 file that touches the set DAO.

- The gap between the bottom of the status bar and the first content pixel is measured, not
  assumed, on the session screen, the Progress screen, and a history detail screen, each at
  393dp — and is ≤ 40dp on all three once this story ships. If the measured gap was already
  ≤ 40dp before any code changed, that is reported as such rather than treated as a bug to fix.
- No screen, app bar, or panel in `feature/**` consumes `WindowInsets.statusBars` (or
  `.systemBars`) a second time beyond the root `Scaffold` — `LiveHeartRateChip`'s own status-bar
  padding is the one named exception (US-47), since it deliberately floats a reading in that
  area on every screen.
- `GymDimens.PrimaryAction` is 64dp (down from 72dp); `GymDimens.LogRowHeight` no longer exists
  as a separate token. Every `GymDimens` token outside this pair — `PhotoHeight`, `ChartHeight`,
  `Thumbnail`, `MascotHome`/`MascotInline`, `CompactScreenPadding`, and the rest — is unchanged.
- A build-time check fails on a `.dp` literal written directly in `feature/**`, inside a
  `Spacer` height, a vertical `Arrangement.spacedBy`, a `.height(...)` modifier, or vertical
  padding, whose value is not in `{2, 4, 12, 20, 32, 44, 56, 64, 80}`. Scoped to vertical
  spacing and row/element height — the two categories file `01` itself names — not every `.dp`
  literal in the module; icon sizes, stroke widths, and horizontal-only padding are untouched. A
  named `GymDimens` token is exempt regardless of its value.
- The screen gutter (left and right padding) measures 20dp on the four main screens (Train,
  Exercises, Progress, and a history detail screen).

### US-53 — The warm-up is a step, not a strip
Added 2026-08-29, from the `Redesign.dc.html` audit's Turn 5, file `02-warmup-step.md`. See
`adr/0045-the-warm-up-becomes-a-full-screen-step.md`. ADR-0021 ("a warm-up timer that records
nothing") is unchanged by this story — nothing about what is or isn't recorded moves.

- Starting a warm-up replaces the session screen with a dedicated, full-screen step — a count-up
  timer and nothing else logging-related. There is no state where the running warm-up and the
  session's set list, target, or log button are visible together.
- `SKIP`, in the step's header, and `DONE — START LIFTING`, the step's one primary action, both
  end the warm-up and return to the session — consistent with ADR-0021, since stopping the timer
  discards it the same way regardless of which button ends it.
- The next exercise in the session's plan, if any, is shown on the step under a `THEN` label —
  informational only; tapping it does nothing, and it is absent (not blank) when the session has
  no exercises yet.
- The step never shows a step count ("1 of 2" or similar) — this build has no cool-down step to
  count against, and showing one would claim a feature that doesn't exist.
- US-28's constraints are unaffected: the warm-up is still reachable from the session, not from a
  plan; starting one while one is already running still doesn't reset it; and it still never
  blocks logging a set — `TwoTapSetLoggingTest` and `OneTapSetLoggingTest` pass unedited.

### US-54 — The session screen says whether it's following a plan
Added 2026-08-30, from the `Redesign.dc.html` audit's Turn 5, file `03-session-screen.md`
(sub-pieces 1 and 2 of that file — see
`adr/0046-the-session-screens-own-plan-vs-freestyle-contract.md` for why this is the main session
screen's own concept, not a merge with `GuidedExerciseScreen.kt`, which this story does not
touch).

- A session backed by a routine (`SessionProgress.orderIsAPlan`) whose open exercise carries a
  set target shows a kicker naming its position in both the plan and the set:
  `EXERCISE {n} OF {total} · SET {logged + 1} OF {target}`.
- The open exercise otherwise shows `CURRENT`, unchanged from a plain position count — this
  covers both a freestyle session and a plan-backed session whose open exercise has no target of
  its own.
- Logging past the exercise's own planned set count is absorbed silently, not flagged or
  blocked: the kicker drops the exercise-position and `OF {target}` parts and reads `SET {n} ·
  EXTRA` instead. The plan is a suggestion; nothing prevents or warns about exceeding it.
- The section listing every other exercise reads `THEN` when the session is plan-backed, `ALSO
  TODAY` otherwise. Which exercises appear there is unchanged (US-45) — this is a label, not a
  filter.
- A `GUIDED` or `NO PLAN` tag sits beside the session title, matching the same `orderIsAPlan`
  signal the kicker and section label read.
- `Remove` and `Start exercise` are unchanged in position and function — restyled only if this
  story's own rules require it, not deleted, and not gated behind any other story.
- No change to what logging a set does, what `nextLoggableSet` computes, or the rest timer.
  `TwoTapSetLoggingTest`, `OneTapSetLoggingTest`, and `SwitchingExercisesTest` pass unedited.

---

## M4a — Rep, animated

### US-43 — Rep, animated, inside the app
Added 2026-08-15. See `adr/0035-rep-appears-inside-the-app.md`. ADR-0026 named the mascot and
built the launcher icon, and deliberately left "does Rep appear inside the app" open rather than
decided by drift. This closes that question for the generic running figure; the seven
machine-specific placards from the same drawing set are explicitly out of scope.

- Rep, running, plays on Train home in the empty space above the start-workout actions, on the
  warm-up panel while it is running, beside the exercise name on exercise detail, and on the
  guided exercise screen's rest and complete states.
- **Not** on the guided screen while a set is being logged (`MidSetHeader`, `GuidedControls`) —
  constitution §2.1's two-tap path is untouched, and `TwoTapSetLoggingTest` /
  `OneTapSetLoggingTest` / `GuidedFlowScreenTest` pass **unedited**.
- **Not** in the exercise-detail photo slot. That slot stays empty for the 866 of 873 exercises
  with no bundled image, per US-13's absence rule — Rep sits beside the name, never as a
  stand-in for missing media.
- With system animations off (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`), Rep renders as a
  still pose rather than nothing and rather than hanging.
- Rep's sweatband reads correctly in both light and dark mode — measured, not eyeballed: at
  least 3:1 against every surface it is drawn on, per ADR-0035.
- `GymColorSchemeTest` passes unedited: the mascot's colours are never added to `ColorScheme`.

---

## M4b — REP demonstrates exact machines

Runs beside M2 under ADR-0041: bundled, schema-neutral instruction UI with no logged-data or
sync changes. "Line graph" here means an animated line-art technique demonstration, not a data
chart and not automatic rep counting.

### US-50 — REP demonstrates our exact leg press

- A guide exists only for catalog exercise id `492fa83f-3134-5d16-8b03-386dada93dad`
  (Leg Press), selected by exact UUID rather than name, alias, equipment or body part.
- The bundled guide names the actual manufacturer and model and carries Setup, Move and
  Checkpoints cues approved against that model's manufacturer manual and by a designated human
  trainer or machine maintainer. The source reference, reviewer and review date are not null.
- Exercise detail shows a `Form guide` section after the movement photo when the exact reviewed
  guide exists: model label, REP line animation, and the three concise cue groups. The original
  catalog instructions remain available below it as `Full instructions` rather than being
  overwritten.
- Guided setup offers a secondary `Form guide` action. It opens the same reviewed guide and
  returns with weight, reps and set count unchanged. There is no guide animation while a set is
  active, and `TwoTapSetLoggingTest`, `OneTapSetLoggingTest` and `GuidedFlowScreenTest` remain
  unedited.
- Play, Pause and Replay are operable and have semantics that describe the demonstrated motion.
  With system animations disabled, labelled start and end poses and the direction of movement
  render instead of an unexplained frozen frame.
- With an unknown exercise id, missing review metadata or an unreviewed machine variant, no guide
  section or action renders — zero-height absence, never a generic substitute.
- The guide is fully offline. Watching it never counts a rep, changes a form field or writes to
  Room/DataStore.
- Pilot exit requires a real session on the exact leg press, manual verification and recorded
  reviewer sign-off, in light/dark mode, at 200% font, with TalkBack and animations on/off.

### US-51 — Extend the reviewed guide to the other six machines

US-50's acceptance criteria apply independently to each explicit mapping below. Each guide is a
small reviewable change; a missing or failed review leaves only that guide absent.

- Leg Extensions — `a891e1cb-7f5d-5cc8-aed1-ce306ca67343`
- Leverage Chest Press — `0311e6bf-4717-5e7f-b9c3-d7232e22df55`
- Leverage Shoulder Press — `10fec4c1-2a59-50cd-bbf0-af1aee9c6fe6`
- Seated Cable Rows — `c5db7545-f496-5bc0-b69e-ed1bf36d2aed`
- Seated Leg Curl — `32e4fe44-fd87-515a-9836-68304520c90c`
- Wide-Grip Lat Pulldown — `16eb68ba-df84-5d85-bc08-4ae204616974`

## M5 — Health Connect (optional)

### US-20 — Availability
- Given Health Connect is unavailable on the device, the app shows no health UI,
  makes no requests, and functions completely. **No prompt, no nag, no banner.**
- Given it is available but the member is a Teen whose account cannot use it, the
  behaviour is identical to unavailable.

### US-21 — Opt in
- Health integration is **off by default** for every member.
- Turning it on requests only the permissions actually used, one at a time,
  explaining what each is for.
- Denying any permission leaves the rest of the app fully working.

### US-22 — Session metrics
- With permission granted, ending a session reads heart rate and active calories
  for the session window and stores avg HR, max HR, and active kcal on the session.
- If no samples exist for the window, the fields are left null and displayed as
  "not recorded" — never zero, never estimated.

### US-23 — Revoke
See `adr/0040-revoking-health-access-clears-every-column.md`.

- Turning the toggle off stops all reads immediately: the next session to end reads
  nothing. Turning it off is not conditional on answering anything else.
- If any of my workouts carry imported metrics, turning it off offers to delete them,
  naming the real number — "Delete health metrics from 7 workouts?" — the same way
  US-41's import confirmation names real counts.
- If none of my workouts carry any, nothing is offered at all. No dialog, no banner:
  an offer to delete nothing is the nag US-20 forbids.
- Accepting clears the average heart rate, peak heart rate, active calories **and the
  source marker** on every one of my workouts. Afterwards those workouts show no health
  line at all — not "not recorded", which under US-22 means a read happened and found
  nothing, but absent, exactly like a workout logged before I ever opted in.
- Nothing else about a workout changes: it keeps its start, its end, its routine, its
  exercises and every set. My sets are mine; the metrics were borrowed.
- Declining deletes nothing, and is not remembered. Turning the toggle off again later,
  with metrics still there, offers again.
- It only ever touches my own workouts, never another member's.
- A backup file I already exported still holds the metrics — it left this device and the
  app cannot reach it. Importing that file restores them, exactly as US-41 promises, and
  the toggle stays off, so nothing new is read (ADR-0038).
- Exporting after deleting produces a file with no metrics in it.

---

## M5a — Live heart rate from a paired band

Stories: US-46 … US-49. Read `specs/adr/0039-a-live-band-is-not-health-connect.md`
first — this is not Health Connect, and not the same read as US-22.

### US-46 — Pair a band
- Given the device is below API 31, has no Bluetooth adapter, or the toggle is off,
  Settings shows no live-heart-rate UI at all. **No prompt, no nag, no banner** —
  the same absence rule US-20 established for Health Connect.
- Live heart rate is **off by default** for every member.
- Turning it on requests `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`, one at a time,
  each with a plain-language reason shown first, then scans for nearby devices
  advertising the Bluetooth Heart Rate service and lets the member choose one.
- Denying either permission leaves the rest of the app fully working.
- The chosen device is remembered device-locally (not synced, not backed up).

### US-47 — Live heart rate, everywhere
- While a reading exists, the current BPM is visible from every screen in the app,
  not just the session screen.
- When no reading exists — unpaired, disconnected, unavailable, or the toggle off —
  the element is absent entirely: zero height, never a dash, never a zero.
- The reading never blocks or delays anything in the core logging loop
  (constitution §2.1): appearing or disappearing must not shift "Add set" off
  screen or add a tap to logging a set.

### US-48 — Connection honesty
- "Searching" (connecting, no reading yet) and "Lost" (was connected, signal
  dropped) are two distinct, visibly different states — neither is shown as if it
  were a live reading.
- A reading older than a defined staleness threshold is never displayed as current
  (constitution §2.4); once stale, the display moves to "Lost".

### US-49 — Unpair
- Turning the toggle off stops scanning and drops any open connection immediately.
- Nothing from this feature is ever persisted (ADR-0039), so unlike US-23 there is
  nothing to offer to delete — turning it off is instant and complete.

---

## M6 — AI coaching

### US-24 — Session suggestion
- I can request a suggestion for today; the response includes recommended
  exercises with target sets, reps, and load, grounded in my logged history.
- The response is labelled as AI-generated.
- Suggested load never exceeds the guardrail in constitution §6.

### US-25 — Find a gap
- The coach identifies under-trained muscle groups from the last 8 weeks and
  proposes specific machines from the catalog to address them.

### US-26 — Explain an exercise
- I can ask how to perform a catalog exercise and get plain-language form cues,
  with a standing caveat to ask gym staff for in-person form checks.

### US-27 — Guardrails
- Prompts seeking weight-loss targets, body-composition assessment, appearance
  commentary, injury diagnosis, or rehab programming return a refusal plus a
  suggestion to consult a professional. Covered by an adversarial test suite.
- The refusal is friendly and does not lecture; one sentence, then move on.
- A network or function failure degrades to "coaching unavailable" without
  affecting any other feature.
