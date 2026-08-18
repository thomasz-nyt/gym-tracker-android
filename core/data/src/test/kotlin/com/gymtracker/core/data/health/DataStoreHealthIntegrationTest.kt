package com.gymtracker.core.data.health

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** US-21: off by default for every member, and the choice survives a restart. */
class DataStoreHealthIntegrationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { folder.newFile("health.preferences_pb") }

    @Test
    fun `a new install has health integration off`() =
        runTest {
            assertFalse(DataStoreHealthIntegration(store()).current())
        }

    @Test
    fun `turning it on survives a new instance over the same store`() =
        runTest {
            val shared = store()
            DataStoreHealthIntegration(shared).set(true)

            assertEquals(true, DataStoreHealthIntegration(shared).current())
        }

    @Test
    fun `observers see the change`() =
        runTest {
            val integration = DataStoreHealthIntegration(store())

            integration.observe().test {
                assertEquals(false, awaitItem())

                integration.set(true)

                assertEquals(true, awaitItem())
            }
        }
}
