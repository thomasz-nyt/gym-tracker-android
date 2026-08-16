package com.gymtracker.core.domain.backup

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.units.WeightUnit
import java.time.Duration

/**
 * Everything a backup file carries (US-40, US-41, ADR-0034).
 *
 * Exactly the five member tables plus the three DataStore keys ADR-0034 names — no more,
 * no less:
 *
 * - The `exercises` catalog is **not here**. It is derived data with deterministic (UUIDv5)
 *   ids, so it re-seeds from the bundled asset on a fresh install; see `data-model.md`
 *   § "What travels in a backup".
 * - [memberId] travels with the file **and is restored, not rewritten**, on import. Every read
 *   in the app — `sessions`, `routines`, both `SetRepository` prefill queries — filters on it,
 *   so rows imported under a different id would be present in the database and invisible to
 *   every screen. This is the load-bearing decision in ADR-0034.
 * - [unit] and [restDefault] are ADR-0008 and US-05's preference, so a restore does not leave
 *   the member re-picking either. The rest timer's *running* end time is deliberately absent —
 *   ADR-0005 puts it in DataStore precisely because it describes a rest in progress on one
 *   device, not the member.
 * - `updated_at` and `sync_state` are absent from every row. Both are documented on
 *   `SessionEntity`/`RoutineEntity` as carrying "no meaning until M2," so import re-derives
 *   them exactly as every other write path already does.
 */
data class BackupContents(
    val memberId: UserId,
    val unit: WeightUnit,
    val restDefault: Duration,
    val sessions: List<WorkoutSession>,
    val sessionExercises: List<SessionExercise>,
    val sets: List<ExerciseSet>,
    val routines: List<Routine>,
    val routineItems: List<RoutineItem>,
)
