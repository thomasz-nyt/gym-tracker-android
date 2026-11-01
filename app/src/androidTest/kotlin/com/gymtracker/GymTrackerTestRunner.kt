package com.gymtracker

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Swaps in Hilt's test Application so instrumented tests can replace bindings.
 *
 * Without this the real [com.gymtracker.app.GymTrackerApplication] runs, and its catalog
 * seeding would race every test.
 */
class GymTrackerTestRunner : AndroidJUnitRunner() {
    override fun callActivityOnCreate(
        activity: Activity,
        icicle: Bundle?,
    ) {
        super.callActivityOnCreate(activity, icicle)
        // Opt-in device QA renders only this isolated test app; it never unlocks keyguard.
        if (InstrumentationRegistry.getArguments().getString("reviewShowWhenLocked") == "true") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION") // API 26 has no setShowWhenLocked API.
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun newApplication(
        loader: ClassLoader?,
        name: String?,
        context: Context?,
    ): Application = super.newApplication(loader, HiltTestApplication::class.java.name, context)
}
