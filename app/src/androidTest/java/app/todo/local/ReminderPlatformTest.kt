package app.todo.local

import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class ReminderPlatformTest {
    private val app = ApplicationProvider.getApplicationContext<TodoApplication>()
    private val repo get() = app.repository
    private val scheduler get() = app.reminders
    private val ids = mutableListOf<String>()
    private fun shell(command: String) { InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } } }
    @Before fun permissions() {
        shell("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS")
        shell("appops set ${app.packageName} SCHEDULE_EXACT_ALARM allow")
        scheduler.channel()
    }
    @After fun clean() = runBlocking {
        ids.forEach { repo.delete(it) }; scheduler.reconcile(false)
        // Revoking permissions kills this process. The host script resets them after instrumentation exits.
        Unit
    }
    @Test fun backgroundAlarmDeliversOnceAndNotificationActionCompletes() = runBlocking {
        val at = LocalDateTime.now().plusMinutes(1).withSecond(0).withNano(0)
        val t = Task(title = "系统提醒验收", scheduleDate = at.toLocalDate().toString(), scheduleTime = at.toLocalTime().toString())
        ids += t.id
        repo.save(t, emptyList(), Reminder(t.id, "schedule"), true)
        assertTrue(scheduler.reconcile(false)); assertTrue(scheduler.exactAllowed())
        val r = repo.dao.reminder(t.id)!!
        shell("input keyevent KEYCODE_HOME")
        val manager = app.getSystemService(NotificationManager::class.java)
        withTimeout(80_000) { while (manager.activeNotifications.none { it.tag == t.id }) delay(300) }
        assertEquals(r.generation, repo.dao.reminder(t.id)!!.delivered)
        assertEquals(1, manager.activeNotifications.count { it.tag == t.id })
        scheduler.receive(Intent().setAction("remind").putExtra("id", t.id).putExtra("generation", r.generation).putExtra("at", Rules.reminderInstant(t, r, ZoneId.systemDefault())!!.toEpochMilli()))
        assertEquals(1, manager.activeNotifications.count { it.tag == t.id })
        manager.activeNotifications.first { it.tag == t.id }.notification.actions.first().actionIntent.send()
        withTimeout(8_000) { while (repo.dao.task(t.id)?.completedAt == null) delay(100) }
        assertTrue(manager.activeNotifications.none { it.tag == t.id })
    }
    @Test fun staleGenerationNeverPostsNotification() = runBlocking {
        val at = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0)
        val t = Task(title = "旧回调", scheduleDate = at.toLocalDate().toString(), scheduleTime = at.toLocalTime().toString())
        ids += t.id; repo.save(t, emptyList(), Reminder(t.id, "schedule"), true)
        scheduler.receive(Intent().setAction("remind").putExtra("id", t.id).putExtra("generation", "invalid").putExtra("at", System.currentTimeMillis()))
        assertNull(repo.dao.reminder(t.id)!!.delivered)
        assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.none { it.tag == t.id })
    }
}
