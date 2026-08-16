package com.gymtracker.core.domain.backup

import com.gymtracker.core.domain.TestData
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals

/**
 * US-40: exporting reads back exactly what a member has logged, unchanged.
 */
class ExportBackupTest {
    private val member = UserId("member-1")

    @Test
    fun `exports every row the member owns, across all five tables`() =
        runTest {
            val fixture = TestData.memberWithARoutineAndASession(member)
            val contents =
                BackupContents(
                    memberId = member,
                    unit = WeightUnit.KG,
                    restDefault = Duration.ofSeconds(90),
                    sessions = fixture.sessions,
                    sessionExercises = fixture.sessionExercises,
                    sets = fixture.sets,
                    routines = fixture.routines,
                    routineItems = fixture.routineItems,
                )
            val store = FakeBackupStore(seed = mapOf(member to contents))

            val exported = ExportBackup(store)(member)

            assertEquals(contents, exported, "export is a plain read, not a transformation")
        }

    @Test
    fun `exporting does not modify the underlying data`() =
        runTest {
            val fixture = TestData.memberWithARoutineAndASession(member)
            val contents =
                BackupContents(
                    memberId = member,
                    unit = WeightUnit.LB,
                    restDefault = Duration.ofSeconds(60),
                    sessions = fixture.sessions,
                    sessionExercises = fixture.sessionExercises,
                    sets = fixture.sets,
                    routines = fixture.routines,
                    routineItems = fixture.routineItems,
                )
            val store = FakeBackupStore(seed = mapOf(member to contents))
            val export = ExportBackup(store)

            export(member)
            val second = export(member)

            assertEquals(contents, second, "exporting twice in a row must not change the data")
        }
}
