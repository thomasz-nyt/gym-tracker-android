package com.gymtracker.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gymtracker.core.data.exercise.ExerciseDao
import com.gymtracker.core.data.exercise.ExerciseEntity
import com.gymtracker.core.data.routine.RoutineDao
import com.gymtracker.core.data.routine.RoutineEntity
import com.gymtracker.core.data.routine.RoutineItemDao
import com.gymtracker.core.data.routine.RoutineItemEntity
import com.gymtracker.core.data.session.SessionDao
import com.gymtracker.core.data.session.SessionEntity
import com.gymtracker.core.data.sessionexercise.SessionExerciseDao
import com.gymtracker.core.data.sessionexercise.SessionExerciseEntity
import com.gymtracker.core.data.set.SetDao
import com.gymtracker.core.data.set.SetEntity

/**
 * The local database, which is the source of truth for the UI (constitution §2).
 *
 * `sync_queue` from `data-model.md` arrives with M2, the story that needs it. Every other table
 * `data-model.md` describes for M0–M3a is already an entity below.
 */
@Database(
    entities = [
        SessionEntity::class,
        ExerciseEntity::class,
        SessionExerciseEntity::class,
        SetEntity::class,
        RoutineEntity::class,
        RoutineItemEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class GymTrackerDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun exerciseDao(): ExerciseDao

    abstract fun sessionExerciseDao(): SessionExerciseDao

    abstract fun setDao(): SetDao

    abstract fun routineDao(): RoutineDao

    abstract fun routineItemDao(): RoutineItemDao

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
        private const val V7_ROUTINES = 7
        private const val V8_TARGETS = 8

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
         * Adds `routines` and `routine_items` (US-29, ADR-0020). Additive.
         *
         * `sessions`, `session_exercises` and `sets` are untouched — a routine relates to a
         * session only by having been copied into it, and a copy leaves no column behind.
         * That is what lets a device upgrade mid-workout without noticing.
         *
         * Note what these tables do not have: no weight, rep or set column anywhere. ADR-0020
         * buys the routine concept by storing a shape rather than a prescription, and the
         * schema is where that promise is actually kept.
         */
        val MIGRATION_6_7 =
            object : Migration(V6_ALIASES_AND_UNSPECIFIED_EQUIPMENT, V7_ROUTINES) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `routines` (
                            `id` TEXT NOT NULL,
                            `user_id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `position` INTEGER NOT NULL,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            `sync_state` TEXT NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_routines_user_id_position` " +
                            "ON `routines` (`user_id`, `position`)",
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `routine_items` (
                            `id` TEXT NOT NULL,
                            `routine_id` TEXT NOT NULL,
                            `exercise_id` TEXT NOT NULL,
                            `position` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            `sync_state` TEXT NOT NULL,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`routine_id`) REFERENCES `routines`(`id`)
                                ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`)
                                ON UPDATE NO ACTION ON DELETE NO ACTION
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_routine_items_routine_id_position` " +
                            "ON `routine_items` (`routine_id`, `position`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_routine_items_exercise_id` " +
                            "ON `routine_items` (`exercise_id`)",
                    )
                }
            }

        /**
         * Adds a target to `routine_items` and `session_exercises` (US-30, ADR-0027).
         *
         * Three nullable columns on each — `sessions` and `sets` are explicitly untouched,
         * which is the ADR's central bargain: a target is a snapshot copied at
         * `StartSessionFromRoutine` time, never a live pointer back to the routine, so a device
         * upgrading mid-workout keeps every row it already had, with no target on any of them
         * until one is set.
         */
        val MIGRATION_7_8 =
            object : Migration(V7_ROUTINES, V8_TARGETS) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `routine_items` ADD COLUMN `target_sets` INTEGER")
                    db.execSQL("ALTER TABLE `routine_items` ADD COLUMN `target_reps` INTEGER")
                    db.execSQL("ALTER TABLE `routine_items` ADD COLUMN `target_weight_kg` REAL")
                    db.execSQL("ALTER TABLE `session_exercises` ADD COLUMN `target_sets` INTEGER")
                    db.execSQL("ALTER TABLE `session_exercises` ADD COLUMN `target_reps` INTEGER")
                    db.execSQL("ALTER TABLE `session_exercises` ADD COLUMN `target_weight_kg` REAL")
                }
            }
    }
}
