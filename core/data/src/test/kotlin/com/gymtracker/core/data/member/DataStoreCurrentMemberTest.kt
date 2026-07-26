package com.gymtracker.core.data.member

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * `data-model.md` § "Identity before M2": one local member UUID per install, generated on
 * first launch and stable thereafter, because it is stamped on every session and set.
 */
class DataStoreCurrentMemberTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun dataStore(name: String = "member.preferences_pb"): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { folder.newFile(name) }

    @Test
    fun `the same id is returned on every call`() =
        runTest {
            val member = DataStoreCurrentMember(dataStore())

            val first = member.id()
            val second = member.id()

            assertEquals(first, second)
        }

    @Test
    fun `the id survives a new instance over the same store`() =
        runTest {
            val store = dataStore()

            val first = DataStoreCurrentMember(store).id()
            val second = DataStoreCurrentMember(store).id()

            assertEquals(first, second, "a restart must not orphan every session already logged")
        }

    @Test
    fun `the generated id is a uuid`() =
        runTest {
            val id = DataStoreCurrentMember(dataStore()).id()

            assertEquals(id.value, UUID.fromString(id.value).toString())
        }

    @Test
    fun `separate installs get separate ids`() =
        runTest {
            val first = DataStoreCurrentMember(dataStore("a.preferences_pb")).id()
            val second = DataStoreCurrentMember(dataStore("b.preferences_pb")).id()

            assertNotEquals(first, second)
        }
}
