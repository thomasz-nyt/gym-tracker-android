package com.gymtracker.core.domain.machineguide

import com.gymtracker.core.domain.model.ExerciseId
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Stable identity for one reviewed revision of an exact-machine guide. */
@JvmInline
value class MachineGuideId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Machine guide id cannot be blank" }
    }
}

/** The app-native geometry program that a reviewed guide is allowed to request. */
enum class MachineDemonstration {
    LEG_PRESS,
    LEG_EXTENSION,
    LEVERAGE_CHEST_PRESS,
    LEVERAGE_SHOULDER_PRESS,
    SEATED_CABLE_ROW,
    SEATED_LEG_CURL,
    WIDE_GRIP_LAT_PULLDOWN,
}

/** Concise trainer cues shown before the catalog's longer source instructions. */
data class MachineGuideCues(
    val setup: List<String>,
    val movement: List<String>,
    val checkpoints: List<String>,
) {
    init {
        require(setup.hasOnlyRealCues()) { "Setup cues cannot be empty" }
        require(movement.hasOnlyRealCues()) { "Movement cues cannot be empty" }
        require(checkpoints.hasOnlyRealCues()) { "Checkpoint cues cannot be empty" }
    }
}

/**
 * Reviewed instruction for one exact machine mapped to one exact catalog exercise.
 *
 * Construction enforces ADR-0041's provenance gate so an unreviewed DTO cannot accidentally
 * become renderable data. This is advisory content only; it contains no rep count or log value.
 */
data class MachineGuide(
    val id: MachineGuideId,
    val exerciseId: ExerciseId,
    val manufacturer: String,
    val model: String,
    val cues: MachineGuideCues,
    val manualReference: String,
    val reviewer: String,
    val reviewedAt: LocalDate,
    val demonstration: MachineDemonstration,
) {
    init {
        require(manufacturer.isNotBlank()) { "Machine manufacturer cannot be blank" }
        require(model.isNotBlank()) { "Machine model cannot be blank" }
        require(manualReference.isNotBlank()) { "Manufacturer manual reference cannot be blank" }
        require(reviewer.isNotBlank()) { "Guide reviewer cannot be blank" }
    }
}

/** Offline source of reviewed exact-machine guides. */
interface MachineGuideRepository {
    /** Emits a guide only for an explicit, uniquely reviewed [exerciseId] mapping. */
    fun observeFor(exerciseId: ExerciseId): Flow<MachineGuide?>
}

private fun List<String>.hasOnlyRealCues(): Boolean = isNotEmpty() && all(String::isNotBlank)
