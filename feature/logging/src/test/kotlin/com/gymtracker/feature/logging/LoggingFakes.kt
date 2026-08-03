package com.gymtracker.feature.logging

import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.guided.GuidedPlan
import com.gymtracker.core.domain.guided.GuidedPlanStore
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.RestTimerStore
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetRepository
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

/**
 * The hand-written fakes the logging tests share, per `specs/testing-strategy.md`.
 *
 * Lifted out of `ActiveSessionViewModelTest` when that class outgrew detekt's size limit and
 * the guided-flow tests moved to [GuidedFlowTest]. They emulate SQL semantics — the cascades
 * especially — because that is what makes a fake worth having over a mock.
 */

internal class FakeGuidedPlanStore : GuidedPlanStore {
    private val state = MutableStateFlow<GuidedPlan?>(null)

    override val plan: Flow<GuidedPlan?> = state

    override suspend fun setPlan(plan: GuidedPlan?) {
        state.value = plan
    }
}

internal class FakeRestTimerStore : RestTimerStore {
    private val endsAt = MutableStateFlow<java.time.Instant?>(null)
    private val default = MutableStateFlow(Duration.ofSeconds(60))
    private val asked = MutableStateFlow(false)

    override val restEndsAt = endsAt
    override val defaultRest = default
    override val shouldAskForNotificationPermission = asked.map { !it }

    override suspend fun setRestEndsAt(instant: java.time.Instant?) {
        endsAt.value = instant
    }

    override suspend fun setDefaultRest(rest: Duration) {
        default.value = rest
    }

    override suspend fun markNotificationPermissionAsked() {
        asked.value = true
    }
}

internal class FakeUnitPreference : UnitPreference {
    private val state = MutableStateFlow(WeightUnit.LB)

    override fun observe(): Flow<WeightUnit> = state

    override suspend fun current(): WeightUnit = state.value

    override suspend fun set(unit: WeightUnit) {
        state.value = unit
    }
}

/**
 * @param sessionOf stands in for the join through `session_exercises` that gives a set its
 *   session (ADR-0004). Sets know their appearance; only that table knows the session.
 */
internal class FakeSets(
    private val sessionOf: (SessionExerciseId) -> SessionId?,
) : SetRepository {
    private val state = MutableStateFlow(emptyList<ExerciseSet>())
    val lastFor = mutableMapOf<ExerciseId, String>()

    val all: List<ExerciseSet> get() = state.value

    fun seed(set: ExerciseSet) {
        state.value = state.value + set
    }

    /** Stands in for the `ON DELETE CASCADE` from `sessions` through `session_exercises`. */
    fun cascadeDelete(sessionId: SessionId) {
        state.value = state.value.filterNot { sessionOf(it.sessionExerciseId) == sessionId }
    }

    /** Stands in for the `ON DELETE CASCADE` from one `session_exercises` row (US-02c). */
    fun cascadeDeleteExercise(sessionExerciseId: SessionExerciseId) {
        state.value = state.value.filterNot { it.sessionExerciseId == sessionExerciseId }
    }

    override fun observeForSessionExercise(sessionExerciseId: SessionExerciseId): Flow<List<ExerciseSet>> =
        state.map { rows -> rows.filter { it.sessionExerciseId == sessionExerciseId }.sortedBy { it.setIndex } }

    override fun observeForSessions(sessionIds: List<SessionId>): Flow<List<ExerciseSet>> =
        state.map { rows -> rows.filter { sessionOf(it.sessionExerciseId) in sessionIds } }

    override suspend fun lastSetOf(
        exerciseId: ExerciseId,
        member: UserId,
    ): ExerciseSet? = lastFor[exerciseId]?.let { id -> state.value.firstOrNull { it.id == id } }

    override suspend fun lastSetAtInSession(sessionId: SessionId): Instant? =
        state.value.filter { sessionOf(it.sessionExerciseId) == sessionId }.maxOfOrNull { it.performedAt }

    override suspend fun nextSetIndex(sessionExerciseId: SessionExerciseId): Int =
        state.value.count { it.sessionExerciseId == sessionExerciseId } + 1

    override suspend fun add(set: ExerciseSet) {
        state.value = state.value + set
    }
}

internal class FakeCatalog : ExerciseCatalog {
    private fun exercise(
        id: String,
        name: String,
    ) = Exercise(
        id = ExerciseId(id),
        name = name,
        aliases = emptyList(),
        primaryMuscles = emptyList(),
        secondaryMuscles = emptyList(),
        equipment = Equipment.BARBELL,
        instructions = emptyList(),
        mediaUrl = null,
        mediaType = null,
        youtubeUrl = null,
        source = "test",
    )

    private val all = listOf(exercise("bench", "Bench Press"), exercise("squat", "Squat"))

    // Ranking is all this has to supply now; narrowing is CatalogQuery's, and the
    // interface's search() runs it for us. The fake no longer reimplements matching,
    // so it cannot drift from the real thing.
    override fun observeRanked(forMember: UserId): Flow<List<Exercise>> = MutableStateFlow(all)
}

internal class FakeSessionExercises(
    private val cascade: (SessionExerciseId) -> Unit = {},
) : SessionExerciseRepository {
    private val state = MutableStateFlow(emptyList<SessionExercise>())

    val all: List<SessionExercise> get() = state.value

    /** Stands in for the `ON DELETE CASCADE` on `session_exercises.session_id`. */
    fun cascadeDelete(sessionId: SessionId) {
        state.value = state.value.filterNot { it.sessionId == sessionId }
    }

    override fun observeForSession(sessionId: SessionId): Flow<List<SessionExercise>> =
        state.map { rows -> rows.filter { it.sessionId == sessionId }.sortedBy { it.position } }

    override fun observeForSessions(sessionIds: List<SessionId>): Flow<List<SessionExercise>> =
        state.map { rows -> rows.filter { it.sessionId in sessionIds }.sortedBy { it.position } }

    override suspend fun find(id: SessionExerciseId): SessionExercise? = state.value.firstOrNull { it.id == id }

    override suspend fun add(sessionExercise: SessionExercise) {
        state.value = state.value + sessionExercise
    }

    override suspend fun remove(id: SessionExerciseId) {
        state.value = state.value.filterNot { it.id == id }
        cascade(id)
    }

    // MAX(position) + 1, as the DAO does it. A count would reuse a position after a
    // removal from the middle of a session (US-02c).
    override suspend fun nextPosition(sessionId: SessionId): Int =
        (state.value.filter { it.sessionId == sessionId }.maxOfOrNull { it.position } ?: 0) + 1
}

internal class FakeCurrentMember(
    private val id: UserId,
) : CurrentMember {
    override suspend fun id(): UserId = id
}

internal class FakeSessions(
    initial: List<WorkoutSession> = emptyList(),
    private val cascade: (SessionId) -> Unit = {},
) : SessionRepository {
    private val state = MutableStateFlow(initial)

    val all: List<WorkoutSession> get() = state.value

    override fun observeActiveSession(userId: UserId): Flow<WorkoutSession?> =
        state.map { sessions -> sessions.lastOrNull { it.userId == userId && it.endedAt == null } }

    override fun observeFinishedSessions(userId: UserId): Flow<List<WorkoutSession>> =
        state.map { sessions ->
            sessions
                .filter { it.userId == userId && it.endedAt != null }
                .sortedByDescending { it.startedAt }
        }

    override suspend fun findActiveSession(userId: UserId): WorkoutSession? =
        state.value.lastOrNull { it.userId == userId && it.endedAt == null }

    override suspend fun findSession(id: SessionId): WorkoutSession? = state.value.firstOrNull { it.id == id }

    override suspend fun startSession(session: WorkoutSession) {
        state.value = state.value + session
    }

    override suspend fun restoreSession(session: WorkoutSession) {
        state.value = state.value + session
    }

    override suspend fun endSession(
        id: SessionId,
        endedAt: Instant,
    ) {
        state.value = state.value.map { if (it.id == id) it.copy(endedAt = endedAt) else it }
    }

    override suspend fun deleteSession(id: SessionId) {
        state.value = state.value.filterNot { it.id == id }
        cascade(id)
    }
}
