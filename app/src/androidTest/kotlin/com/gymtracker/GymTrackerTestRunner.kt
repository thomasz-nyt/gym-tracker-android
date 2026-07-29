package com.gymtracker

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Swaps in Hilt's test Application so instrumented tests can replace bindings.
 *
 * Without this the real [com.gymtracker.app.GymTrackerApplication] runs, and its catalog
 * seeding would race every test.
 */
class GymTrackerTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        loader: ClassLoader?,
        name: String?,
        context: Context?,
    ): Application = super.newApplication(loader, HiltTestApplication::class.java.name, context)
}
