package com.gymtracker.core.domain.backup

import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.session.SessionRepository
import kotlinx.coroutines.CancellationException

/** What happened when the member asked to import a file. */
sealed interface ImportBackupResult {
    data object Imported : ImportBackupResult

    data class Refused(
        val reason: ImportRefusalReason,
    ) : ImportBackupResult
}

/** Why an import did not happen (US-41). Reported so the screen can say why, not just that it failed. */
sealed interface ImportRefusalReason {
    data class Invalid(
        val problems: List<String>,
    ) : ImportRefusalReason

    /** A workout is running. Constitution §2.1: the core loop must never vanish out from under itself. */
    data object SessionActive : ImportRefusalReason

    data class UnknownExercises(
        val missingExerciseIds: Set<ExerciseId>,
    ) : ImportRefusalReason
}

/**
 * US-41: replace everything with what a backup file holds — unless a workout is running, or the
 * file references an exercise this build does not have. Either refusal leaves the database
 * untouched; [BackupStore.replaceAll] is called only once both checks have passed.
 *
 * The active-session check runs **before** validation, on purpose: refusing for the reason a
 * member can act on immediately (finish the workout) takes priority over a reason about the
 * file itself, which they cannot fix mid-set anyway.
 */
class ImportBackup(
    private val sessions: SessionRepository,
    private val catalog: ExerciseCatalog,
    private val store: BackupStore,
    private val safety: BackupSafetyStore,
) {
    suspend operator fun invoke(
        memberId: UserId,
        contents: BackupContents,
    ): ImportBackupResult = restore(memberId, contents, createSafety = true)

    /** The last private pre-restore copy, also available for an explicit export. */
    suspend fun safetyCopy(): BackupContents? = safety.read()

    /** Restores the safety copy without replacing that copy with the state being recovered. */
    suspend fun recover(memberId: UserId): ImportBackupResult {
        val contents =
            safety.read()
                ?: return ImportBackupResult.Refused(
                    ImportRefusalReason.Invalid(listOf("No safety copy is available.")),
                )
        return restore(memberId, contents, createSafety = false)
    }

    // Storage ports report unrelated IO/SQL failures; this UI boundary retains the safety copy.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun restore(
        memberId: UserId,
        contents: BackupContents,
        createSafety: Boolean,
    ): ImportBackupResult {
        try {
            return restoreValidated(memberId, contents, createSafety)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return ImportBackupResult.Refused(
                ImportRefusalReason.Invalid(
                    listOf("Restore failed: ${error.message ?: "storage error"}. Your safety copy is retained."),
                ),
            )
        }
    }

    private suspend fun restoreValidated(
        memberId: UserId,
        contents: BackupContents,
        createSafety: Boolean,
    ): ImportBackupResult {
        if (sessions.findActiveSession(memberId) != null) {
            return ImportBackupResult.Refused(ImportRefusalReason.SessionActive)
        }

        return when (val validation = ValidateBackup(contents, catalog.knownExerciseIds())) {
            is BackupValidationResult.Valid -> {
                if (createSafety) {
                    val before = store.read(memberId)
                    check(ValidateBackup(before, catalog.knownExerciseIds()) is BackupValidationResult.Valid) {
                        "Current data cannot be safely backed up"
                    }
                    safety.write(before)
                    check(safety.read() == before) { "Safety copy verification failed" }
                }
                if (sessions.findActiveSession(memberId) != null) {
                    ImportBackupResult.Refused(ImportRefusalReason.SessionActive)
                } else {
                    store.replaceAll(validation.contents)
                    ImportBackupResult.Imported
                }
            }
            is BackupValidationResult.Invalid ->
                ImportBackupResult.Refused(ImportRefusalReason.Invalid(validation.problems))
            is BackupValidationResult.UnknownExercises ->
                ImportBackupResult.Refused(ImportRefusalReason.UnknownExercises(validation.missingExerciseIds))
        }
    }
}
