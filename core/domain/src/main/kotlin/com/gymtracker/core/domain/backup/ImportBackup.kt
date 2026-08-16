package com.gymtracker.core.domain.backup

import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.session.SessionRepository

/** What happened when the member asked to import a file. */
sealed interface ImportBackupResult {
    data object Imported : ImportBackupResult

    data class Refused(
        val reason: ImportRefusalReason,
    ) : ImportBackupResult
}

/** Why an import did not happen (US-41). Reported so the screen can say why, not just that it failed. */
sealed interface ImportRefusalReason {
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
) {
    suspend operator fun invoke(
        memberId: UserId,
        contents: BackupContents,
    ): ImportBackupResult {
        if (sessions.findActiveSession(memberId) != null) {
            return ImportBackupResult.Refused(ImportRefusalReason.SessionActive)
        }

        return when (val validation = ValidateBackup(contents, catalog.knownExerciseIds())) {
            is BackupValidationResult.Valid -> {
                store.replaceAll(validation.contents)
                ImportBackupResult.Imported
            }
            is BackupValidationResult.UnknownExercises ->
                ImportBackupResult.Refused(ImportRefusalReason.UnknownExercises(validation.missingExerciseIds))
        }
    }
}
