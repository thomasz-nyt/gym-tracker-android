package com.gymtracker.core.domain.backup

import java.time.Instant

/**
 * Turns [BackupContents] into the text a backup file holds (US-40, ADR-0034).
 *
 * An interface here, an implementation (`BackupCodec`) in `:core:data` — the same split
 * `ExerciseCatalog`/`RoomExerciseCatalog` already follows. The file format is JSON, but that is
 * an implementation detail this module deliberately does not know: `kotlinx.serialization` and
 * the DTOs it needs live entirely behind this interface, the same way `CatalogSeeder`'s
 * `BundledExercise` never leaks past `:core:data`.
 */
interface BackupEncoder {
    /** [exportedAt] and [appVersion] are diagnostic envelope fields, never read back. */
    fun encode(
        contents: BackupContents,
        exportedAt: Instant,
        appVersion: String,
    ): String
}
