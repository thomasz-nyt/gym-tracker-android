package com.gymtracker.core.domain.backup

import com.gymtracker.core.domain.model.UserId
import java.time.Clock
import java.time.Instant

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
        val contents = exportBackup(memberId)
        val json = encoder.encode(contents, Instant.now(clock), appVersion.name())
        fileWriter.write(destination, json)
    }
}
