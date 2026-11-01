package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Net membership/order changes to inspect after the workout is already saved. */
data class RoutineChangeReview(
    val session: WorkoutSession,
    val original: Routine?,
    val originalItems: List<RoutineItem>,
    val movements: List<SessionExercise>,
    val signature: String,
    val canUpdateOriginal: Boolean,
)

/** Reuse is always an explicit choice; actual sets never become authored targets. */
enum class RoutineReuseChoice { UPDATE_ORIGINAL, SAVE_AS_NEW, TODAY_ONLY }

/** A template failure never rolls back or hides the independently saved workout. */
sealed interface RoutineReuseResult {
    data class Saved(
        val routineId: RoutineId,
    ) : RoutineReuseResult

    data object TodayOnly : RoutineReuseResult

    data object Conflict : RoutineReuseResult

    data class Failed(
        val message: String,
    ) : RoutineReuseResult
}

/** Reviewed net structure reuse, isolated from ordinary performance logging (US-65, ADR-0051). */
class RoutineReuse(
    private val sessions: SessionRepository,
    private val movements: SessionExerciseRepository,
    private val routines: RoutineRepository,
    private val items: RoutineItemRepository,
    private val store: RoutineTemplateStore,
    private val newId: () -> String,
) {
    /** Returns null for unchanged, active, foreign or already handled workouts. */
    suspend fun preview(
        id: SessionId,
        member: UserId,
    ): RoutineChangeReview? {
        val session = sessions.findSession(id)?.takeIf { it.userId == member && !it.isActive && it.routine != null }
        return session?.let { changedReview(it, member) }
    }

    private suspend fun changedReview(
        session: WorkoutSession,
        member: UserId,
    ): RoutineChangeReview? {
        val source = checkNotNull(session.routine)
        val baseline = session.workflow.originalRoutineItems.sortedBy { it.position }
        val current = movements.observeForSession(session.id).first().sortedBy { it.position }
        val originalKeys = baseline.map { it.id.value to it.exerciseId.value }
        val currentKeys = current.map { (it.routineItemId ?: "new:${it.id.value}") to it.exerciseId.value }
        val signature =
            currentKeys.joinToString(
                "\n",
            ) { (id, exercise) -> "${id.length}:$id${exercise.length}:$exercise" }
        val baselineKnown = baseline.isNotEmpty() || session.workflow.structuralEdits
        if (!baselineKnown ||
            originalKeys == currentKeys ||
            session.workflow.reviewedStructure == signature
        ) {
            return null
        }
        val original = routines.find(RoutineId(source.id))?.takeIf { it.userId == member }
        val originalItems = original?.let { items.itemsOf(it.id) }.orEmpty()
        return RoutineChangeReview(
            session,
            original,
            originalItems,
            current,
            signature,
            original != null && original.name == source.name && originalItems == baseline,
        )
    }

    /**
     * Rechecks the preview and delegates atomic comparison/replacement; failures retain the review.
     * IO and SQL failures have unrelated types; return a retryable result after the workout is saved.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun apply(
        review: RoutineChangeReview,
        choice: RoutineReuseChoice,
        name: String? = null,
    ): RoutineReuseResult =
        try {
            applyReviewed(review, choice, name)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            RoutineReuseResult.Failed(error.message ?: "Could not save the routine. Your workout is saved.")
        }

    private suspend fun applyReviewed(
        review: RoutineChangeReview,
        choice: RoutineReuseChoice,
        name: String?,
    ): RoutineReuseResult {
        val latest = preview(review.session.id, review.session.userId)
        if (latest == null || latest.signature != review.signature) return RoutineReuseResult.Conflict
        return when (choice) {
            RoutineReuseChoice.TODAY_ONLY ->
                if (store.reviewed(
                        latest.session,
                        latest.signature,
                    )
                ) {
                    RoutineReuseResult.TodayOnly
                } else {
                    RoutineReuseResult.Conflict
                }
            RoutineReuseChoice.UPDATE_ORIGINAL ->
                if (!latest.canUpdateOriginal || !review.canUpdateOriginal) {
                    RoutineReuseResult.Conflict
                } else {
                    save(latest, checkNotNull(latest.original), update = true)
                }
            RoutineReuseChoice.SAVE_AS_NEW -> {
                val title = name?.trim().orEmpty()
                if (title.isBlank()) {
                    RoutineReuseResult.Failed("Enter a name for the new routine.")
                } else {
                    save(
                        latest,
                        Routine(
                            RoutineId(newId()),
                            latest.session.userId,
                            title,
                            routines.nextRoutinePosition(latest.session.userId),
                        ),
                        update = false,
                    )
                }
            }
        }
    }

    private suspend fun save(
        review: RoutineChangeReview,
        replacement: Routine,
        update: Boolean,
    ): RoutineReuseResult {
        val source =
            review.session.workflow.originalRoutineItems
                .associateBy { it.id.value }
        val replacements =
            review.movements.mapIndexed { index, movement ->
                val authored = movement.routineItemId?.let(source::get)?.takeIf { it.exerciseId == movement.exerciseId }
                val target = authored?.target ?: movement.target.takeIf { movement.routineItemId == null }
                RoutineItem(
                    if (update && authored != null) authored.id else RoutineItemId(newId()),
                    replacement.id,
                    movement.exerciseId,
                    index + 1,
                    target,
                )
            }
        val write =
            RoutineTemplateWrite(
                if (update) review.original else null,
                if (update) review.originalItems else emptyList(),
                replacement,
                replacements,
                review.session,
                review.signature,
            )
        return if (store.apply(write)) RoutineReuseResult.Saved(replacement.id) else RoutineReuseResult.Conflict
    }
}
