package app.todo.local

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Feedback is emitted only after the task change is saved successfully. */
class CompletionFeedback(private val context: Context) {
    private var tone: ToneGenerator? = null

    fun play(view: View, completed: Boolean, haptics: Boolean, sound: Boolean) {
        if (haptics) runCatching {
            view.performHapticFeedback(if (completed && Build.VERSION.SDK_INT >= 30)
                HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        }
        if (!completed || !sound) return
        runCatching {
            val audio = context.getSystemService(AudioManager::class.java)
            val notifications = context.getSystemService(NotificationManager::class.java)
            if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
                audio.getStreamVolume(AudioManager.STREAM_SYSTEM) == 0 ||
                notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
            val player = tone ?: ToneGenerator(AudioManager.STREAM_SYSTEM, 25).also { tone = it }
            player.startTone(ToneGenerator.TONE_PROP_ACK, 100)
        }
    }

    fun release() { runCatching { tone?.release() }; tone = null }
}
