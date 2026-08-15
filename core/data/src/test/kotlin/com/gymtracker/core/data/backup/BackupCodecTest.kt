package com.gymtracker.core.data.backup

import com.gymtracker.core.domain.backup.BackupContents
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * US-40, US-41, ADR-0034: the file's contract. Domain shapes under a versioned envelope, not
 * raw Room rows — chosen specifically so a nullable field added later does not invalidate every
 * backup already on disk. `formatVersion` is the one thing that does invalidate a file, and only
 * when it names a version this build cannot read.
 */
class BackupCodecTest {
    private val codec = BackupCodec(Json { ignoreUnknownKeys = true })
    private val member = UserId("member-1")
    private val exportedAt: Instant = Instant.parse("2026-08-15T18:00:00Z")

    private val contents =
        BackupContents(
            memberId = member,
            unit = WeightUnit.KG,
            restDefault = Duration.ofSeconds(90),
            sessions =
                listOf(
                    WorkoutSession(
                        id = SessionId("s1"),
                        userId = member,
                        gymName = "Home gym",
                        startedAt = Instant.parse("2026-08-10T18:00:00Z"),
                        endedAt = Instant.parse("2026-08-10T19:00:00Z"),
                        metrics = null,
                    ),
                ),
            sessionExercises =
                listOf(
                    SessionExercise(
                        id = SessionExerciseId("se1"),
                        sessionId = SessionId("s1"),
                        exerciseId = ExerciseId("Bench"),
                        position = 1,
                        target = null,
                    ),
                ),
            sets =
                listOf(
                    ExerciseSet(
                        id = "set1",
                        sessionExerciseId = SessionExerciseId("se1"),
                        setIndex = 1,
                        weightKg = 60.0,
                        reps = 5,
                        rpe = null,
                        performedAt = Instant.parse("2026-08-10T18:05:00Z"),
                    ),
                ),
            routines = listOf(Routine(RoutineId("r1"), member, "Upper A", 1)),
            routineItems =
                listOf(
                    RoutineItem(
                        id = RoutineItemId("ri1"),
                        routineId = RoutineId("r1"),
                        exerciseId = ExerciseId("Bench"),
                        position = 1,
                        target = MovementTarget(sets = 3, reps = 8, weightKg = 61.25),
                    ),
                ),
        )

    @Test
    fun `encoding and decoding a file returns the contents unchanged`() {
        val json = codec.encode(contents, exportedAt, appVersion = "1.0")

        val decoded = codec.decode(json)

        assertEquals(contents, decoded)
    }

    @Test
    fun `the file is valid, readable JSON`() {
        val json = codec.encode(contents, exportedAt, appVersion = "1.0")

        assertTrue(json.contains("\"formatVersion\""))
        assertTrue(Json.parseToJsonElement(json).toString().isNotBlank())
    }

    @Test
    fun `encoding twice produces byte-identical output`() {
        val first = codec.encode(contents, exportedAt, appVersion = "1.0")
        val second = codec.encode(contents, exportedAt, appVersion = "1.0")

        assertEquals(first, second, "export must not depend on anything but its inputs")
    }

    @Test
    fun `a field added after this build still decodes, absent`() {
        val json = codec.encode(contents, exportedAt, appVersion = "1.0")
        // Simulate a future build's envelope carrying one more field this build has never
        // heard of, appended to the payload object.
        val withUnknownField = json.replaceFirst("\"unit\"", "\"aFieldFromTheFuture\":\"whatever\",\"unit\"")

        val decoded = codec.decode(withUnknownField)

        assertEquals(contents, decoded, "an unknown field must not break decoding")
    }

    @Test
    fun `a future format version is refused by name, not partially read`() {
        val json = codec.encode(contents, exportedAt, appVersion = "1.0")
        val fromTheFuture = json.replaceFirst("\"formatVersion\":1", "\"formatVersion\":99")

        val error = assertFailsWith<UnsupportedBackupFormatException> { codec.decode(fromTheFuture) }

        assertEquals(99, error.fileVersion)
    }
}
