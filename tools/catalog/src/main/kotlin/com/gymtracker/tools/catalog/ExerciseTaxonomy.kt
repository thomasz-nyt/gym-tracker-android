package com.gymtracker.tools.catalog

import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment

/**
 * Maps free-exercise-db's taxonomy onto the domain enums (`kickoff.md` § Seed data).
 *
 * Unknown values throw rather than defaulting. `OTHER` is a decision we have made about
 * specific source values, not a bucket for things nobody has looked at — if a catalog refresh
 * introduces a new muscle or a new piece of equipment, the conversion should stop and make
 * someone choose.
 */
object ExerciseTaxonomy {
    private val MUSCLES: Map<String, BodyPart> =
        mapOf(
            "abdominals" to BodyPart.CORE,
            // The source splits the hips finer than the app does; both land on GLUTES.
            "abductors" to BodyPart.GLUTES,
            "adductors" to BodyPart.GLUTES,
            "biceps" to BodyPart.BICEPS,
            "calves" to BodyPart.CALVES,
            "chest" to BodyPart.CHEST,
            "forearms" to BodyPart.FOREARMS,
            "glutes" to BodyPart.GLUTES,
            "hamstrings" to BodyPart.HAMSTRINGS,
            // Four source values for the back; the app has one.
            "lats" to BodyPart.BACK,
            "lower back" to BodyPart.BACK,
            "middle back" to BodyPart.BACK,
            "traps" to BodyPart.BACK,
            // No NECK in the domain enum, and neck work is shoulder-adjacent in this app.
            "neck" to BodyPart.SHOULDERS,
            "quadriceps" to BodyPart.QUADS,
            "shoulders" to BodyPart.SHOULDERS,
            "triceps" to BodyPart.TRICEPS,
        )

    private val EQUIPMENT: Map<String, Equipment> =
        mapOf(
            "bands" to Equipment.BAND,
            "barbell" to Equipment.BARBELL,
            "body only" to Equipment.BODYWEIGHT,
            "cable" to Equipment.CABLE,
            "dumbbell" to Equipment.DUMBBELL,
            // An EZ bar is a barbell for the purpose of "what do I load it with".
            "e-z curl bar" to Equipment.BARBELL,
            "kettlebells" to Equipment.KETTLEBELL,
            "machine" to Equipment.MACHINE,
            // Nothing in the domain enum fits these, and inventing entries for them would add
            // concepts the constitution asks us not to add.
            "exercise ball" to Equipment.OTHER,
            "foam roll" to Equipment.OTHER,
            "medicine ball" to Equipment.OTHER,
            "other" to Equipment.OTHER,
        )

    /** Source muscle names this mapping knows about. */
    val knownMuscles: Set<String> get() = MUSCLES.keys

    /** Source equipment names this mapping knows about. */
    val knownEquipment: Set<String> get() = EQUIPMENT.keys

    /** @throws IllegalArgumentException if [source] is a muscle the mapping has never seen. */
    fun bodyPart(source: String): BodyPart =
        MUSCLES[source.normalise()]
            ?: throw IllegalArgumentException(
                "Unmapped muscle '$source'. Add it to ExerciseTaxonomy.MUSCLES and its test table.",
            )

    /**
     * @param source the raw equipment value, which is absent for 77 of the 873 source
     *   exercises. Absent is [Equipment.UNSPECIFIED], **not** [Equipment.OTHER] (ADR-0015):
     *   "the catalog does not say" and "miscellaneous equipment" are different answers, and
     *   collapsing them made the M3 equipment filter claim knowledge it did not have.
     * @throws IllegalArgumentException if [source] is present but unrecognised.
     */
    fun equipment(source: String?): Equipment {
        val normalised = source?.normalise().orEmpty()
        if (normalised.isEmpty()) return Equipment.UNSPECIFIED

        return EQUIPMENT[normalised]
            ?: throw IllegalArgumentException(
                "Unmapped equipment '$source'. Add it to ExerciseTaxonomy.EQUIPMENT and its test table.",
            )
    }

    private fun String.normalise(): String = trim().lowercase()
}
