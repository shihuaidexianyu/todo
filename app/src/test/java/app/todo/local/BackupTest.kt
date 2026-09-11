package app.todo.local

import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    private val t = Task(id = "t1", title = "报告", note = "保留\n备注", scheduleDate = "2026-09-10", scheduleTime = "09:00")
    private val tag = Tag(id = "tag1", name = "工作")
    private val backup = Backup(listOf(t), listOf(tag), listOf(TaskTag(t.id, tag.id)), listOf(Reminder(t.id, "schedule")), listOf(DayClosure("2026-09-10", 1, "Asia/Shanghai")))
    @Test fun roundTripPreservesAllFields() { assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup))) }
    @Test(expected = IllegalArgumentException::class) fun duplicateTaskRejected() { BackupCodec.validate(backup.copy(tasks = listOf(t, t))) }
    @Test(expected = IllegalArgumentException::class) fun danglingTagRejected() { BackupCodec.validate(backup.copy(tags = emptyList())) }
    @Test(expected = IllegalArgumentException::class) fun duplicateNormalizedTagRejected() { BackupCodec.validate(backup.copy(tags = listOf(Tag(name = "Work"), Tag(name = "work")))) }
    @Test(expected = IllegalArgumentException::class) fun unknownVersionRejected() { BackupCodec.decode(BackupCodec.encode(backup).replace("\"schema_version\": 1", "\"schema_version\": 99")) }
    @Test(expected = IllegalArgumentException::class) fun missingReminderSourceRejected() { BackupCodec.validate(backup.copy(tasks = listOf(t.copy(scheduleTime = null)))) }
    @Test(expected = IllegalArgumentException::class) fun invalidCompletionRejected() { BackupCodec.validate(backup.copy(tasks = listOf(t.copy(completedOn = "2026-09-10")))) }
    @Test(expected = IllegalArgumentException::class) fun duplicateLinksRejected() { BackupCodec.validate(backup.copy(links = backup.links + backup.links)) }
    @Test fun editorDraftRoundTrip() { val d = EditorDraft(t.copy(title = ""), listOf("未保存"), backup.reminders.first(), true); assertEquals(d, EditorDraft.decode(d.encode())) }
}
