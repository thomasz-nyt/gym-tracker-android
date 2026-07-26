package com.gymtracker.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gymtracker.core.data.session.SessionDao
import com.gymtracker.core.data.session.SessionEntity

/**
 * The local database, which is the source of truth for the UI (constitution §2).
 *
 * Only `sessions` exists so far. `exercises`, `session_exercises`, `sets` and `sync_queue`
 * from `data-model.md` arrive with the stories that need them.
 */
@Database(
    entities = [SessionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class GymTrackerDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "gym-tracker.db"
    }
}
