package com.gymtracker.tools.catalog

import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `data-model.md` § "Catalog IDs are deterministic": ids are UUIDv5 over a fixed namespace and
 * the source slug, so every device seeds identical ids and `sets.exercise_id` needs no
 * remapping at the first M2 sync.
 */
class CatalogConverterTest {
    private fun source(
        id: String = "Lat_Pulldown",
        name: String = "Lat Pulldown",
        equipment: String? = "cable",
        primary: List<String> = listOf("lats"),
        secondary: List<String> = emptyList(),
        instructions: List<String> = listOf("Sit down.", "Pull the bar."),
    ) = SourceExercise(
        id = id,
        name = name,
        equipment = equipment,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        instructions = instructions,
    )

    @Test
    fun `uuid5 matches the RFC 4122 test vector`() {
        // If this breaks, every id in the catalog has silently changed.
        assertEquals(
            UUID.fromString("cfbff0d1-9375-5685-968c-48ce8b15ae17"),
            uuid5(UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"), "example.com"),
        )
    }

    @Test
    fun `the same slug always produces the same id`() {
        val first = CatalogConverter.convert(listOf(source())).single()
        val second = CatalogConverter.convert(listOf(source())).single()

        assertEquals(first.id, second.id)
        assertEquals(
            "95c05761-b951-5887-8e35-24c0a77af798",
            CatalogConverter.convert(listOf(source(id = "3_4_Sit-Up"))).single().id,
        )
    }

    @Test
    fun `different slugs produce different ids`() {
        val ids = CatalogConverter.convert(listOf(source(id = "A"), source(id = "B"))).map { it.id }

        assertEquals(2, ids.toSet().size)
    }

    @Test
    fun `the id is a well formed uuid`() {
        val id = CatalogConverter.convert(listOf(source())).single().id

        assertEquals(id, UUID.fromString(id).toString())
    }

    @Test
    fun `taxonomy is applied to both primary and secondary muscles`() {
        val converted =
            CatalogConverter
                .convert(listOf(source(primary = listOf("lats", "traps"), secondary = listOf("abdominals"))))
                .single()

        assertEquals(listOf(BodyPart.BACK, BodyPart.BACK), converted.primaryMuscles)
        assertEquals(listOf(BodyPart.CORE), converted.secondaryMuscles)
    }

    @Test
    fun `missing equipment becomes UNSPECIFIED, not OTHER`() {
        // ADR-0015: "the catalog does not say" is a different answer from "miscellaneous
        // equipment", and it was 77 of the 873 rows hiding inside OTHER.
        val converted = CatalogConverter.convert(listOf(source(equipment = null))).single()

        assertEquals(Equipment.UNSPECIFIED, converted.equipment)
    }

    @Test
    fun `equipment that is genuinely miscellaneous is still OTHER`() {
        val converted = CatalogConverter.convert(listOf(source(equipment = "medicine ball"))).single()

        assertEquals(Equipment.OTHER, converted.equipment)
    }

    @Test
    fun `the bundled catalog ships no media url`() {
        // kickoff.md § Seed data: mirror media into our own storage at M3, never hotlink.
        val converted = CatalogConverter.convert(listOf(source())).single()

        assertEquals(null, converted.mediaUrl)
        assertEquals(null, converted.youtubeUrl)
    }

    @Test
    fun `provenance is recorded on every exercise`() {
        val converted = CatalogConverter.convert(listOf(source())).single()

        assertEquals("free-exercise-db", converted.source)
    }

    @Test
    fun `name and instructions survive unchanged`() {
        val converted =
            CatalogConverter
                .convert(
                    listOf(source(name = "Barbell Bench Press", instructions = listOf("A", "B"))),
                ).single()

        assertEquals("Barbell Bench Press", converted.name)
        assertEquals(listOf("A", "B"), converted.instructions)
    }

    @Test
    fun `output is sorted by name so the file diffs cleanly on a refresh`() {
        val converted =
            CatalogConverter.convert(
                listOf(source(id = "z", name = "Zercher Squat"), source(id = "a", name = "Ab Roller")),
            )

        assertEquals(listOf("Ab Roller", "Zercher Squat"), converted.map { it.name })
    }

    @Test
    fun `a duplicate slug is an error rather than a silently dropped exercise`() {
        val error =
            assertThrows<IllegalArgumentException> {
                CatalogConverter.convert(listOf(source(id = "same"), source(id = "same")))
            }

        assertTrue(error.message.orEmpty().contains("same"))
    }

    @Test
    fun `starter exercises are flagged and everything else is not`() {
        val converted =
            CatalogConverter.convert(
                listOf(source(id = "Barbell_Squat", name = "Barbell Squat"), source(id = "obscure", name = "Obscure")),
            )

        assertEquals(true, converted.single { it.name == "Barbell Squat" }.isStarter)
        assertEquals(false, converted.single { it.name == "Obscure" }.isStarter)
    }

    @Test
    fun `a starter exercise carries the image bundled for it`() {
        val converted = CatalogConverter.convert(listOf(source(id = "Barbell_Squat", name = "Barbell Squat"))).single()

        assertEquals("Barbell_Squat.jpg", converted.imageAsset)
    }

    @Test
    fun `a non-starter exercise has no image rather than a broken one`() {
        // constitution §2: absent is shown as absent, never faked.
        val converted = CatalogConverter.convert(listOf(source(id = "obscure", name = "Obscure"))).single()

        assertNull(converted.imageAsset)
    }

    @Test
    fun `the starter list has no duplicates`() {
        assertEquals(STARTER_EXERCISE_SLUGS.size, STARTER_EXERCISE_SLUGS.toSet().size)
    }

    @Test
    fun `aliases are attached to the exercise they name`() {
        // ADR-0015. "pec deck" is what the household calls the Butterfly machine.
        val converted = CatalogConverter.convert(listOf(source(id = "Butterfly", name = "Butterfly"))).single()

        assertEquals(EXERCISE_ALIASES.getValue("Butterfly"), converted.aliases)
    }

    @Test
    fun `an exercise nobody has nicknamed carries no aliases`() {
        val converted = CatalogConverter.convert(listOf(source(id = "obscure", name = "Obscure"))).single()

        assertEquals(emptyList(), converted.aliases)
    }

    @Test
    fun `no alias merely repeats a word already in the exercise's own name`() {
        // Such an alias is dead data: the name search already matches it. Keeping the table
        // honest is what stops it growing into a synonym dictionary nobody maintains.
        val named =
            EXERCISE_ALIASES.keys.associateWith { slug -> slug.replace('_', ' ') }

        EXERCISE_ALIASES.forEach { (slug, aliases) ->
            val name = named.getValue(slug)
            aliases.forEach { alias ->
                assertTrue(
                    !name.contains(alias, ignoreCase = true),
                    "'$alias' is already inside '$name', so the name search finds it",
                )
            }
        }
    }

    @Test
    fun `no alias is claimed by two different exercises`() {
        // A duplicate would make one of the two unreachable by that term, silently.
        val all = EXERCISE_ALIASES.values.flatten()

        assertEquals(all.size, all.toSet().size, "duplicate alias across exercises: $all")
    }

    @Test
    fun `an unmapped muscle stops the conversion`() {
        assertThrows<IllegalArgumentException> {
            CatalogConverter.convert(listOf(source(primary = listOf("spleen"))))
        }
    }
}
