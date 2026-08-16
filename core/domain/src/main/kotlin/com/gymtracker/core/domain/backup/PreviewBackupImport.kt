package com.gymtracker.core.domain.backup

import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.session.SessionRepository

/** What the confirm dialog needs before anything is written, or why the import cannot proceed. */
sealed interface ImportPreviewResult {
    /**
     * [incomingSessionCount] and [incomingRoutineCount] are the file's; the "current" pair are
     * already on the device.
     */
    data class Ready(
        val incoming: BackupContents,
        val incomingSessionCount: Int,
        val incomingRoutineCount: Int,
        val currentSessionCount: Int,
        val currentRoutineCount: Int,
    ) : ImportPreviewResult

    data class Refused(
        val reason: ImportRefusalReason,
    ) : ImportPreviewResult

    /** The file could not even be read or parsed — not one of this app's own exports, or damaged. */
    data class Unreadable(
        val message: String,
    ) : ImportPreviewResult
}

/**
 * US-41: everything a confirm dialog needs, computed **before** writing anything — reading and
 * decoding the file, checking for a running session, and validating its exercise references,
 * plus the current on-device counts to show alongside the file's own. [ImportBackup] repeats the
 * session and validation checks at confirm time, on purpose: this preview and the actual import
 * are two separate reads of live state, and either can have changed in between.
 */
class PreviewBackupImport(
    private val fileReader: BackupFileReader,
    private val decoder: BackupDecoder,
    private val catalog: ExerciseCatalog,
    private val sessions: SessionRepository,
    private val store: BackupStore,
) {
    suspend operator fun invoke(
        memberId: UserId,
        source: String,
    ): ImportPreviewResult {
        // The two things that can go wrong reading an arbitrary external file — a bad read
        // (IOException) and a bad parse (kotlinx.serialization's SerializationException, or
        // UnsupportedBackupFormatException) — are unrelated exception hierarchies, and every
        // one of them means the same thing to the member: this file cannot be imported.
        return runCatching { decoder.decode(fileReader.read(source)) }
            .fold(
                onSuccess = { contents -> resolveAgainstLiveState(memberId, contents) },
                onFailure = { ImportPreviewResult.Unreadable(it.message ?: "could not read this file") },
            )
    }

    private suspend fun resolveAgainstLiveState(
        memberId: UserId,
        contents: BackupContents,
    ): ImportPreviewResult {
        if (sessions.findActiveSession(memberId) != null) {
            return ImportPreviewResult.Refused(ImportRefusalReason.SessionActive)
        }

        return when (val validation = ValidateBackup(contents, catalog.knownExerciseIds())) {
            is BackupValidationResult.Valid -> {
                val current = store.read(memberId)
                ImportPreviewResult.Ready(
                    incoming = contents,
                    incomingSessionCount = contents.sessions.size,
                    incomingRoutineCount = contents.routines.size,
                    currentSessionCount = current.sessions.size,
                    currentRoutineCount = current.routines.size,
                )
            }
            is BackupValidationResult.UnknownExercises ->
                ImportPreviewResult.Refused(ImportRefusalReason.UnknownExercises(validation.missingExerciseIds))
        }
    }
}
