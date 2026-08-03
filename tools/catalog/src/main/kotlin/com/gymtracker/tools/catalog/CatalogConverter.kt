package com.gymtracker.tools.catalog

import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.UUID

/** One entry as free-exercise-db publishes it. Unused source fields are simply not declared. */
@Serializable
data class SourceExercise(
    val id: String,
    val name: String,
    val equipment: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
)

/** One entry as the app bundles it. Mirrors `Exercise` in `:core:domain`. */
@Serializable
data class CatalogExercise(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val primaryMuscles: List<BodyPart>,
    val secondaryMuscles: List<BodyPart>,
    val equipment: Equipment,
    val instructions: List<String>,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("youtube_url") val youtubeUrl: String? = null,
    val source: String,
    /** Pinned above the alphabetical tail when the member has no history (ADR-0007). */
    @SerialName("is_starter") val isStarter: Boolean = false,
    /** File name under `assets/exercise_images/`, or null when no image is bundled. */
    @SerialName("image_asset") val imageAsset: String? = null,
)

/**
 * Converts the free-exercise-db catalog into the app's schema.
 *
 * Deliberately strict: an unmapped taxonomy value or a duplicate slug fails the conversion.
 * A catalog refresh that quietly mangled 900 exercises would be very hard to notice.
 */
object CatalogConverter {
    /** Provenance recorded on every bundled exercise (`data-model.md`). */
    const val SOURCE = "free-exercise-db"

    /**
     * Fixed namespace for catalog ids: `uuid5(NAMESPACE_URL, <this project's catalog URL>)`.
     * It must never change — every `sets.exercise_id` on every device points at ids derived
     * from it.
     */
    val NAMESPACE: UUID = UUID.fromString("db4d89a0-3fb8-5863-a109-dee16d1e7566")

    fun convert(source: List<SourceExercise>): List<CatalogExercise> {
        val starters = STARTER_EXERCISE_SLUGS.toSet()
        val slugs = source.mapTo(mutableSetOf()) { it.id }
        // Only enforced against a full catalog: unit tests convert two-row fixtures.
        if (slugs.size > STARTER_EXERCISE_SLUGS.size) {
            val absent = starters - slugs
            require(absent.isEmpty()) {
                "Starter slugs missing from the source catalog: $absent. " +
                    "The catalog was refreshed and STARTER_EXERCISE_SLUGS needs updating."
            }

            // Same reasoning for aliases (ADR-0015): an alias pointing at an exercise that no
            // longer exists is a search term that silently stops finding anything.
            val orphaned = EXERCISE_ALIASES.keys - slugs
            require(orphaned.isEmpty()) {
                "Alias slugs missing from the source catalog: $orphaned. " +
                    "The catalog was refreshed and EXERCISE_ALIASES needs updating."
            }
        }

        source
            .groupingBy { it.id }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .let { duplicates ->
                require(duplicates.isEmpty()) { "Duplicate source slugs, which would collide as ids: $duplicates" }
            }

        return source
            .map { exercise ->
                CatalogExercise(
                    id = uuid5(NAMESPACE, exercise.id).toString(),
                    name = exercise.name,
                    // What the household calls it, where the source's name is not that
                    // (ADR-0015). Empty for most of the catalog.
                    aliases = EXERCISE_ALIASES[exercise.id].orEmpty(),
                    primaryMuscles = exercise.primaryMuscles.map(ExerciseTaxonomy::bodyPart),
                    secondaryMuscles = exercise.secondaryMuscles.map(ExerciseTaxonomy::bodyPart),
                    equipment = ExerciseTaxonomy.equipment(exercise.equipment),
                    instructions = exercise.instructions,
                    // Media arrives at M3, mirrored into our own storage rather than hotlinked.
                    mediaUrl = null,
                    youtubeUrl = null,
                    source = SOURCE,
                    isStarter = exercise.id in starters,
                    // Only starters ship an image; the rest stay null rather than pointing at
                    // something that is not there (ADR-0007, constitution §2).
                    imageAsset = if (exercise.id in starters) "${exercise.id}.jpg" else null,
                )
            }.sortedBy { it.name }
    }
}

/**
 * RFC 4122 version 5 (SHA-1) UUID.
 *
 * `UUID.nameUUIDFromBytes` is version 3 (MD5), so it cannot be used here — `data-model.md`
 * specifies v5 and the ids are permanent.
 */
fun uuid5(
    namespace: UUID,
    name: String,
): UUID {
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(namespace.toBytes())
    digest.update(name.toByteArray(Charsets.UTF_8))
    val hash = digest.digest()

    hash[VERSION_BYTE] = ((hash[VERSION_BYTE].toInt() and VERSION_MASK) or VERSION_5).toByte()
    hash[VARIANT_BYTE] = ((hash[VARIANT_BYTE].toInt() and VARIANT_MASK) or VARIANT_RFC4122).toByte()

    return UUID(hash.toLongAt(0), hash.toLongAt(Long.SIZE_BYTES))
}

// RFC 4122 §4.3: the version goes in the high nibble of byte 6, the variant in the top two
// bits of byte 8.
private const val VERSION_BYTE = 6
private const val VARIANT_BYTE = 8
private const val VERSION_MASK = 0x0F
private const val VERSION_5 = 0x50
private const val VARIANT_MASK = 0x3F
private const val VARIANT_RFC4122 = 0x80
private const val BYTE_MASK = 0xFFL

private fun UUID.toBytes(): ByteArray =
    ByteArray(2 * Long.SIZE_BYTES).also { bytes ->
        mostSignificantBits.writeInto(bytes, 0)
        leastSignificantBits.writeInto(bytes, Long.SIZE_BYTES)
    }

private fun Long.writeInto(
    target: ByteArray,
    offset: Int,
) {
    for (i in 0 until Long.SIZE_BYTES) {
        target[offset + i] = (this ushr (Byte.SIZE_BITS * (Long.SIZE_BYTES - 1 - i))).toByte()
    }
}

private fun ByteArray.toLongAt(offset: Int): Long {
    var value = 0L
    for (i in 0 until Long.SIZE_BYTES) {
        value = (value shl Byte.SIZE_BITS) or (this[offset + i].toLong() and BYTE_MASK)
    }
    return value
}
