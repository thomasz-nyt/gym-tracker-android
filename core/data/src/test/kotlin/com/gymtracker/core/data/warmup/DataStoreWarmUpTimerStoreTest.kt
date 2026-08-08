package com.gymtracker.core.data.warmup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.gymtracker.core.data.rest.DataStoreRestTimerStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** ADR-0021: the warm-up survives the process because it is stored as a start time. */
class DataStoreWarmUpTimerStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val startedAt: Instant = Instant.parse("2026-08-08T18:00:00Z")

    private fun store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { folder.newFile("warmup.preferences_pb") }

    @Test
    fun `no warm-up is running on a fresh install`() =
        runTest {
            assertNull(DataStoreWarmUpTimerStore(store()).warmUpStartedAt.first())
        }

    @Test
    fun `a running warm-up is readable by a new instance over the same store`() =
        runTest {
            // Standing in for the app being killed mid-warm-up and reopened.
            val shared = store()
            DataStoreWarmUpTimerStore(shared).setWarmUpStartedAt(startedAt)

            assertEquals(startedAt, DataStoreWarmUpTimerStore(shared).warmUpStartedAt.first())
        }

    @Test
    fun `instants survive to the millisecond`() =
        runTest {
            val odd = Instant.parse("2026-08-08T18:00:00.123Z")
            val shared = store()

            DataStoreWarmUpTimerStore(shared).setWarmUpStartedAt(odd)

            assertEquals(odd, DataStoreWarmUpTimerStore(shared).warmUpStartedAt.first())
        }

    @Test
    fun `stopping the warm-up removes it`() =
        runTest {
            val subject = DataStoreWarmUpTimerStore(store())
            subject.setWarmUpStartedAt(startedAt)

            subject.setWarmUpStartedAt(null)

            assertNull(subject.warmUpStartedAt.first())
        }

    @Test
    fun `the warm-up key is its own, so it cannot disturb the rest timer`() =
        runTest {
            // ADR-0021 puts this beside the rest timer in DataStore, not on top of it:
            // a warm-up must never end a rest, and a rest must never end a warm-up.
            val shared = store()
            val rest = DataStoreRestTimerStore(shared)
            rest.setRestEndsAt(startedAt.plusSeconds(60))

            DataStoreWarmUpTimerStore(shared).setWarmUpStartedAt(startedAt)

            assertEquals(startedAt.plusSeconds(60), rest.restEndsAt.first(), "the rest is untouched")
            assertEquals(startedAt, DataStoreWarmUpTimerStore(shared).warmUpStartedAt.first())
        }
}
