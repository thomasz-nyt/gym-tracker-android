package com.gymtracker.feature.routines

import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.routine.RoutineItemRepository
import com.gymtracker.core.domain.routine.RoutineRepository
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetRepository
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Hand-written fakes for the routines screens, per `specs/testing-strategy.md`.
 *
 * Copied in shape from the logging module's rather than shared across modules: a fake is part
 * of the suite that owns it, and a cross-module test fixture would couple two features that
 * have no production dependency on each other.
 */
internal class FakeRoutines(
    private val cascade: (RoutineId) -> Unit = {},
) : RoutineRepository {
    private val state = MutableStateFlow(emptyList<Routine>())

    val all: List<Routine> get() = state.value

    override fun observeRoutines(userId: UserId): Flow<List<Routine>> =
        state.map { rows -> rows.filter { it.userId == userId }.sortedBy { it.position } }

    override suspend fun find(id: RoutineId): Routine? = state.value.firstOrNull { it.id == id }

    override suspend fun add(routine: Routine) {
        state.value = state.value + routine
    }

    override suspend fun rename(
        id: RoutineId,
        name: String,
    ) {
        state.value = state.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun delete(id: RoutineId) {
        state.value = state.value.filterNot { it.id == id }
        cascade(id)
    }

    override suspend fun nextRoutinePosition(userId: UserId): Int =
        (state.value.filter { it.userId == userId }.maxOfOrNull { it.position } ?: 0) + 1
}

internal class FakeRoutineItems : RoutineItemRepository {
    private val state = MutableStateFlow(emptyList<RoutineItem>())

    val all: List<RoutineItem> get() = state.value

    fun cascadeDelete(routineId: RoutineId) {
        state.value = state.value.filterNot { it.routineId == routineId }
    }

    override fun observeItems(routineId: RoutineId): Flow<List<RoutineItem>> = state.map { forRoutine(routineId) }

    override suspend fun itemsOf(routineId: RoutineId): List<RoutineItem> = forRoutine(routineId)

    override suspend fun addItem(item: RoutineItem) {
        state.value = state.value + item
    }

    override suspend fun removeItem(id: RoutineItemId) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun nextItemPosition(routineId: RoutineId): Int =
        (forRoutine(routineId).maxOfOrNull { it.position } ?: 0) + 1

    override suspend fun setItemPositions(positions: Map<RoutineItemId, Int>) {
        state.value = state.value.map { item -> positions[item.id]?.let { item.copy(position = it) } ?: item }
    }

    private fun forRoutine(routineId: RoutineId) =
        state.value.filter { it.routineId == routineId }.sortedBy { it.position }
}

internal class FakeCatalog(
    names: Map<String, String> = mapOf("bench" to "Bench Press", "squat" to "Squat", "row" to "Seated Cable Rows"),
) : ExerciseCatalog {
    private val all =
        names.map { (id, name) ->
            Exercise(
                id = ExerciseId(id),
                name = name,
                aliases = emptyList(),
                primaryMuscles = emptyList(),
                secondaryMuscles = emptyList(),
                equipment = Equipment.MACHINE,
                instructions = emptyList(),
                mediaUrl = null,
                mediaType = null,
                youtubeUrl = null,
                source = "test",
            )
        }

    override fun observeRanked(forMember: UserId): Flow<List<Exercise>> = MutableStateFlow(all)
}

internal class FakeCurrentMember(
    private val id: UserId,
) : CurrentMember {
    override suspend fun id(): UserId = id
}

internal class FakeUnitPreference(
    initial: WeightUnit = WeightUnit.LB,
) : UnitPreference {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<WeightUnit> = state

    override suspend fun current(): WeightUnit = state.value

    override suspend fun set(unit: WeightUnit) {
        state.value = unit
    }
}

internal class FakeSets : SetRepository {
    private val state = MutableStateFlow(emptyList<ExerciseSet>())
    val lastFor = mutableMapOf<ExerciseId, String>()

    fun seed(set: ExerciseSet) {
        state.value = state.value + set
    }

    override fun observeForSessionExercise(sessionExerciseId: SessionExerciseId): Flow<List<ExerciseSet>> =
        state.map { rows -> rows.filter { it.sessionExerciseId == sessionExerciseId } }

    override fun observeForSessions(sessionIds: List<SessionId>): Flow<List<ExerciseSet>> = state

    override suspend fun lastSetOf(
        exerciseId: ExerciseId,
        member: UserId,
    ): ExerciseSet? = lastFor[exerciseId]?.let { id -> state.value.firstOrNull { it.id == id } }

    override suspend fun lastSetOfBefore(
        exerciseId: ExerciseId,
        member: UserId,
        excludingSessionId: SessionId,
    ): ExerciseSet? = null

    override suspend fun lastSetAtInSession(sessionId: SessionId): Instant? = null

    override suspend fun nextSetIndex(sessionExerciseId: SessionExerciseId): Int = 1

    override suspend fun add(set: ExerciseSet) {
        state.value = state.value + set
    }

    override suspend fun update(set: ExerciseSet) = Unit

    override suspend fun delete(id: String): ExerciseSet? = null
}

internal class FakeSessions(
    initial: List<WorkoutSession> = emptyList(),
) : SessionRepository {
    private val state = MutableStateFlow(initial)

    val all: List<WorkoutSession> get() = state.value

    override fun observeActiveSession(userId: UserId): Flow<WorkoutSession?> =
        state.map { rows -> rows.lastOrNull { it.userId == userId && it.endedAt == null } }

    override fun observeFinishedSessions(userId: UserId): Flow<List<WorkoutSession>> =
        state.map { rows -> rows.filter { it.endedAt != null } }

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
    }
}

internal class FakeSessionExercises : SessionExerciseRepository {
    private val state = MutableStateFlow(emptyList<SessionExercise>())

    val all: List<SessionExercise> get() = state.value

    fun forSession(sessionId: SessionId): List<SessionExercise> =
        state.value.filter { it.sessionId == sessionId }.sortedBy { it.position }

    override fun observeForSession(sessionId: SessionId): Flow<List<SessionExercise>> =
        state.map { forSession(sessionId) }

    override fun observeForSessions(sessionIds: List<SessionId>): Flow<List<SessionExercise>> = state

    override suspend fun find(id: SessionExerciseId): SessionExercise? = state.value.firstOrNull { it.id == id }

    override suspend fun add(sessionExercise: SessionExercise) {
        state.value = state.value + sessionExercise
    }

    override suspend fun remove(id: SessionExerciseId) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun nextPosition(sessionId: SessionId): Int =
        (forSession(sessionId).maxOfOrNull { it.position } ?: 0) + 1
}
