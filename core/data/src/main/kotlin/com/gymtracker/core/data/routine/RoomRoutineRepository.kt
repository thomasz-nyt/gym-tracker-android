package com.gymtracker.core.data.routine

import com.gymtracker.core.data.session.SYNC_STATE_PENDING
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.routine.RoutineItemRepository
import com.gymtracker.core.domain.routine.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/** [RoutineRepository] over Room. */
class RoomRoutineRepository
    @Inject
    constructor(
        private val dao: RoutineDao,
    ) : RoutineRepository {
        override fun observeRoutines(userId: UserId): Flow<List<Routine>> =
            dao.observeRoutines(userId.value).map { rows -> rows.map { it.toDomain() } }

        override suspend fun find(id: RoutineId): Routine? = dao.find(id.value)?.toDomain()

        override suspend fun add(routine: Routine) {
            val now = Instant.now().toEpochMilli()
            dao.insert(
                RoutineEntity(
                    id = routine.id.value,
                    userId = routine.userId.value,
                    name = routine.name,
                    position = routine.position,
                    createdAt = now,
                    updatedAt = now,
                    syncState = SYNC_STATE_PENDING,
                ),
            )
        }

        override suspend fun rename(
            id: RoutineId,
            name: String,
        ) {
            dao.rename(id.value, name, Instant.now().toEpochMilli())
        }

        override suspend fun delete(id: RoutineId) {
            dao.delete(id.value)
        }

        override suspend fun nextRoutinePosition(userId: UserId): Int = dao.maxPosition(userId.value) + 1
    }

/** [RoutineItemRepository] over Room. */
class RoomRoutineItemRepository
    @Inject
    constructor(
        private val dao: RoutineItemDao,
    ) : RoutineItemRepository {
        override fun observeItems(routineId: RoutineId): Flow<List<RoutineItem>> =
            dao.observeItems(routineId.value).map { rows -> rows.map { it.toDomain() } }

        override suspend fun itemsOf(routineId: RoutineId): List<RoutineItem> =
            dao.itemsOf(routineId.value).map { it.toDomain() }

        override suspend fun addItem(item: RoutineItem) {
            dao.insert(item.toEntity())
        }

        override suspend fun updateItem(item: RoutineItem) {
            dao.update(item.toEntity())
        }

        override suspend fun removeItem(id: RoutineItemId) {
            dao.delete(id.value)
        }

        override suspend fun nextItemPosition(routineId: RoutineId): Int = dao.maxPosition(routineId.value) + 1

        override suspend fun setItemPositions(positions: Map<RoutineItemId, Int>) {
            dao.setPositions(
                positions.entries.associate { (id, position) -> id.value to position },
                Instant.now().toEpochMilli(),
            )
        }
    }
