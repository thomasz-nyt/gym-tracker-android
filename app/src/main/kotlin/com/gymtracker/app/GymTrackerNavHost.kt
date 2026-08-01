package com.gymtracker.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.feature.catalog.BrowseRoute
import com.gymtracker.feature.catalog.ExerciseDetailRoute
import com.gymtracker.feature.logging.LoggingRoute
import kotlinx.serialization.Serializable

/**
 * The app's destinations (ADR-0013), as type-safe routes per `tech-stack.md`.
 *
 * Dialogs are deliberately not here. Set entry (US-03) and the stale-session prompt (US-01)
 * are questions about the screen you are already on, and making them destinations would put a
 * navigation boundary in the middle of the two-tap path.
 */
@Serializable
internal object Logging

@Serializable
internal object Browse

@Serializable
internal data class ExerciseDetail(
    val exerciseId: String,
)

/**
 * The navigation graph.
 *
 * **The start destination is not restored from a saved back stack.** [LoggingRoute] still
 * derives what it shows — home, or the session you are in — from Room, which is what makes
 * "reopen and you are back in your session" survive the process being killed (US-01). The
 * back stack only decides where *back* goes within a launch. ADR-0013 makes that the
 * condition of adopting navigation at all.
 */
@Composable
fun GymTrackerNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Logging,
        modifier = modifier,
    ) {
        composable<Logging> {
            LoggingRoute(onBrowseCatalog = { navController.navigate(Browse) })
        }

        composable<Browse> {
            // Reached from home, so a tap opens the exercise rather than adding it to a
            // session. The in-session "add an exercise" path is US-02's and is unchanged.
            BrowseRoute(
                onChosen = { id -> navController.navigate(ExerciseDetail(id.value)) },
                onBack = navController::popBackStack,
            )
        }

        composable<ExerciseDetail> { entry ->
            ExerciseDetailRoute(
                exerciseId = ExerciseId(entry.toRoute<ExerciseDetail>().exerciseId),
                onBack = navController::popBackStack,
            )
        }
    }
}
