package com.gymtracker.tools.catalog

import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * The mapping from free-exercise-db's taxonomy onto the domain enums (`kickoff.md` § Seed data).
 *
 * The two `every ... value maps` cases are the important ones: they enumerate every value that
 * actually occurs in the source catalog, so refreshing the catalog and gaining a new muscle or
 * equipment name fails here rather than silently landing everything in OTHER.
 */
class ExerciseTaxonomyTest {
    /** Every distinct `primaryMuscles`/`secondaryMuscles` value in free-exercise-db. */
    private val sourceMuscles =
        mapOf(
            "abdominals" to BodyPart.CORE,
            "abductors" to BodyPart.GLUTES,
            "adductors" to BodyPart.GLUTES,
            "biceps" to BodyPart.BICEPS,
            "calves" to BodyPart.CALVES,
            "chest" to BodyPart.CHEST,
            "forearms" to BodyPart.FOREARMS,
            "glutes" to BodyPart.GLUTES,
            "hamstrings" to BodyPart.HAMSTRINGS,
            "lats" to BodyPart.BACK,
            "lower back" to BodyPart.BACK,
            "middle back" to BodyPart.BACK,
            "neck" to BodyPart.SHOULDERS,
            "quadriceps" to BodyPart.QUADS,
            "shoulders" to BodyPart.SHOULDERS,
            "traps" to BodyPart.BACK,
            "triceps" to BodyPart.TRICEPS,
        )

    /** Every distinct `equipment` value in free-exercise-db. */
    private val sourceEquipment =
        mapOf(
            "bands" to Equipment.BAND,
            "barbell" to Equipment.BARBELL,
            "body only" to Equipment.BODYWEIGHT,
            "cable" to Equipment.CABLE,
            "dumbbell" to Equipment.DUMBBELL,
            "e-z curl bar" to Equipment.BARBELL,
            "exercise ball" to Equipment.OTHER,
            "foam roll" to Equipment.OTHER,
            "kettlebells" to Equipment.KETTLEBELL,
            "machine" to Equipment.MACHINE,
            "medicine ball" to Equipment.OTHER,
            "other" to Equipment.OTHER,
        )

    @Test
    fun `every muscle value in the source catalog maps`() {
        sourceMuscles.forEach { (source, expected) ->
            assertEquals(expected, ExerciseTaxonomy.bodyPart(source), "muscle '$source'")
        }
    }

    @Test
    fun `every equipment value in the source catalog maps`() {
        sourceEquipment.forEach { (source, expected) ->
            assertEquals(expected, ExerciseTaxonomy.equipment(source), "equipment '$source'")
        }
    }

    @Test
    fun `absent equipment is OTHER, not a guess`() {
        // 77 of the 873 source exercises have no equipment field at all.
        assertEquals(Equipment.OTHER, ExerciseTaxonomy.equipment(null))
        assertEquals(Equipment.OTHER, ExerciseTaxonomy.equipment(""))
    }

    @Test
    fun `matching ignores case and surrounding space`() {
        assertEquals(BodyPart.BACK, ExerciseTaxonomy.bodyPart("  Lower Back "))
        assertEquals(Equipment.BARBELL, ExerciseTaxonomy.equipment("E-Z Curl Bar"))
    }

    @Test
    fun `an unknown muscle fails the conversion instead of being swallowed`() {
        val error = assertThrows<IllegalArgumentException> { ExerciseTaxonomy.bodyPart("spleen") }

        assertEquals(true, error.message?.contains("spleen"), "the message must name the value")
    }

    @Test
    fun `an unknown equipment value fails the conversion instead of becoming OTHER`() {
        // OTHER is reserved for source values we have decided map to OTHER. A value we have
        // never seen is a catalog change we need to look at, not something to bucket silently.
        assertThrows<IllegalArgumentException> { ExerciseTaxonomy.equipment("hydraulic press") }
    }

    @Test
    fun `the mapping covers every source value and nothing else is claimed`() {
        assertEquals(sourceMuscles.keys, ExerciseTaxonomy.knownMuscles)
        assertEquals(sourceEquipment.keys, ExerciseTaxonomy.knownEquipment)
    }
}
