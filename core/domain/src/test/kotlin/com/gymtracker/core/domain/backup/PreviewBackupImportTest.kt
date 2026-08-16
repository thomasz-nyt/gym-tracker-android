package com.gymtracker.core.domain.backup

import com.gymtracker.core.domain.TestData
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.FakeSessionRepository
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * US-41: what the confirm dialog needs before anything is written — the file's own counts and
 * the counts already on the device, or a reason the import cannot proceed at all.
 */
class PreviewBackupImportTest {
    private val member = UserId("member-1")
    private val known = setOf(TestData.BENCH, TestData.SQUAT)
    private val fixture = TestData.memberWithARoutineAndASession(member)

    private val incoming =
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

    private fun preview(
        reader: BackupFileReader = BackupFileReader { "raw" },
        decoder: BackupDecoder = BackupDecoder { incoming },
        catalog: ExerciseCatalog = FakeCatalogStub(known),
        sessions: FakeSessionRepository = FakeSessionRepository(),
        store: FakeBackupStore = FakeBackupStore(seed = mapOf(member to emptyContents(member))),
    ) = PreviewBackupImport(reader, decoder, catalog, sessions, store)

    private fun emptyContents(memberId: UserId) =
        BackupContents(
            memberId = memberId,
            unit = WeightUnit.LB,
            restDefault = Duration.ofSeconds(60),
            sessions = emptyList(),
            sessionExercises = emptyList(),
            sets = emptyList(),
            routines = emptyList(),
            routineItems = emptyList(),
        )

    @Test
    fun `a valid file reports both the incoming and the current counts`() =
        runTest {
            val store =
                FakeBackupStore(
                    seed =
                        mapOf(
                            member to
                                emptyContents(member).copy(
                                    sessions =
                                        List(3) {
                                            WorkoutSession(SessionId("s$it"), member, null, Instant.now(), null, null)
                                        },
                                ),
                        ),
                )

            val result = preview(store = store)(member, "content://fake")

            val ready = assertIs<ImportPreviewResult.Ready>(result)
            assertEquals(incoming, ready.incoming)
            assertEquals(1, ready.incomingSessionCount)
            assertEquals(1, ready.incomingRoutineCount)
            assertEquals(3, ready.currentSessionCount)
            assertEquals(0, ready.currentRoutineCount)
        }

    @Test
    fun `an unreadable file is reported, not thrown`() =
        runTest {
            val failingReader = BackupFileReader { throw java.io.IOException("boom") }
            val result = preview(reader = failingReader)(member, "content://fake")

            assertIs<ImportPreviewResult.Unreadable>(result)
        }

    @Test
    fun `a file from a newer format version is reported as unreadable, by name`() =
        runTest {
            val result =
                preview(decoder = BackupDecoder { throw UnsupportedBackupFormatException(99, 1) })(
                    member,
                    "content://fake",
                )

            val unreadable = assertIs<ImportPreviewResult.Unreadable>(result)
            assertEquals(true, unreadable.message.contains("99"))
        }

    @Test
    fun `refuses while a session is active, before the file is even validated`() =
        runTest {
            val sessions = FakeSessionRepository()
            sessions.startSession(
                WorkoutSession(SessionId("active"), member, null, Instant.now(), null, null),
            )

            val result = preview(sessions = sessions, catalog = FakeCatalogStub(emptySet()))(member, "content://fake")

            val refused = assertIs<ImportPreviewResult.Refused>(result)
            assertIs<ImportRefusalReason.SessionActive>(refused.reason)
        }

    @Test
    fun `refuses a file referencing an exercise this build does not have, naming it`() =
        runTest {
            val result = preview(catalog = FakeCatalogStub(setOf(TestData.SQUAT)))(member, "content://fake")

            val refused = assertIs<ImportPreviewResult.Refused>(result)
            val reason = assertIs<ImportRefusalReason.UnknownExercises>(refused.reason)
            assertEquals(setOf(TestData.BENCH), reason.missingExerciseIds)
        }

    private class FakeCatalogStub(
        private val ids: Set<ExerciseId>,
    ) : ExerciseCatalog {
        override fun observeRanked(forMember: UserId) = error("not needed for this test")

        override suspend fun knownExerciseIds(): Set<ExerciseId> = ids
    }
}
