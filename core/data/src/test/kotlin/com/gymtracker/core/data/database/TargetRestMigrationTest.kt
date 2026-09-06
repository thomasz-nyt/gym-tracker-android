package com.gymtracker.core.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR-0050: the v10 → v11 upgrade adds `target_rest_seconds` to both target tables and touches
 * nothing else. Validated against the exported `11.json`, so the schema on disk and the
 * migration cannot drift apart.
 */
@RunWith(RobolectricTestRunner::class)
class TargetRestMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            GymTrackerDatabase::class.java,
        )

    @Test
    fun `migrating from 10 to 11 keeps every target and names no rest for any of them`() {
        val name = "migration-10-11.db"

        helper.createDatabase(name, 10).use { v10 ->
            v10.execSQL(
                "INSERT INTO sessions (id, user_id, gym_name, started_at, ended_at, avg_hr, max_hr, " +
                    "active_kcal, metrics_source, updated_at, sync_state, routine_name, routine_id) " +
                    "VALUES ('s1', 'u1', NULL, 1000, NULL, NULL, NULL, NULL, NULL, 1000, 'PENDING', NULL, NULL)",
            )
            v10.execSQL(
                "INSERT INTO session_exercises (id, session_id, exercise_id, position, updated_at, sync_state, " +
                    "target_sets, target_reps, target_weight_kg) " +
                    "VALUES ('se1', 's1', 'bench', 1, 1000, 'PENDING', 3, 8, 61.23)",
            )
        }

        val v11 = helper.runMigrationsAndValidate(name, 11, true, GymTrackerDatabase.MIGRATION_10_11)

        v11.query("SELECT target_sets, target_reps, target_rest_seconds FROM session_exercises WHERE id = 'se1'").use {
            assertTrue(it.moveToFirst(), "an additive migration must not lose a row already on the device")
            assertEquals(3, it.getInt(0))
            assertEquals(8, it.getInt(1))
            assertTrue(it.isNull(2), "no rest is invented for a movement that never named one — the default applies")
        }
        assertTrue(v11.hasColumn("routine_items", "target_rest_seconds"), "routine_items gains the same column")
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.hasColumn(
        table: String,
        column: String,
    ): Boolean =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }.any { it == column }
        }
}
