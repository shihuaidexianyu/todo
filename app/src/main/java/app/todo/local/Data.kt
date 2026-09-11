package app.todo.local

import android.app.Application
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import java.util.Locale
import java.util.UUID

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "", val note: String = "",
    val scheduleDate: String? = null, val scheduleTime: String? = null,
    val dueDate: String? = null, val dueTime: String? = null,
    val completedAt: Long? = null, val completedOn: String? = null,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val revision: Long = 0
)
@Entity(tableName = "tags", indices = [Index(value = ["normalizedName"], unique = true)])
data class Tag(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String, val normalizedName: String = normalize(name))
fun normalize(name: String) = name.trim().lowercase(Locale.ROOT)
@Entity(tableName = "task_tags", primaryKeys = ["taskId", "tagId"], foreignKeys = [
    ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("tagId")])
data class TaskTag(val taskId: String, val tagId: String)
@Entity(tableName = "reminders", foreignKeys = [ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)])
data class Reminder(@PrimaryKey val taskId: String, val mode: String, val date: String? = null, val time: String? = null,
    val generation: String = UUID.randomUUID().toString(), val delivered: String? = null)
@Entity(tableName = "closures")
data class DayClosure(@PrimaryKey val date: String, val closedAt: Long, val zoneId: String)
data class TaskRecord(
    @Embedded val task: Task,
    @Relation(parentColumn = "id", entityColumn = "id", associateBy = Junction(TaskTag::class, parentColumn = "taskId", entityColumn = "tagId")) val tags: List<Tag>,
    @Relation(parentColumn = "id", entityColumn = "taskId") val reminders: List<Reminder>
) { val reminder get() = reminders.firstOrNull() }
@Dao
interface TodoDao {
    @Transaction @Query("SELECT * FROM tasks") fun observeTasks(): Flow<List<TaskRecord>>
    @Query("SELECT * FROM tags ORDER BY normalizedName") fun observeTags(): Flow<List<Tag>>
    @Query("SELECT * FROM closures") fun observeClosures(): Flow<List<DayClosure>>
    @Transaction @Query("SELECT * FROM tasks") suspend fun records(): List<TaskRecord>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: String): Task?
    @Query("SELECT * FROM reminders WHERE taskId = :id") suspend fun reminder(id: String): Reminder?
    @Query("SELECT * FROM tasks") suspend fun tasks(): List<Task>
    @Query("SELECT * FROM tags") suspend fun tags(): List<Tag>
    @Query("SELECT * FROM task_tags") suspend fun links(): List<TaskTag>
    @Query("SELECT * FROM reminders") suspend fun reminders(): List<Reminder>
    @Query("SELECT * FROM closures") suspend fun closures(): List<DayClosure>
    @Upsert suspend fun put(task: Task)
    @Upsert suspend fun put(tag: Tag)
    @Upsert suspend fun put(reminder: Reminder)
    @Upsert suspend fun put(closure: DayClosure)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun put(link: TaskTag)
    @Query("DELETE FROM task_tags WHERE taskId = :id") suspend fun clearLinks(id: String)
    @Query("DELETE FROM reminders WHERE taskId = :id") suspend fun clearReminder(id: String)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun deleteTask(id: String)
    @Query("DELETE FROM tags WHERE id = :id") suspend fun deleteTag(id: String)
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM closures") suspend fun clearClosures()
}
@Database(entities = [Task::class, Tag::class, TaskTag::class, Reminder::class, DayClosure::class], version = 1, exportSchema = true)
abstract class TodoDatabase : RoomDatabase() { abstract fun dao(): TodoDao }

class TodoApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, TodoDatabase::class.java, "todo.db").build() }
    val repository by lazy { Repository(database) }
    val reminders by lazy { ReminderScheduler(this, repository) }
}

data class ScheduleChange(val revision: Long, val date: String?, val time: String?)
class Repository(val db: TodoDatabase, private val suppliedClock: Clock? = null) {
    val clock: Clock get() = suppliedClock ?: Clock.systemDefaultZone()
    val dao = db.dao()
    // Also serializes alarm delivery against mutations, avoiding stale notifications after completion.
    val gate = Mutex()
    fun today() = LocalDate.now(clock)
    suspend fun save(task: Task, names: List<String>, reminder: Reminder?, isNew: Boolean) = gate.withLock {
        db.withTransaction {
            Rules.validate(task)
            val old = dao.task(task.id)
            // A process may die after committing a new task but before removing its draft.
            if (isNew && old != null && old.copy(updatedAt = task.updatedAt, revision = task.revision) == task.copy(title = task.title.trim())) {
                val record = dao.records().first { it.task.id == task.id }
                val existingReminder = record.reminder
                val sameReminder = if (reminder == null) existingReminder == null else existingReminder != null &&
                    reminder.mode == existingReminder.mode && reminder.date == existingReminder.date && reminder.time == existingReminder.time
                if (sameReminder && record.tags.map { normalize(it.name) }.toSet() == names.map(::normalize).toSet()) return@withTransaction
            }
            check(if (isNew) old == null else old != null && old.revision == task.revision) { "任务已在其他入口修改，请重新打开后编辑" }
            val previous = dao.reminder(task.id)
            val validReminder = reminder?.takeIf { Rules.reminderLocal(task, it) != null }
            if (validReminder != null && (previous == null || previous.mode != validReminder.mode ||
                    previous.date != validReminder.date || previous.time != validReminder.time)) {
                require(Rules.reminderInstant(task, validReminder, clock.zone)!!.isAfter(clock.instant())) { "新设的提醒必须在未来" }
            }
            dao.put(task.copy(title = task.title.trim(), revision = (old?.revision ?: -1) + 1, updatedAt = clock.millis()))
            dao.clearLinks(task.id)
            names.map { it.trim() }.distinctBy(::normalize).forEach { name ->
                require(name.isNotEmpty() && name.length <= 30) { "标签名称需为 1–30 字符" }
                val tag = dao.tags().firstOrNull { it.normalizedName == normalize(name) } ?: Tag(name = name).also { dao.put(it) }
                dao.put(TaskTag(task.id, tag.id))
            }
            if (validReminder == null) dao.clearReminder(task.id) else {
                val same = previous != null && previous.mode == validReminder.mode && previous.date == validReminder.date && previous.time == validReminder.time &&
                    old != null && Rules.reminderLocal(old, previous) == Rules.reminderLocal(task, validReminder)
                val next = if (same) previous!! else validReminder.copy(generation = UUID.randomUUID().toString(), delivered = null)
                val past = !Rules.reminderInstant(task, next, clock.zone)!!.isAfter(clock.instant())
                dao.put(if (!same && past) next.copy(delivered = next.generation) else next)
            }
        }
    }
    suspend fun complete(id: String, completed: Boolean) = gate.withLock { db.withTransaction { completeInside(id, completed) } }
    suspend fun completeInside(id: String, completed: Boolean) {
        val task = dao.task(id) ?: return
        if ((task.completedAt != null) == completed) return
        dao.put(task.copy(completedAt = if (completed) clock.millis() else null,
            completedOn = if (completed) today().toString() else null, updatedAt = clock.millis(), revision = task.revision + 1))
        if (!completed) dao.reminder(id)?.let { r ->
            if (Rules.reminderInstant(task, r, clock.zone)?.isAfter(clock.instant()) == false) dao.put(r.copy(delivered = r.generation))
        }
    }
    suspend fun delete(id: String) = gate.withLock { dao.deleteTask(id) }
    suspend fun tag(name: String, id: String? = null) = gate.withLock { db.withTransaction {
        require(name.trim().isNotEmpty() && name.trim().length <= 30) { "标签名称需为 1–30 字符" }
        val existing = dao.tags().firstOrNull { it.normalizedName == normalize(name) }
        if (id != null && existing != null && existing.id != id) {
            dao.links().filter { it.tagId == id }.forEach { dao.put(it.copy(tagId = existing.id)) }; dao.deleteTag(id)
        } else if (existing == null || id != null) dao.put(Tag(id ?: UUID.randomUUID().toString(), name.trim()))
    } }
    suspend fun deleteTag(id: String) = gate.withLock { dao.deleteTag(id) }
    suspend fun closeDay(date: LocalDate, changes: Map<String, ScheduleChange>) = gate.withLock { db.withTransaction {
        changes.forEach { (id, change) ->
            val task = dao.task(id)
            if (task != null && task.completedAt == null) {
                check(task.revision == change.revision) { "有任务已被修改，请退出收尾并重新确认" }
                val next = task.copy(scheduleDate = change.date, scheduleTime = change.time, revision = task.revision + 1, updatedAt = clock.millis())
                Rules.validate(next); dao.put(next)
                dao.reminder(id)?.takeIf { it.mode == "schedule" && (task.scheduleDate != next.scheduleDate || task.scheduleTime != next.scheduleTime) }?.let { old ->
                    if (next.scheduleTime == null) dao.clearReminder(id) else {
                        val r = old.copy(generation = UUID.randomUUID().toString(), delivered = null)
                        dao.put(if (Rules.reminderInstant(next, r, clock.zone)!!.isAfter(clock.instant())) r else r.copy(delivered = r.generation))
                    }
                }
            }
        }
        dao.put(DayClosure(date.toString(), clock.millis(), clock.zone.id))
    } }
}

object Rules {
    fun validate(t: Task) {
        require(t.title.trim().isNotEmpty() && t.title.length <= 300) { "标题需为 1–300 字符" }
        require(t.note.length <= 5000) { "备注不能超过 5,000 字符" }
        listOf(t.scheduleDate to t.scheduleTime, t.dueDate to t.dueTime).forEach { (d, time) ->
            require(d != null || time == null) { "请先选择日期" }
            d?.let { require(LocalDate.parse(it).toString() == it) }
            time?.let { require(LocalTime.parse(it).second == 0 && it.length == 5) }
        }
        require((t.completedAt == null) == (t.completedOn == null))
        t.completedOn?.let { LocalDate.parse(it) }
    }
    fun today(t: Task, d: LocalDate) = t.completedAt == null &&
        (t.scheduleDate?.let { it <= d.toString() } == true || t.dueDate?.let { it <= d.toString() } == true)
    fun overdue(t: Task, now: LocalDateTime): Boolean = t.completedAt == null && t.dueDate?.let {
        if (t.dueTime == null) it < now.toLocalDate().toString() else LocalDateTime.of(LocalDate.parse(it), LocalTime.parse(t.dueTime)) < now
    } == true
    fun conflict(t: Task): Boolean {
        val s = t.scheduleDate ?: return false; val d = t.dueDate ?: return false
        return s > d || s == d && t.scheduleTime != null && t.dueTime != null && t.scheduleTime > t.dueTime
    }
    fun group(t: Task, now: LocalDateTime) = when { overdue(t, now) -> "已过截止"; t.scheduleDate?.let { it < now.toLocalDate().toString() } == true -> "此前安排"; else -> "今天" }
    fun todaySort(now: LocalDateTime): Comparator<TaskRecord> = compareBy<TaskRecord> {
        when (group(it.task, now)) { "已过截止" -> 0; "此前安排" -> 1; else -> 2 }
    }.thenBy {
        val t = it.task
        when (group(t, now)) {
            "已过截止" -> t.dueDate + "T" + (t.dueTime ?: "24:00")
            "此前安排" -> t.scheduleDate + "T" + (t.scheduleTime ?: "23:59")
            else -> (if (t.scheduleDate == now.toLocalDate().toString()) t.scheduleTime else null)
                ?: (if (t.dueDate == now.toLocalDate().toString()) t.dueTime else null) ?: "99:99"
        }
    }.thenBy { it.task.createdAt }.thenBy { it.task.id }
    fun futureDate(t: Task, d: LocalDate): String? = listOfNotNull(t.scheduleDate, t.dueDate).filter { it > d.toString() }.minOrNull()
    fun reminderLocal(t: Task, r: Reminder): LocalDateTime? {
        val (date, time) = when (r.mode) { "schedule" -> t.scheduleDate to t.scheduleTime; "due" -> t.dueDate to t.dueTime; "custom" -> r.date to r.time; else -> return null }
        return if (date == null || time == null) null else LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
    }
    // In a DST gap use the FIRST valid local instant (not the default shifted minute).
    fun resolve(local: LocalDateTime, zone: ZoneId): ZonedDateTime {
        val offsets = zone.rules.getValidOffsets(local)
        return if (offsets.isEmpty()) zone.rules.getTransition(local)!!.dateTimeAfter.atZone(zone)
        else ZonedDateTime.ofLocal(local, zone, offsets.first())
    }
    fun reminderInstant(t: Task, r: Reminder, zone: ZoneId): Instant? = reminderLocal(t, r)?.let { resolve(it, zone).toInstant() }
}
