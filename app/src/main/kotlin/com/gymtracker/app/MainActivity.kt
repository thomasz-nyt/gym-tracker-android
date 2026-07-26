package com.gymtracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import com.gymtracker.feature.logging.LoggingRoute
import dagger.hilt.android.AndroidEntryPoint

/** The single activity. A navigation graph arrives when there is more than one destination. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GymTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LoggingRoute()
                }
            }
        }
    }
}
