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
  appended to the session.
- Recently used exercises appear first, before alphabetical results.
- With no history yet, a curated set of common gym movements appears above the
  alphabetical results, so a new member does not meet 873 exercises in alphabetical
  order. History outranks it as soon as there is any (ADR-0007).
- Starter exercises show a bundled photo of the movement. Exercises without one show
  no image rather than a placeholder; the rest of the catalog gets media at M3.
- The same exercise may appear twice in one session.

### US-03 — Log a set  ← the story that matters most
- Given an exercise in the active session, when I tap "Add set", weight and reps
  are **prefilled from my most recent set of that exercise** (any session).
- When no prior set exists, the fields are empty and focused.
- Confirming a set requires **no more than 2 taps** when the prefilled values are
  correct. Asserted by an instrumented test that counts interactions.
- Weight accepts one decimal place; reps are whole numbers ≥ 1.
- The set is persisted locally before any UI transition. Killing the app
  immediately after does not lose it.

### US-04 — Correct a mistake
- I can edit weight, reps, or RPE of any set in the current session.
- I can delete a set, with undo available for 5 seconds.
- Editing a past session's set is possible from history and recalculates PRs.

### US-05 — Rest timer
- After logging a set, a rest timer starts automatically at my configured
  default (90 seconds until changed in settings).
- The timer keeps running when the app is backgrounded and notifies at zero.
- If notification permission is denied, the timer still runs and displays
  in-app. The permission is requested once and never re-prompted.
- I can dismiss or skip it. It never blocks logging the next set.

### US-06 — End a session and see history
- Ending a session sets `ended_at` and returns me to home.
- History lists sessions newest-first with date, duration, exercise count, and
  total volume.
- A session with no sets is discarded rather than saved.
- A session finished via the stale-session prompt (US-01) gets `ended_at` = its
  last set's `performed_at`.

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

---

## M3 — Catalog and media

### US-12 — Browse and filter
- Filter the catalog by body part and by equipment; combine both.
- Search matches on name and common aliases.

### US-13 — See how a machine works
- The exercise detail screen shows a looping GIF (or a video, or text if neither).
- It lists primary and secondary muscles worked.
- It shows numbered instruction steps.
- Media already viewed is available offline.

### US-14 — Link out to a video
- Where a YouTube link exists, tapping it opens the external browser or app.
- No embedded player, no third-party SDK.

### US-15 — Record our own gym's equipment
- I can record or pick a short clip and attach it to a catalog exercise for my
  household, overriding the stock media for us only.

---

## M4 — Progress

### US-16 — Per-exercise trend
- For a chosen exercise, a chart of estimated 1RM over time, with top-set weight
  and total volume as switchable series.
- Estimated 1RM uses Epley and is labelled as an estimate.

### US-17 — Volume by body part
- Weekly training volume grouped by primary muscle group, for a chosen range.

### US-18 — Personal records
- A PR is detected on save and shown inline at the moment it happens.
- A PR list per exercise with dates.

### US-19 — Honest empty states
- With no data, charts show a clear "not enough data yet" state rather than an
  empty grid or a zero line.
- With a single data point, no trend line is drawn and no trend is claimed.

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
