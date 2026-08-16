package com.gymtracker.core.domain.backup

/**
 * Writes an already-encoded backup file to wherever the member chose it (US-40, ADR-0034).
 *
 * [destination] is a platform file identifier in its string form — a content URI's `toString()`
 * on Android — opaque to the domain, which neither parses nor interprets it, only passes it
 * through. The member picks it in a system file picker (`:feature:settings`), and the Android
 * implementation turns it back into a `Uri` to open a stream through `ContentResolver`. Kept as
 * a string rather than `android.net.Uri` for the same reason every other domain interface is
 * platform-free: this module must compile without the Android plugin.
 */
fun interface BackupFileWriter {
    suspend fun write(
        destination: String,
        content: String,
    )
}
