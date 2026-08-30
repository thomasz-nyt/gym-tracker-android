package com.gymtracker

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.gymtracker.core.data.exercise.CatalogSeeder
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.RestTimerStore
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetRepository
import com.gymtracker.feature.logging.rest.RestNotifier
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * US-54: the complaint that started the story, held down.
 *
 * "The notification cannot be tapped to open the app" is not a thing a unit test can see — the
 * `contentIntent` either reaches the posted `Notification` or it does not, and only the platform
 * knows. `getActiveNotifications` returns this app's own posts, so the assertion is direct
 * rather than a proxy for one.
 *
 * The content itself is asserted in `DescribeRestNotificationTest`; what is checked here is the
 * part that only exists once a real `Notification` has been built.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RestNotificationTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @Inject
    lateinit var sessions: SessionRepository

    @Inject
    lateinit var sessionExercises: SessionExerciseRepository

    @Inject
    lateinit var sets: SetRepository

    @Inject
    lateinit var catalog: ExerciseCatalog

    @Inject
    lateinit var catalogSeeder: CatalogSeeder

    @Inject
    lateinit var currentMember: CurrentMember

    @Inject
    lateinit var restTimerStore: RestTimerStore

    @Inject
    lateinit var notifier: RestNotifier

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    /** The same fixture shape as `OneTapSetLoggingTest`: a logged set is what makes a "next" exist. */
    @Before
    fun aSessionWithOneSetLogged() {
        runBlocking {
            hilt.inject()
            catalogSeeder.seedIfEmpty(Instant.now().toEpochMilli())

            val member = currentMember.id()
            val benchPress = catalog.search(EXERCISE, member).first().first { it.name == EXERCISE }
            val now = Instant.now()

            sessions.observeActiveSession(member).first()?.let { sessions.deleteSession(it.id) }
            sessions.deleteSession(TODAY_SESSION)
            restTimerStore.setRestEndsAt(null)
            manager.cancelAll()

            sessions.startSession(WorkoutSession(TODAY_SESSION, member, null, now, null, null))
            sessionExercises.add(SessionExercise(TODAY, TODAY_SESSION, benchPress.id, 1))
            sets.add(
                ExerciseSet(
                    id = "set-rest-notification",
                    sessionExerciseId = TODAY,
                    setIndex = 1,
                    // 61.23 kg is exactly 135 lb, the unit this household reads (ADR-0008).
                    weightKg = 61.23,
                    reps = 8,
                    rpe = null,
                    performedAt = now,
                ),
            )
        }
    }

    @After
    fun discardTheSession() {
        runBlocking {
            manager.cancelAll()
            restTimerStore.setRestEndsAt(null)
            sessions.deleteSession(TODAY_SESSION)
        }
    }

    @Test
    fun theRestingNotificationCanBeTapped() {
        runBlocking {
            notifier.showResting(Instant.now().plus(Duration.ofSeconds(60)))

            // The bug this story opened on: there was no content intent at all, so a tap did
            // nothing at all.
            assertNotNull(posted(RESTING_ID).contentIntent, "tapping it must open the app")
        }
    }

    @Test
    fun theRestOverNotificationCanBeTapped() {
        runBlocking {
            notifier.showRestOver()

            assertNotNull(posted(REST_OVER_ID).contentIntent, "tapping it must open the app")
        }
    }

    @Test
    fun theRestingNotificationCountsDownWithoutUsTickingIt() {
        runBlocking {
            val endsAt = Instant.now().plus(Duration.ofSeconds(60))

            notifier.showResting(endsAt)

            val posted = posted(RESTING_ID)
            // ADR-0046: the countdown is rendered by the platform from `when`, which is what
            // makes an ongoing notification cheap enough to have at all. If these three ever
            // stop being set, something in our process has started ticking instead.
            assertTrue(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER), "chronometer")
            assertTrue(posted.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN), "counting down")
            assertEquals(endsAt.toEpochMilli(), posted.`when`, "counting down to when the rest ends")
        }
    }

    @Test
    fun theRestingNotificationCarriesBothActions() {
        runBlocking {
            notifier.showResting(Instant.now().plus(Duration.ofSeconds(60)))

            val titles = posted(RESTING_ID).actions.orEmpty().map { it.title.toString() }
            // LOG SET is present *during* the rest on purpose — US-05's "it never blocks
            // logging the next set" has to hold in the shade too.
            assertEquals(listOf("LOG SET", "SKIP REST"), titles)
        }
    }

    @Test
    fun theRestOverNotificationOffersTheSetItIsAbout() {
        runBlocking {
            notifier.showRestOver()

            val posted = posted(REST_OVER_ID)
            assertEquals(listOf("LOG SET"), posted.actions.orEmpty().map { it.title.toString() })
            assertTrue(
                posted.extras
                    .getCharSequence(Notification.EXTRA_TEXT)
                    .toString()
                    .contains("135 lb × 8"),
                "it should name the set it is about, in the member's unit",
            )
        }
    }

    @Test
    fun theRestingNotificationIsOngoingAndSilent() {
        runBlocking {
            notifier.showResting(Instant.now().plus(Duration.ofSeconds(60)))

            val posted = posted(RESTING_ID)
            assertTrue(posted.flags and Notification.FLAG_ONGOING_EVENT != 0, "not swipe-dismissable")
            // A rest starts every 60 seconds. Its own channel, at LOW, is what keeps this from
            // popping a heads-up each time (ADR-0046).
            assertEquals(RESTING_CHANNEL, posted.channelId)
            assertEquals(
                NotificationManager.IMPORTANCE_LOW,
                manager.getNotificationChannel(RESTING_CHANNEL).importance,
            )
        }
    }

    @Test
    fun dismissingTheRestTakesTheNotificationWithIt() {
        runBlocking {
            notifier.showResting(Instant.now().plus(Duration.ofSeconds(60)))

            notifier.dismissResting()

            assertTrue(
                manager.activeNotifications.none { it.id == RESTING_ID },
                "a skipped rest must not leave a countdown running on the lock screen",
            )
        }
    }

    private fun posted(id: Int): Notification =
        manager.activeNotifications
            .firstOrNull { it.id == id }
            ?.notification
            ?: error("no notification posted with id $id")

    private companion object {
        const val EXERCISE = "Barbell Bench Press - Medium Grip"
        val TODAY_SESSION = SessionId("session-rest-notification")
        val TODAY = SessionExerciseId("se-rest-notification")

        const val RESTING_ID = 2
        const val REST_OVER_ID = 1
        const val RESTING_CHANNEL = "rest-running"
    }
}
