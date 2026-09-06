package com.gymtracker.core.data.sync

import com.gymtracker.core.data.routine.RoutineEntity
import com.gymtracker.core.data.routine.RoutineItemEntity
import com.gymtracker.core.data.session.SYNC_STATE_PENDING
import com.gymtracker.core.data.session.SessionEntity
import com.gymtracker.core.data.sessionexercise.SessionExerciseEntity
import com.gymtracker.core.data.set.SetEntity
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * US-57: [SyncPayloadCodec] is row-shaped, not domain-shaped like `BackupCodec` — it must carry
 * `updated_at` (which last-write-wins is keyed on) and, for the two tables that have one,
 * `user_id`, both of which `BackupCodec`'s own DTOs drop (ADR-0043's amendment).
 */
class SyncPayloadCodecTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val codec = SyncPayloadCodec(json)

    @Test
    fun `a session's payload carries updated_at and user_id, which a backup drops`() {
        val entity =
            SessionEntity(
                id = "s1",
                userId = "alice",
                gymName = "Downtown",
                startedAt = 1_000L,
                endedAt = 2_000L,
                avgHr = 120,
                maxHr = 160,
                activeKcal = 300,
                metricsSource = "health_connect",
                updatedAt = 5_000L,
                syncState = SYNC_STATE_PENDING,
                routineName = "Upper A",
                routineId = "r1",
            )

        val payload = codec.encode(entity)
        val dto = json.decodeFromString(SyncSessionDto.serializer(), payload)

        assertEquals("s1", dto.id)
        assertEquals("alice", dto.userId)
        assertEquals("Downtown", dto.gymName)
        assertEquals(1_000L, dto.startedAt)
        assertEquals(2_000L, dto.endedAt)
        assertEquals(120, dto.avgHr)
        assertEquals(160, dto.maxHr)
        assertEquals(300, dto.activeKcal)
        assertEquals("health_connect", dto.metricsSource)
        assertEquals("Upper A", dto.routineName)
        assertEquals("r1", dto.routineId)
        assertEquals(5_000L, dto.updatedAt, "the field last-write-wins is keyed on")
        assertFalse(payload.contains("PENDING"), "sync_state is Room-only bookkeeping, not part of the payload")
    }

    @Test
    fun `a session exercise's payload carries its target and updated_at`() {
        val entity =
            SessionExerciseEntity(
                id = "se1",
                sessionId = "s1",
                exerciseId = "bench",
                position = 1,
                updatedAt = 4_000L,
                syncState = SYNC_STATE_PENDING,
                targetSets = 3,
                targetReps = 8,
                targetWeightKg = 61.25,
                targetRestSeconds = 90,
            )

        val dto = json.decodeFromString(SyncSessionExerciseDto.serializer(), codec.encode(entity))

        assertEquals("se1", dto.id)
        assertEquals("s1", dto.sessionId)
        assertEquals("bench", dto.exerciseId)
        assertEquals(1, dto.position)
        assertEquals(3, dto.targetSets)
        assertEquals(8, dto.targetReps)
        assertEquals(61.25, dto.targetWeightKg)
        assertEquals(90, dto.targetRestSeconds, "the rest travels with the target (ADR-0050)")
        assertEquals(4_000L, dto.updatedAt)
    }

    @Test
    fun `a set's payload carries a null weight for a bodyweight set, not zero`() {
        val entity =
            SetEntity(
                id = "set1",
                sessionExerciseId = "se1",
                setIndex = 1,
                weightKg = null,
                reps = 12,
                rpe = null,
                performedAt = 3_000L,
                updatedAt = 3_500L,
                syncState = SYNC_STATE_PENDING,
            )

        val dto = json.decodeFromString(SyncSetDto.serializer(), codec.encode(entity))

        assertEquals("set1", dto.id)
        assertEquals("se1", dto.sessionExerciseId)
        assertEquals(1, dto.setIndex)
        assertEquals(null, dto.weightKg)
        assertEquals(12, dto.reps)
        assertEquals(3_000L, dto.performedAt)
        assertEquals(3_500L, dto.updatedAt)
    }

    @Test
    fun `a routine's payload carries its user_id and created_at`() {
        val entity =
            RoutineEntity(
                id = "r1",
                userId = "alice",
                name = "Upper A",
                position = 1,
                createdAt = 1_000L,
                updatedAt = 2_000L,
                syncState = SYNC_STATE_PENDING,
            )

        val dto = json.decodeFromString(SyncRoutineDto.serializer(), codec.encode(entity))

        assertEquals("r1", dto.id)
        assertEquals("alice", dto.userId)
        assertEquals("Upper A", dto.name)
        assertEquals(1, dto.position)
        assertEquals(1_000L, dto.createdAt)
        assertEquals(2_000L, dto.updatedAt)
    }

    @Test
    fun `a routine item's payload carries its target and updated_at, but no user_id`() {
        // routine_items has no user_id column in either Room or Postgres (ADR-0043's amendment)
        // — RLS reaches it through its parent routine instead.
        val entity =
            RoutineItemEntity(
                id = "ri1",
                routineId = "r1",
                exerciseId = "bench",
                position = 1,
                updatedAt = 2_500L,
                syncState = SYNC_STATE_PENDING,
                targetSets = 3,
                targetReps = 8,
                targetWeightKg = 61.25,
                targetRestSeconds = 90,
            )

        val payload = codec.encode(entity)
        val dto = json.decodeFromString(SyncRoutineItemDto.serializer(), payload)

        assertEquals("ri1", dto.id)
        assertEquals("r1", dto.routineId)
        assertEquals("bench", dto.exerciseId)
        assertEquals(3, dto.targetSets)
        assertEquals(8, dto.targetReps)
        assertEquals(61.25, dto.targetWeightKg)
        assertEquals(90, dto.targetRestSeconds, "the rest travels with the target (ADR-0050)")
        assertEquals(2_500L, dto.updatedAt)
        assertFalse(payload.contains("userId"), "routine_items carries no user_id in either schema")
    }
}
