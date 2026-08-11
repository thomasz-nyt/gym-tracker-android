package com.gymtracker.core.domain.model

import java.time.Instant

/**
 * A gym session: a thin container that groups sets (`data-model.md`).
 *
 * @property endedAt null while the session is active. It is only ever set from a real
 *   timestamp — the app never invents an end time (US-01).
 * @property metrics null unless a health source provided them. Absence is a first-class
 *   state, never zero (constitution §2, §5).
 * @property routine US-32's provenance (ADR-0028): the routine this session was started
 *   from, or null for an ordinary "Start workout." Written once, at start, by
 *   [com.gymtracker.core.domain.routine.StartSessionFromRoutine] — nothing else in the
 *   domain ever sets or changes it.
 */
data class WorkoutSession(
    val id: SessionId,
    val userId: UserId,
    val gymName: String?,
    val startedAt: Instant,
    val endedAt: Instant?,
    val metrics: SessionMetrics?,
    val routine: RoutineOrigin? = null,
) {
    /** True while this session is the member's active one. */
    val isActive: Boolean get() = endedAt == null
}

/** Wearable-derived session summary. Every field is nullable, always. */
data class SessionMetrics(
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val activeKilocalories: Int?,
    val source: String?,
)

/**
 * A session's routine, copied once at start and never read back through a repository
 * (US-32, ADR-0028).
 *
 * [id] is a bare `String`, deliberately not [RoutineId]: resolving it back to a routine —
 * `routines.find(RoutineId(id))` — takes a deliberate, greppable wrap that isn't there
 * today, which is most of what keeps this provenance rather than a live pointer. Nothing in
 * the domain reads [id]; it exists so a future story (a per-routine count, a "last run of
 * this routine" comparison) does not have to leave a permanent gap for every session logged
 * before that story is written. [name] is what renders — "Upper A · Tue 4 Aug" — and it
 * stays what the routine was called at the time, even if the routine is later renamed or
 * deleted.
 */
data class RoutineOrigin(
    val id: String,
    val name: String,
)
