package app.todo.local

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.ZoneId

class ReminderScheduler(private val context: Context, private val repo: Repository) {
    private val alarm = context.getSystemService(AlarmManager::class.java)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences("reminder_registry", Context.MODE_PRIVATE)
    companion object { const val CHANNEL = "task_reminders" }
    fun channel() { manager.createNotificationChannel(NotificationChannel(CHANNEL, "待办提醒", NotificationManager.IMPORTANCE_DEFAULT).apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE }) }
    fun notificationsAllowed(): Boolean {
        channel()
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
    fun status(): String = if (!notificationsAllowed()) "通知未开启" else if (!exactAllowed()) "提醒可能延迟" else "通知已开启 · 精确提醒已授权"
    private fun intent(id: String) = Intent(context, ReminderReceiver::class.java).setAction("remind").setData(Uri.parse("todo://reminder/$id"))
    private fun pending(id: String, r: Reminder? = null, at: Long = 0): PendingIntent = PendingIntent.getBroadcast(context, 0,
        intent(id).putExtra("id", id).putExtra("generation", r?.generation).putExtra("at", at), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun cancel(id: String) { alarm.cancel(pending(id)); manager.cancel(id, 1) }
    suspend fun reconcile(catchUp: Boolean = true): Boolean = repo.gate.withLock {
        channel()
        val records = repo.dao.records()
        val current = records.filter { it.task.completedAt == null && it.reminder != null }
        val previous = prefs.getStringSet("ids", emptySet())!!.toSet()
        val ids = current.map { it.task.id }.toSet()
        (previous - ids).forEach(::cancel)
        // Registry is persisted before scheduling so a crash cannot orphan an alarm.
        prefs.edit().putStringSet("ids", ids).commit()
        val allowed = notificationsAllowed()
        val wasAllowed = prefs.getBoolean("allowed", allowed)
        val missed = mutableListOf<Pair<Task, Reminder>>()
        var success = true
        current.forEach { record ->
            val t = record.task; val r = record.reminder!!
            val at = Rules.reminderInstant(t, r, ZoneId.systemDefault()) ?: return@forEach
            try {
                alarm.cancel(pending(t.id))
                val oldGen = prefs.getString("gen:${t.id}", null)
                if (oldGen != r.generation) manager.cancel(t.id, 1)
                prefs.edit().putString("gen:${t.id}", r.generation).apply()
                if (r.delivered == r.generation) return@forEach
                if (at.isAfter(repo.clock.instant())) {
                    if (allowed) {
                        val pi = pending(t.id, r, at.toEpochMilli())
                        if (exactAllowed()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
                        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
                    }
                } else {
                    if (allowed && wasAllowed && catchUp && Duration.between(at, repo.clock.instant()).toHours() < 24) missed += t to r
                    repo.dao.put(r.copy(delivered = r.generation))
                }
            } catch (_: Exception) { success = false }
        }
        if (missed.isNotEmpty()) {
            try { manager.notify("missed", 2, base("有 ${missed.size} 项错过的待办提醒", "打开 todo 查看任务")
                .setContentIntent(openTask(null)).build()) } catch (_: Exception) { success = false }
        }
        prefs.edit().putBoolean("allowed", allowed).putBoolean("failed", !success).apply()
        success
    }
    fun failed() = prefs.getBoolean("failed", false)
    private fun openTask(id: String?): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).setData(Uri.parse("todo://task/${id ?: "today"}")).putExtra("taskId", id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun base(title: String, reason: String): NotificationCompat.Builder {
        val show = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("lock_title", false)
        val public = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_todo).setContentTitle("todo").setContentText("你有一项待办提醒").build()
        return NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_todo).setContentTitle(title).setContentText(reason)
            .setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(if (show) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
    }
    suspend fun receive(intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val scheduledAt = intent.getLongExtra("at", 0)
        if (intent.action == "remind" && scheduledAt > 0 && repo.clock.millis() - scheduledAt > 60_000) {
            reconcile(catchUp = true)
            return
        }
        repo.gate.withLock {
                val t = repo.dao.task(id) ?: return@withLock
                if (intent.action == "complete") { repo.db.withTransaction { repo.completeInside(id, true) }; cancel(id); return@withLock }
                val r = repo.dao.reminder(id) ?: return@withLock
                val at = Rules.reminderInstant(t, r, ZoneId.systemDefault()) ?: return@withLock
                if (t.completedAt != null || r.generation != intent.getStringExtra("generation") || r.delivered == r.generation ||
                    at.toEpochMilli() != intent.getLongExtra("at", 0) || at.isAfter(repo.clock.instant())) return@withLock
                // Claim durably before interacting with NotificationManager: at-most-once delivery.
                repo.dao.put(r.copy(delivered = r.generation))
                if (notificationsAllowed()) {
                    val complete = PendingIntent.getBroadcast(context, 0, Intent(context, ReminderReceiver::class.java).setAction("complete")
                        .setData(Uri.parse("todo://complete/$id")).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    manager.notify(id, 1, base(t.title, when (r.mode) { "schedule" -> "安排时间到了"; "due" -> "截止时间到了"; else -> "待办提醒" })
                        .setContentIntent(openTask(id)).addAction(R.drawable.ic_todo, "完成", complete).build())
                }
        }
    }
    fun cancelAll() { prefs.getStringSet("ids", emptySet())!!.forEach(::cancel); manager.cancelAll() }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { (context.applicationContext as TodoApplication).reminders.receive(intent) } catch (_: Exception) {
                context.getSharedPreferences("reminder_registry", Context.MODE_PRIVATE).edit().putBoolean("failed", true).apply()
            } finally { pending.finish() }
        }
    }
}
class ReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { (context.applicationContext as TodoApplication).reminders.reconcile() } catch (_: Exception) {
                context.getSharedPreferences("reminder_registry", Context.MODE_PRIVATE).edit().putBoolean("failed", true).apply()
            } finally { pending.finish() }
        }
    }
}
