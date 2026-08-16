package com.gymtracker.core.data.backup

import androidx.room.withTransaction
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.routine.toDomain
import com.gymtracker.core.data.routine.toEntity
import com.gymtracker.core.data.session.toDomain
import com.gymtracker.core.data.session.toEntity
import com.gymtracker.core.data.sessionexercise.toDomain
import com.gymtracker.core.data.sessionexercise.toEntity
import com.gymtracker.core.data.set.toDomain
import com.gymtracker.core.data.set.toEntity
import com.gymtracker.core.domain.backup.BackupContents
import com.gymtracker.core.domain.backup.BackupStore
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.rest.RestTimerStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * [BackupStore] over Room and DataStore (US-40, US-41, ADR-0034).
 *
 * Takes [GymTrackerDatabase] itself rather than one DAO per table — every other Room-backed
 * repository in this codebase is scoped to a single table and takes a single DAO, but this one
 * is deliberately not: it spans all five backed-up tables, and [replaceAll] needs
 * `database.withTransaction` regardless, so the database is the natural thing to hold.
 */
class RoomBackupStore
    @Inject
    constructor(
        private val database: GymTrackerDatabase,
        private val unitPreference: UnitPreference,
        private val restTimerStore: RestTimerStore,
        private val currentMember: CurrentMember,
    ) : BackupStore {
        override suspend fun read(memberId: UserId): BackupContents {
            val userId = memberId.value
            return BackupContents(
                memberId = memberId,
                unit = unitPreference.current(),
                restDefault = restTimerStore.defaultRest.first(),
                sessions = database.sessionDao().allForUser(userId).map { it.toDomain() },
                sessionExercises = database.sessionExerciseDao().allForUser(userId).map { it.toDomain() },
                sets = database.setDao().allForUser(userId).map { it.toDomain() },
                routines = database.routineDao().allForUser(userId).map { it.toDomain() },
                routineItems = database.routineItemDao().allForUser(userId).map { it.toDomain() },
            )
        }

        /**
         * The SQL half runs inside one `withTransaction`, so a failure partway through leaves
         * the previous data intact rather than a half-wiped table (ADR-0034). The identity and
         * preference writes that follow are not part of that transaction — DataStore has no
         * transactional relationship to Room — but they only run once the transaction has
         * already committed successfully, never before.
         */
        override suspend fun replaceAll(contents: BackupContents) {
            val userId = contents.memberId.value

            database.withTransaction {
                database.sessionDao().deleteAllForUser(userId)
                database.routineDao().deleteAllForUser(userId)

                database.sessionDao().insertAll(contents.sessions.map { it.toEntity() })
                database.sessionExerciseDao().insertAll(contents.sessionExercises.map { it.toEntity() })
                database.setDao().insertAll(contents.sets.map { it.toEntity() })
                database.routineDao().insertAll(contents.routines.map { it.toEntity() })
                database.routineItemDao().insertAll(contents.routineItems.map { it.toEntity() })
            }

            currentMember.restore(contents.memberId)
            unitPreference.set(contents.unit)
            restTimerStore.setDefaultRest(contents.restDefault)
        }
    }
