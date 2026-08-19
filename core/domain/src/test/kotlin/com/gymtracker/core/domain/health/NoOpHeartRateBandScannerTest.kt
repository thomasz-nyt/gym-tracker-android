package com.gymtracker.core.domain.health

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** US-46: the default binding is unavailable and finds nothing, the same contract every no-op here pins. */
class NoOpHeartRateBandScannerTest {
    @Test
    fun `always Unavailable`() {
        assertEquals(HeartRateBandAvailability.Unavailable, NoOpHeartRateBandScanner().availability())
    }

    @Test
    fun `finds nothing`() =
        runTest {
            NoOpHeartRateBandScanner().scan().test {
                awaitComplete()
            }
        }
}
