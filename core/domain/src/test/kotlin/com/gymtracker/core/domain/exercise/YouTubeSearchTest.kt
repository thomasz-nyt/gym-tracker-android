package com.gymtracker.core.domain.exercise

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * US-14, as ADR-0015 rewrote it: the catalog ships no curated links, so this is a search and
 * the app says so.
 */
class YouTubeSearchTest {
    @Test
    fun `the query is the exercise name, so the search is about the movement`() {
        assertEquals(
            "https://www.youtube.com/results?search_query=Barbell+Bench+Press",
            YouTubeSearch.forExercise("Barbell Bench Press"),
        )
    }

    @Test
    fun `characters that would break the URL are escaped, not dropped`() {
        // Real catalog names: "Barbell Bench Press - Medium Grip", "3/4 Sit-Up",
        // "Hyperextensions (Back Extensions)". A raw slash or bracket would truncate or
        // corrupt the query.
        assertEquals(
            "https://www.youtube.com/results?search_query=3%2F4+Sit-Up",
            YouTubeSearch.forExercise("3/4 Sit-Up"),
        )
        assertEquals(
            "https://www.youtube.com/results?search_query=Hyperextensions+%28Back+Extensions%29",
            YouTubeSearch.forExercise("Hyperextensions (Back Extensions)"),
        )
    }

    @Test
    fun `an ampersand cannot smuggle in another query parameter`() {
        val url = checkNotNull(YouTubeSearch.forExercise("Squat & Press"))

        assertEquals("https://www.youtube.com/results?search_query=Squat+%26+Press", url)
        assertTrue(!url.contains('&'), "a bare & would start a second parameter")
    }

    @Test
    fun `it is always https`() {
        assertTrue(checkNotNull(YouTubeSearch.forExercise("Deadlift")).startsWith("https://"))
    }

    @Test
    fun `a blank name has nothing to search for`() {
        // Nothing in the catalog is unnamed, but a link that searches for nothing is worse
        // than no link (constitution §2).
        assertEquals(null, YouTubeSearch.forExercise("   "))
    }
}
