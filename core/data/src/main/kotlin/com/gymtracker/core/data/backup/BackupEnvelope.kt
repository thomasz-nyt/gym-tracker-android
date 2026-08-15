package com.gymtracker.core.data.backup

import kotlinx.serialization.Serializable

/**
 * The on-disk shape of a backup file (US-40, US-41, ADR-0034).
 *
 * Domain-shaped, not a mirror of the Room schema — the whole reason is that Room moved
 * v7 → v9 in the space of a week, and a column-shaped file would let every migration
 * invalidate every backup already on disk. Every DTO field below is either non-optional
 * because [BackupCodec] always writes it, or carries a default so a field a **future** build
 * adds can be missing here without breaking decode.
 *
 * [formatVersion] is the one thing that *does* invalidate a file: [BackupCodec.decode] refuses
 * anything with a version higher than [CURRENT_FORMAT_VERSION], by name, rather than reading it
 * partially. Bumping this constant is only correct when a change means an older build could not
 * make sense of the file — a new nullable field is never such a change.
 */
@Serializable
internal data class BackupEnvelopeDto(
    val formatVersion: Int,
    /** Epoch milliseconds. When this file was written — diagnostic only, never read back. */
    val exportedAt: Long,
    /** The app build that wrote this file — diagnostic only, never read back. */
    val appVersion: String,
    val payload: BackupPayloadDto,
)

/**
 * Everything [com.gymtracker.core.domain.backup.BackupContents] carries, in a form
 * `kotlinx.serialization` can write: value classes unwrapped to their raw string, [java.time
 * .Instant] to epoch milliseconds, [java.time.Duration] to seconds.
 */
@Serializable
internal data class BackupPayloadDto(
    val memberId: String,
    /** [com.gymtracker.core.domain.units.WeightUnit]'s name — `"KG"` or `"LB"`. */
    val unit: String,
    val restDefaultSeconds: Long,
    val sessions: List<SessionDto>,
    val sessionExercises: List<SessionExerciseDto>,
    val sets: List<SetDto>,
    val routines: List<RoutineDto>,
    val routineItems: List<RoutineItemDto>,
)

@Serializable
internal data class SessionDto(
    val id: String,
    val gymName: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val activeKilocalories: Int? = null,
    val metricsSource: String? = null,
    val routineId: String? = null,
    val routineName: String? = null,
)

@Serializable
internal data class SessionExerciseDto(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    val targetWeightKg: Double? = null,
)

@Serializable
internal data class SetDto(
    val id: String,
    val sessionExerciseId: String,
    val setIndex: Int,
    val weightKg: Double? = null,
    val reps: Int,
    val rpe: Double? = null,
    val performedAt: Long,
)

@Serializable
internal data class RoutineDto(
    val id: String,
    val name: String,
    val position: Int,
)

@Serializable
internal data class RoutineItemDto(
    val id: String,
    val routineId: String,
    val exerciseId: String,
    val position: Int,
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    val targetWeightKg: Double? = null,
)

internal const val CURRENT_FORMAT_VERSION = 1

/**
 * Thrown by [BackupCodec.decode] when a file names a [BackupEnvelopeDto.formatVersion] newer
 * than this build understands.
 */
class UnsupportedBackupFormatException(
    val fileVersion: Int,
    val supportedVersion: Int,
) : Exception(
        "backup file is format version $fileVersion, this build only understands up to $supportedVersion",
    )
