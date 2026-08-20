package com.gymtracker.core.domain.health

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** US-46: the default binding must be silent, the same contract [NoOpHealthMetricsSource] pins. */
class NoOpLiveHeartRateSourceTest {
    @Test
    fun `always emits Unavailable, once`() =
        runTest {
            NoOpLiveHeartRateSource().observe().test {
                assertEquals(LiveHeartRate.Unavailable, awaitItem())
                awaitComplete()
            }
        }
}
