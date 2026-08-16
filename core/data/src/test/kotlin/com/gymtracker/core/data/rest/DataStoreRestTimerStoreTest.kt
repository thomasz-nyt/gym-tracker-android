package com.gymtracker.core.data.rest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ADR-0010: the timer survives the process because it is stored as an end time. */
class DataStoreRestTimerStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val endsAt: Instant = Instant.parse("2026-07-28T18:01:30Z")

    private fun store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { folder.newFile("rest.preferences_pb") }

    @Test
    fun `a fresh install rests for one minute`() =
        runTest {
            assertEquals(Duration.ofSeconds(60), DataStoreRestTimerStore(store()).defaultRest.first())
        }

    @Test
    fun `no rest is running on a fresh install`() =
        runTest {
            assertNull(DataStoreRestTimerStore(store()).restEndsAt.first())
        }

    @Test
    fun `a running rest is readable by a new instance over the same store`() =
        runTest {
            // Standing in for the app being killed mid-rest and reopened.
            val shared = store()
            DataStoreRestTimerStore(shared).setRestEndsAt(endsAt)

            assertEquals(endsAt, DataStoreRestTimerStore(shared).restEndsAt.first())
        }

    @Test
    fun `instants survive to the millisecond`() =
        runTest {
            val odd = Instant.parse("2026-07-28T18:01:30.123Z")
            val shared = store()

            DataStoreRestTimerStore(shared).setRestEndsAt(odd)

            assertEquals(odd, DataStoreRestTimerStore(shared).restEndsAt.first())
        }

    @Test
    fun `clearing the rest removes it`() =
        runTest {
            val subject = DataStoreRestTimerStore(store())
            subject.setRestEndsAt(endsAt)

            subject.setRestEndsAt(null)

            assertNull(subject.restEndsAt.first())
        }

    @Test
    fun `a changed default survives a restart`() =
        runTest {
            val shared = store()
            DataStoreRestTimerStore(shared).setDefaultRest(Duration.ofSeconds(120))

            assertEquals(Duration.ofSeconds(120), DataStoreRestTimerStore(shared).defaultRest.first())
        }

    @Test
    fun `the permission is asked once, and that outlives the process`() =
        runTest {
            // US-05: "requested once and never re-prompted" has to mean across restarts, or
            // it is not a promise at all.
            val shared = store()
            assertTrue(DataStoreRestTimerStore(shared).shouldAskForNotificationPermission.first())

            DataStoreRestTimerStore(shared).markNotificationPermissionAsked()

            assertEquals(false, DataStoreRestTimerStore(shared).shouldAskForNotificationPermission.first())
        }

    @Test
    fun `no total is stored on a fresh install`() =
        runTest {
            assertNull(DataStoreRestTimerStore(store()).restTotal.first())
        }

    @Test
    fun `setRest writes the end time and the total atomically, readable by a new instance`() =
        runTest {
            val shared = store()
            DataStoreRestTimerStore(shared).setRest(endsAt, Duration.ofSeconds(90))

            val reopened = DataStoreRestTimerStore(shared)
            assertEquals(endsAt, reopened.restEndsAt.first())
            assertEquals(Duration.ofSeconds(90), reopened.restTotal.first())
        }

    @Test
    fun `clearing the rest through setRestEndsAt(null) also clears the total`() =
        runTest {
            val subject = DataStoreRestTimerStore(store())
            subject.setRest(endsAt, Duration.ofSeconds(90))

            subject.setRestEndsAt(null)

            assertNull(subject.restTotal.first())
        }

    @Test
    fun `changing the default afterwards does not change a total already stored`() =
        runTest {
            val shared = store()
            val subject = DataStoreRestTimerStore(shared)
            subject.setRest(endsAt, Duration.ofSeconds(60))

            subject.setDefaultRest(Duration.ofSeconds(120))

            assertEquals(Duration.ofSeconds(60), DataStoreRestTimerStore(shared).restTotal.first())
        }
}
