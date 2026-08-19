package com.gymtracker.core.data.health

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.gymtracker.core.domain.health.HeartRateBandSelection
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** US-46: off, no device, is a new install's state; both fields survive a restart independently. */
class DataStoreHeartRateBandPreferenceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { folder.newFile("heart_rate_band.preferences_pb") }

    @Test
    fun `a new install is off with no device`() =
        runTest {
            val selection = DataStoreHeartRateBandPreference(store()).current()

            assertEquals(HeartRateBandSelection(enabled = false, deviceAddress = null), selection)
        }

    @Test
    fun `enabling survives a new instance over the same store`() =
        runTest {
            val shared = store()
            DataStoreHeartRateBandPreference(shared).setEnabled(true)

            assertEquals(true, DataStoreHeartRateBandPreference(shared).current().enabled)
        }

    @Test
    fun `a chosen device survives a new instance over the same store`() =
        runTest {
            val shared = store()
            DataStoreHeartRateBandPreference(shared).setDevice("AA:BB:CC:DD:EE:FF")

            assertEquals(
                "AA:BB:CC:DD:EE:FF",
                DataStoreHeartRateBandPreference(shared).current().deviceAddress,
            )
        }

    @Test
    fun `the device survives the toggle being turned off (US-49 - pairing is not deleted)`() =
        runTest {
            val preference = DataStoreHeartRateBandPreference(store())
            preference.setDevice("AA:BB:CC:DD:EE:FF")
            preference.setEnabled(true)

            preference.setEnabled(false)

            assertEquals("AA:BB:CC:DD:EE:FF", preference.current().deviceAddress)
        }

    @Test
    fun `setting the device to null forgets it`() =
        runTest {
            val preference = DataStoreHeartRateBandPreference(store())
            preference.setDevice("AA:BB:CC:DD:EE:FF")

            preference.setDevice(null)

            assertNull(preference.current().deviceAddress)
        }

    @Test
    fun `observers see both fields change`() =
        runTest {
            val preference = DataStoreHeartRateBandPreference(store())

            preference.observe().test {
                assertEquals(HeartRateBandSelection(false, null), awaitItem())

                preference.setEnabled(true)
                assertEquals(HeartRateBandSelection(true, null), awaitItem())

                preference.setDevice("AA:BB:CC:DD:EE:FF")
                assertEquals(HeartRateBandSelection(true, "AA:BB:CC:DD:EE:FF"), awaitItem())
            }
        }
}
