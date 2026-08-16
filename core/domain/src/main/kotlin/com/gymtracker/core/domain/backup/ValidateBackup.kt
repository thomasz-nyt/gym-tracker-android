package com.gymtracker.core.domain.backup

import com.gymtracker.core.domain.model.ExerciseId

/** What [ValidateBackup] decided. */
sealed interface BackupValidationResult {
    data class Valid(
        val contents: BackupContents,
    ) : BackupValidationResult

    /**
     * [missingExerciseIds] is what US-41 asks the app to say: which exercises this build's
     * catalog does not have, so the same file can still restore cleanly on a build that does.
     */
    data class UnknownExercises(
        val missingExerciseIds: Set<ExerciseId>,
    ) : BackupValidationResult
}

/**
 * Whether a decoded backup can be fully applied (US-41, ADR-0034) — the one check the file
 * itself cannot answer, because it only knows what a build's catalog contains at import time.
 *
 * A pure function: runs to completion before [ImportBackup] writes anything, so a file that
 * cannot be fully restored leaves the database untouched, exactly as before the attempt.
 */
object ValidateBackup {
    operator fun invoke(
        contents: BackupContents,
        knownExerciseIds: Set<ExerciseId>,
    ): BackupValidationResult {
        val referenced =
            contents.sessionExercises.map { it.exerciseId }.toSet() +
                contents.routineItems.map { it.exerciseId }.toSet()
        val missing = referenced - knownExerciseIds

        return if (missing.isEmpty()) {
            BackupValidationResult.Valid(contents)
        } else {
            BackupValidationResult.UnknownExercises(missing)
        }
    }
}
