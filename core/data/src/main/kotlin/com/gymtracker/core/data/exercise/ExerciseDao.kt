package com.gymtracker.core.data.exercise

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Queries over the bundled exercise catalog. */
@Dao
interface ExerciseDao {
    /**
     * The catalog, ranked by how recently the member last used each exercise (US-02).
     *
     * Recency wins over alphabetical for every result set, including a narrowed one: typing
     * "bench" floats the bench variants you actually do above the ones you never touch. "Last
     * used" is the start of the most recent session the exercise appeared in.
     *
     * Among exercises the member has never done, starters come next (ADR-0007), so a new
     * member sees common gym movements rather than "3/4 Sit-Up". As soon as there is history
     * it takes over the top and the starter set drops away on its own.
     *
     * `COLLATE NOCASE` keeps the alphabetical tail case-insensitive.
     *
     * **Ranking only.** The `WHERE e.name LIKE …` this query used to carry moved into
     * `CatalogQuery` in `:core:domain` at M3, so that matching names, matching aliases and
     * filtering by body part and equipment are one testable rule instead of a SQL predicate
     * and two Kotlin ones. All 873 rows come back; the caller narrows them.
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
        ORDER BY
            used.last_used IS NULL ASC,
            used.last_used DESC,
            e.is_starter DESC,
            e.name COLLATE NOCASE ASC
        """,
    )
    fun observeRanked(userId: String): Flow<List<ExerciseEntity>>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    /** Every id the catalog has (US-41, ADR-0034) — what a backup's exercise refs are checked against. */
    @Query("SELECT id FROM exercises")
    suspend fun allIds(): List<String>

    @Insert
    suspend fun insertAll(exercises: List<ExerciseEntity>)
}
