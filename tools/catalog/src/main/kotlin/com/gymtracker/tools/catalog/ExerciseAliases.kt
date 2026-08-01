package com.gymtracker.tools.catalog

/**
 * What the household calls things, mapped onto what the catalog calls them (ADR-0015, US-12).
 *
 * free-exercise-db ships no aliases at all — 0 of 873 — so US-12's "search matches on name
 * and common aliases" had nothing behind it. This is the supply. It is the one piece of
 * catalog data the household can author better than the source, because it is knowledge about
 * how people talk rather than about the exercise.
 *
 * Keyed on free-exercise-db source slugs, not display names, for the same reason
 * [STARTER_EXERCISE_SLUGS] is: the slug is what the id is derived from. `CatalogConverterTest`
 * asserts every one resolves against the real catalog, so a refresh that renames an exercise
 * fails the build rather than silently dropping the alias.
 *
 * **Only aliases that earn their place.** A term already contained in the exercise's name is
 * matched by the name search and adding it here would be dead data — "crunch machine" is
 * already inside "Ab Crunch Machine". Every entry below is a term the name search misses:
 * a different word for the movement ("pec deck" for Butterfly), an abbreviation ("RDL"), a
 * spacing the catalog does not use ("pull up" against "Pullups"), or a common misspelling.
 *
 * Expect to add to this as the household uses the app. A missing alias is a search that comes
 * back empty, not a bug, and this is a data file.
 */
val EXERCISE_ALIASES: Map<String, List<String>> =
    mapOf(
        // Different words for the same movement.
        "Butterfly" to listOf("pec deck", "pec fly", "chest fly"),
        "Cable_Crossover" to listOf("cable fly"),
        "Lying_Triceps_Press" to listOf("skull crusher", "skullcrusher"),
        "Side_Lateral_Raise" to listOf("lat raise", "delt raise", "shoulder fly"),
        "Face_Pull" to listOf("rear delt pull"),
        "Leg_Press" to listOf("sled press"),
        "Machine_Bench_Press" to listOf("chest machine"),
        "Standing_Calf_Raises" to listOf("calf machine"),
        // Abbreviations people actually type on a gym floor.
        "Romanian_Deadlift" to listOf("rdl"),
        "Barbell_Shoulder_Press" to listOf("ohp", "overhead press", "military press"),
        "Barbell_Squat" to listOf("back squat"),
        // Spacings and singulars the catalog's own names do not contain.
        "Pullups" to listOf("pull up", "pull-up", "chin up"),
        "Pushups" to listOf("push up", "push-up"),
        "Triceps_Pushdown" to listOf("tricep pushdown", "rope pushdown"),
        "Barbell_Curl" to listOf("bicep curl", "biceps curl"),
        "Seated_Cable_Rows" to listOf("seated row"),
        "Wide-Grip_Lat_Pulldown" to listOf("lat pull down"),
        // Misspellings common enough to be worth catching.
        "Dumbbell_Flyes" to listOf("dumbell fly"),
    )
