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
 * US-41 end to end: refuse while a session is active, refuse a file with exercises this build
 * does not have, otherwise replace everything.
 */
class ImportBackupTest {
    private val member = UserId("member-1")
    private val known = setOf(TestData.BENCH, TestData.SQUAT)
    private val fixture = TestData.memberWithARoutineAndASession(member)

    private val contents =
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

    private fun importBackup(
        sessions: FakeSessionRepository = FakeSessionRepository(),
        catalog: ExerciseCatalogStub = ExerciseCatalogStub(known),
        store: FakeBackupStore = FakeBackupStore(),
    ) = ImportBackup(sessions, catalog, store) to store

    @Test
    fun `replaces everything when the file is valid and no session is running`() =
        runTest {
            val (importBackup, store) = importBackup()

            val result = importBackup(member, contents)

            assertIs<ImportBackupResult.Imported>(result)
            assertEquals(contents, store.lastReplaced)
        }

    @Test
    fun `refuses while a session is active, and writes nothing`() =
        runTest {
            val sessions = FakeSessionRepository()
            sessions.startSession(
                WorkoutSession(
                    id = SessionId("active"),
                    userId = member,
                    gymName = null,
                    startedAt = Instant.parse("2026-08-15T18:00:00Z"),
                    endedAt = null,
                    metrics = null,
                ),
            )
            val (importBackup, store) = importBackup(sessions = sessions)

            val result = importBackup(member, contents)

            val refused = assertIs<ImportBackupResult.Refused>(result)
            assertIs<ImportRefusalReason.SessionActive>(refused.reason)
            assertEquals(null, store.lastReplaced, "nothing is written when the import is refused")
        }

    @Test
    fun `refuses a file referencing an exercise this build does not have, and writes nothing`() =
        runTest {
            val (importBackup, store) =
                importBackup(catalog = ExerciseCatalogStub(setOf(TestData.SQUAT))) // BENCH missing

            val result = importBackup(member, contents)

            val refused = assertIs<ImportBackupResult.Refused>(result)
            val reason = assertIs<ImportRefusalReason.UnknownExercises>(refused.reason)
            assertEquals(setOf(TestData.BENCH), reason.missingExerciseIds)
            assertEquals(null, store.lastReplaced)
        }

    @Test
    fun `the active-session check runs before validation, so a running workout is never partly checked`() =
        runTest {
            val sessions = FakeSessionRepository()
            sessions.startSession(
                WorkoutSession(
                    id = SessionId("active"),
                    userId = member,
                    gymName = null,
                    startedAt = Instant.parse("2026-08-15T18:00:00Z"),
                    endedAt = null,
                    metrics = null,
                ),
            )
            // A catalog that would also fail validation -- the refusal reason proves which
            // check ran first.
            val (importBackup, _) = importBackup(sessions = sessions, catalog = ExerciseCatalogStub(emptySet()))

            val result = importBackup(member, contents)

            val refused = assertIs<ImportBackupResult.Refused>(result)
            assertIs<ImportRefusalReason.SessionActive>(refused.reason)
        }

    private class ExerciseCatalogStub(
        private val ids: Set<ExerciseId>,
    ) : ExerciseCatalog {
        override fun observeRanked(forMember: UserId) = error("not needed for this test")

        override suspend fun knownExerciseIds(): Set<ExerciseId> = ids
    }
}
