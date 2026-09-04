package com.gymtracker.feature.logging

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import org.junit.Test
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether "Save set" / "Save changes" should actually be tappable.
 *
 * `SetEntryController.confirm()` and `SetEditController.save()` already refuse to write an
 * unparseable weight or RPE — `validated()` returns null and the call is a no-op. Neither
 * sheet's own `enabled` predicate checked for that, though: it looked only at reps and sets, so
 * typing `abc` into Weight or RPE left the button enabled and tapping it did nothing, silently.
 * `canSave()` is the one predicate both the button and the write now share, so the two cannot
 * drift apart again the way they did here.
 */
class SetSaveabilityTest {
    @Test
    fun `a blank weight is saveable — that is a bodyweight set`() {
        assertTrue(entry(weight = "").canSave())
    }

    @Test
    fun `a numeric weight and RPE are both saveable`() {
        assertTrue(entry(weight = "135", rpe = "8").canSave())
    }

    @Test
    fun `an unparseable weight is not saveable`() {
        assertFalse(entry(weight = "abc").canSave(), "typing garbage must disable Save set, not silently no-op it")
    }

    @Test
    fun `an unparseable RPE is not saveable`() {
        assertFalse(entry(rpe = "abc").canSave(), "typing garbage must disable Save set, not silently no-op it")
    }

    @Test
    fun `a blank RPE is saveable — RPE is optional`() {
        assertTrue(entry(rpe = "").canSave())
    }

    @Test
    fun `reps below one is not saveable`() {
        assertFalse(entry(reps = "0").canSave())
    }

    @Test
    fun `unparseable reps is not saveable`() {
        assertFalse(entry(reps = "abc").canSave())
    }

    @Test
    fun `sets below one is not saveable`() {
        assertFalse(entry(sets = "0").canSave())
    }

    @Test
    fun `edit — a blank weight is saveable`() {
        assertTrue(edit(weight = "").canSave())
    }

    @Test
    fun `edit — an unparseable weight is not saveable`() {
        assertFalse(edit(weight = "abc").canSave(), "typing garbage must disable Save changes, not silently no-op it")
    }

    @Test
    fun `edit — an unparseable RPE is not saveable`() {
        assertFalse(edit(rpe = "abc").canSave(), "typing garbage must disable Save changes, not silently no-op it")
    }

    @Test
    fun `edit — reps below one is not saveable`() {
        assertFalse(edit(reps = "0").canSave())
    }

    private fun entry(
        weight: String = "135",
        reps: String = "8",
        sets: String = "1",
        rpe: String = "",
    ) = SetEntry(
        sessionExerciseId = SessionExerciseId("se-1"),
        exerciseName = "Bench Press",
        weight = weight,
        reps = reps,
        sets = sets,
        rpe = rpe,
        prefilled = false,
        fromHistory = false,
    )

    private fun edit(
        weight: String = "135",
        reps: String = "8",
        rpe: String = "",
    ) = SetEdit(
        set =
            ExerciseSet(
                id = "set-1",
                sessionExerciseId = SessionExerciseId("se-1"),
                setIndex = 1,
                weightKg = 61.23,
                reps = 8,
                rpe = null,
                performedAt = FIXED_INSTANT,
            ),
        exerciseName = "Bench Press",
        weight = weight,
        reps = reps,
        rpe = rpe,
    )

    private companion object {
        val FIXED_INSTANT: Instant = Instant.parse("2026-08-07T17:30:00Z")
    }
}
