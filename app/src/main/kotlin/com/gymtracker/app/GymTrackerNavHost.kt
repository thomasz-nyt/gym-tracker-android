package com.gymtracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/**
 * @property pickForSession whether a tap adds the exercise to the workout in progress
 *   (US-02's path, reached from the session) or opens its detail screen (reached from home).
 *   The same screen either way, which is what US-12 asks for.
 */
@Serializable
internal data class Browse(
    val pickForSession: Boolean = false,
)

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
        composable<Logging> { entry ->
            // Browse hands an exercise back here rather than knowing anything about sessions,
            // which is what lets one screen serve both entry points.
            val picked by entry.savedStateHandle
                .getStateFlow<String?>(PICKED_EXERCISE, null)
                .collectAsStateWithLifecycle()

            LoggingRoute(
                onBrowseCatalog = { navController.navigate(Browse(pickForSession = false)) },
                onAddExercise = { navController.navigate(Browse(pickForSession = true)) },
                pickedExerciseId = picked,
                onPickHandled = { entry.savedStateHandle[PICKED_EXERCISE] = null },
            )
        }

        composable<Browse> { entry ->
            val pickForSession = entry.toRoute<Browse>().pickForSession

            BrowseRoute(
                onChosen = { id ->
                    if (pickForSession) {
                        // Straight back to the session with the exercise added — US-02's path
                        // gains no steps by having become a destination.
                        navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_EXERCISE, id.value)
                        navController.popBackStack()
                    } else {
                        navController.navigate(ExerciseDetail(id.value))
                    }
                },
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

/** Where the browse screen leaves the exercise it was asked to pick. */
private const val PICKED_EXERCISE = "picked-exercise"
