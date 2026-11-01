package com.gymtracker.core.data.routine

import androidx.room.withTransaction
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.session.RoomSessionRepository
import com.gymtracker.core.data.session.toDomain
import com.gymtracker.core.data.sync.SyncPayloadCodec
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.routine.RoutineTemplateStore
import com.gymtracker.core.domain.routine.RoutineTemplateWrite
import javax.inject.Inject

/** Compare, replace and mark reviewed in one transaction; actual workout rows remain snapshots. */
class RoomRoutineTemplateStore
    @Inject
    constructor(
        private val database: GymTrackerDatabase,
        codec: SyncPayloadCodec,
    ) : RoutineTemplateStore {
        private val routines = RoomRoutineRepository(database.routineDao(), database, codec)
        private val items = RoomRoutineItemRepository(database.routineItemDao(), database, codec)
        private val sessions = RoomSessionRepository(database.sessionDao(), database, codec)

        override suspend fun apply(write: RoutineTemplateWrite): Boolean =
            database.withTransaction {
                val current = finishedSnapshot(write.session)
                if (current == null || !matchesOriginal(write)) {
                    false
                } else {
                    val replacement = write.replacement
                    check(replacement.userId == current.userId) { "Routine owner changed" }
                    check(write.replacementItems.all { it.routineId == replacement.id }) { "Invalid movement parent" }
                    if (write.expectedRoutine == null) {
                        routines.add(replacement.copy(position = routines.nextRoutinePosition(replacement.userId)))
                    }
                    items.itemsOf(replacement.id).forEach { items.removeItem(it.id) }
                    write.replacementItems.forEach { items.addItem(it) }
                    mark(current, write.signature)
                    true
                }
            }

        override suspend fun reviewed(
            session: WorkoutSession,
            signature: String,
        ): Boolean =
            database.withTransaction {
                val current = finishedSnapshot(session)
                if (current == null) {
                    false
                } else {
                    mark(current, signature)
                    true
                }
            }

        private suspend fun finishedSnapshot(expected: WorkoutSession): WorkoutSession? =
            database.sessionDao().find(expected.id.value)?.toDomain()?.takeIf {
                !it.isActive &&
                    it.userId == expected.userId &&
                    it.endedAt == expected.endedAt &&
                    it.routine == expected.routine
            }

        private suspend fun matchesOriginal(write: RoutineTemplateWrite): Boolean {
            val expected = write.expectedRoutine
            val current = routines.find(write.replacement.id)
            return if (expected == null) {
                current == null
            } else {
                current == expected && items.itemsOf(expected.id) == write.expectedItems
            }
        }

        private suspend fun mark(
            current: WorkoutSession,
            signature: String,
        ) {
            sessions.updateSession(current.copy(workflow = current.workflow.copy(reviewedStructure = signature)))
        }
    }
