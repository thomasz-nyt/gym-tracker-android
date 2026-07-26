package com.gymtracker.core.domain

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M0 only: proves the `:core:domain` test toolchain named in `specs/tech-stack.md`
 * — JUnit 5, kotlin.test, kotlinx-coroutines-test, Turbine and MockK — is actually wired up.
 *
 * There is no production code in this module yet, and there must not be until M1.
 * Delete this test once real domain tests exercise the same tools.
 */
class DomainTestToolchainTest {
    @Test
    fun `junit 5 and kotlin test run`() {
        assertTrue(true, "JUnit 5 is the test platform for :core:domain")
    }

    @Test
    fun `turbine can assert on a flow inside runTest`() =
        runTest {
            val flow: Flow<Int> = flowOf(1, 2, 3)

            flow.test {
                assertEquals(1, awaitItem())
                assertEquals(2, awaitItem())
                assertEquals(3, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `mockk can stub and verify a third-party seam`() {
        val clock = mockk<Clock>()
        every { clock.epochMillis() } returns 1_234L

        assertEquals(1_234L, clock.epochMillis())
        verify(exactly = 1) { clock.epochMillis() }
    }
}

/**
 * Stands in for the kind of awkward third-party seam `specs/testing-strategy.md`
 * reserves MockK for. Declared in the test source set on purpose — `:core:domain`
 * has no production code at M0.
 */
internal interface Clock {
    fun epochMillis(): Long
}
