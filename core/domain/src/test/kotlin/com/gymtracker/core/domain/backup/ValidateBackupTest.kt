package com.gymtracker.core.domain.backup

import com.gymtracker.core.domain.TestData
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.units.WeightUnit
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * US-41: a file that references an exercise this build does not have is refused, naming what
 * is missing. Table-driven over every place a backup can name an exercise id.
 */
class ValidateBackupTest {
    private val member = UserId("member-1")
    private val known = setOf(TestData.BENCH, TestData.SQUAT)

    private val fixture = TestData.memberWithARoutineAndASession(member)

    private fun contents(
        sessionExercises: List<SessionExercise> = fixture.sessionExercises,
        routineItems: List<RoutineItem> = fixture.routineItems,
    ) = BackupContents(
        memberId = member,
        unit = WeightUnit.LB,
        restDefault = Duration.ofSeconds(60),
        sessions = fixture.sessions,
        sessionExercises = sessionExercises,
        sets = fixture.sets,
        routines = fixture.routines,
        routineItems = routineItems,
    )

    @Test
    fun `a file whose exercises are all known is valid`() {
        // The fixture's session and routine item both reference BENCH, which is known.
        val result = ValidateBackup(contents(), known)

        assertIs<BackupValidationResult.Valid>(result)
    }

    @Test
    fun `an empty file is trivially valid`() {
        val empty =
            BackupContents(
                memberId = member,
                unit = WeightUnit.LB,
                restDefault = Duration.ofSeconds(60),
                sessions = emptyList(),
                sessionExercises = emptyList(),
                sets = emptyList(),
                routines = emptyList(),
                routineItems = emptyList(),
            )

        assertIs<BackupValidationResult.Valid>(ValidateBackup(empty, known))
    }

    @Test
    fun `a session_exercise referencing an unknown exercise is refused, naming it`() {
        val unknown = ExerciseId("Some_Exercise_This_Build_Dropped")
        val withUnknown =
            fixture.sessionExercises.map { it.copy(exerciseId = unknown) }

        val result = ValidateBackup(contents(sessionExercises = withUnknown), known)

        val refused = assertIs<BackupValidationResult.UnknownExercises>(result)
        assertEquals(setOf(unknown), refused.missingExerciseIds)
    }

    @Test
    fun `a routine_item referencing an unknown exercise is refused, naming it`() {
        val unknown = ExerciseId("Some_Exercise_This_Build_Dropped")
        val withUnknown =
            listOf(
                RoutineItem(
                    id = RoutineItemId("ri-unknown"),
                    routineId = fixture.routines.single().id,
                    exerciseId = unknown,
                    position = 1,
                    target = MovementTarget(sets = null, reps = null, weightKg = null),
                ),
            )

        val result = ValidateBackup(contents(routineItems = withUnknown), known)

        val refused = assertIs<BackupValidationResult.UnknownExercises>(result)
        assertEquals(setOf(unknown), refused.missingExerciseIds)
    }

    @Test
    fun `unknown exercises from both tables are named together, once each`() {
        val unknownA = ExerciseId("Dropped_A")
        val unknownB = ExerciseId("Dropped_B")
        val withUnknownSessionExercise = fixture.sessionExercises.map { it.copy(exerciseId = unknownA) }
        val withUnknownRoutineItem =
            listOf(
                RoutineItem(
                    id = RoutineItemId("ri-unknown"),
                    routineId = fixture.routines.single().id,
                    exerciseId = unknownB,
                    position = 1,
                    target = null,
                ),
            )

        val result =
            ValidateBackup(
                contents(sessionExercises = withUnknownSessionExercise, routineItems = withUnknownRoutineItem),
                known,
            )

        val refused = assertIs<BackupValidationResult.UnknownExercises>(result)
        assertEquals(setOf(unknownA, unknownB), refused.missingExerciseIds)
    }
}
