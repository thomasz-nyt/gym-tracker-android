package com.gymtracker.feature.logging.rest

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject

/**
 * Whether the app is on screen right now — the one thing "Rest over" needs to know that the
 * stored end time cannot tell it (US-56 as amended 2026-09-05). With the session screen open, the
 * band already reads 0:00 and the cue has just pulsed; a heads-up over it would only cover
 * `LOG SET`. A notification is for when you are not looking.
 *
 * An interface so [RestOverReceiver]'s one decision has a seam; the real answer is the process
 * lifecycle.
 */
fun interface AppForeground {
    fun isInForeground(): Boolean
}

/** STARTED or above on the process lifecycle means an activity of ours is visible. Main thread only. */
class ProcessForeground
    @Inject
    constructor() : AppForeground {
        override fun isInForeground(): Boolean =
            ProcessLifecycleOwner
                .get()
                .lifecycle
                .currentState
                .isAtLeast(Lifecycle.State.STARTED)
    }
