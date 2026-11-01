package com.gymtracker.core.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WorkoutWorkflowMigrationTest {
    @get:Rule val helper =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), GymTrackerDatabase::class.java)

    @Test
    fun `11 to 12 preserves active workout target and every actual set`() {
        val name = "migration-workflow.db"
        helper.createDatabase(name, 11).use { old ->
            old.execSQL(
                "INSERT INTO sessions (id,user_id,started_at,updated_at,sync_state) " +
                    "VALUES ('s','alice',1000,1000,'PENDING')",
            )
            old.execSQL(
                "INSERT INTO session_exercises " +
                    "(id,session_id,exercise_id,position,target_sets,target_reps,target_weight_kg," +
                    "target_rest_seconds,updated_at,sync_state) " +
                    "VALUES ('e','s','bench',1,3,12,50,90,1000,'PENDING')",
            )
            old.execSQL(
                "INSERT INTO sets " +
                    "(id,session_exercise_id,set_index,weight_kg,reps,performed_at,updated_at,sync_state) " +
                    "VALUES ('actual','e',1,50,10,2000,2000,'PENDING')",
            )
        }
        helper.runMigrationsAndValidate(name, 12, true, GymTrackerDatabase.MIGRATION_11_12).use { db ->
            db.query("SELECT ended_at,workflow_json FROM sessions WHERE id='s'").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
                assertTrue(it.isNull(1))
            }
            db
                .query(
                    "SELECT target_sets,target_reps,target_rest_seconds,outcome FROM session_exercises WHERE id='e'",
                ).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(3, it.getInt(0))
                    assertEquals(12, it.getInt(1))
                    assertEquals(90, it.getInt(2))
                    assertTrue(it.isNull(3))
                }
            db.query("SELECT weight_kg,reps FROM sets WHERE id='actual'").use {
                assertTrue(it.moveToFirst())
                assertEquals(50.0, it.getDouble(0))
                assertEquals(10, it.getInt(1))
            }
        }
    }
}
