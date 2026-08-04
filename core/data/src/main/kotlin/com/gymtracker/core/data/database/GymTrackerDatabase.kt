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
import com.gymtracker.core.data.set.SetDao
import com.gymtracker.core.data.set.SetEntity

/**
 * The local database, which is the source of truth for the UI (constitution §2).
 *
 * `session_exercises`, `sets` and `sync_queue` from `data-model.md` arrive with the stories
 * that need them.
 */
@Database(
    entities = [SessionEntity::class, ExerciseEntity::class, SessionExerciseEntity::class, SetEntity::class],
    version = 7,
    exportSchema = true,
)
abstract class GymTrackerDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun exerciseDao(): ExerciseDao

    abstract fun sessionExerciseDao(): SessionExerciseDao

    abstract fun setDao(): SetDao

    companion object {
        const val NAME = "gym-tracker.db"

        // Schema versions, named so the migrations below read as a chain rather than as
        // unexplained numbers.
        private const val V1_SESSIONS = 1
        private const val V2_CATALOG = 2
        private const val V3_SESSION_EXERCISES = 3
        private const val V4_SETS = 4
        private const val V5_STARTER_EXERCISES = 5
        private const val V6_ALIASES_AND_UNSPECIFIED_EQUIPMENT = 6
        private const val V7_FINISHED_EXERCISES = 7

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

        /** Adds `sets` (US-03). Additive; existing tables untouched. */
        val MIGRATION_3_4 =
            object : Migration(V3_SESSION_EXERCISES, V4_SETS) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `sets` (
                            `id` TEXT NOT NULL,
                            `session_exercise_id` TEXT NOT NULL,
                            `set_index` INTEGER NOT NULL,
                            `weight_kg` REAL,
                            `reps` INTEGER NOT NULL,
                            `rpe` REAL,
                            `performed_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            `sync_state` TEXT NOT NULL,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`session_exercise_id`) REFERENCES `session_exercises`(`id`)
                                ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_sets_session_exercise_id_performed_at` " +
                            "ON `sets` (`session_exercise_id`, `performed_at`)",
                    )
                }
            }

        /**
         * Adds the starter flag and bundled image to `exercises` (ADR-0007).
         *
         * The catalog is re-seeded rather than back-filled: it is derived data with no member
         * content in it, so wiping and re-inserting from the bundled asset is simpler and
         * cannot leave half-populated rows. `session_exercises.exercise_id` still resolves,
         * because ids are UUIDv5 over the source slug and therefore unchanged.
         */
        val MIGRATION_4_5 =
            object : Migration(V4_SETS, V5_STARTER_EXERCISES) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `exercises` ADD COLUMN `is_starter` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `exercises` ADD COLUMN `image_asset` TEXT")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_is_starter` ON `exercises` (`is_starter`)")
                    db.execSQL("DELETE FROM `exercises`")
                }
            }

        /**
         * Re-seeds the catalog for M3 (ADR-0015): equipment the source never recorded becomes
         * `UNSPECIFIED` instead of `OTHER`, and 18 exercises gain the aliases US-12 searches.
         *
         * No column changes — both fields already exist. This wipes and re-inserts for the
         * same reason `MIGRATION_4_5` does: the catalog is derived data with no member content
         * in it, so re-seeding from the bundled asset cannot leave half-updated rows.
         * `session_exercises.exercise_id` still resolves afterwards, because catalog ids are
         * UUIDv5 over the source slug and the slugs did not change — verified against the
         * regenerated asset, which added and removed zero ids.
         */
        val MIGRATION_5_6 =
            object : Migration(V5_STARTER_EXERCISES, V6_ALIASES_AND_UNSPECIFIED_EQUIPMENT) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DELETE FROM `exercises`")
                }
            }

        /**
         * Adds `finished_at` to `session_exercises` (US-02d, ADR-0019). Additive and nullable:
         * every appearance a device already holds reads as in progress, which is the only
         * honest answer for a mark the member has not made.
         */
        val MIGRATION_6_7 =
            object : Migration(V6_ALIASES_AND_UNSPECIFIED_EQUIPMENT, V7_FINISHED_EXERCISES) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `session_exercises` ADD COLUMN `finished_at` INTEGER")
                }
            }
    }
}
