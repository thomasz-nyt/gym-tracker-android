package com.gymtracker.core.domain.model

/** Identifies a member. Before M2 this is a locally generated UUID (see `data-model.md`). */
@JvmInline
value class UserId(
    val value: String,
)

/** Identifies a catalog exercise. Deterministic UUIDv5 for bundled exercises. */
@JvmInline
value class ExerciseId(
    val value: String,
)

/** Identifies a workout session. */
@JvmInline
value class SessionId(
    val value: String,
)

/** Identifies one appearance of an exercise within a session (ADR-0004). */
@JvmInline
value class SessionExerciseId(
    val value: String,
)
