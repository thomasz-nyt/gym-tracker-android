package com.gymtracker.feature.progress

import app.cash.turbine.test
import com.gymtracker.core.domain.TestData
import com.gymtracker.core.domain.exercise.FakeExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.progress.WeeklyVolumeByBodyPart
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * US-17 and US-19 as the volume screen sees them.
 *
 * The arithmetic is [WeeklyVolumeByBodyPart]'s and is tested in `:core:domain` against
 * hand-computed figures. What is asserted here is the wiring: the right window of weeks, the
 * order they are read in, and — the two that matter — that a muscle trained by two movements
 * arrives as **one** bar, and that "nothing logged" arrives as a state rather than as a row of
 * zero-length bars.
 *
 * The clock is fixed. `TestData` is pinned to May 2026, so a window measured from
 * `Instant.now()` would slide off the fixture and this file would start passing or failing by
 * the calendar (`specs/testing-strategy.md` § Fixture data).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyVolumeViewModelTest {
    private val sessions = FakeSessionRepository()
    private val sessionExercises = FakeSessionExerciseRepository()
    private val sets = FakeSetRepository()
    private val catalog = FakeExerciseCatalog(TestData.exercises)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        member: UserId = TestData.PROGRESSING,
        today: String = LAST_FIXTURE_SUNDAY,
    ) = WeeklyVolumeViewModel(
        weeklyVolume = WeeklyVolumeByBodyPart(sessions, sessionExercises, sets, catalog, ZoneOffset.UTC),
        currentMember = FakeCurrentMember(member),
        unitPreference = FakeUnitPreference(),
        clock = Clock.fixed(Instant.parse("${today}T12:00:00Z"), ZoneOffset.UTC),
        zone = ZoneOffset.UTC,
    )

    private suspend fun load(fixture: TestData.Fixture) {
        fixture.sessions.forEach { sessions.startSession(it) }
        fixture.sessionExercises.forEach {
            sessionExercises.add(it)
            sets.belongsTo(it)
        }
        fixture.sets.forEach { sets.add(it) }
    }

    @Test
    fun `the window is the last eight weeks, most recent first`() =
        runTest {
            load(TestData.twelveWeeksOfProgress())
            val viewModel = viewModel().also { it.open() }

            viewModel.uiState.test {
                val weeks = expectMostRecentItem().weeks
                assertEquals(WEEKS_SHOWN, weeks.size)
                // Most recent first, the way the history list already reads: the week you are
                // in is the one you came to look at.
                assertEquals(LocalDate.parse("2026-07-20"), weeks.first().weekStarting)
                assertEquals(LocalDate.parse("2026-06-01"), weeks.last().weekStarting)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a muscle trained by two movements is one bar, not two`() =
        runTest {
            // US-17 groups by muscle, not by exercise. Rows and pulldowns are both BACK, so the
            // week shows one Back bar carrying the sum — 58.75x15 + 63.75x15 in the last week.
            load(TestData.twelveWeeksOfProgress())
            val viewModel = viewModel().also { it.open() }

            viewModel.uiState.test {
                val latest = expectMostRecentItem().weeks.first()
                val back = latest.byBodyPart.filter { it.bodyPart == BodyPart.BACK }

                assertEquals(1, back.size, "back appears once, got ${latest.byBodyPart}")
                assertEquals(1837.5, back.single().volumeKg, TOLERANCE)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `each week reads heaviest muscle first`() =
        runTest {
            load(TestData.twelveWeeksOfProgress())
            val viewModel = viewModel().also { it.open() }

            viewModel.uiState.test {
                val latest = expectMostRecentItem().weeks.first()
                assertEquals(
                    listOf(BodyPart.HAMSTRINGS, BodyPart.BACK, BodyPart.QUADS, BodyPart.CHEST),
                    latest.byBodyPart.map { it.bodyPart },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the busiest muscle-week in the window is what the bars are drawn against`() =
        runTest {
            // Every bar in the range shares one scale, so a light week looks lighter than a
            // heavy one. Scaling per week would draw every week's top muscle full-width and
            // claim they were equal. The peak here is hamstrings in the last week: 127.5x15.
            load(TestData.twelveWeeksOfProgress())
            val viewModel = viewModel().also { it.open() }

            viewModel.uiState.test {
                assertEquals(1912.5, expectMostRecentItem().peakVolumeKg, TOLERANCE)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a week with no training is still a week, not a gap`() =
        runTest {
            // The domain returns empty weeks on purpose — closing the gap would imply you
            // trained every week. The screen has to keep them, so the run of blank weeks after
            // one session is visible as a run.
            load(TestData.oneSessionOnly(TestData.SPARSE))
            val viewModel = viewModel(member = TestData.SPARSE, today = "2026-06-14").also { it.open() }

            viewModel.uiState.test {
                val weeks = expectMostRecentItem().weeks
                assertEquals(WEEKS_SHOWN, weeks.size)
                assertEquals(1, weeks.count { it.byBodyPart.isNotEmpty() }, "one week was trained")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a member who has logged nothing gets the honest empty state, not a floor of zeroes`() =
        runTest {
            // US-19: "with no data, charts show a clear 'not enough data yet' state rather than
            // an empty grid or a zero line."
            load(TestData.noData())
            val viewModel = viewModel(member = TestData.EMPTY).also { it.open() }

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertFalse(state.hasVolume, "nothing was logged, so there is nothing to draw")
                assertEquals(0.0, state.peakVolumeKg, TOLERANCE)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `training in the window means there is something to draw`() =
        runTest {
            load(TestData.twelveWeeksOfProgress())
            val viewModel = viewModel().also { it.open() }

            viewModel.uiState.test {
                assertTrue(expectMostRecentItem().hasVolume)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the range defaults to eight weeks`() =
        runTest {
            load(TestData.twelveWeeksOfProgress())
            val viewModel = viewModel().also { it.open() }

            viewModel.uiState.test {
                assertEquals(VolumeRange.EIGHT_WEEKS, expectMostRecentItem().range)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `choosing a wider range re-reads a wider window`() =
        runTest {
            load(TestData.twelveWeeksOfProgress())
            val viewModel = viewModel().also { it.open() }

            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.onRangeChanged(VolumeRange.TWELVE_WEEKS)

                val state = expectMostRecentItem()
                assertEquals(VolumeRange.TWELVE_WEEKS, state.range)
                assertEquals(12, state.weeks.size)
                assertEquals(LocalDate.parse("2026-07-20"), state.weeks.first().weekStarting)
                assertEquals(LocalDate.parse("2026-05-04"), state.weeks.last().weekStarting)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `choosing a narrower range re-reads a narrower window`() =
        runTest {
            load(TestData.twelveWeeksOfProgress())
            val viewModel = viewModel().also { it.open() }

            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.onRangeChanged(VolumeRange.FOUR_WEEKS)

                val state = expectMostRecentItem()
                assertEquals(VolumeRange.FOUR_WEEKS, state.range)
                assertEquals(4, state.weeks.size)
                assertEquals(LocalDate.parse("2026-07-20"), state.weeks.first().weekStarting)
                assertEquals(LocalDate.parse("2026-06-29"), state.weeks.last().weekStarting)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the member's unit reaches the screen, so the loads can be labelled`() =
        runTest {
            load(TestData.twelveWeeksOfProgress())
            val viewModel = viewModel().also { it.open() }

            viewModel.uiState.test {
                assertEquals(WeightUnit.LB, expectMostRecentItem().unit)
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
        /** The Sunday closing `TestData`'s twelfth week, so the window ends on a full week. */
        const val LAST_FIXTURE_SUNDAY = "2026-07-26"
        const val TOLERANCE = 0.001
    }
}
