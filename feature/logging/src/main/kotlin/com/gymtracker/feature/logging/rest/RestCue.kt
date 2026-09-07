package com.gymtracker.feature.logging.rest

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.gymtracker.core.domain.rest.RestCueTonePreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The rest cue itself (ADR-0049): a short haptic pattern, always, and a short tone when the
 * member has turned one on and the phone is not silenced.
 *
 * Fired at ten seconds by [RestCueReceiver] and at zero by [RestOverReceiver], both from exact
 * alarms — so this runs with the screen off and the app in the background, which is the whole
 * reason it exists. It is not a notification and does not post one; it is not gated on
 * `POST_NOTIFICATIONS`, and it never touches the timer, which stays the stored end time
 * (ADR-0010).
 */
@Singleton
class RestCue
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val tone: RestCueTonePreference,
    ) {
        /** Pulses, and beeps if the member asked for it and the ringer is in normal mode. */
        suspend fun play() {
            vibrate()
            if (tone.current() && ringerIsNormal()) beep()
        }

        private fun vibrate() {
            val vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Vibrator::class.java)
                }
            if (vibrator?.hasVibrator() != true) return
            vibrator.vibrate(VibrationEffect.createWaveform(PATTERN_MILLIS, NO_REPEAT))
        }

        private fun ringerIsNormal(): Boolean =
            context.getSystemService(AudioManager::class.java)?.ringerMode == AudioManager.RINGER_MODE_NORMAL

        /**
         * A single short beep on the notification stream — the stream a rest-over notification
         * would use — held open only for as long as the tone plays before releasing the generator.
         */
        private suspend fun beep() =
            withContext(Dispatchers.Default) {
                val generator =
                    runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME) }.getOrNull()
                        ?: return@withContext
                try {
                    generator.startTone(ToneGenerator.TONE_PROP_BEEP2, TONE_MILLIS)
                    delay(TONE_MILLIS.toLong())
                } finally {
                    generator.release()
                }
            }

        private companion object {
            /** Off, on, off, on — two short pulses, distinct from a notification's single buzz. */
            val PATTERN_MILLIS = longArrayOf(0, 70, 90, 70)
            const val NO_REPEAT = -1
            const val TONE_VOLUME = 80
            const val TONE_MILLIS = 250
        }
    }
