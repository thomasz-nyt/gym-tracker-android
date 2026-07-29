package com.gymtracker.core.domain.model

/** Muscle groups, as the app groups them (`data-model.md`). */
enum class BodyPart {
    CHEST,
    BACK,
    SHOULDERS,
    BICEPS,
    TRICEPS,
    FOREARMS,
    QUADS,
    HAMSTRINGS,
    GLUTES,
    CALVES,
    CORE,
    FULL_BODY,
}

/** What the exercise is performed on (`data-model.md`). */
enum class Equipment {
    MACHINE,
    CABLE,
    BARBELL,
    DUMBBELL,
    SMITH,
    BODYWEIGHT,
    KETTLEBELL,
    BAND,
    OTHER,
}

/** Demo media kind. `NONE` is a real answer, not a missing one (constitution §2). */
enum class MediaType {
    GIF,
    VIDEO,
    NONE,
}

/**
 * A catalog exercise.
 *
 * @property mediaUrl null until M3 mirrors media into Supabase Storage. The bundled catalog
 *   deliberately ships no media URL rather than hotlinking someone else's endpoint
 *   (`kickoff.md` § Seed data).
 * @property source provenance: `free-exercise-db` for the bundled catalog, `household` for
 *   exercises a family member creates.
 * @property isStarter a common gym movement, shown above the alphabetical tail to a member
 *   with no history yet (ADR-0007).
 * @property imageAsset a bundled photo of the movement, or null when none ships for it.
 *   Null means no image — the UI shows nothing rather than a fake placeholder.
 */
data class Exercise(
    val id: ExerciseId,
    val name: String,
    val aliases: List<String>,
    val primaryMuscles: List<BodyPart>,
    val secondaryMuscles: List<BodyPart>,
    val equipment: Equipment,
    val instructions: List<String>,
    val mediaUrl: String?,
    val mediaType: MediaType?,
    val youtubeUrl: String?,
    val source: String,
    val isStarter: Boolean = false,
    val imageAsset: String? = null,
)
