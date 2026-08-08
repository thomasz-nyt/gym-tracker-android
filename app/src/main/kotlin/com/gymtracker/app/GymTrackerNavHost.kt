package com.gymtracker.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.feature.catalog.BrowseRoute
import com.gymtracker.feature.catalog.ExerciseDetailRoute
import com.gymtracker.feature.logging.HistoryRoute
import com.gymtracker.feature.logging.LoggingRoute
import com.gymtracker.feature.logging.SessionPresenceViewModel
import com.gymtracker.feature.logging.WorkoutDetailRoute
import com.gymtracker.feature.routines.RoutineEditorRoute
import com.gymtracker.feature.routines.RoutinesRoute
import kotlinx.serialization.Serializable

/**
 * The app's destinations (ADR-0013, ADR-0024), as type-safe routes per `tech-stack.md`.
 *
 * Dialogs are deliberately not here. Set entry (US-03) and the stale-session prompt (US-01)
 * are questions about the screen you are already on, and making them destinations would put a
 * navigation boundary in the middle of the two-tap path. The guided flow (US-05a) is not here
 * for a different reason — ADR-0017 keeps it out so a killed process can still resume mid-set,
 * which a back-stack entry cannot promise.
 */
@Serializable
internal object Logging

/**
 * @property pickForSession whether a tap adds the exercise to the workout in progress
 *   (US-02's path, reached from the session) or opens its detail screen (reached from home).
 *   The same screen either way, which is what US-12 asks for. When picking, the screen stays
 *   up across taps so several exercises can be added in one visit (US-02a).
 */
@Serializable
internal data class Browse(
    val pickForSession: Boolean = false,
)

@Serializable
internal data class ExerciseDetail(
    val exerciseId: String,
)

/** The member's saved routines (US-29, ADR-0020). */
@Serializable
internal object Routines

/** One routine, being edited (US-29). A drill-down, so the bar is hidden over it. */
@Serializable
internal data class RoutineEditor(
    val routineId: String,
)

/** Past workouts (US-06, ADR-0024). */
@Serializable
internal object History

/** One past workout in full (US-06b, ADR-0024). */
@Serializable
internal data class WorkoutDetail(
    val sessionId: String,
)

/**
 * The four top-level destinations the bottom bar shows (ADR-0024), in the order they appear.
 *
 * Routines is the fourth this file said would arrive with ADR-0020, and it sits next to Train
 * because starting one is how a workout begins when you have a plan. The editor is not here:
 * it is a drill-down, reached from this tab and exited through the top bar.
 */
private enum class TopLevelDestination(
    val route: Any,
    val label: String,
) {
    TRAIN(Logging, "Train"),
    ROUTINES(Routines, "Routines"),
    EXERCISES(Browse(pickForSession = false), "Exercises"),
    HISTORY(History, "History"),
}

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
    // The smallest signal the bar needs — see SessionPresenceViewModel's doc for why this is
    // not read off ActiveSessionViewModel instead.
    val presence: SessionPresenceViewModel = hiltViewModel()
    val hasActiveSession by presence.hasActiveSession.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (backStackEntry.showsBottomBar(hasActiveSession)) {
                GymBottomBar(
                    selected = backStackEntry.topLevelDestinationOrNull(),
                    onSelect = { tab -> navController.navigateToTab(tab) },
                )
            }
        },
    ) { padding ->
        GymTrackerNavGraph(navController = navController, modifier = Modifier.padding(padding))
    }
}

/**
 * Switches tabs, flattening the stack back to one place per tab rather than growing
 * History -> WorkoutDetail -> History -> ... every time the bar is used to leave a drill-down.
 */
private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** The graph itself, split out of [GymTrackerNavHost] to keep that composable short. */
@Composable
private fun GymTrackerNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = Logging, modifier = modifier) {
        composable<Logging> { entry ->
            // Browse hands exercises back here rather than knowing anything about sessions,
            // which is what lets one screen serve both entry points.
            val picked by entry.savedStateHandle
                .getStateFlow(PICKED_EXERCISES, ArrayList<String>())
                .collectAsStateWithLifecycle()

            LoggingRoute(
                // Home's two shortcuts lead to places that are *also* tabs, so they switch
                // tabs rather than pushing. Pushing a top-level destination onto Train's stack
                // is what shipped the dead Train tab: the bar's popUpTo(start) { saveState }
                // saved the pushed entry on the way out and restoreState put it straight back,
                // so tapping Train from history restored history. See TabNavigationTest.
                onBrowseCatalog = { navController.navigateToTab(TopLevelDestination.EXERCISES) },
                onOpenHistory = { navController.navigateToTab(TopLevelDestination.HISTORY) },
                // Not a tab: this is Browse in its picking mode (US-02a), reached from a running
                // session and genuinely a drill-down, so it stays a push.
                onAddExercise = { navController.navigate(Browse(pickForSession = true)) },
                pickedExerciseIds = picked,
                onPicksHandled = { entry.savedStateHandle[PICKED_EXERCISES] = ArrayList<String>() },
            )
        }

        composable<Browse> { entry ->
            val pickForSession = entry.toRoute<Browse>().pickForSession

            BrowseRoute(
                pickForSession = pickForSession,
                onChosen = { id -> navController.onExerciseChosenInBrowse(pickForSession, id) },
                onBack = navController::popBackStack,
            )
        }

        composable<ExerciseDetail> { entry ->
            ExerciseDetailRoute(
                exerciseId = ExerciseId(entry.toRoute<ExerciseDetail>().exerciseId),
                onBack = navController::popBackStack,
            )
        }

        composable<Routines> {
            RoutinesRoute(
                onEditRoutine = { id -> navController.navigate(RoutineEditor(id.value)) },
                // Starting a routine puts you in a workout, which lives on Train. A tab switch
                // rather than a push, for the reason the Logging route's shortcuts document.
                onWorkoutStarted = { navController.navigateToTab(TopLevelDestination.TRAIN) },
            )
        }

        composable<RoutineEditor> { entry ->
            // Browse leaves its picks on the previous entry's handle, which is this one — the
            // same mechanism the session uses, with no knowledge of routines in it.
            val picked by entry.savedStateHandle
                .getStateFlow(PICKED_EXERCISES, ArrayList<String>())
                .collectAsStateWithLifecycle()

            RoutineEditorRoute(
                routineId = RoutineId(entry.toRoute<RoutineEditor>().routineId),
                onBack = navController::popBackStack,
                onAddExercise = { navController.navigate(Browse(pickForSession = true)) },
                pickedExerciseIds = picked,
                onPicksHandled = { entry.savedStateHandle[PICKED_EXERCISES] = ArrayList<String>() },
            )
        }

        composable<History> {
            HistoryRoute(onOpenWorkout = { id -> navController.navigate(WorkoutDetail(id.value)) })
        }

        composable<WorkoutDetail> { entry ->
            WorkoutDetailRoute(
                sessionId = SessionId(entry.toRoute<WorkoutDetail>().sessionId),
                onBack = navController::popBackStack,
            )
        }
    }
}

/**
 * What a tap on a browse result means (US-12): picked into the session in progress, or opened
 * for its detail.
 */
private fun NavHostController.onExerciseChosenInBrowse(
    pickForSession: Boolean,
    id: ExerciseId,
) {
    if (pickForSession) {
        // Accumulated rather than popped on the first pick: picking three exercises is one
        // visit (US-02a). The browse screen decides when it is finished, through onBack.
        val handle = previousBackStackEntry?.savedStateHandle
        val alreadyPicked = handle?.get<ArrayList<String>>(PICKED_EXERCISES).orEmpty()
        // ArrayList, not the List the rest of the code sees: a SavedStateHandle value has to
        // survive being written to a Bundle.
        handle?.set(PICKED_EXERCISES, ArrayList(alreadyPicked + id.value))
    } else {
        navigate(ExerciseDetail(id.value))
    }
}

/**
 * Text-only, per the constitution's §7 dependency rule: there is no icon dependency in this
 * app, and `StepperField` already draws its own +/− as text for the same reason. Adding one
 * for three tab labels would need an ADR this feature does not otherwise need.
 *
 * **Known deviation from ADR-0019.** `NavigationBarItem` (material3 1.4.0) reads the fixed
 * `CornerFull` token for its selected-item indicator and, unlike `PrimaryActionButton` /
 * `SecondaryActionButton` and the outlined "Delete set" button, exposes no `shape` parameter to
 * override it with `MaterialTheme.shapes.large` the way `Shape.kt` documents doing elsewhere.
 * The indicator pill will render as a stadium regardless of `GymShapes` until Material3 adds
 * that hook, or until this bar is replaced with a hand-built one. Flagged rather than silently
 * accepted — this is the exact footgun `Shape.kt` warns has bitten the project before.
 */
@Composable
private fun GymBottomBar(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Text(tab.label, style = MaterialTheme.typography.labelLarge) },
                label = null,
                alwaysShowLabel = false,
            )
        }
    }
}

/**
 * Whether the bar shows over [this] destination, and the running session (ADR-0024).
 *
 * Three places show it: Train (only when no workout is running), Exercises in its non-picking
 * mode, and History. Everything else — exercise detail, workout detail, and browse *picking*
 * an exercise into a running session — is a drill-down, and drill-downs exit through the system
 * back gesture like every other Android screen, not through the bar.
 */
private fun NavBackStackEntry?.showsBottomBar(hasActiveSession: Boolean): Boolean {
    val entry = this ?: return false
    val destination = entry.destination
    return when {
        destination.hasRoute<Logging>() -> !hasActiveSession
        destination.hasRoute<Browse>() -> !entry.toRoute<Browse>().pickForSession
        destination.hasRoute<Routines>() -> true
        destination.hasRoute<History>() -> true
        else -> false
    }
}

/** Which tab [this] destination belongs to, for highlighting it in the bar. */
private fun NavBackStackEntry?.topLevelDestinationOrNull(): TopLevelDestination? {
    val destination = this?.destination ?: return null
    return when {
        destination.hasRoute<Logging>() -> TopLevelDestination.TRAIN
        destination.hasRoute<Browse>() -> TopLevelDestination.EXERCISES
        destination.hasRoute<Routines>() -> TopLevelDestination.ROUTINES
        destination.hasRoute<History>() -> TopLevelDestination.HISTORY
        else -> null
    }
}

/**
 * Where the browse screen leaves the exercises it was asked to pick.
 *
 * A list, not one id: a single visit can add several (US-02a), and the same exercise twice
 * (US-02), so this is appended to rather than overwritten.
 */
private const val PICKED_EXERCISES = "picked-exercises"
