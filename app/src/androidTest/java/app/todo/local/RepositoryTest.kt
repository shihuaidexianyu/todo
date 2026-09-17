package app.todo.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: TodoDatabase
    private lateinit var repo: Repository
    private val clock = Clock.fixed(Instant.parse("2026-09-10T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TodoDatabase::class.java).build(); repo = Repository(db, clock) }
    @After fun close() { db.close() }
    private suspend fun add(t: Task, names: List<String> = emptyList(), r: Reminder? = null): Task { repo.save(t, names, r, true); return repo.dao.task(t.id)!! }
    @Test fun tagReuseDeleteKeepsTaskAndTimes() = runBlocking {
        val t = add(Task(title = "工作", dueDate = "2026-09-11"), listOf(" Work ", "work"))
        assertEquals(1, repo.dao.tags().size); assertEquals(1, repo.dao.links().size)
        repo.deleteTag(repo.dao.tags().first().id)
        assertEquals(t, repo.dao.task(t.id)); assertTrue(repo.dao.links().isEmpty())
    }
    @Test fun completionIsIdempotentAndKeepsDue() = runBlocking {
        val t = add(Task(title = "报告", dueDate = "2026-09-10"))
        repo.complete(t.id, true); val first = repo.dao.task(t.id)!!; repo.complete(t.id, true)
        assertEquals(first, repo.dao.task(t.id)); assertEquals("2026-09-10", first.completedOn); assertEquals(t.dueDate, first.dueDate)
        repo.complete(t.id, false); assertNull(repo.dao.task(t.id)!!.completedAt); assertNull(repo.dao.task(t.id)!!.completedOn)
    }
    @Test fun endDayOnlyChangesSelectedScheduleAndKeepsDue() = runBlocking {
        val t = add(Task(title = "报告", scheduleDate = "2026-09-10", scheduleTime = "19:00", dueDate = "2026-09-10"))
        val untouched = add(Task(title = "不变", scheduleDate = "2026-09-10"))
        repo.closeDay(LocalDate.parse("2026-09-10"), mapOf(t.id to ScheduleChange(t.revision, "2026-09-11", "19:00")))
        val next = repo.dao.task(t.id)!!; assertEquals("2026-09-11", next.scheduleDate); assertEquals("2026-09-10", next.dueDate)
        assertTrue(Rules.today(next, LocalDate.parse("2026-09-10"))); assertEquals(untouched, repo.dao.task(untouched.id))
        repo.closeDay(LocalDate.parse("2026-09-10"), emptyMap()); assertEquals(1, repo.dao.closures().size)
    }
    @Test fun endDayDoesNotReviveCompletedOrDeletedTasks() = runBlocking {
        val a = add(Task(title = "完成", scheduleDate = "2026-09-10")); val b = add(Task(title = "删除", scheduleDate = "2026-09-10"))
        repo.complete(a.id, true); repo.delete(b.id)
        repo.closeDay(LocalDate.parse("2026-09-10"), listOf(a, b).associate { it.id to ScheduleChange(it.revision, "2026-09-11", null) })
        assertNotNull(repo.dao.task(a.id)!!.completedAt); assertEquals("2026-09-10", repo.dao.task(a.id)!!.scheduleDate); assertNull(repo.dao.task(b.id))
    }
    @Test fun staleEndDayRollsBackEveryChangeAndClosure() = runBlocking {
        val a = add(Task(title = "先调整", scheduleDate = "2026-09-10")); val b = add(Task(title = "已修改", scheduleDate = "2026-09-10"))
        repo.save(b.copy(title = "新标题"), emptyList(), null, false)
        val result = runCatching { repo.closeDay(LocalDate.parse("2026-09-10"), linkedMapOf(a.id to ScheduleChange(a.revision, "2026-09-11", null), b.id to ScheduleChange(b.revision, "2026-09-11", null))) }
        assertTrue(result.isFailure); assertEquals(a, repo.dao.task(a.id)); assertTrue(repo.dao.closures().isEmpty())
    }
    @Test fun followedReminderChangesGenerationAndCancelSourceRemovesIt() = runBlocking {
        var t = Task(title = "提醒", scheduleDate = "2026-09-11", scheduleTime = "09:00")
        t = add(t, r = Reminder(t.id, "schedule")); val r = repo.dao.reminder(t.id)!!
        repo.save(t.copy(scheduleDate = "2026-09-12"), emptyList(), r, false)
        assertNotEquals(r.generation, repo.dao.reminder(t.id)!!.generation)
        val next = repo.dao.task(t.id)!!; repo.save(next.copy(scheduleDate = null, scheduleTime = null), emptyList(), repo.dao.reminder(t.id), false)
        assertNull(repo.dao.reminder(t.id))
    }
    @Test fun newPastReminderRejectedWithoutWritingTask() = runBlocking {
        val t = Task(title = "过去", scheduleDate = "2026-09-09", scheduleTime = "09:00")
        assertTrue(runCatching { add(t, r = Reminder(t.id, "schedule")) }.isFailure); assertTrue(repo.dao.tasks().isEmpty())
    }
    @Test fun movingExistingReminderIntoPastDoesNotCatchUp() = runBlocking {
        var t = Task(title = "提醒", scheduleDate = "2026-09-11", scheduleTime = "09:00"); t = add(t, r = Reminder(t.id, "schedule"))
        repo.save(t.copy(scheduleDate = "2026-09-09"), emptyList(), repo.dao.reminder(t.id), false)
        val r = repo.dao.reminder(t.id)!!; assertEquals(r.generation, r.delivered)
    }
    @Test fun editingStaleTaskCannotOverwriteCompletion() = runBlocking {
        val t = add(Task(title = "待完成")); repo.complete(t.id, true)
        assertTrue(runCatching { repo.save(t.copy(title = "旧编辑"), emptyList(), null, false) }.isFailure)
        assertNotNull(repo.dao.task(t.id)!!.completedAt)
    }
    @Test fun renameToExistingTagMergesAssociations() = runBlocking {
        val t = add(Task(title = "有标签"), listOf("A", "B")); val tags = repo.dao.tags()
        repo.tag("b", tags.first { it.name == "A" }.id)
        assertEquals(1, repo.dao.tags().size); assertEquals(1, repo.dao.links().size); assertEquals(t, repo.dao.task(t.id))
    }
}
