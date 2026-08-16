package com.gymtracker.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.gymtracker.core.designsystem.R
import com.gymtracker.core.designsystem.component.GymNavItem
import com.gymtracker.core.designsystem.component.GymNavigationBar
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.feature.catalog.BrowseRoute
import com.gymtracker.feature.catalog.ExerciseDetailRoute
import com.gymtracker.feature.logging.HistoryRoute
import com.gymtracker.feature.logging.LoggingRoute
import com.gymtracker.feature.logging.SessionPresenceViewModel
import com.gymtracker.feature.logging.WorkoutDetailRoute
import com.gymtracker.feature.progress.ExerciseProgressRoute
import com.gymtracker.feature.progress.WeeklyVolumeRoute
import com.gymtracker.feature.routines.RoutineEditorRoute
import com.gymtracker.feature.routines.RoutinesRoute
import com.gymtracker.feature.settings.SettingsRoute
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

/** Export, import and the preference controls (US-40, US-41, US-42, M3c). A drill-down from Train's header. */
@Serializable
internal object Settings

/**
 * One exercise's progress over time (US-16). A drill-down from its detail screen, and (US-33)
 * from Progress's top section — the same destination and the same exercise either way.
 */
@Serializable
internal data class ExerciseProgress(
    val exerciseId: String,
)

/**
 * Weekly volume by muscle (US-17). A drill-down from Progress (née History).
 *
 * ADR-0024 originally said the History tab "becomes Progress and gains its charts when M4
 * lands," gated on PR detection (US-18) and a time range selector. US-18 shipped 2026-08-09;
 * the selector has not (see `roadmap.md`'s M4 section) — the maintainer chose to rename ahead
 * of it anyway (US-33), rather than wait for the whole milestone. That is recorded there as a
 * deliberate call, not a contradiction of this comment's original condition.
 *
 * A destination of its own rather than a panel inside the Progress screen, for the reason
 * `ExerciseProgress` is one: `:feature:logging` would otherwise have to depend on
 * `:feature:progress`.
 */
@Serializable
internal object WeeklyVolume

/** Past workouts, with a reason to open them beyond "what did I do" (US-06, US-33, ADR-0024). */
@Serializable
internal object History

/** One past workout in full (US-06b, ADR-0024). */
@Serializable
internal data class WorkoutDetail(
    val sessionId: String,
)

/**
 * The three top-level destinations the bottom bar shows (ADR-0030, superseding ADR-0024's
 * four), in the order they appear.
 *
 * Routines is not here. ADR-0024 anticipated it arriving as a fourth tab; `Redesign.dc.html`'s
 * section 2a asks for the opposite — "a setup surface you touch monthly, not a daily
 * destination" — and it is now reached the same way `RoutineEditor` always has been: a
 * drill-down, pushed from Train's one outlined `Routines` button, exited through the system
 * back gesture.
 */
private enum class TopLevelDestination(
    val route: Any,
    val label: String,
    @param:DrawableRes val icon: Int,
) {
    TRAIN(Logging, "Train", R.drawable.ic_nav_train),
    EXERCISES(Browse(pickForSession = false), "Exercises", R.drawable.ic_nav_exercises),
    HISTORY(History, "Progress", R.drawable.ic_nav_progress),
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
                // Routines is a drill-down now (ADR-0030), reached only from here — a push,
                // like RoutineEditor and Browse's picking mode above.
                onOpenRoutines = { navController.navigate(Routines) },
                // Settings (M3c) is the same shape: a push from Train's header, not a tab.
                onOpenSettings = { navController.navigate(Settings) },
                pickedExerciseIds = picked,
                onPicksHandled = { entry.savedStateHandle[PICKED_EXERCISES] = ArrayList<String>() },
            )
        }

        catalogDestinations(navController)

        composable<Routines> {
            RoutinesRoute(
                onEditRoutine = { id -> navController.navigate(RoutineEditor(id.value)) },
                onBack = navController::popBackStack,
                // Starting a routine puts you in a workout, which lives on Train. A tab switch
                // rather than a push, for the reason the Logging route's shortcuts document.
                onWorkoutStarted = { navController.navigateToTab(TopLevelDestination.TRAIN) },
            )
        }

        composable<Settings> {
            SettingsRoute(onBack = navController::popBackStack)
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
            HistoryRoute(
                onOpenWorkout = { id -> navController.navigate(WorkoutDetail(id.value)) },
                onSeeWeeklyVolume = { navController.navigate(WeeklyVolume) },
                onSeeExerciseProgress = { id -> navController.navigate(ExerciseProgress(id.value)) },
            )
        }

        composable<WeeklyVolume> {
            WeeklyVolumeRoute(onBack = navController::popBackStack)
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
 * Browsing the catalog, one exercise's detail, and its progress (US-12, US-13, US-16).
 *
 * Split out of [GymTrackerNavGraph] for the reason that function was split out of
 * [GymTrackerNavHost]: the graph is a list, and a list that outgrows a screen stops reading
 * as one.
 */
private fun NavGraphBuilder.catalogDestinations(navController: NavHostController) {
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
            onSeeProgress = { id -> navController.navigate(ExerciseProgress(id.value)) },
        )
    }

    composable<ExerciseProgress> { entry ->
        ExerciseProgressRoute(
            exerciseId = ExerciseId(entry.toRoute<ExerciseProgress>().exerciseId),
            onBack = navController::popBackStack,
            onOpenWorkout = { id -> navController.navigate(WorkoutDetail(id.value)) },
        )
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
 * Built from primitives rather than `NavigationBar` (ADR-0030) — see [GymNavigationBar]'s own
 * doc comment for why restyling is not an option. The icons are hand-authored vector drawables
 * in `:core:designsystem/res/drawable`, not an icon library: no new dependency, the same
 * reasoning `StepperField`'s +/− glyphs and `DrillDownTopBar`'s "Back" label already use.
 */
@Composable
private fun GymBottomBar(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val entries = TopLevelDestination.entries
    GymNavigationBar(
        items = entries.map { tab -> GymNavItem(icon = tab.icon, label = tab.label) },
        selectedIndex = entries.indexOf(selected),
        onSelect = { index -> onSelect(entries[index]) },
    )
}

/**
 * Whether the bar shows over [this] destination, and the running session (ADR-0030, superseding
 * ADR-0024's list).
 *
 * Two places show it: Train (only when no workout is running) and Exercises in its non-picking
 * mode — Progress reads `History` and its own check stays below. Everything else — exercise
 * detail, workout detail, Routines, and browse *picking* an exercise into a running session —
 * is a drill-down, and drill-downs exit through the system back gesture like every other Android
 * screen, not through the bar. Routines moved into that group with this ADR; it used to be a
 * tab.
 */
private fun NavBackStackEntry?.showsBottomBar(hasActiveSession: Boolean): Boolean {
    val entry = this ?: return false
    val destination = entry.destination
    return when {
        destination.hasRoute<Logging>() -> !hasActiveSession
        destination.hasRoute<Browse>() -> !entry.toRoute<Browse>().pickForSession
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
