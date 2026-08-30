package com.gymtracker.app

import android.app.Application
import android.util.Log
import com.gymtracker.core.data.exercise.CatalogSeeder
import com.gymtracker.feature.logging.rest.RestNotificationCoordinator
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

    @Inject
    lateinit var restNotifications: RestNotificationCoordinator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        seedCatalog()
        // Process-scoped on purpose: a rest outlives the screen that started it, and after a
        // process kill this re-reads the stored end time and converges (US-54, ADR-0046).
        restNotifications.start(scope)
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
