package com.gymtracker.app

import android.app.Application
import android.util.Log
import com.gymtracker.core.data.exercise.CatalogSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Hilt's application entry point. */
@HiltAndroidApp
class GymTrackerApplication : Application() {
    @Inject
    lateinit var catalogSeeder: CatalogSeeder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        seedCatalog()
    }

    /**
     * Seeds the bundled exercise catalog on first launch (US-02). Idempotent, so this runs on
     * every start and does nothing once the catalog is present.
     *
     * Failure is logged rather than fatal: a member with an active session should still be able
     * to open the app, even if the catalog is somehow unreadable.
     */
    private fun seedCatalog() {
        scope.launch {
            runCatching { catalogSeeder.seedIfEmpty(System.currentTimeMillis()) }
                .onSuccess { inserted -> if (inserted > 0) Log.i(TAG, "Seeded $inserted exercises") }
                .onFailure { error -> Log.e(TAG, "Could not seed the exercise catalog", error) }
        }
    }

    private companion object {
        const val TAG = "GymTracker"
    }
}
