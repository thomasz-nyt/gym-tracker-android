package com.gymtracker.core.domain.backup

/**
 * Reads a backup file's raw contents from wherever the member picked it (US-41, ADR-0034).
 *
 * [source] is a platform file identifier in its string form — the same shape [BackupFileWriter]
 * takes it — so this port stays free of `android.net.Uri` too.
 *
 * @throws java.io.IOException if [source] cannot be opened or read.
 */
fun interface BackupFileReader {
    suspend fun read(source: String): String
}
