package com.gymtracker.core.domain.backup

/** One private, durable pre-restore snapshot. It survives restart, but not uninstall (US-66). */
interface BackupSafetyStore {
    /** Atomically replaces the rolling copy only after encoded bytes have been verified. */
    suspend fun write(contents: BackupContents)

    /** Reads the validated copy, or null if this installation has not made one. */
    suspend fun read(): BackupContents?
}
