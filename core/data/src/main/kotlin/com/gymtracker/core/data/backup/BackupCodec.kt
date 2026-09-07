package com.gymtracker.core.data.backup

import com.gymtracker.core.domain.backup.BackupContents
import com.gymtracker.core.domain.backup.BackupDecoder
import com.gymtracker.core.domain.backup.BackupEncoder
import com.gymtracker.core.domain.backup.UnsupportedBackupFormatException
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItem
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.RoutineOrigin
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.SessionMetrics
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Translates [BackupContents] to and from the JSON a backup file holds (US-40, US-41,
 * ADR-0034). Modeled on `CatalogSeeder`'s `BundledExercise` — a `@Serializable` DTO in
 * `:core:data` mapping to and from a domain type, the pattern this repo already uses for its
 * one other bundled-JSON format. Implements [BackupEncoder] and [BackupDecoder] so
 * `:feature:settings` calls this through the domain ports and never needs to depend on
 * `:core:data` directly — the same split `RoomExerciseCatalog` follows for
 * [com.gymtracker.core.domain.exercise.ExerciseCatalog].
 *
 * `TooManyFunctions` is suppressed for the same reason `SetDao` suppresses it: one pair of
 * encode/decode functions per row type is exactly one responsibility — converting
 * [BackupContents] to and from JSON — and splitting it would scatter that responsibility across
 * several files rather than reduce it.
 */
@Suppress("TooManyFunctions")
class BackupCodec
    @Inject
    constructor(
        private val json: Json,
    ) : BackupEncoder,
        BackupDecoder {
        /** [exportedAt] and [appVersion] are diagnostic envelope fields, never read back. */
        override fun encode(
            contents: BackupContents,
            exportedAt: Instant,
            appVersion: String,
        ): String =
            json.encodeToString(
                BackupEnvelopeDto.serializer(),
                BackupEnvelopeDto(
                    formatVersion = CURRENT_FORMAT_VERSION,
                    exportedAt = exportedAt.toEpochMilli(),
                    appVersion = appVersion,
                    payload = contents.toDto(),
                ),
            )

        override fun decode(raw: String): BackupContents {
            val envelope = json.decodeFromString(BackupEnvelopeDto.serializer(), raw)
            if (envelope.formatVersion > CURRENT_FORMAT_VERSION) {
                throw UnsupportedBackupFormatException(envelope.formatVersion, CURRENT_FORMAT_VERSION)
            }
            return envelope.payload.toDomain()
        }

        private fun BackupContents.toDto() =
            BackupPayloadDto(
                memberId = memberId.value,
                unit = unit.name,
                restDefaultSeconds = restDefault.seconds,
                sessions = sessions.map { it.toDto() },
                sessionExercises = sessionExercises.map { it.toDto() },
                sets = sets.map { it.toDto() },
                routines = routines.map { it.toDto() },
                routineItems = routineItems.map { it.toDto() },
            )

        private fun BackupPayloadDto.toDomain(): BackupContents {
            val member = UserId(memberId)
            return BackupContents(
                memberId = member,
                unit = WeightUnit.entries.first { it.name == unit },
                restDefault = Duration.ofSeconds(restDefaultSeconds),
                sessions = sessions.map { it.toDomain(member) },
                sessionExercises = sessionExercises.map { it.toDomain() },
                sets = sets.map { it.toDomain() },
                routines = routines.map { it.toDomain(member) },
                routineItems = routineItems.map { it.toDomain() },
            )
        }

        private fun WorkoutSession.toDto() =
            SessionDto(
                id = id.value,
                gymName = gymName,
                startedAt = startedAt.toEpochMilli(),
                endedAt = endedAt?.toEpochMilli(),
                avgHeartRate = metrics?.avgHeartRate,
                maxHeartRate = metrics?.maxHeartRate,
                activeKilocalories = metrics?.activeKilocalories,
                metricsSource = metrics?.source,
                routineId = routine?.id,
                routineName = routine?.name,
            )

        /**
         * [member] rather than a per-row field: a session's owner in a backup is always the
         * file's own [BackupPayloadDto.memberId] — the whole reason it exists (ADR-0034).
         */
        private fun SessionDto.toDomain(member: UserId) =
            WorkoutSession(
                id = SessionId(id),
                userId = member,
                gymName = gymName,
                startedAt = Instant.ofEpochMilli(startedAt),
                endedAt = endedAt?.let(Instant::ofEpochMilli),
                metrics = metricsOrNull(),
                routine = if (routineId != null && routineName != null) RoutineOrigin(routineId, routineName) else null,
            )

        private fun SessionDto.metricsOrNull(): SessionMetrics? {
            val hasNothing =
                avgHeartRate == null && maxHeartRate == null && activeKilocalories == null && metricsSource == null
            return if (hasNothing) {
                null
            } else {
                SessionMetrics(avgHeartRate, maxHeartRate, activeKilocalories, metricsSource)
            }
        }

        private fun SessionExercise.toDto() =
            SessionExerciseDto(
                id = id.value,
                sessionId = sessionId.value,
                exerciseId = exerciseId.value,
                position = position,
                targetSets = target?.sets,
                targetReps = target?.reps,
                targetWeightKg = target?.weightKg,
                targetRestSeconds = target?.restSeconds,
            )

        private fun SessionExerciseDto.toDomain() =
            SessionExercise(
                id = SessionExerciseId(id),
                sessionId = SessionId(sessionId),
                exerciseId = ExerciseId(exerciseId),
                position = position,
                target = targetOrNull(targetSets, targetReps, targetWeightKg, targetRestSeconds),
            )

        private fun ExerciseSet.toDto() =
            SetDto(
                id = id,
                sessionExerciseId = sessionExerciseId.value,
                setIndex = setIndex,
                weightKg = weightKg,
                reps = reps,
                rpe = rpe,
                performedAt = performedAt.toEpochMilli(),
            )

        private fun SetDto.toDomain() =
            ExerciseSet(
                id = id,
                sessionExerciseId = SessionExerciseId(sessionExerciseId),
                setIndex = setIndex,
                weightKg = weightKg,
                reps = reps,
                rpe = rpe,
                performedAt = Instant.ofEpochMilli(performedAt),
            )

        private fun Routine.toDto() = RoutineDto(id = id.value, name = name, position = position)

        /** Same reasoning as [SessionDto.toDomain]: a routine's owner is the file's memberId. */
        private fun RoutineDto.toDomain(member: UserId) =
            Routine(
                id = RoutineId(id),
                userId = member,
                name = name,
                position = position,
            )

        private fun RoutineItem.toDto() =
            RoutineItemDto(
                id = id.value,
                routineId = routineId.value,
                exerciseId = exerciseId.value,
                position = position,
                targetSets = target?.sets,
                targetReps = target?.reps,
                targetWeightKg = target?.weightKg,
                targetRestSeconds = target?.restSeconds,
            )

        private fun RoutineItemDto.toDomain() =
            RoutineItem(
                id = RoutineItemId(id),
                routineId = RoutineId(routineId),
                exerciseId = ExerciseId(exerciseId),
                position = position,
                target = targetOrNull(targetSets, targetReps, targetWeightKg, targetRestSeconds),
            )

        /**
         * Shares `RoutineItemEntity.toTarget`'s all-null-means-absent rule (US-13's pattern), the
         * rest included (ADR-0050) — a file written by a build that knows the rest must not decode a
         * rest-only target as no target.
         */
        private fun targetOrNull(
            sets: Int?,
            reps: Int?,
            weightKg: Double?,
            restSeconds: Int?,
        ): MovementTarget? =
            if (listOfNotNull(sets, reps, weightKg, restSeconds).isEmpty()) {
                null
            } else {
                MovementTarget(sets, reps, weightKg, restSeconds)
            }
    }
