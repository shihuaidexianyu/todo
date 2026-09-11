package app.todo.local

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RulesTest {
    private val day = LocalDate.of(2026, 9, 10)
    private val now = day.atTime(12, 0)
    private fun task(sd: String? = null, st: String? = null, dd: String? = null, dt: String? = null) = Task(title = "测试", scheduleDate = sd, scheduleTime = st, dueDate = dd, dueTime = dt)
    @Test fun dueOnlyRemainsUnscheduledAndToday() { val t = task(dd = "2026-09-10"); assertNull(t.scheduleDate); assertTrue(Rules.today(t, day)); assertFalse(Rules.overdue(t, now)) }
    @Test fun dateOnlyDeadlineLastsWholeDay() { val t = task(dd = "2026-09-10"); assertFalse(Rules.overdue(t, day.atTime(23, 59, 59))); assertTrue(Rules.overdue(t, day.plusDays(1).atStartOfDay())) }
    @Test fun timedDeadlineBoundary() { val t = task(dd = "2026-09-10", dt = "12:00"); assertFalse(Rules.overdue(t, now)); assertTrue(Rules.overdue(t, now.plusNanos(1))) }
    @Test fun oldScheduleIsNotOverdue() { val t = task(sd = "2026-09-09", dd = "2026-09-12"); assertTrue(Rules.today(t, day)); assertFalse(Rules.overdue(t, now)); assertEquals("此前安排", Rules.group(t, now)) }
    @Test fun tomorrowScheduledDueTodayStillToday() { val t = task(sd = "2026-09-11", dd = "2026-09-10"); assertTrue(Rules.today(t, day)); assertTrue(Rules.conflict(t)) }
    @Test fun dateOnlyDoesNotInferMidnight() { assertFalse(Rules.conflict(task(sd = "2026-09-10", st = "20:00", dd = "2026-09-10"))); assertTrue(Rules.conflict(task(sd = "2026-09-10", st = "20:00", dd = "2026-09-10", dt = "19:00"))) }
    @Test fun overdueGroupWinsOverOldSchedule() { assertEquals("已过截止", Rules.group(task(sd = "2026-09-08", dd = "2026-09-09"), now)) }
    @Test fun completedTasksNeverTodayOrOverdue() { val t = task(sd = "2026-09-09", dd = "2026-09-09").copy(completedAt = 1, completedOn = "2026-09-10"); assertFalse(Rules.today(t, day)); assertFalse(Rules.overdue(t, now)) }
    @Test fun futureDateUsesEarliestFutureField() { assertEquals("2026-09-11", Rules.futureDate(task(sd = "2026-09-12", dd = "2026-09-11"), day)) }
    @Test fun dateOnlyCannotFollowReminder() { assertNull(Rules.reminderLocal(task(sd = "2026-09-10"), Reminder("id", "schedule"))) }
    @Test fun customReminderIndependentOfSchedule() { val r = Reminder("id", "custom", "2026-09-12", "09:15"); assertEquals(Rules.reminderLocal(task(sd = "2026-09-10"), r), Rules.reminderLocal(task(sd = "2026-09-15"), r)) }
    @Test fun localClockSurvivesZoneChanges() { val t = task(sd = "2026-09-10", st = "09:00"); val r = Reminder(t.id, "schedule"); val local = Rules.reminderLocal(t, r)!!; assertEquals(9, Rules.resolve(local, ZoneId.of("Asia/Shanghai")).hour); assertEquals(9, Rules.resolve(local, ZoneId.of("Europe/London")).hour); assertNotEquals(Rules.reminderInstant(t, r, ZoneId.of("Asia/Shanghai")), Rules.reminderInstant(t, r, ZoneId.of("Europe/London"))) }
    @Test fun dstGapUsesFirstValidMinute() { val z = Rules.resolve(LocalDateTime.of(2026, 3, 8, 2, 30), ZoneId.of("America/New_York")); assertEquals(LocalTime.of(3, 0), z.toLocalTime()) }
    @Test fun dstOverlapUsesFirstOccurrence() { val z = Rules.resolve(LocalDateTime.of(2026, 11, 1, 1, 30), ZoneId.of("America/New_York")); assertEquals(ZoneOffset.ofHours(-4), z.offset) }
    @Test fun stableSortAndTodayTimeFallback() {
        val a = task(sd = "2026-09-10", st = "10:00").copy(id = "a", createdAt = 1)
        val b = task(dd = "2026-09-10", dt = "09:00").copy(id = "b", createdAt = 1)
        val c = task(sd = "2026-09-10").copy(id = "c", createdAt = 1)
        assertEquals(listOf("b", "a", "c"), listOf(c, a, b).map { TaskRecord(it, emptyList(), emptyList()) }.sortedWith(Rules.todaySort(now)).map { it.task.id })
    }
    @Test(expected = IllegalArgumentException::class) fun invalidTimeWithoutDateRejected() { Rules.validate(task(st = "09:00")) }
    @Test(expected = IllegalArgumentException::class) fun blankTitleRejected() { Rules.validate(Task(title = "  ")) }
    @Test(expected = IllegalArgumentException::class) fun partialCompletionRejected() { Rules.validate(task().copy(completedAt = 1)) }
    @Test fun midnightRecomputesWithoutMutating() { val t = task(sd = "2026-09-11"); assertFalse(Rules.today(t, day)); assertTrue(Rules.today(t, day.plusDays(1))); assertEquals("2026-09-11", t.scheduleDate) }
}
