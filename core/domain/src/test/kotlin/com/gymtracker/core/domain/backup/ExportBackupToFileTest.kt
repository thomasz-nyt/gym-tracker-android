package com.gymtracker.core.domain.backup

import com.gymtracker.core.domain.TestData
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** US-40 end to end, against fakes for every port this composes. */
class ExportBackupToFileTest {
    private val member = UserId("member-1")
    private val now: Instant = Instant.parse("2026-08-15T18:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private val contents =
        with(TestData.memberWithARoutineAndASession(member)) {
            BackupContents(
                memberId = member,
                unit = WeightUnit.KG,
                restDefault = Duration.ofSeconds(60),
                sessions = sessions,
                sessionExercises = sessionExercises,
                sets = sets,
                routines = routines,
                routineItems = routineItems,
            )
        }

    private class FakeEncoder : BackupEncoder {
        var lastArgs: Triple<BackupContents, Instant, String>? = null

        override fun encode(
            contents: BackupContents,
            exportedAt: Instant,
            appVersion: String,
        ): String {
            lastArgs = Triple(contents, exportedAt, appVersion)
            return "encoded:${contents.memberId.value}"
        }
    }

    private class FakeFileWriter(
        private val failWith: Throwable? = null,
    ) : BackupFileWriter {
        var lastDestination: String? = null
        var lastContent: String? = null

        override suspend fun write(
            destination: String,
            content: String,
        ) {
            failWith?.let { throw it }
            lastDestination = destination
            lastContent = content
        }
    }

    @Test
    fun `reads, encodes, and writes to the given destination`() =
        runTest {
            val store = FakeBackupStore(seed = mapOf(member to contents))
            val encoder = FakeEncoder()
            val writer = FakeFileWriter()
            val export =
                ExportBackupToFile(
                    exportBackup = ExportBackup(store),
                    encoder = encoder,
                    fileWriter = writer,
                    appVersion = AppVersion { "1.0-test" },
                    clock = clock,
                )

            export(member, "content://fake/backup.json")

            assertEquals(contents, encoder.lastArgs?.first)
            assertEquals(now, encoder.lastArgs?.second)
            assertEquals("1.0-test", encoder.lastArgs?.third)
            assertEquals("content://fake/backup.json", writer.lastDestination)
            assertEquals("encoded:${member.value}", writer.lastContent)
        }

    @Test
    fun `a write failure propagates rather than being swallowed`() =
        runTest {
            val store = FakeBackupStore(seed = mapOf(member to contents))
            val export =
                ExportBackupToFile(
                    exportBackup = ExportBackup(store),
                    encoder = FakeEncoder(),
                    fileWriter = FakeFileWriter(failWith = IllegalStateException("no space")),
                    appVersion = AppVersion { "1.0-test" },
                    clock = clock,
                )

            assertFailsWith<IllegalStateException> { export(member, "content://fake/backup.json") }
        }
}
