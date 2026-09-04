package com.gymtracker.core.data.member

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.withTransaction
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.session.SYNC_STATE_PENDING
import com.gymtracker.core.data.sync.SyncEntityNames
import com.gymtracker.core.data.sync.SyncPayloadCodec
import com.gymtracker.core.data.sync.syncWriteEntry
import com.gymtracker.core.domain.member.AccountAdoption
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

/**
 * [AccountAdoption] over Room and DataStore (US-58, ADR-0042).
 *
 * Implemented directly over [GymTrackerDatabase], the same shape as
 * [com.gymtracker.core.data.backup.RoomBackupStore] and for the same reason: the re-key spans
 * two tables (`sessions`, `routines`) that each have their own repository, and only the
 * database itself can wrap both in one transaction.
 *
 * `has_completed_first_sign_in` lives in the same [DataStore] as `local_member_id`
 * (`DataStoreCurrentMember`), per ADR-0005's rule that both describe this install, not a row
 * any table holds — kept private here since nothing outside this class needs to read it.
 */
class RoomAccountAdoption
    @Inject
    constructor(
        private val database: GymTrackerDatabase,
        private val preferences: DataStore<Preferences>,
        private val currentMember: CurrentMember,
        private val codec: SyncPayloadCodec,
    ) : AccountAdoption {
        override suspend fun adopt(signedInAs: UserId) {
            if (!hasAdoptedAccount()) {
                reassign(signedInAs)
                markAdopted()
            }
            currentMember.restore(signedInAs)
        }

        /**
         * Re-keys every `sessions` and `routines` row the device's *current* member id owns —
         * read before either raw `UPDATE` runs, since a bulk `UPDATE` reports a row count, not
         * the rows it touched, and the outbox needs the full post-update row to build its
         * payload. Building the "after" row in Kotlin from the "before" one avoids a second
         * round trip per row: both raw queries below touch exactly `user_id`, `updated_at` and
         * `sync_state`, and nothing else, so `.copy()` can reproduce the after-state exactly.
         */
        private suspend fun reassign(signedInAs: UserId) {
            val localId = currentMember.id().value
            val newId = signedInAs.value
            val updatedAt = Instant.now().toEpochMilli()

            database.withTransaction {
                val sessionsBefore = database.sessionDao().allForUser(localId)
                database.sessionDao().reassignOwner(localId, newId, updatedAt)
                sessionsBefore.forEach { before ->
                    val after = before.copy(userId = newId, updatedAt = updatedAt, syncState = SYNC_STATE_PENDING)
                    database.syncQueueDao().insert(
                        syncWriteEntry(SyncEntityNames.SESSIONS, after.id, codec.encode(after)),
                    )
                }

                val routinesBefore = database.routineDao().allForUser(localId)
                database.routineDao().reassignOwner(localId, newId, updatedAt)
                routinesBefore.forEach { before ->
                    val after = before.copy(userId = newId, updatedAt = updatedAt, syncState = SYNC_STATE_PENDING)
                    database.syncQueueDao().insert(
                        syncWriteEntry(SyncEntityNames.ROUTINES, after.id, codec.encode(after)),
                    )
                }
            }
        }

        private suspend fun hasAdoptedAccount(): Boolean = preferences.data.first()[HAS_ADOPTED] ?: false

        private suspend fun markAdopted() {
            preferences.edit { it[HAS_ADOPTED] = true }
        }

        private companion object {
            val HAS_ADOPTED = booleanPreferencesKey("has_completed_first_sign_in")
        }
    }
