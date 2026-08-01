package com.gymtracker.core.domain.exercise

import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * US-12: filter by body part and equipment, combine both, and match names as well as the
 * aliases ADR-0015 authors.
 *
 * The ranking stays in SQL — recently used, then starters, then alphabetical — so everything
 * here has to preserve the order it is given.
 */
class CatalogQueryTest {
    private fun exercise(
        name: String,
        primary: List<BodyPart> = listOf(BodyPart.CHEST),
        secondary: List<BodyPart> = emptyList(),
        equipment: Equipment = Equipment.MACHINE,
        aliases: List<String> = emptyList(),
    ) = Exercise(
        id = ExerciseId(name.lowercase().replace(' ', '-')),
        name = name,
        aliases = aliases,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        equipment = equipment,
        instructions = emptyList(),
        mediaUrl = null,
        mediaType = null,
        youtubeUrl = null,
        source = "test",
    )

    private val latPulldown =
        exercise(
            "Wide-Grip Lat Pulldown",
            primary = listOf(BodyPart.BACK),
            secondary = listOf(BodyPart.BICEPS),
            equipment = Equipment.CABLE,
            aliases = listOf("pulldown", "lat pulldown"),
        )
    private val benchPress =
        exercise("Barbell Bench Press", primary = listOf(BodyPart.CHEST), equipment = Equipment.BARBELL)
    private val chestPress =
        exercise("Leverage Chest Press", primary = listOf(BodyPart.CHEST), equipment = Equipment.MACHINE)
    private val seatedRow =
        exercise("Seated Cable Row", primary = listOf(BodyPart.BACK), equipment = Equipment.CABLE)
    private val abWheel =
        exercise("Ab Roller", primary = listOf(BodyPart.CORE), equipment = Equipment.UNSPECIFIED)

    private val catalog = listOf(latPulldown, benchPress, chestPress, seatedRow, abWheel)

    private fun query(
        text: String = "",
        filter: CatalogFilter = CatalogFilter(),
    ) = CatalogQuery.apply(catalog, text, filter).map { it.name }

    @Test
    fun `an empty query with no filters returns the whole catalog, in the order given`() {
        assertEquals(catalog.map { it.name }, query())
    }

    @Test
    fun `a name match is a case-insensitive substring, as the old SQL LIKE was`() {
        // US-02's search behaviour must not change when its matching moves out of SQL.
        assertEquals(listOf("Barbell Bench Press"), query("bench"))
        assertEquals(listOf("Barbell Bench Press"), query("BENCH"))
        assertEquals(listOf("Barbell Bench Press"), query("Bench Press"))
    }

    @Test
    fun `an alias matches even when the name shares nothing with it`() {
        // ADR-0015: the household searches by feel. "pulldown" has to find the machine.
        assertEquals(listOf("Wide-Grip Lat Pulldown"), query("pulldown"))
    }

    @Test
    fun `alias matching is case-insensitive too`() {
        assertEquals(listOf("Wide-Grip Lat Pulldown"), query("PullDown"))
    }

    @Test
    fun `a query matching nothing returns nothing rather than everything`() {
        assertEquals(emptyList(), query("zercher"))
    }

    @Test
    fun `whitespace around a query is ignored`() {
        assertEquals(listOf("Barbell Bench Press"), query("  bench  "))
    }

    @Test
    fun `a body-part filter matches the muscles the exercise actually trains`() {
        assertEquals(
            listOf("Wide-Grip Lat Pulldown", "Seated Cable Row"),
            query(filter = CatalogFilter(bodyParts = setOf(BodyPart.BACK))),
        )
    }

    @Test
    fun `a body-part filter ignores secondary muscles`() {
        // The lat pulldown works biceps secondarily. Filtering for biceps and being handed
        // every pulling movement in the catalog is not a useful filter — you are looking for
        // exercises that train biceps, not ones that involve them.
        assertTrue(BodyPart.BICEPS in latPulldown.secondaryMuscles)

        assertEquals(emptyList(), query(filter = CatalogFilter(bodyParts = setOf(BodyPart.BICEPS))))
    }

    @Test
    fun `several body parts widen the results rather than narrowing them`() {
        val bothGroups = CatalogFilter(bodyParts = setOf(BodyPart.BACK, BodyPart.CORE))

        assertEquals(listOf("Wide-Grip Lat Pulldown", "Seated Cable Row", "Ab Roller"), query(filter = bothGroups))
    }

    @Test
    fun `an equipment filter selects on what the exercise is performed on`() {
        assertEquals(
            listOf("Wide-Grip Lat Pulldown", "Seated Cable Row"),
            query(filter = CatalogFilter(equipment = setOf(Equipment.CABLE))),
        )
    }

    @Test
    fun `equipment the catalog never recorded is filterable in its own right`() {
        // ADR-0015: UNSPECIFIED is a real answer — "we do not know" — and you can ask for it.
        assertEquals(listOf("Ab Roller"), query(filter = CatalogFilter(equipment = setOf(Equipment.UNSPECIFIED))))
    }

    @Test
    fun `body part and equipment narrow each other`() {
        // US-12: "combine both". Across dimensions it is AND; within one it is OR.
        val backCable = CatalogFilter(bodyParts = setOf(BodyPart.BACK), equipment = setOf(Equipment.CABLE))
        val backBarbell = CatalogFilter(bodyParts = setOf(BodyPart.BACK), equipment = setOf(Equipment.BARBELL))

        assertEquals(listOf("Wide-Grip Lat Pulldown", "Seated Cable Row"), query(filter = backCable))
        assertEquals(emptyList(), query(filter = backBarbell), "no barbell back movement in this fixture")
    }

    @Test
    fun `the query and the filters both apply, rather than one replacing the other`() {
        val cable = CatalogFilter(equipment = setOf(Equipment.CABLE))

        assertEquals(listOf("Seated Cable Row"), query("row", cable))
        assertEquals(emptyList(), query("bench", cable), "the bench press is not on a cable")
    }

    @Test
    fun `an empty filter constrains nothing`() {
        assertTrue(CatalogFilter().isEmpty)
        assertEquals(catalog.size, query(filter = CatalogFilter()).size)
    }

    @Test
    fun `a filter reports how many choices are active, for the screen to show`() {
        val filter = CatalogFilter(bodyParts = setOf(BodyPart.BACK, BodyPart.CHEST), equipment = setOf(Equipment.CABLE))

        assertEquals(3, filter.count)
        assertTrue(!filter.isEmpty)
    }
}
