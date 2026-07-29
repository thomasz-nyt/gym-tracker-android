package com.gymtracker.core.data.exercise

import android.content.Context
import com.gymtracker.core.data.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import javax.inject.Inject

/** One entry of the bundled catalog asset, as `:tools:catalog` writes it. */
@Serializable
internal data class BundledExercise(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val equipment: String,
    val instructions: List<String> = emptyList(),
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("youtube_url") val youtubeUrl: String? = null,
    val source: String,
    @SerialName("is_starter") val isStarter: Boolean = false,
    @SerialName("image_asset") val imageAsset: String? = null,
)

/**
 * Seeds the catalog from the bundled asset.
 *
 * The catalog ships in the APK rather than being fetched, so it works on first launch with no
 * network (`kickoff.md` § Seed data, constitution §2).
 */
class CatalogSeeder
    @Inject
    constructor(
        private val dao: ExerciseDao,
        private val assets: CatalogAssetReader,
        private val json: Json,
        @param:IoDispatcher private val io: CoroutineDispatcher,
    ) {
        /**
         * Inserts the bundled catalog if the table is empty. Idempotent, so it is safe to call
         * on every launch.
         *
         * @return how many exercises were inserted; zero if the catalog was already present.
         */
        suspend fun seedIfEmpty(now: Long): Int =
            withContext(io) {
                if (dao.count() > 0) return@withContext 0

                val bundled =
                    assets.open().use { stream ->
                        json.decodeFromString<List<BundledExercise>>(stream.readBytes().decodeToString())
                    }

                dao.insertAll(bundled.map { it.toEntity(now) })
                bundled.size
            }

        private fun BundledExercise.toEntity(now: Long) =
            ExerciseEntity(
                id = id,
                name = name,
                aliasesJson = json.encodeToString(aliases),
                primaryJson = json.encodeToString(primaryMuscles),
                secondaryJson = json.encodeToString(secondaryMuscles),
                equipment = equipment,
                instructionsJson = json.encodeToString(instructions),
                mediaUrl = mediaUrl,
                mediaType = null,
                youtubeUrl = youtubeUrl,
                source = source,
                isStarter = isStarter,
                imageAsset = imageAsset,
                updatedAt = now,
            )
    }

/** Where the bundled catalog comes from. An interface so tests do not need an APK. */
fun interface CatalogAssetReader {
    fun open(): InputStream
}

/** Reads the catalog out of the APK's assets. */
class AndroidCatalogAssetReader
    @Inject
    constructor(
        private val context: Context,
    ) : CatalogAssetReader {
        override fun open(): InputStream = context.assets.open(ASSET_NAME)

        private companion object {
            const val ASSET_NAME = "exercises.json"
        }
    }
