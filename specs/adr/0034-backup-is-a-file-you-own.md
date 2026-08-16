# ADR-0034: A backup is a file you own, and restoring it replaces everything

- **Status:** accepted
- **Date:** 2026-08-15
- **Deciders:** maintainer (chose the shape of every fork below, in an interview), agent
  (scoped the options, found the identity problem)
- **Relates to:** ADR-0005 (DataStore holds what describes this install), ADR-0008 (unit
  preference), ADR-0010 (the rest timer is a stored instant), ADR-0020 and ADR-0027 (the
  snapshot rule this borrows from), ADR-0030 (three tabs, drill-downs for everything else),
  constitution §2.2 (offline), §2.4 (honest data), §5 (the user can export everything),
  US-05, US-11

## Context

Uninstalling the app deletes the Room database **and** the DataStore preferences. The
maintainer reinstalls several times a week to test on device, and uses the same app to log
real weekly training. Those two facts are in direct conflict today: testing destroys
training history, and nothing can bring it back.

The specs already promise an export. Constitution §5: "the user can export everything they
have logged… testable requirements, not settings-screen decoration." US-11 and the M2
roadmap line say the same. But all three are **M2**, which is postponed with no date, and
US-11 is written around an account — "I can delete my account; all my rows are removed" — a
sentence that has no meaning before auth exists.

More to the point: **there is no import anywhere in the specs at all.** US-11 is a data-rights
export, the kind you hand to a regulator. An export you cannot restore does not survive a
reinstall, which is the actual problem. This ADR is about the round trip, and it leaves US-11
alone to keep meaning what it says at M2.

### The identity problem, which is the whole reason this needs an ADR

`DataStoreCurrentMember` generates one local member UUID and stores it in DataStore, with a
KDoc that already saw the risk: "Generated once and never regenerated: it is stamped on every
session and set, so losing it would orphan everything already logged."

An uninstall loses it. And nearly every read in the app filters on it — `SessionDao.
observeActive`, `observeFinished`, `RoutineDao.observeRoutines`, `ExerciseDao.observeRanked`,
and both prefill queries in `SetDao`. So a restore that faithfully rewrites every row and
stops there produces a database full of workouts **no screen can see**, and the failure is
invisible: the app looks empty, exactly as it did before the import. Identity is not a detail
of this feature; it is the feature.

## Options considered

### Merge semantics

1. **Replace-all — chosen.** Wipe the member's rows, insert the file's. The primary case is
   restore-onto-empty, where merge has nothing to merge against and would never be exercised
   on the happy path.
2. **Merge by row id.** Rejected. It needs a rule for "same set id, different values" — and
   that rule is precisely what M2's sync engine is specified to own (`data-model.md` § Sync:
   last-write-wins on `updated_at`, three cases, its own ADR). Writing a second, different
   answer now means the project has two, and the one written first tends to win by accident.
   The risk replace-all leaves — importing a stale file over newer real workouts — is handled
   by a confirmation that names the real counts, not by an algorithm.

### What is in the file

1. **Member data only — chosen.** `sessions`, `session_exercises`, `sets`, `routines`,
   `routine_items`. The `exercises` catalog is excluded and re-seeds from the APK.
2. **Member data plus the catalog.** Rejected, and the project already made this argument
   twice. Migrations v4→v5 and v5→v6 both wipe and re-seed `exercises` outright, justified in
   their own KDoc as "derived data with no member content in it… `session_exercises.
   exercise_id` still resolves, because ids are UUIDv5 over the source slug." `CatalogSeeder.
   seedIfEmpty` is idempotent and `ExerciseDao.insertAll` is its only caller — there is no
   feature anywhere that creates an exercise. So the catalog re-materializes identically on
   reinstall, and putting 873 rows of instructions in a backup would make import responsible
   for restoring derived data, reversing the position those two migrations took.

### The shape of the records

1. **Domain entities under a versioned envelope — chosen.** The file speaks in `:core:domain`
   types, wrapped in a `formatVersion` / `exportedAt` / `appVersion` envelope.
2. **Raw Room rows, schema version checked strictly.** Rejected. The database moved v7 → v9
   in the last week alone, and the last three migrations were all nullable column additions.
   Column-shaped files would mean **every migration invalidates every backup already on
   disk** — the worst possible property for someone reinstalling weekly during active
   development, which is the entire motivating case.
3. **Raw Room rows, migrated on import.** Rejected: a second migration path, written and
   tested alongside the real one, free to disagree with it.

The two columns the domain model lacks cost nothing. `SessionEntity` and `RoutineEntity` both
document `updated_at` and `sync_state` as carrying "no meaning until M2," and `sync_state` is
written `PENDING` at every call site and read by nothing. Import re-derives both exactly as
every other write path already does — which is also the correct answer for a future sync:
restored rows are pending upload.

### A file that will not apply cleanly

1. **Reject the whole file, name what is missing — chosen.** Validation runs to completion
   before anything is written, and the database is untouched on failure.
2. **Import what resolves, skip the rest.** Rejected. Under replace-all the wipe has already
   happened, so a "skipped" workout is gone from the device, and the file was the only copy.
   Quietly losing part of the data is the exact failure this feature exists to prevent.

## Decision

**A manually triggered, local-only JSON round trip, with no account, no network and no new
dependency**, taken ahead of M2 for the reason M3a and M3b were taken ahead of it: none of it
needs a backend.

The file carries the five member tables in domain shapes, plus three DataStore keys:

```
local_member_id        identity — restored, because without it the rows are invisible
weight_unit            ADR-0008
default_rest_seconds   US-05's "60 seconds until changed in settings"
```

and deliberately **not** `rest_ends_at`, `warm_up_started_at`, the six `guided_*` keys, or
`notification_permission_asked`. ADR-0005 puts state in DataStore precisely because it
describes *this device or this install*; a rest countdown and a half-finished guided exercise
are the clearest cases of that, and they have no business surviving into a different install.

Import **restores the member id rather than rewriting the rows.** The id names the person,
not the install — which is also what `data-model.md` § "Identity before M2" already assumes
when it says the local UUID's rows get re-assigned to a Supabase user in one UPDATE at sign-in.
Rewriting instead would mutate every row on the way in and cost the round trip its strongest
property: that export → wipe → import is an identity function, assertable by direct comparison.

### Two things import refuses to do

**It refuses while a workout is running.** `observeActive` drives the session screen, and the
guided flow's DataStore state points at a `session_exercise` row a wipe would delete.
Constitution §2.1 makes the core loop sacred; having a session vanish mid-set is the least
sacred thing available. The rest timer is *not* a hazard here and does not need handling —
`RestAlarm` carries no session id and is documented as "only ever a notification trigger… a
missed or cancelled alarm costs a buzz, never the timer."

**It refuses a file it cannot fully apply**, naming what is missing. The realistic trigger is
a catalog revision that drops or renames a source slug: `routine_items` carries a real
`FOREIGN KEY(exercise_id) REFERENCES exercises(id)`. Both v5 and v6 confirmed by hand that the
re-seed "added and removed zero ids" — a hand check, which is to say one that can fail.

### Enforcement

1. **Validation is a pure function** in `:core:domain`, not a series of guards inside the
   write path. `ValidateBackup` takes decoded contents plus the locally present exercise ids
   and returns the contents or a typed failure. Table-driven, one case per failure mode. It
   cannot be partially skipped, because the writer takes its output as input.
2. **The write is one `withTransaction`.** Delete-then-insert cannot half-apply; a test
   asserts that a failure mid-write leaves the original data intact.
3. **The round trip is the definitive test**, not a collection of field assertions: seed →
   export → wipe → import → read back identical, member id included.
4. **`TwoTapSetLoggingTest` and `OneTapSetLoggingTest` pass unedited.** Nothing here touches
   the logging path, and that is the claim to prove rather than assert.

## Consequences

- A reinstall stops costing training history, which is what makes the app usable as both a
  test target and a real log at the same time.
- Where the file *goes* is the user's decision, not the app's: the Storage Access Framework
  hands back a URI, and putting it in a cloud-synced folder is a choice made in the file
  picker. The app gains no cloud dependency, no permission, and no knowledge of where its
  backups live — which also means **the app cannot promise a backup exists.** It is a file you
  own, with everything that implies in both directions.
- Export and import need a home, and there was none: **the app has no Settings screen**, and
  `UnitPreference` is read at nine call sites across ViewModels and controllers and set at
  zero. US-05 and ADR-0008 have both promised a control that never existed. US-42 builds the
  screen and closes both gaps in the same pass — a scope decision the maintainer made
  explicitly, rather than a gap discovered late.
- Settings arrives as a **drill-down from Train's header, not a fourth tab.** US-36 and
  ADR-0030 deliberately settled the bottom bar at three items and moved Routines off it; a
  new tab would reopen a decision made two days ago for a screen visited far less often.
- **Revisit when M2 lands sync.** A working sync engine may make a local file redundant for
  the reinstall case, at which point this becomes what US-11 always described — a data-rights
  export — and import may be worth removing rather than maintaining beside a merge engine that
  does the job properly. **Whichever way that goes, import must not grow merge semantics in
  the meantime.** The moment this file learns to reconcile two versions of a row, the project
  has two conflict-resolution rules, and this ADR's central argument is spent.
- The envelope's `formatVersion` is the thing to watch. It starts at 1 and must be bumped by
  any change that an older build could not read; a nullable addition is not such a change, and
  a test asserts that an envelope missing a later field still decodes.

## This is more than one PR

Per `CLAUDE.md`'s ~400-line rule:

1. **This ADR, US-40/41/42, and the `roadmap.md`, `data-model.md` and `tech-stack.md` notes.**
   No code. This PR.
2. **US-40, export:** `BackupContents`, the `BackupStore` port, `ExportBackup`, the
   `@Serializable` envelope and codec in `:core:data`, and the `:feature:settings` module with
   the SAF write.
3. **US-41, import:** `ValidateBackup`, `ImportBackup`, `replaceAll` in one transaction, the
   confirm dialog, the active-session refusal.
4. **US-42, the preference controls:** the unit toggle and the rest default, wired to setters
   that already exist and are already bound.
