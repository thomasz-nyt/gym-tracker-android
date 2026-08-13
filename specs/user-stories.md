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
- Given an exercise in the active session, I can start it. Weight, reps and a sets
  count are prefilled exactly as in US-03, and confirming begins the exercise.
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

### US-11 — Export and delete
- I can export all my data as JSON to a file.
- I can delete my account; all my rows are removed and this is verified by a test.

### US-15 — Record our own gym's equipment
Moved here from M3 on 2026-08-01: it is written in terms of a household, and there is
no household until this milestone (ADR-0014).
- I can record or pick a short clip and attach it to a catalog exercise for my
  household, overriding the stock media for us only.
- The clip is stored on the device and works offline; sharing it with the household
  goes through the same sync engine as everything else in this milestone.

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

- A PR is detected on save and shown inline at the moment it happens.
- A PR list per exercise with dates.
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
- **This does not close US-18.** The inline announcement at the moment a record is set,
  and a standing per-exercise list of records with dates, are both still unbuilt. This
  is a third, additional place a record is shown, not a replacement for either.

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
- **Rows are not tappable in this pass.** Opening the workout they came from, or jumping
  from a logged set anywhere in the app straight to this screen, are both left for a
  follow-up: the audit's frame implies the second, but doing it well means deciding what a
  tap on a set means on every screen that already gives a set a tap target of its own
  (`WorkoutDetailScreen`'s opens the editor), which is a real design decision and not one
  this story invents by default. This screen remains reached exactly as US-16 already
  reaches it — from the catalog, or Progress's top section.
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
- Turning the toggle off stops all reads immediately and offers to delete
  previously imported metrics.

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
