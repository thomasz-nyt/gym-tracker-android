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

/**
 * What the exercise is performed on (`data-model.md`).
 *
 * [OTHER] and [UNSPECIFIED] are different answers and must stay that way (ADR-0015): the
 * first says the equipment is real and miscellaneous — an exercise ball, a foam roller — and
 * the second says the catalog never recorded any. Collapsing them made 27% of the catalog
 * claim to be "other", which is a metric shown as present when it is absent (constitution §2).
 */
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
    UNSPECIFIED,
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
