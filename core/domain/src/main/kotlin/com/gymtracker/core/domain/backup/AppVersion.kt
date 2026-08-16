package com.gymtracker.core.domain.backup

/**
 * The app build writing a backup file (US-40, ADR-0034) — diagnostic only, part of the envelope
 * and never read back. An interface because reading it is platform-specific (`PackageManager`
 * on Android); the domain only needs the resulting string.
 */
fun interface AppVersion {
    fun name(): String
}
