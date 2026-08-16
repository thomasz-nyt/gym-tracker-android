package com.gymtracker.core.domain.backup

/**
 * Thrown by [BackupDecoder.decode] when a file names a format version newer than this build
 * understands (US-41, ADR-0034) — refused by name rather than read partially.
 *
 * A plain exception in `:core:domain` rather than `:core:data`: [BackupDecoder]'s own contract
 * promises it, and the version number itself carries no JSON or Room detail — [fileVersion] and
 * [supportedVersion] are just integers.
 */
class UnsupportedBackupFormatException(
    val fileVersion: Int,
    val supportedVersion: Int,
) : Exception(
        "backup file is format version $fileVersion, this build only understands up to $supportedVersion",
    )
