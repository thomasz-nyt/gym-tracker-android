package com.gymtracker.core.domain.backup

import java.time.Instant

/** The file's creation time and writer build, shown before any personal data is replaced. */
data class BackupMetadata(
    val exportedAt: Instant,
    val appVersion: String,
)
