# ADR-0043: The sync engine — an outbox, last-write-wins, and a status that stays silent when there is nothing to report

- **Status:** accepted
- **Date:** 2026-08-29
- **Deciders:** maintainer, agent
- **Relates to:** ADR-0042 (sign-in is optional; this engine is what a signed-in device
  runs), ADR-0039 (a live band's chip already shares the one global top-bar slot this ADR
  contends for), `specs/data-model.md` §Sync and §`sync_state`, `specs/tech-stack.md`
  (WorkManager, "constrained on network availability"), US-10

## Context

`data-model.md` already states the rule this engine implements — "last-write-wins per row
on `updated_at`, with the client clock trusted only for ordering its own edits... deletes
are hard deletes propagated through `sync_queue`" — and already reserves the column and the
table name (`sync_state` on every local table since migration v1; `sync_queue` sketched in
the Postgres section as future bookkeeping). It also names the three cases that must be
tested: local-only change, remote-only change, both changed. What it does not do is say how
`sync_queue` is populated, when it drains, or where a member sees any of this happening —
three things that have to be decided before migration v9 → v10 can be written.

A fourth question is new, not inherited: `LiveHeartRateChip`'s own doc comment claims the
`GymTrackerNavHost` `topBar` slot as one "which nothing else in the app uses" (ADR-0039).
US-10 requires a sync status indicator distinguishing synced / pending / error, and this
repo's working convention — established by `LiveHeartRateChip` itself, and by
`HealthMetricsSource`'s `Unavailable` before it — is that a feature the member has not
engaged renders nothing at all, not a reassuring "all clear." Applied literally to sync,
that convention breaks: "nothing to report" and "everything is synced" are not the same
claim, and only one of them is something silence can honestly say.

## Options considered

### How `sync_queue` is populated

1. **Every write to a syncable table also enqueues its own outbox row, in the same Room
   transaction — chosen.** A DAO write already sets `sync_state = 'PENDING'` at every call
   site (`SessionDao`, `RoutineDao`, and their siblings); this adds one more statement,
   `INSERT INTO sync_queue`, to each of those same transactions, with `payload_json` built
   from the row exactly as `BackupCodec` already serialises it for export.
2. **Derive the outbox at drain time by scanning every table for `sync_state = 'PENDING'`.**
   Rejected for deletes specifically: `data-model.md`'s own text is explicit that "deletes
   are hard deletes" — a deleted row leaves no `PENDING` flag anywhere to scan for, because
   there is no row left to carry one. `sync_queue` exists precisely to record an operation
   that the row itself can no longer describe after it happens, so it cannot be reconstructed
   after the fact from row state.

### When the queue drains

1. **A WorkManager periodic + network-constrained worker, plus an immediate one-shot
   enqueue after every write — chosen**, per `tech-stack.md`'s existing row ("Background
   sync | WorkManager | Constrained on network availability"). The one-shot keeps sync fast
   on the common case (already online); the periodic worker is what survives app kill and
   catches a connectivity change the app was not open to observe.
2. **Sync only in the foreground, on a timer while the app is open.** Rejected: fails US-10's
   own requirement outright ("surviving app kill") and would leave a household member's
   changes stuck until they happen to reopen the app.

### The three required conflict cases

`data-model.md` requires local-only, remote-only, and both-changed be tested; this ADR
states what "both changed" resolves to, since the data model names the rule but not the
tie-break:

1. **Later `updated_at` wins outright; an exact tie keeps the local row's payload but takes
   the remote `updated_at` — chosen.** Two people do not edit the same set
   (`data-model.md`'s own reasoning for ruling out CRDTs), so an exact-timestamp tie is
   vanishingly rare and not worth a second column (e.g. a per-device tiebreaker id) to
   resolve deterministically across devices; keeping the local payload is simplest and
   costs nothing a real household would ever notice.
2. **Field-level merge** (keep whichever device changed each column most recently, not
   whichever changed the row most recently). Rejected as unnecessary complexity: no table
   in `data-model.md` has two columns a household member would plausibly edit from two
   devices between syncs, and row-level LWW is what the data model already commits to in
   prose.

### Where the sync status renders

1. **Share `GymTrackerNavHost`'s `topBar` slot with `LiveHeartRateChip`; render nothing
   when synced — chosen.** The slot becomes a small status row, heart rate on one side and
   sync on the other, each independently absent per its own state. `Synced` renders
   nothing — silence here does not mean "nothing to report," it means "nothing more urgent
   than usual to report," which is the same reading `LiveHeartRateChip.Unavailable`
   already gives its own silence (absence is not a claim of a healthy state, it is the
   absence of anything worth a member's attention). `Pending` renders a small muted count
   ("3 pending"); `Error` renders a tappable row that opens Settings. The full three-state
   truth — including a timestamp ("Last synced 14:02") the chip has no room for — lives in
   Settings, which is where a member can actually distinguish "synced a second ago" from
   "synced this morning," the finer-grained claim US-10's "distinguishes: synced / pending
   / error" is really asking for.
2. **A separate, always-visible chip.** Rejected: the `topBar` is the one slot every
   destination shares (`LiveHeartRateChip`'s own doc comment), and a second permanent chrome
   element competing for the same strip either crowds the heart-rate reading or needs its
   own new slot this ADR has no reason to invent.
3. **Settings only, no global signal.** Rejected: a failing sync would then be invisible
   until a member goes looking for it, the exact failure mode offline-first apps are known
   for and the one US-10 names a status indicator to prevent.

### The migration

1. **Room migration v9 → v10, additive: one new table, `sync_queue`, plus no changes to any
   existing table — chosen.** Every existing table already carries `updated_at` and
   `sync_state`; this migration adds nothing to them. Follows the pattern every migration
   since v7 has used (v7's `routines`/`routine_items`, v8's `target_*` columns, v9's
   `sessions.routine_id`/`routine_name`): additive, scoped to exactly what its own ADR
   claims.

## Decision

`SyncQueueDao` gains one `INSERT` alongside every existing `sync_state = 'PENDING'` write,
in the same transaction. A `SyncWorker` (`:core:data`, `CoroutineWorker`) drains the queue
oldest-first when signed in and online — enqueued as a `OneTimeWorkRequest` immediately
after each write and as a `PeriodicWorkRequest` (network-constrained) for the app-kill and
reconnect cases — applying last-write-wins per row on `updated_at` with an exact tie kept
local. `SyncQueueDao.pendingCount()` and a new `SyncStatus` sealed type (`Synced /
Pending(count) / Error(message)`, mirroring `LiveHeartRate`'s own shape) back a
`SyncIndicatorChip` that shares `GymTrackerNavHost`'s `topBar` with `LiveHeartRateChip`,
silent exactly when `Synced`. Migration v9 → v10 adds only `sync_queue(id, entity,
entity_id, op, payload_json, created_at, attempts)`; no existing table's schema changes.

This engine only ever runs for a signed-in member (ADR-0042); `SyncWorker`'s first check is
`AuthSource.session()`, and it is a no-op — not an error state — for a member who has never
signed in, the same no-op-is-silent contract `HealthMetricsSource` and
`LiveHeartRateSource` already use for a toggle that is off.

## Consequences

- A member can watch a set they just logged go from "3 pending" to silence without opening
  Settings, and can find out *when* it last synced, and *why* it is stuck, only by opening
  Settings — a deliberate two-tier disclosure, not an oversight.
- `sync_queue` grows unboundedly until this ADR's own follow-up decides a retention or
  compaction rule; not addressed here because nothing in M2's exit criterion needs it, and
  inventing one now would be exactly the kind of unrequested scope CLAUDE.md asks this repo
  to avoid.
- The exact-tie tiebreak (option 1 under "conflict cases") means two devices racing to the
  same millisecond on the same row is the one case this ADR does not resolve
  deterministically across which device's payload survives — accepted because
  `data-model.md` itself argues this case does not occur in practice ("two people do not
  edit the same set").
- `attempts` on `sync_queue` exists for a future backoff/dead-letter policy; this ADR does
  not yet specify one, so `SyncWorker`'s first version may retry indefinitely on failure —
  revisit before shipping if a real household hits a poison-pill row.
- Revisit the topBar-sharing decision if a third global signal ever needs the same slot;
  two independently-absent elements already share it comfortably, but a third would need
  its own layout decision this ADR does not make.
