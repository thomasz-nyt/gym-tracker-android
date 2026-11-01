package com.gymtracker.core.domain.backup

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementTarget

/** The graph/value checks shared by preview, safety copies and replacement (US-66). */
internal object BackupGraphValidation {
    fun problems(contents: BackupContents): List<String> =
        buildList {
            addAll(identities(contents))
            addAll(relations(contents))
            addAll(values(contents))
        }

    private fun identities(contents: BackupContents): List<String> =
        with(contents) {
            val tables =
                mapOf(
                    "Workouts" to sessions.map { it.id.value },
                    "Movements" to sessionExercises.map { it.id.value },
                    "Sets" to sets.map { it.id },
                    "Routines" to routines.map { it.id.value },
                    "Routine movements" to routineItems.map { it.id.value },
                )
            buildList {
                if (memberId.value.isBlank()) add("The backup owner is missing.")
                tables.forEach { (label, ids) ->
                    if (ids.any(String::isBlank) || ids.distinct().size != ids.size) {
                        add("$label contains missing or duplicate identities.")
                    }
                }
            }
        }

    private fun relations(contents: BackupContents): List<String> =
        with(contents) {
            val sessionIds = sessions.map { it.id }.toSet()
            val movementIds = sessionExercises.map { it.id }.toSet()
            val routineIds = routines.map { it.id }.toSet()
            buildList {
                if (sessions.any { it.userId != memberId } || routines.any { it.userId != memberId }) {
                    add("Every record must belong to the backup owner.")
                }
                if (sessionExercises.any { it.sessionId !in sessionIds }) {
                    add(
                        "A movement references a missing workout.",
                    )
                }
                if (sets.any { it.sessionExerciseId !in movementIds }) add("A set references a missing movement.")
                if (routineItems.any { it.routineId !in routineIds }) add("A movement references a missing routine.")
            }
        }

    private fun values(contents: BackupContents): List<String> =
        with(contents) {
            buildList {
                if (restDefault.isNegative || restDefault.isZero) add("Default rest must be positive.")
                if (sessions.count { it.isActive } > 1) add("More than one workout is active.")
                if (sessions.any { it.endedAt?.isBefore(it.startedAt) == true }) add("A workout ends before it starts.")
                if (routines.any { it.name.isBlank() || it.position < 1 }) {
                    add(
                        "A routine has an invalid name or position.",
                    )
                }
                if (sessionExercises.any { it.position < 1 } || routineItems.any { it.position < 1 }) {
                    add("Movement positions must be positive.")
                }
                if (sets.any { !it.valid() }) add("A set contains an invalid load, repetition count, index or effort.")
                if (sets.groupBy { it.sessionExerciseId }.values.any { rows ->
                        rows.distinctBy { it.setIndex }.size !=
                            rows.size
                    }
                ) {
                    add("Set numbers are duplicated within a movement.")
                }
                val targets = sessionExercises.mapNotNull { it.target } + routineItems.mapNotNull { it.target }
                if (targets.any { !it.valid() }) add("A planned target contains invalid values.")
            }
        }

    private fun ExerciseSet.valid() = setIndex > 0 && reps > 0 && validLoad(weightKg) && validEffort(rpe)

    private fun MovementTarget.valid() =
        listOf(sets, reps, restSeconds).all { it == null || it > 0 } && validLoad(weightKg)

    private fun validLoad(value: Double?): Boolean = value == null || value.isFinite() && value >= 0.0

    private fun validEffort(value: Double?): Boolean =
        value == null || value in MIN_EFFORT..MAX_EFFORT && (value * 2) % 1.0 == 0.0

    private const val MIN_EFFORT = 5.0
    private const val MAX_EFFORT = 10.0
}
