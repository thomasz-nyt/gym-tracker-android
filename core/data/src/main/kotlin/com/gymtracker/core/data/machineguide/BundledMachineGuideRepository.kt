package com.gymtracker.core.data.machineguide

import android.content.Context
import com.gymtracker.core.data.di.IoDispatcher
import com.gymtracker.core.domain.machineguide.MachineDemonstration
import com.gymtracker.core.domain.machineguide.MachineGuide
import com.gymtracker.core.domain.machineguide.MachineGuideCues
import com.gymtracker.core.domain.machineguide.MachineGuideId
import com.gymtracker.core.domain.machineguide.MachineGuideRepository
import com.gymtracker.core.domain.model.ExerciseId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject

/** Reads the bundled reviewed-guide manifest; replaceable in a JVM test. */
fun interface MachineGuideAssetReader {
    suspend fun read(): String
}

/** Android asset implementation. The source SVGs themselves are deliberately not packaged. */
class AndroidMachineGuideAssetReader
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val io: CoroutineDispatcher,
    ) : MachineGuideAssetReader {
        override suspend fun read(): String =
            withContext(io) {
                context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            }

        private companion object {
            const val ASSET_NAME = "machine_guides.json"
        }
    }

/**
 * Cached, offline repository for ADR-0041's exact-id manifest.
 *
 * Invalid content fails absent. Duplicate exercise mappings are omitted rather than resolved by
 * list order, because picking either would make the selected machine an accident of JSON order.
 */
class BundledMachineGuideRepository
    @Inject
    constructor(
        private val assets: MachineGuideAssetReader,
        private val json: Json,
    ) : MachineGuideRepository {
        @Volatile
        private var cached: Map<ExerciseId, MachineGuide>? = null
        private val loadMutex = Mutex()

        override fun observeFor(exerciseId: ExerciseId): Flow<MachineGuide?> =
            flow { emit(guides()[exerciseId]) }

        private suspend fun guides(): Map<ExerciseId, MachineGuide> =
            cached ?: loadMutex.withLock {
                cached ?: parse(assets.read()).also { cached = it }
            }

        private fun parse(raw: String): Map<ExerciseId, MachineGuide> =
            runCatching { json.decodeFromString<List<MachineGuideDto>>(raw) }
                .getOrElse { emptyList() }
                .mapNotNull { dto -> runCatching(dto::toDomain).getOrNull() }
                .groupBy(MachineGuide::exerciseId)
                .mapNotNull { (exerciseId, guides) ->
                    guides.singleOrNull()?.let { exerciseId to it }
                }.toMap()
    }

@Serializable
private data class MachineGuideDto(
    val id: String = "",
    val exerciseId: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val setup: List<String> = emptyList(),
    val movement: List<String> = emptyList(),
    val checkpoints: List<String> = emptyList(),
    val manualReference: String = "",
    val reviewer: String = "",
    val reviewedAt: String = "",
    val demonstration: String = "",
) {
    fun toDomain(): MachineGuide =
        MachineGuide(
            id = MachineGuideId(id),
            exerciseId = ExerciseId(exerciseId),
            manufacturer = manufacturer,
            model = model,
            cues = MachineGuideCues(setup, movement, checkpoints),
            manualReference = manualReference,
            reviewer = reviewer,
            reviewedAt = LocalDate.parse(reviewedAt),
            demonstration = MachineDemonstration.valueOf(demonstration),
        )
}
