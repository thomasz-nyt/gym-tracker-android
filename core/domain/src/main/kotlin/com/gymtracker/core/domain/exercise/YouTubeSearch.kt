package com.gymtracker.core.domain.exercise

import java.net.URLEncoder

/**
 * A YouTube **search** for an exercise (US-14, ADR-0015).
 *
 * Not a curated link, and the UI must not present it as one. free-exercise-db ships no video
 * URLs at all — 0 of 873 — and inventing them is not possible, so what the app can honestly
 * offer is a search for the exercise's name. Saying "watch a video of this" would imply
 * somebody had checked it; nobody has (constitution §2).
 *
 * A plain URL rather than an embedded player: no third-party SDK and no account, which keeps
 * it available to the Teen persona (constitution §3). This is the only thing in M3 that needs
 * the network, and the rest of the detail screen does not depend on it.
 */
object YouTubeSearch {
    private const val RESULTS = "https://www.youtube.com/results?search_query="

    /**
     * @return the search URL, or null if there is nothing to search for.
     */
    fun forExercise(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null

        // Names carry slashes, brackets and ampersands — "3/4 Sit-Up", "Hyperextensions (Back
        // Extensions)". Unescaped, an ampersand would start a second query parameter.
        return RESULTS + URLEncoder.encode(trimmed, Charsets.UTF_8.name())
    }
}
