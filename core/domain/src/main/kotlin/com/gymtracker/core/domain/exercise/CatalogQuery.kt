package com.gymtracker.core.domain.exercise

import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise

/**
 * What the browse screen is currently asking of the catalog (US-12).
 *
 * Empty sets mean "no constraint on this dimension", not "match nothing" — a screen with no
 * chips selected shows the whole catalog.
 */
data class CatalogFilter(
    val bodyParts: Set<BodyPart> = emptySet(),
    val equipment: Set<Equipment> = emptySet(),
) {
    val isEmpty: Boolean get() = bodyParts.isEmpty() && equipment.isEmpty()

    /** How many chips are lit, for the screen to show beside a "Clear" affordance. */
    val count: Int get() = bodyParts.size + equipment.size
}

/**
 * Narrows the catalog by text and filters (US-12).
 *
 * This is deliberately not SQL. The ranking is — recently used, then starters, then
 * alphabetical, which needs a join against the member's sessions — but the whole catalog is
 * 873 rows and the app already holds all of them, so the predicates live here where they are
 * table-testable against hand-written fixtures rather than against a database
 * (`specs/testing-strategy.md`). It also means typing no longer costs a query per keystroke.
 */
object CatalogQuery {
    /**
     * @param exercises the ranked catalog. The returned list preserves this order.
     * @param query matched case-insensitively against the name and against each alias.
     *   Blank matches everything.
     */
    fun apply(
        exercises: List<Exercise>,
        query: String,
        filter: CatalogFilter,
    ): List<Exercise> {
        val text = query.trim()
        return exercises.filter { it.matches(text) && it.satisfies(filter) }
    }

    private fun Exercise.matches(query: String): Boolean {
        if (query.isEmpty()) return true
        return name.contains(query, ignoreCase = true) ||
            aliases.any { it.contains(query, ignoreCase = true) }
    }

    /**
     * Across dimensions this is AND, within one it is OR: "back or chest, on a cable or a
     * machine" is the shape US-12's "combine both" describes.
     *
     * Body parts match **primary** muscles only. Secondary would mean filtering for biceps
     * returns every pulling movement in the catalog, which is not what someone looking for a
     * biceps exercise is asking.
     */
    private fun Exercise.satisfies(filter: CatalogFilter): Boolean {
        val bodyPartOk = filter.bodyParts.isEmpty() || primaryMuscles.any { it in filter.bodyParts }
        val equipmentOk = filter.equipment.isEmpty() || equipment in filter.equipment
        return bodyPartOk && equipmentOk
    }
}
