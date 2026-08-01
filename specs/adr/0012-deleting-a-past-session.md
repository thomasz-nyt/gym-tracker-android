# ADR-0012: Deleting a past session

- **Status:** accepted
- **Date:** 2026-08-01
- **Deciders:** maintainer (requested), agent (scoped)

## Context

The maintainer's words: *"also need to be able to delete the past workout to clean the test
data."* Real workouts are now being logged on a real device alongside sessions created while
testing, and there is no way to remove the latter short of clearing app data — which takes
the real ones with it.

No story covered this. The neighbouring ones:

- **US-04** deletes a *set*, "with undo available for 5 seconds".
- **US-11** (M2) deletes an *account* and all its rows. Wrong granularity, wrong milestone.
- **US-01** discards an abandoned session, but only one that has no sets.

So this needs a story of its own, added as **US-06a**, and it needs history to exist first —
US-06's history list, which M1 had not built yet, is the surface you delete from.

## Options considered

1. **Hard delete immediately; hold a snapshot in memory; undo restores it.** The delete is
   real the moment it is asked for. Undo re-inserts the session, its `session_exercises` and
   its `sets` with their original ids.
2. **Deferred delete: hide the row for 5 seconds, then delete for real.** Less code — no
   snapshot, no restore path. Rejected: the delete lives in a coroutine that dies with the
   screen, so backgrounding the app inside the window silently cancels it and the workout
   the member just deleted is still there when they come back. It also races the M2 sync
   engine, which would have to know that a visible row is scheduled to vanish.
3. **Soft delete: a `deleted_at` column.** Rejected. It costs a migration, and every query
   in the app and every future chart has to remember to filter on it — a filter that is
   silently wrong when forgotten. `data-model.md` § Sync has already committed the other
   way: "Deletes are hard deletes propagated through `sync_queue`."
4. **A confirmation dialog instead of undo.** Rejected: US-04 already sets the house pattern
   for destructive actions on logged data, and a modal in the way is worse than an undo you
   can ignore. It is also the wrong safety net — a confirm asks before you know what you
   lost; an undo asks after you can see it.

## Decision

Option 1, with US-06a written to match US-04's wording so the two destructive actions in the
app behave the same way.

- **Only finished sessions are deletable.** History lists finished sessions only, so the
  session you are currently in cannot be deleted out from under you; US-01 already owns
  ending or discarding that one.
- **No migration.** `session_exercises.session_id` and `sets.session_exercise_id` are already
  `ON DELETE CASCADE` in both the Room schema and `data-model.md`'s Postgres schema, so
  deleting the session row removes its exercises and sets with it.
- **The snapshot is the session plus its `session_exercises` plus its `sets`**, held in
  memory only, restored through the same repository `add` calls the app uses to write them
  the first time. Ids are preserved, so a restored workout is the same workout and not a copy.
- **Undo lives for 5 seconds**, as in US-04.

## Consequences

- Killing the app inside the undo window loses the undo, not the delete. That is the right
  direction to fail: the member asked for the delete, and the app should not quietly
  countermand them. It is also the only outcome that stays true when M2 turns the delete
  into a synced operation.
- Restoring writes `updated_at = now` and `sync_state = PENDING` on every restored row, which
  is what M2's last-write-wins needs to see. The rows are otherwise byte-identical to what
  was deleted.
- The snapshot holds a whole session's sets in memory for five seconds. A long workout is a
  few dozen rows; this is not a size worth engineering around.
- Prefill (US-03) reads the member's most recent set of an exercise, so deleting the session
  that held it changes what the next set prefills with. That is correct — the set is gone —
  and it is the one place where deleting history is visible from the core loop.
- **Revisit at M2**, when the delete and the restore both have to enqueue sync operations.
  The snapshot is the payload a restore would replay, so the shape should survive.
