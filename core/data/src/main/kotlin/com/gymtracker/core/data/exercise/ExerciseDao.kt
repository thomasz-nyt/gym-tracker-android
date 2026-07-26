package com.gymtracker.core.data.exercise

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Queries over the bundled exercise catalog. */
@Dao
interface ExerciseDao {
    /**
     * Catalog search. `LIKE` is case-insensitive for ASCII in SQLite, and `COLLATE NOCASE`
     * keeps the ordering case-insensitive too, so "Ab Roller" and "arnold press" interleave
     * the way a reader expects. Recency ordering is layered on top in US-02's second half.
     */
    @Query(
        "SELECT * FROM exercises WHERE :query = '' OR name LIKE '%' || :query || '%' " +
            "ORDER BY name COLLATE NOCASE ASC",
    )
    fun search(query: String): Flow<List<ExerciseEntity>>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(exercises: List<ExerciseEntity>)
}
