package com.gymtracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.feature.logging.rest.RestNotificationCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** The single activity, hosting the navigation graph (ADR-0013). */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var restNotifications: RestNotificationCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GymTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GymTrackerNavHost()
                }
            }
        }
    }

    /**
     * Whether we can post a notification is not part of the stored rest, so nothing about
     * `restEndsAt` changes when permission is granted — and US-05 asks for that permission
     * *during* the member's first rest. Re-applying on resume covers the dialog closing, and
     * equally a member turning notifications back on in system Settings and coming back.
     */
    override fun onResume() {
        super.onResume()
        restNotifications.reapply()
    }
}
