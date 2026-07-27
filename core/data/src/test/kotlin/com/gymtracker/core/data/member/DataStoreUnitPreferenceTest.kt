package com.gymtracker.core.data.member

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

/** ADR-0008: pounds by default, and the choice survives a restart. */
class DataStoreUnitPreferenceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { folder.newFile("units.preferences_pb") }

    @Test
    fun `a new install is in pounds`() =
        runTest {
            assertEquals(WeightUnit.LB, DataStoreUnitPreference(store()).current())
        }

    @Test
    fun `the chosen unit survives a new instance over the same store`() =
        runTest {
            val shared = store()
            DataStoreUnitPreference(shared).set(WeightUnit.KG)

            assertEquals(WeightUnit.KG, DataStoreUnitPreference(shared).current())
        }

    @Test
    fun `observers see the change`() =
        runTest {
            val preference = DataStoreUnitPreference(store())

            preference.observe().test {
                assertEquals(WeightUnit.LB, awaitItem())

                preference.set(WeightUnit.KG)

                assertEquals(WeightUnit.KG, awaitItem())
            }
        }
}
