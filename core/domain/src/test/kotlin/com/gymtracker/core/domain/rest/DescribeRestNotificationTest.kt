package com.gymtracker.core.domain.rest

import com.gymtracker.core.domain.exercise.FakeExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.FakeSessionRepository
import com.gymtracker.core.domain.sessionexercise.FakeSessionExerciseRepository
import com.gymtracker.core.domain.set.FakeSetRepository
import com.gymtracker.core.domain.set.PrefillFromLastSet
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-54, ADR-0046: what the rest notification says, decided in the domain rather than in a
 * `BroadcastReceiver`.
 *
 * ADR-0010 conceded that the receiver and the notification are untestable glue. This is the
 * class that keeps that concession honest — if anything here were decided in the receiver
 * instead, none of these cases could be asserted at all.
 */
class DescribeRestNotificationTest {
    private val now: Instant = Instant.parse("2026-08-30T18:00:00Z")
    private val alice = UserId("alice")
    private val bench = ExerciseId("bench")
    private val thisSession = SessionId("today")

    private val sessions = FakeSessionRepository()
    private val sessionExercises = FakeSessionExerciseRepository()
    private val sets = FakeSetRepository()
    private val catalog = FakeExerciseCatalog(listOf(catalogEntry(bench, "Barbell Bench Press")))
    private val unitPreference = FakeUnitPreference()

    private val describe =
        DescribeRestNotification(
            sessions = sessions,
            currentMember = FakeCurrentMember(alice),
            unitPreference = unitPreference,
            determineUpNextSet = DetermineUpNextSet(sessionExercises, sets, PrefillFromLastSet(sets)),
            catalog = catalog,
        )

    @Test
    fun `there is nothing to say when no session is running`() =
        runTest {
            assertNull(describe(), "no session, no notification content to invent")
        }

    @Test
    fun `there is nothing to say until a set has been logged`() =
        runTest {
            startSession()
            appear("se-1", bench)

            // US-54: the notification falls back to its static text rather than naming a set
            // that does not exist (constitution §2.4).
            assertNull(describe())
        }

    @Test
    fun `the notice names the movement, the set coming, and what it is prefilled with`() =
        runTest {
            startSession()
            val appearance = appear("se-1", bench)
            logged(appearance, id = "s1", weightKg = 60.0, reps = 8)

            val notice = describe()

            assertEquals("Barbell Bench Press", notice?.exerciseName)
            assertEquals(2, notice?.setNumber, "one set logged, so the next one is 2")
            assertEquals(60.0, notice?.weight)
            assertEquals(8, notice?.reps)
            assertEquals(appearance.id, notice?.sessionExerciseId)
            assertEquals(bench, notice?.exerciseId)
        }

    @Test
    fun `the weight is in the member's own unit`() =
        runTest {
            unitPreference.set(WeightUnit.LB)
            startSession()
            val appearance = appear("se-1", bench)
            logged(appearance, id = "s1", weightKg = 61.23, reps = 5)

            val notice = describe()

            assertEquals(135.0, notice?.weight, "61.23 kg is 135 lb")
            assertEquals(WeightUnit.LB, notice?.unit, "the unit travels with the number it describes")
        }

    @Test
    fun `a movement the catalog does not know is left unnamed rather than named badly`() =
        runTest {
            val ghost = ExerciseId("not-in-the-catalog")
            startSession()
            val appearance = appear("se-1", ghost)
            logged(appearance, id = "s1", weightKg = 40.0, reps = 10)

            val notice = describe()

            // US-13's absence rule: render nothing in its place, never a placeholder and never
            // the raw id.
            assertNull(notice?.exerciseName)
            assertEquals(2, notice?.setNumber, "the set is still real even when the name is not available")
        }

    private suspend fun startSession() {
        sessions.startSession(
            WorkoutSession(thisSession, alice, gymName = null, startedAt = now, endedAt = null, metrics = null),
        )
    }

    private suspend fun appear(
        id: String,
        exercise: ExerciseId,
    ): SessionExercise {
        val appearance = SessionExercise(SessionExerciseId(id), thisSession, exercise, position = 1)
        sessionExercises.add(appearance)
        sets.belongsTo(appearance)
        return appearance
    }

    private suspend fun logged(
        appearance: SessionExercise,
        id: String,
        weightKg: Double,
        reps: Int,
    ) {
        val set = ExerciseSet(id, appearance.id, 1, weightKg, reps, null, now)
        sets.add(set)
        sets.lastFor[appearance.exerciseId] = set.id
    }

    private fun catalogEntry(
        id: ExerciseId,
        name: String,
    ) = Exercise(
        id = id,
        name = name,
        aliases = emptyList(),
        primaryMuscles = listOf(BodyPart.CHEST),
        secondaryMuscles = emptyList(),
        equipment = Equipment.BARBELL,
        instructions = emptyList(),
        mediaUrl = null,
        mediaType = null,
        youtubeUrl = null,
        source = "free-exercise-db",
    )

    private class FakeCurrentMember(
        private val id: UserId,
    ) : CurrentMember {
        override suspend fun id(): UserId = id

        override suspend fun restore(id: UserId) = error("not needed for this test")
    }

    private class FakeUnitPreference : UnitPreference {
        private val state = MutableStateFlow(WeightUnit.KG)

        override fun observe(): Flow<WeightUnit> = state

        override suspend fun current(): WeightUnit = state.value

        override suspend fun set(unit: WeightUnit) {
            state.value = unit
        }
    }
}
