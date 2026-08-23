package com.gymtracker.core.domain.machineguide

import com.gymtracker.core.domain.model.ExerciseId
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertFailsWith

class MachineGuideTest {
    @Test
    fun `a reviewed guide requires exact machine provenance and every cue group`() {
        validGuide()

        listOf(
            { validGuide(manufacturer = "") },
            { validGuide(model = " ") },
            { validGuide(manualReference = "") },
            { validGuide(reviewer = "") },
            { validGuide(setup = emptyList()) },
            { validGuide(movement = emptyList()) },
            { validGuide(checkpoints = emptyList()) },
        ).forEach { invalid -> assertFailsWith<IllegalArgumentException> { invalid() } }
    }

    @Test
    fun `cue text cannot smuggle an empty instruction into a nonempty group`() {
        assertFailsWith<IllegalArgumentException> {
            validGuide(checkpoints = listOf("Knees follow toes", "  "))
        }
    }

    private fun validGuide(
        manufacturer: String = "Example Fitness",
        model: String = "LP-100",
        manualReference: String = "manuals.example/LP-100",
        reviewer: String = "Gym trainer",
        setup: List<String> = listOf("Set the seat"),
        movement: List<String> = listOf("Press through the platform"),
        checkpoints: List<String> = listOf("Keep hips against the pad"),
    ): MachineGuide =
        MachineGuide(
            id = MachineGuideId("leg-press-lp-100-v1"),
            exerciseId = ExerciseId("492fa83f-3134-5d16-8b03-386dada93dad"),
            manufacturer = manufacturer,
            model = model,
            cues = MachineGuideCues(setup, movement, checkpoints),
            manualReference = manualReference,
            reviewer = reviewer,
            reviewedAt = LocalDate.parse("2026-08-23"),
            demonstration = MachineDemonstration.LEG_PRESS,
        )
}
