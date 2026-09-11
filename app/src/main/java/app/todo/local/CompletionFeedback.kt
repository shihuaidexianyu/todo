package app.todo.local

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.SoundPool
import android.media.AudioAttributes
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Feedback is emitted only after the task change is saved successfully. */
class CompletionFeedback(private val context: Context) {
    @Volatile private var loaded = false
    private var sample = 0
    private var stream = 0
    private val pool = runCatching {
        SoundPool.Builder().setMaxStreams(1).setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build().also {
            it.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
            sample = it.load(context, R.raw.complete, 1)
        }
    }.getOrNull()

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
            val player = pool ?: return
            if (!loaded) return // Never play a delayed confirmation after the interaction.
            if (stream != 0) player.stop(stream)
            stream = player.play(sample, .7f, .7f, 1, 0, 1f)
        }
    }

    fun release() { loaded = false; runCatching { pool?.release() } }
}
