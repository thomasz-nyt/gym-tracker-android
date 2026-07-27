package com.gymtracker.core.data.exercise

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Queries over the bundled exercise catalog. */
@Dao
interface ExerciseDao {
    /**
     * Catalog search, ranked by how recently the member last used each exercise (US-02).
     *
     * Recency wins over alphabetical for every result set, including a filtered one: typing
     * "bench" floats the bench variants you actually do above the ones you never touch. "Last
     * used" is the start of the most recent session the exercise appeared in.
     *
     * `LIKE` is case-insensitive for ASCII in SQLite, and `COLLATE NOCASE` keeps the
     * alphabetical tail case-insensitive too.
     */
    @Query(
        """
        SELECT e.* FROM exercises e
        LEFT JOIN (
            SELECT se.exercise_id AS exercise_id, MAX(s.started_at) AS last_used
            FROM session_exercises se
            JOIN sessions s ON s.id = se.session_id
            WHERE s.user_id = :userId
            GROUP BY se.exercise_id
        ) used ON used.exercise_id = e.id
        WHERE :query = '' OR e.name LIKE '%' || :query || '%'
        ORDER BY used.last_used IS NULL ASC, used.last_used DESC, e.name COLLATE NOCASE ASC
        """,
    )
    fun search(
        query: String,
        userId: String,
    ): Flow<List<ExerciseEntity>>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(exercises: List<ExerciseEntity>)
}
