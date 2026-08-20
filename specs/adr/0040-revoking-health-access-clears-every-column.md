# ADR-0040: Revoking health access clears every column, including the source marker

- **Status:** accepted
- **Date:** 2026-08-20
- **Deciders:** maintainer, with the agent

## Context

US-23 is M5's last open box: *"Turning the toggle off stops all reads immediately and
offers to delete previously imported metrics."* Everything it builds on already exists —
ADR-0038 established Health Connect as an enhancement layer with a per-member, device-local
toggle, and US-22 shipped the read, the on-device aggregation and the four columns on
`sessions`.

Three forces shape the decision:

**Constitution §2.4, honest data.** US-22 draws a distinction the UI depends on: a workout
that was never read for renders *nothing*, and a workout that was read for and yielded no
samples renders *"Heart rate not recorded · Calories not recorded"*. That distinction is
carried entirely by `metrics_source`. `SessionEntity.metrics()` returns null only when all
four columns are null; if any one survives, the row renders as "read, found nothing".

**Constitution §5, data protection.** A member can delete what they have. Health metrics
are the one class of data in this app that came from somewhere else, and revoking access to
the source should be able to take the derived data with it.

**ADR-0038's asymmetry.** The backup envelope carries the metrics but deliberately not the
toggle. That asymmetry has a consequence US-23 has to state rather than discover.

## Options considered

1. **Clear the three metric columns, leave `metrics_source`** — pros: reads as "we looked
   and found nothing", which is literally true after the fact. Cons: it is *not* what
   happened, and it is indistinguishable from a genuine empty read. Every erased workout
   would carry a permanent "not recorded" line the member explicitly asked to be rid of.
   This is the failure mode the decision exists to prevent.
2. **Clear all four columns together** — pros: the row returns to exactly the state of a
   workout logged before the member ever opted in, which is the honest description of a
   member who has revoked. Cons: none material; the information genuinely is being deleted
   on request.
3. **Delete the sessions outright** — rejected without much thought. The sets are the
   member's own work; the metrics were borrowed. Deleting a workout because its heart-rate
   data was revoked would be catastrophic and is called out as an explicit test case.
4. **Remember a decline in DataStore** — pros: no repeated dialog. Cons: ADR-0005's own rule
   is that a device-local preference describes something the member chose about their data;
   a flag that only suppresses a dialog describes nothing. The offer is already
   self-limiting — it fires only when metrics exist — and if you decline, log more workouts
   and revoke again, those new rows are a new fact worth re-offering.

## Decision

Revoking clears `avg_hr`, `max_hr`, `active_kcal` **and** `metrics_source` together, in one
`UPDATE` guarded on the row actually having metrics; the toggle-off write is unconditional
and happens before the offer; declining deletes nothing and is not remembered.

## Consequences

**Easier.** A revoked workout is byte-for-byte a workout that never had metrics, so every
existing render path — `FinishSummaryScreen`, `WorkoutDetailScreen`, `BackupCodec` — does
the right thing with no change. The store of "was this read for" stays a single column with
a single meaning.

**Harder.** "Stops reads immediately" and "deletes what was imported" are now two separate
guarantees that must not be conflated. `RecordSessionMetrics` already gates on
`HealthIntegration.current()`, so the first is true the instant the DataStore write lands —
which means it is proved by a test rather than implemented, and a future refactor that moves
that gate breaks a promise no longer obviously connected to it.

**We are committed to:** the `WHERE`-guard on the update. Without it every metrics-free
session takes a fresh `updated_at` and `sync_state = 'PENDING'`, which M2's last-write-wins
sync would then push wholesale — a sync storm caused by a member toggling a switch.

**An exported backup is out of reach, deliberately.** SAF hands back a one-shot write URI;
the app holds no persistent handle and must not acquire one. So a file exported before the
revoke still contains the metrics, and **importing it restores them** — which is correct, not
a leak: US-41 promises exactly what you had, and silently stripping a field would be the
dishonest-data failure §2.4 forbids. The toggle is not in the envelope (ADR-0038), so a
restore brings back old numbers and still performs no new reads. Exporting *after* a revoke
produces a file with no metrics, automatically, because `BackupCodec` reads `session.metrics`.

**What would cause us to revisit:** M2 arriving. Once sessions sync, "delete my metrics" has
to mean deleting them on every device, not just this one, and the `sync_state = 'PENDING'`
this update already sets is the hook for that — but the cross-device story is M2's to tell.
