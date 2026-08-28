package com.gymtracker

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gymtracker.core.data.database.GymTrackerDatabase
import com.gymtracker.core.data.session.SYNC_STATE_PENDING
import com.gymtracker.core.data.session.SessionEntity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.test.assertTrue

/**
 * Two deliberately symmetric methods. Each dirties Room and DataStore after asserting both are
 * empty, so whichever runs second catches a shared persistence binding.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PersistenceIsolationTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject
    lateinit var database: GymTrackerDatabase

    @Inject
    lateinit var preferences: DataStore<Preferences>

    @Before
    fun inject() = hilt.inject()

    @Test
    fun firstTestGetsFreshPersistence() = assertFreshThenDirty()

    @Test
    fun secondTestGetsFreshPersistence() = assertFreshThenDirty()

    private fun assertFreshThenDirty() {
        runBlocking {
            assertTrue(database.sessionDao().allForUser(PROBE_USER).isEmpty(), "Room leaked from another test")
            assertTrue(
                preferences.data
                    .first()
                    .asMap()
                    .isEmpty(),
                "DataStore leaked from another test",
            )

            database.sessionDao().insert(
                SessionEntity(
                    id = PROBE_SESSION,
                    userId = PROBE_USER,
                    gymName = null,
                    startedAt = 1L,
                    endedAt = null,
                    avgHr = null,
                    maxHr = null,
                    activeKcal = null,
                    metricsSource = null,
                    updatedAt = 1L,
                    syncState = SYNC_STATE_PENDING,
                ),
            )
            preferences.edit { it[PROBE_KEY] = "dirty" }
        }
    }

    private companion object {
        const val PROBE_USER = "persistence-isolation-user"
        const val PROBE_SESSION = "persistence-isolation-session"
        val PROBE_KEY = stringPreferencesKey("persistence-isolation-probe")
    }
}
