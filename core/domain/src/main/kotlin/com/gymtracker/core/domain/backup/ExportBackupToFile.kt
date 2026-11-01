package com.gymtracker.core.domain.backup

import com.gymtracker.core.domain.model.UserId
import java.time.Clock

/**
 * US-40, end to end: reads a member's data, encodes it, and writes it to a destination they
 * chose. What `:feature:settings`' Settings screen calls when Export is tapped — everything
 * else in this package is a piece this composes.
 */
class ExportBackupToFile(
    private val exportBackup: ExportBackup,
    private val encoder: BackupEncoder,
    private val fileWriter: BackupFileWriter,
    private val appVersion: AppVersion,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        memberId: UserId,
        destination: String,
    ) {
        writeSnapshot(BackupSnapshot(exportBackup(memberId)), destination)
    }

    /** Exports the chosen recovery copy, preserving its original age and build metadata. */
    suspend fun writeSnapshot(
        snapshot: BackupSnapshot,
        destination: String,
    ) {
        val metadata = snapshot.metadata ?: BackupMetadata(clock.instant(), appVersion.name())
        val json = encoder.encode(snapshot.contents, metadata.exportedAt, metadata.appVersion)
        fileWriter.write(destination, json)
    }
}
