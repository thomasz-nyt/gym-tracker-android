package com.gymtracker.core.domain.rest

import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.set.FakeSetRepository
import com.gymtracker.core.domain.set.LogSet
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.SetPrefill
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-54, ADR-0046: logging the set that is up next, shared by the session screen's one-tap
 * button (US-35) and the notification's `LOG SET` action.
 *
 * Shared on purpose. Two call sites writing "the next set" their own way is exactly how the
 * rest panel and the entry sheet would come to disagree about what the next set is, which
 * `DetermineUpNextSet` already refuses to allow for the *reading* half of the same question.
 */
class LogUpNextSetTest {
    private val now: Instant = Instant.parse("2026-08-30T18:00:00Z")
    private val bench = ExerciseId("bench")
    private val appearance = SessionExerciseId("se-1")

    private val sets = FakeSetRepository()
    private val store = FakeRestTimerStore()
    private val unitPreference = FakeUnitPreference()

    private val logUpNextSet =
        LogUpNextSet(
            logSets = LogSets(LogSet(sets, Clock.fixed(now, ZoneOffset.UTC)) { "new-set" }),
            restTimer = RestTimer(store, Clock.fixed(now, ZoneOffset.UTC)),
            unitPreference = unitPreference,
        )

    private fun upNext(
        weight: Double?,
        reps: Int,
    ) = UpNextSet(
        sessionExerciseId = appearance,
        exerciseId = bench,
        setNumber = 2,
        prefill = SetPrefill(weight = weight, reps = reps),
        comparison = null,
    )

    @Test
    fun `it writes exactly one set, at the prefill`() =
        runTest {
            logUpNextSet(upNext(weight = 60.0, reps = 8))

            assertEquals(1, sets.all.size, "one tap, one set — never a batch")
            assertEquals(60.0, sets.all.single().weightKg)
            assertEquals(8, sets.all.single().reps)
            assertEquals(appearance, sets.all.single().sessionExerciseId)
        }

    @Test
    fun `the prefilled weight is converted from the member's unit before it is stored`() =
        runTest {
            unitPreference.set(WeightUnit.LB)

            logUpNextSet(upNext(weight = 135.0, reps = 5))

            // ADR-0006: kilograms are canonical, and this path must not be the one place that
            // forgets — a notification that stored 135 kg would be a silent 3x error.
            assertEquals(61.23, sets.all.single().weightKg!!, absoluteTolerance = 0.01)
        }

    @Test
    fun `a bodyweight movement is recorded as absent, not as zero`() =
        runTest {
            logUpNextSet(upNext(weight = null, reps = 12))

            assertNull(sets.all.single().weightKg, "constitution §2.4: absent is not zero")
        }

    @Test
    fun `the rest that follows starts on its own`() =
        runTest {
            logUpNextSet(upNext(weight = 60.0, reps = 8))

            // The whole point of the action: log and the timer is already running, without the
            // member having to open the app to start it.
            assertEquals(now.plusSeconds(60), store.restEndsAt.first())
        }

    @Test
    fun `it returns the set it wrote, so the caller can report on it`() =
        runTest {
            val logged = logUpNextSet(upNext(weight = 60.0, reps = 8))

            // The session screen needs this for its personal-record check (US-35).
            assertEquals(sets.all.single(), logged)
        }

    private class FakeUnitPreference : UnitPreference {
        private val state = MutableStateFlow(WeightUnit.KG)

        override fun observe(): Flow<WeightUnit> = state

        override suspend fun current(): WeightUnit = state.value

        override suspend fun set(unit: WeightUnit) {
            state.value = unit
        }
    }

    private class FakeRestTimerStore : RestTimerStore {
        private val endsAt = MutableStateFlow<Instant?>(null)
        private val total = MutableStateFlow<Duration?>(null)
        private val default = MutableStateFlow(Duration.ofSeconds(60))
        private val asked = MutableStateFlow(false)

        override val restEndsAt = endsAt
        override val restTotal = total
        override val defaultRest = default
        override val shouldAskForNotificationPermission = asked.map { !it }

        override suspend fun setRestEndsAt(instant: Instant?) {
            endsAt.value = instant
            if (instant == null) total.value = null
        }

        override suspend fun setRest(
            endsAt: Instant,
            total: Duration,
        ) {
            this.endsAt.value = endsAt
            this.total.value = total
        }

        override suspend fun setDefaultRest(rest: Duration) {
            default.value = rest
        }

        override suspend fun markNotificationPermissionAsked() {
            asked.value = true
        }
    }
}
