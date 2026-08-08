package com.gymtracker.core.domain

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.SessionMetrics
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import java.time.Duration
import java.time.Instant

/**
 * The shared fixtures every chart and coaching test runs against
 * (`specs/testing-strategy.md` § Fixture data).
 *
 * > Do not invent ad-hoc data per test file — divergent fixtures are how chart bugs hide.
 *
 * A test-fixtures source set rather than a test one, so `:feature:progress` can reach the same
 * rows without depending on `:core:domain`'s tests.
 *
 * Everything here is **deterministic**: fixed instants, fixed progressions, no randomness and
 * no `Instant.now()`. A chart test that fails only on Tuesdays is worse than no chart test.
 */
object TestData {
    /** Twelve weeks of real training. The member most tests want. */
    val PROGRESSING = UserId("fixture-progressing")

    /** Exactly one session — US-19's sparse-data edge, where no trend may be claimed. */
    val SPARSE = UserId("fixture-sparse")

    /** Never logged anything. The empty state. */
    val EMPTY = UserId("fixture-empty")

    /** The Monday the fixtures start from. A Monday so "week" boundaries are unsurprising. */
    val FIRST_SESSION: Instant = Instant.parse("2026-05-04T18:00:00Z")

    val BENCH = ExerciseId("Barbell_Bench_Press_Medium_Grip")
    val SQUAT = ExerciseId("Barbell_Squat")
    val DEADLIFT = ExerciseId("Barbell_Deadlift")
    val ROW = ExerciseId("Seated_Cable_Rows")
    val PULLDOWN = ExerciseId("Wide-Grip_Lat_Pulldown")

    /** The five lifts, with the load each starts at and gains per week. */
    private val LIFTS =
        listOf(
            Lift(BENCH, startKg = 60.0, weeklyGainKg = 1.25),
            Lift(SQUAT, startKg = 80.0, weeklyGainKg = 2.5),
            Lift(DEADLIFT, startKg = 100.0, weeklyGainKg = 2.5),
            Lift(ROW, startKg = 45.0, weeklyGainKg = 1.25),
            Lift(PULLDOWN, startKg = 50.0, weeklyGainKg = 1.25),
        )

    private data class Lift(
        val exerciseId: ExerciseId,
        val startKg: Double,
        val weeklyGainKg: Double,
    )

    /** Rows to load into repositories, in the shape the domain reads them. */
    data class Fixture(
        val sessions: List<WorkoutSession> = emptyList(),
        val sessionExercises: List<SessionExercise> = emptyList(),
        val sets: List<ExerciseSet> = emptyList(),
    )

    /**
     * Twelve weekly sessions, all five lifts each, three sets of five.
     *
     * Progression is linear and small — the load a household actually adds — so a trend has a
     * slope a test can state exactly rather than eyeball. Week 0 is [FIRST_SESSION]; each
     * later week is seven days on.
     */
    fun twelveWeeksOfProgress(member: UserId = PROGRESSING): Fixture {
        val sessions = mutableListOf<WorkoutSession>()
        val appearances = mutableListOf<SessionExercise>()
        val sets = mutableListOf<ExerciseSet>()

        repeat(WEEKS) { week ->
            val startedAt = FIRST_SESSION.plus(Duration.ofDays(7L * week))
            val sessionId = SessionId("fixture-session-$week")
            sessions +=
                WorkoutSession(
                    id = sessionId,
                    userId = member,
                    gymName = null,
                    startedAt = startedAt,
                    endedAt = startedAt.plus(Duration.ofMinutes(SESSION_MINUTES)),
                    metrics = null,
                )

            LIFTS.forEachIndexed { position, lift ->
                val appearance = SessionExerciseId("fixture-se-$week-$position")
                appearances += SessionExercise(appearance, sessionId, lift.exerciseId, position + 1)

                val weight = lift.startKg + lift.weeklyGainKg * week
                repeat(SETS_PER_LIFT) { setIndex ->
                    sets +=
                        ExerciseSet(
                            id = "fixture-set-$week-$position-$setIndex",
                            sessionExerciseId = appearance,
                            setIndex = setIndex + 1,
                            weightKg = weight,
                            reps = REPS,
                            rpe = null,
                            // Each set its own timestamp, minutes apart, as US-05a requires of
                            // real ones — a shared timestamp is what ADR-0009 got wrong.
                            performedAt = startedAt.plus(Duration.ofMinutes(position * 10L + setIndex * 3L)),
                        )
                }
            }
        }

        return Fixture(sessions, appearances, sets)
    }

    /**
     * One session, one lift, three sets. US-19's "with a single data point, no trend line is
     * drawn and no trend is claimed".
     */
    fun oneSessionOnly(member: UserId = SPARSE): Fixture {
        val sessionId = SessionId("fixture-sparse-session")
        val appearance = SessionExerciseId("fixture-sparse-se")

        return Fixture(
            sessions =
                listOf(
                    WorkoutSession(
                        id = sessionId,
                        userId = member,
                        gymName = null,
                        startedAt = FIRST_SESSION,
                        endedAt = FIRST_SESSION.plus(Duration.ofMinutes(SESSION_MINUTES)),
                        metrics = null,
                    ),
                ),
            sessionExercises = listOf(SessionExercise(appearance, sessionId, BENCH, 1)),
            sets =
                List(SETS_PER_LIFT) { setIndex ->
                    ExerciseSet(
                        id = "fixture-sparse-set-$setIndex",
                        sessionExerciseId = appearance,
                        setIndex = setIndex + 1,
                        weightKg = 60.0,
                        reps = REPS,
                        rpe = null,
                        performedAt = FIRST_SESSION.plus(Duration.ofMinutes(setIndex * 3L)),
                    )
                },
        )
    }

    /** Nothing at all. Kept as a named thing so the empty state is tested on purpose. */
    fun noData(): Fixture = Fixture()

    /**
     * Two otherwise identical sessions, one with health metrics and one without (M5).
     *
     * The pair exists so the optional-feature suite can prove a screen renders the same
     * either way — constitution §3: a member who declines Health Connect gets a complete app,
     * and §2.4: absent metrics are absent, never zero.
     *
     * @return the session with metrics, then the one without.
     */
    fun withAndWithoutMetrics(member: UserId = PROGRESSING): Pair<WorkoutSession, WorkoutSession> {
        val base =
            WorkoutSession(
                id = SessionId("fixture-with-metrics"),
                userId = member,
                gymName = null,
                startedAt = FIRST_SESSION,
                endedAt = FIRST_SESSION.plus(Duration.ofMinutes(SESSION_MINUTES)),
                metrics = null,
            )

        return base.copy(
            metrics =
                SessionMetrics(
                    avgHeartRate = 128,
                    maxHeartRate = 164,
                    activeKilocalories = 410,
                    source = "health_connect",
                ),
        ) to base.copy(id = SessionId("fixture-without-metrics"))
    }

    private const val WEEKS = 12
    private const val SETS_PER_LIFT = 3
    private const val REPS = 5
    private const val SESSION_MINUTES = 55L
}
