package com.gymtracker.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gymtracker.core.data.exercise.ExerciseDao
import com.gymtracker.core.data.exercise.ExerciseEntity
import com.gymtracker.core.data.session.SessionDao
import com.gymtracker.core.data.session.SessionEntity

/**
 * The local database, which is the source of truth for the UI (constitution §2).
 *
 * `session_exercises`, `sets` and `sync_queue` from `data-model.md` arrive with the stories
 * that need them.
 */
@Database(
    entities = [SessionEntity::class, ExerciseEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class GymTrackerDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun exerciseDao(): ExerciseDao

    companion object {
        const val NAME = "gym-tracker.db"

        /**
         * Adds the catalog table (US-02). Purely additive — `sessions` is untouched, so a
         * device that already has an active session keeps it.
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `exercises` (
                            `id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `aliases_json` TEXT NOT NULL,
                            `primary_json` TEXT NOT NULL,
                            `secondary_json` TEXT NOT NULL,
                            `equipment` TEXT NOT NULL,
                            `instructions_json` TEXT NOT NULL,
                            `media_url` TEXT,
                            `media_type` TEXT,
                            `youtube_url` TEXT,
                            `source` TEXT NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)")
                }
            }
    }
}
