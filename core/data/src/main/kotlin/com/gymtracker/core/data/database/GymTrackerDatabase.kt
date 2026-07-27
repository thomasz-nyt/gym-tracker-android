package com.gymtracker.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gymtracker.core.data.exercise.ExerciseDao
import com.gymtracker.core.data.exercise.ExerciseEntity
import com.gymtracker.core.data.session.SessionDao
import com.gymtracker.core.data.session.SessionEntity
import com.gymtracker.core.data.sessionexercise.SessionExerciseDao
import com.gymtracker.core.data.sessionexercise.SessionExerciseEntity

/**
 * The local database, which is the source of truth for the UI (constitution §2).
 *
 * `session_exercises`, `sets` and `sync_queue` from `data-model.md` arrive with the stories
 * that need them.
 */
@Database(
    entities = [SessionEntity::class, ExerciseEntity::class, SessionExerciseEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class GymTrackerDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun exerciseDao(): ExerciseDao

    abstract fun sessionExerciseDao(): SessionExerciseDao

    companion object {
        const val NAME = "gym-tracker.db"

        // Schema versions, named so the migrations below read as a chain rather than as
        // unexplained numbers.
        private const val V1_SESSIONS = 1
        private const val V2_CATALOG = 2
        private const val V3_SESSION_EXERCISES = 3

        /**
         * Adds the catalog table (US-02). Purely additive — `sessions` is untouched, so a
         * device that already has an active session keeps it.
         */
        val MIGRATION_1_2 =
            object : Migration(V1_SESSIONS, V2_CATALOG) {
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

        /** Adds `session_exercises` (ADR-0004, US-02). Additive; existing tables untouched. */
        val MIGRATION_2_3 =
            object : Migration(V2_CATALOG, V3_SESSION_EXERCISES) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `session_exercises` (
                            `id` TEXT NOT NULL,
                            `session_id` TEXT NOT NULL,
                            `exercise_id` TEXT NOT NULL,
                            `position` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            `sync_state` TEXT NOT NULL,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`session_id`) REFERENCES `sessions`(`id`)
                                ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_session_exercises_session_id` " +
                            "ON `session_exercises` (`session_id`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_session_exercises_exercise_id` " +
                            "ON `session_exercises` (`exercise_id`)",
                    )
                }
            }
    }
}
