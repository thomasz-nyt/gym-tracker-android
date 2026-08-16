package com.gymtracker.core.domain.backup

/**
 * Turns a backup file's raw text back into [BackupContents] (US-41, ADR-0034) — the inverse of
 * [BackupEncoder], and implemented by the same class in `:core:data` (`BackupCodec`) for the
 * same reason: the JSON shape is an implementation detail this module does not need to know.
 */
fun interface BackupDecoder {
    /**
     * @throws UnsupportedBackupFormatException if [raw]'s format version is newer than this
     *   build understands.
     * @throws Exception for any other reason [raw] cannot be parsed — not a file this app wrote,
     *   or damaged in transit. Callers treat every failure here the same way: refuse, name
     *   nothing was written, and let the member try a different file.
     */
    fun decode(raw: String): BackupContents
}
