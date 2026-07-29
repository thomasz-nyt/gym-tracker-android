package com.gymtracker.tools.catalog

/**
 * Common gym movements, pinned above the alphabetical tail for a member with no history
 * (ADR-0007).
 *
 * These are free-exercise-db source slugs, not display names, because the slug is what the
 * id is derived from. `CatalogConverterTest` asserts every one of them resolves against the
 * real catalog, so a refresh that renames or drops an exercise fails the build rather than
 * quietly shipping a starter set with holes in it.
 *
 * The bias is deliberate: machines and barbell basics, the things someone new to a gym is
 * actually choosing between (constitution §1 — indoor, equipment-based strength training).
 */
val STARTER_EXERCISE_SLUGS: List<String> =
    listOf(
        // Push
        "Barbell_Bench_Press_-_Medium_Grip",
        "Dumbbell_Bench_Press",
        "Machine_Bench_Press",
        "Smith_Machine_Bench_Press",
        "Leverage_Chest_Press",
        "Dumbbell_Flyes",
        "Cable_Crossover",
        "Barbell_Shoulder_Press",
        "Dumbbell_Shoulder_Press",
        "Side_Lateral_Raise",
        "Triceps_Pushdown",
        "Dips_-_Triceps_Version",
        "Pushups",
        // Pull
        "Wide-Grip_Lat_Pulldown",
        "Seated_Cable_Rows",
        "Bent_Over_Barbell_Row",
        "Pullups",
        "Face_Pull",
        "Barbell_Curl",
        "Dumbbell_Bicep_Curl",
        "Hammer_Curls",
        // Legs
        "Barbell_Squat",
        "Front_Barbell_Squat",
        "Leg_Press",
        "Leg_Extensions",
        "Lying_Leg_Curls",
        "Seated_Leg_Curl",
        "Barbell_Deadlift",
        "Romanian_Deadlift",
        "Dumbbell_Lunges",
        "Barbell_Glute_Bridge",
        "Standing_Calf_Raises",
        // Core
        "Crunches",
        "Plank",
        "Hanging_Leg_Raise",
        "Ab_Crunch_Machine",
    )
