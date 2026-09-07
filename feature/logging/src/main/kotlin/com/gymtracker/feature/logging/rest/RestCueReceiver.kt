package com.gymtracker.feature.logging.rest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires the ten-second cue (ADR-0049) when the alarm [RestAlarm] scheduled for
 * [com.gymtracker.core.domain.rest.RestCueSchedule.cueAt] goes off.
 *
 * The same shape as [RestOverReceiver], for the same reason: a receiver rather than a service,
 * doing one small thing and finishing. It posts nothing — the countdown notification is already
 * in the shade and keeps counting; this is the pulse a phone in a pocket needs to know the ten
 * seconds have started.
 */
@AndroidEntryPoint
class RestCueReceiver : BroadcastReceiver() {
    @Inject
    lateinit var cue: RestCue

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        // Reading the tone preference is a DataStore read — more than onReceive may do inline,
        // well inside what goAsync allows.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                cue.play()
            } finally {
                pending.finish()
            }
        }
    }
}
