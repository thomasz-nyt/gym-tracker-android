package com.gymtracker.core.data.exercise

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.MediaType

/**
 * The `exercises` table from `data-model.md`. List columns are JSON, as the schema specifies.
 *
 * Ids are UUIDv5 over the source slug, produced by `:tools:catalog`, so every device seeds
 * identical ids and `sets.exercise_id` needs no remapping at the first M2 sync.
 */
@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"]), Index(value = ["is_starter"])],
)
data class ExerciseEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "aliases_json") val aliasesJson: String,
    @ColumnInfo(name = "primary_json") val primaryJson: String,
    @ColumnInfo(name = "secondary_json") val secondaryJson: String,
    @ColumnInfo(name = "equipment") val equipment: String,
    @ColumnInfo(name = "instructions_json") val instructionsJson: String,
    @ColumnInfo(name = "media_url") val mediaUrl: String?,
    @ColumnInfo(name = "media_type") val mediaType: String?,
    @ColumnInfo(name = "youtube_url") val youtubeUrl: String?,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "is_starter") val isStarter: Boolean,
    @ColumnInfo(name = "image_asset") val imageAsset: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

internal fun ExerciseEntity.toDomain(decode: (String) -> List<String>): Exercise =
    Exercise(
        id = ExerciseId(id),
        name = name,
        aliases = decode(aliasesJson),
        primaryMuscles = decode(primaryJson).map(BodyPart::valueOf),
        secondaryMuscles = decode(secondaryJson).map(BodyPart::valueOf),
        equipment = Equipment.valueOf(equipment),
        instructions = decode(instructionsJson),
        mediaUrl = mediaUrl,
        mediaType = mediaType?.let(MediaType::valueOf),
        youtubeUrl = youtubeUrl,
        source = source,
        isStarter = isStarter,
        imageAsset = imageAsset,
    )
