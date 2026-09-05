package com.gymtracker.feature.logging

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Tue 4 Aug" — the day a set happened, in the member's own zone and locale, the one convention
 * every "last time" reading in this feature uses: the rest panel's comparison line (ADR-0023),
 * the set sheet's provenance line (US-37) and the open movement's last-time block (US-61).
 *
 * Consolidated 2026-09-05 from two identical private copies (`RestPanel.kt`, `SetSheets.kt`)
 * when the third caller arrived — the same point at which `Durations.kt`'s `asMinutesSeconds`
 * stopped being copied. Not a `LocalDate`: a set's `performed_at` is an instant, and the day it
 * falls on depends on where the member is standing when they read it.
 */
internal fun Instant.asDay(): String =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).withZone(ZoneId.systemDefault()).format(this)
