package com.gymtracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTrackerTheme

/**
 * Health Connect's required "how this app uses your data" screen (US-22).
 *
 * Not reachable from anywhere inside this app — Health Connect's own permissions UI launches it
 * (its "see how this data is used" link), which is why it carries no navigation of its own and
 * no Hilt entry point, unlike [MainActivity]. Its existence is itself the fix for a real crash
 * found on device (API 36): without a manifest-declared handler for this intent, the platform
 * refuses every read with `IllegalStateException: Incorrect health permission state...` — see
 * `HealthConnectMetricsSource`'s own doc for where that surfaced and how the read itself was
 * hardened against it regardless.
 */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GymTrackerTheme {
                Scaffold { padding ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(GymDimens.ScreenPadding),
                        verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
                    ) {
                        Text("How Rep uses your health data", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text =
                                "Rep reads your heart rate and active calories for the time " +
                                    "window of a workout you finish, only while you've turned " +
                                    "this on in Settings. The numbers are aggregated on this " +
                                    "device and stored with that workout — raw samples never " +
                                    "leave your phone and are never uploaded anywhere. Rep " +
                                    "never writes to Health Connect.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}
