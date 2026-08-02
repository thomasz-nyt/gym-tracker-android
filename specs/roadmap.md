# Roadmap

Milestones are sequential. **Do not start a milestone before the previous one's
exit criteria are met.** Each milestone ends in something installable that a family
member could actually use.

Current milestone: **M1** — M0's boxes are all ticked and its exit criterion (green CI on a
PR) was met at PR #4.

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
- [ ] Edit and delete a set
- [x] Rest timer between sets
- [x] End a session, and the session history list (US-06)
- [x] Delete a past workout, with undo (US-06a, ADR-0012)
- [x] Unit preference (kg / lb), stored per user, converted at the edge only. Both units are shown (ADR-0008)
- [x] Add several exercises without leaving the search (US-02a)
- [x] Newest exercise first in the active session (US-02b)
- [x] Remove an exercise from the session, with undo (US-02c)
- [ ] Guided flow through one exercise (US-05a, ADR-0013)
- [ ] Workout detail from history (US-06b)

The last five were added 2026-08-02 from a real session on the gym floor, the same
way US-06a and ADR-0011 arrived. They are ergonomics on the core loop, not new
scope: none adds a table, and the database stays at version 5. A sixth idea from
that session — sensor-based rep counting — is **not** here on purpose; it is
deferred in `adr/0014-sensor-assisted-rep-counting.md` because constitution §2.4
forbids logging an inferred value.

**Exit:** two-tap set logging measured and asserted in an instrumented test. You
personally log three real workouts on your own device without wanting to fix
anything mid-set.

---

## M2 — Accounts, household, sync

Stories: US-07 … US-11

- [ ] Supabase project, migrations in `supabase/migrations/`
- [ ] Auth: sign up, sign in, sign out
- [ ] `households` + `profiles`; invite a member by code
- [ ] RLS policies on every table + pgTAP tests proving cross-household reads fail
- [ ] Sync engine: local-first, WorkManager, last-write-wins per row with
      `updated_at`, conflict cases documented
- [ ] Offline queue survives app kill
- [ ] Data export (JSON) and account deletion

**Exit:** two devices, two family members, same household, log offline, reconnect,
converge. A pgTAP suite proves isolation.

---

## M3 — Exercise catalog and media

Stories: US-12 … US-15

- [ ] Catalog browse and search by name, body part, equipment
- [ ] Body-part tags rendered on the exercise detail screen
- [ ] GIF playback via Coil; mirrored into Supabase Storage, never hotlinked
- [ ] Optional YouTube link-out (external browser, no embedded SDK)
- [ ] Family-recorded clips: record/upload a clip for a specific machine at your gym
- [ ] Step-by-step text instructions, readable offline

**Exit:** every exercise in the catalog has either a GIF, a clip, or text; the
detail screen is fully usable in airplane mode for anything already cached.

---

## M4 — Progress and charts

Stories: US-16 … US-19

- [ ] Estimated 1RM (Epley), volume, and top-set trend per exercise
- [ ] Weekly volume by body part
- [ ] PR detection and history
- [ ] Time range selector; empty and sparse-data states designed, not accidental

**Exit:** charts render correctly with 1 session, 3 sessions, and 200 sessions.
Progression math is unit-tested against a hand-computed fixture table.

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
