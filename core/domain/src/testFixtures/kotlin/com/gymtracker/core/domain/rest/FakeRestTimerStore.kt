package com.gymtracker.core.domain.rest

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

/** In-memory timer for domain workflow tests. */
class FakeRestTimerStore : RestTimerStore {
    private val endsAt = MutableStateFlow<Instant?>(null)
    private val total = MutableStateFlow<Duration?>(null)
    private val default = MutableStateFlow(Duration.ofSeconds(60))
    private val asked = MutableStateFlow(false)

    override val restEndsAt = endsAt
    override val restTotal = total
    override val defaultRest = default
    override val shouldAskForNotificationPermission = asked.map { !it }

    override suspend fun setRestEndsAt(instant: Instant?) {
        endsAt.value = instant
        if (instant == null) total.value = null
    }

    override suspend fun setRest(
        endsAt: Instant,
        total: Duration,
    ) {
        this.endsAt.value = endsAt
        this.total.value = total
    }

    override suspend fun setDefaultRest(rest: Duration) {
        default.value = rest
    }

    override suspend fun markNotificationPermissionAsked() {
        asked.value = true
    }
}
