package com.gymtracker.feature.progress

import app.cash.turbine.test
import com.gymtracker.core.domain.TestData
import com.gymtracker.core.domain.exercise.FakeExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.progress.ExerciseLogOf
import com.gymtracker.core.domain.progress.ExerciseTrend
import com.gymtracker.core.domain.progress.ExerciseTrendOf
import com.gymtracker.core.domain.progress.PersonalRecordsOf
import com.gymtracker.core.domain.session.FakeSessionRepository
import com.gymtracker.core.domain.sessionexercise.FakeSessionExerciseRepository
import com.gymtracker.core.domain.set.FakeSetRepository
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * US-16 and US-19 as the progress screen sees them.
 *
 * The arithmetic is `ExerciseTrendOf`'s and is tested in `:core:domain` against hand-computed
 * figures. What is asserted here is the wiring: the right exercise, the right member, a
 * switchable series, and — the one that matters — that "not enough data" reaches the screen as
 * a state rather than as an empty chart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseProgressViewModelTest {
    private val sessions = FakeSessionRepository()
    private val sessionExercises = FakeSessionExerciseRepository()
    private val sets = FakeSetRepository()

    /** In the catalog, absent from every fixture — the movement you have never done. */
    private val neverPerformed =
        Exercise(
            id = ExerciseId("never-performed"),
            name = "Pec Deck",
            aliases = emptyList(),
            primaryMuscles = listOf(BodyPart.CHEST),
            secondaryMuscles = emptyList(),
            equipment = Equipment.MACHINE,
            instructions = emptyList(),
            mediaUrl = null,
            mediaType = null,
            youtubeUrl = null,
            source = "test",
        )

    private val catalog = FakeExerciseCatalog(TestData.exercises + neverPerformed)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(member: UserId = TestData.PROGRESSING) =
        ExerciseProgressViewModel(
            exerciseTrendOf = ExerciseTrendOf(sessions, sessionExercises, sets, ZoneOffset.UTC),
            exerciseLogOf = ExerciseLogOf(sessions, sessionExercises, sets, ZoneOffset.UTC),
            personalRecordsOf = PersonalRecordsOf(sessions, sessionExercises, sets, ZoneOffset.UTC),
            catalog = catalog,
            currentMember = FakeCurrentMember(member),
            unitPreference = FakeUnitPreference(),
        )

    private suspend fun loadTwelveWeeks() {
        val fixture = TestData.twelveWeeksOfProgress(TestData.PROGRESSING)
        fixture.sessions.forEach { sessions.startSession(it) }
        fixture.sessionExercises.forEach {
            sessionExercises.add(it)
            sets.belongsTo(it)
        }
        fixture.sets.forEach { sets.add(it) }
    }

    @Test
    fun `the screen names the exercise it is charting`() =
        runTest {
            loadTwelveWeeks()
            val viewModel = viewModel().also { it.open(TestData.BENCH) }

            viewModel.uiState.test {
                assertEquals("Barbell Bench Press - Medium Grip", expectMostRecentItem().exerciseName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `twelve weeks of training is a series`() =
        runTest {
            loadTwelveWeeks()
            val viewModel = viewModel().also { it.open(TestData.BENCH) }

            viewModel.uiState.test {
                val trend = expectMostRecentItem().trend
                assertTrue(trend is ExerciseTrend.Series, "got $trend")
                assertEquals(12, trend.points.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a movement never performed reaches the screen as no data, not an empty chart`() =
        runTest {
            // US-19: "with no data, charts show a clear 'not enough data yet' state rather than
            // an empty grid or a zero line." The screen cannot draw a grid it was never given.
            loadTwelveWeeks()
            val viewModel = viewModel().also { it.open(neverPerformed.id) }

            viewModel.uiState.test {
                assertEquals(ExerciseTrend.NoData, expectMostRecentItem().trend)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `one session reaches the screen as a single point`() =
        runTest {
            val fixture = TestData.oneSessionOnly(TestData.SPARSE)
            fixture.sessions.forEach { sessions.startSession(it) }
            fixture.sessionExercises.forEach {
                sessionExercises.add(it)
                sets.belongsTo(it)
            }
            fixture.sets.forEach { sets.add(it) }
            val viewModel = viewModel(TestData.SPARSE).also { it.open(TestData.BENCH) }

            viewModel.uiState.test {
                assertTrue(expectMostRecentItem().trend is ExerciseTrend.SinglePoint)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the estimate is what the chart opens on`() =
        runTest {
            // US-16 lists estimated 1RM first, and it is the series that answers "am I getting
            // stronger" rather than "did I do more today".
            loadTwelveWeeks()
            val viewModel = viewModel().also { it.open(TestData.BENCH) }

            viewModel.uiState.test {
                assertEquals(TrendSeries.ESTIMATED_ONE_REP_MAX, expectMostRecentItem().series)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the series can be switched without reloading the trend`() =
        runTest {
            loadTwelveWeeks()
            val viewModel = viewModel().also { it.open(TestData.BENCH) }

            viewModel.onSeriesChanged(TrendSeries.VOLUME)

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(TrendSeries.VOLUME, state.series)
                assertTrue(state.trend is ExerciseTrend.Series, "the points are unchanged")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the member's unit reaches the screen, so the axis can be labelled`() =
        runTest {
            loadTwelveWeeks()
            val viewModel = viewModel().also { it.open(TestData.BENCH) }

            viewModel.uiState.test {
                assertEquals(WeightUnit.LB, expectMostRecentItem().unit)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- US-34: the log below the chart ----

    @Test
    fun `the log reaches the screen newest first`() =
        runTest {
            loadTwelveWeeks()
            val viewModel = viewModel().also { it.open(TestData.BENCH) }

            viewModel.uiState.test {
                val log = expectMostRecentItem().log
                assertEquals(12, log.size)
                assertTrue(
                    log.zipWithNext().all { (later, earlier) -> later.performedOn > earlier.performedOn },
                    "newest first, the opposite direction from the chart",
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a movement never performed reaches the screen with an empty log, not just no chart`() =
        runTest {
            loadTwelveWeeks()
            val viewModel = viewModel().also { it.open(neverPerformed.id) }

            viewModel.uiState.test {
                assertEquals(emptyList(), expectMostRecentItem().log)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- US-18: the standing per-exercise PR list ----

    @Test
    fun `the record list reaches the screen, one per rep count`() =
        runTest {
            // Twelve weeks of linear progress at the same rep count means one record: the
            // final, heaviest week — 60 + 1.25 x 11 = 73.75 kg at 5 reps, set 2026-07-20.
            loadTwelveWeeks()
            val viewModel = viewModel().also { it.open(TestData.BENCH) }

            viewModel.uiState.test {
                val records = expectMostRecentItem().records
                val record = records.single()
                assertEquals(5, record.reps)
                assertEquals(73.75, record.weightKg, TOLERANCE)
                assertEquals(LocalDate.parse("2026-07-20"), record.achievedOn)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a movement never performed has no records`() =
        runTest {
            loadTwelveWeeks()
            val viewModel = viewModel().also { it.open(neverPerformed.id) }

            viewModel.uiState.test {
                assertEquals(emptyList(), expectMostRecentItem().records)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private class FakeCurrentMember(
        private val id: UserId,
    ) : CurrentMember {
        override suspend fun id(): UserId = id
    }

    private class FakeUnitPreference : UnitPreference {
        private val state = MutableStateFlow(WeightUnit.LB)

        override fun observe(): Flow<WeightUnit> = state

        override suspend fun current(): WeightUnit = state.value

        override suspend fun set(unit: WeightUnit) {
            state.value = unit
        }
    }

    private companion object {
        const val TOLERANCE = 0.001
    }
}
