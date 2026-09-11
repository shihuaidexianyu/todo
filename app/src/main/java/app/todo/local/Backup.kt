package app.todo.local

import androidx.room.withTransaction
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.util.UUID

data class Backup(val tasks: List<Task>, val tags: List<Tag>, val links: List<TaskTag>, val reminders: List<Reminder>, val closures: List<DayClosure>)
object BackupCodec {
    private fun JSONObject.nullString(key: String) = if (isNull(key)) null else getString(key)
    private fun JSONObject.nullLong(key: String) = if (isNull(key)) null else getLong(key)
    private fun <T> JSONArray.mapObjects(block: (JSONObject) -> T) = (0 until length()).map { block(getJSONObject(it)) }
    fun taskJson(t: Task) = JSONObject().apply {
        put("id", t.id); put("title", t.title); put("note", t.note)
        put("schedule_date", t.scheduleDate ?: JSONObject.NULL); put("schedule_time", t.scheduleTime ?: JSONObject.NULL)
        put("due_date", t.dueDate ?: JSONObject.NULL); put("due_time", t.dueTime ?: JSONObject.NULL)
        put("completed_at", t.completedAt ?: JSONObject.NULL); put("completed_on", t.completedOn ?: JSONObject.NULL)
        put("created_at", t.createdAt); put("updated_at", t.updatedAt); put("revision", t.revision)
    }
    fun parseTask(j: JSONObject) = Task(j.getString("id"), j.getString("title"), j.getString("note"),
        j.nullString("schedule_date"), j.nullString("schedule_time"), j.nullString("due_date"), j.nullString("due_time"),
        j.nullLong("completed_at"), j.nullString("completed_on"), j.getLong("created_at"), j.getLong("updated_at"), j.getLong("revision"))
    fun reminderJson(r: Reminder) = JSONObject().apply { put("task_id", r.taskId); put("mode", r.mode); put("date", r.date ?: JSONObject.NULL); put("time", r.time ?: JSONObject.NULL); put("generation", r.generation); put("delivered", r.delivered ?: JSONObject.NULL) }
    fun parseReminder(j: JSONObject) = Reminder(j.getString("task_id"), j.getString("mode"), j.nullString("date"), j.nullString("time"), j.getString("generation"), j.nullString("delivered"))
    fun encode(b: Backup): String = JSONObject().apply {
        put("schema_version", 1)
        put("tasks", JSONArray(b.tasks.map(::taskJson)))
        put("tags", JSONArray(b.tags.map { JSONObject().put("id", it.id).put("name", it.name).put("normalized_name", it.normalizedName) }))
        put("task_tags", JSONArray(b.links.map { JSONObject().put("task_id", it.taskId).put("tag_id", it.tagId) }))
        put("reminders", JSONArray(b.reminders.map(::reminderJson)))
        put("closures", JSONArray(b.closures.map { JSONObject().put("date", it.date).put("closed_at", it.closedAt).put("zone_id", it.zoneId) }))
    }.toString(2)
    fun decode(text: String): Backup {
        require(text.length <= 30_000_000) { "备份文件过大" }
        val j = JSONObject(text)
        require(j.getInt("schema_version") == 1) { "不支持此备份版本" }
        val b = Backup(j.getJSONArray("tasks").mapObjects(::parseTask),
            j.getJSONArray("tags").mapObjects { Tag(it.getString("id"), it.getString("name"), it.getString("normalized_name")) },
            j.getJSONArray("task_tags").mapObjects { TaskTag(it.getString("task_id"), it.getString("tag_id")) },
            j.getJSONArray("reminders").mapObjects(::parseReminder),
            j.getJSONArray("closures").mapObjects { DayClosure(it.getString("date"), it.getLong("closed_at"), it.getString("zone_id")) })
        validate(b); return b
    }
    fun validate(b: Backup) {
        fun <T> unique(list: List<T>) { require(list.distinct().size == list.size) { "备份含重复记录" } }
        unique(b.tasks.map { it.id }); unique(b.tags.map { it.id }); unique(b.tags.map { it.normalizedName })
        unique(b.links); unique(b.reminders.map { it.taskId }); unique(b.closures.map { it.date })
        val tasks = b.tasks.associateBy { it.id }; val tags = b.tags.map { it.id }.toSet()
        b.tasks.forEach { Rules.validate(it); require(it.id.isNotBlank() && it.revision >= 0 && it.createdAt >= 0 && it.updatedAt >= 0) }
        b.tags.forEach { require(it.id.isNotBlank() && it.name.isNotEmpty() && it.name == it.name.trim() && it.name.length <= 30 && it.normalizedName == normalize(it.name)) }
        b.links.forEach { require(it.taskId in tasks && it.tagId in tags) { "备份引用不完整" } }
        b.reminders.forEach { r ->
            val task = tasks[r.taskId] ?: error("备份提醒引用不完整")
            require(r.mode in listOf("schedule", "due", "custom") && r.generation.isNotBlank())
            require(r.delivered == null || r.delivered == r.generation)
            require(r.mode == "custom" || (r.date == null && r.time == null))
            if (r.mode == "custom") { require(r.time?.length == 5); LocalDate.parse(r.date); LocalTime.parse(r.time) }
            require(Rules.reminderLocal(task, r) != null) { "备份提醒缺少时间" }
        }
        b.closures.forEach { require(LocalDate.parse(it.date).toString() == it.date); ZoneId.of(it.zoneId); require(it.closedAt >= 0) }
    }
    suspend fun snapshot(repo: Repository): Backup = repo.gate.withLock { repo.db.withTransaction {
        with(repo.dao) { Backup(tasks(), tags(), links(), reminders(), closures()) }
    } }
    suspend fun restore(repo: Repository, b: Backup) = repo.gate.withLock {
        validate(b)
        repo.db.withTransaction {
            with(repo.dao) {
                clearTasks(); clearTags(); clearClosures()
                b.tasks.forEach { put(it.copy(revision = it.revision + 1)) }; b.tags.forEach { put(it) }; b.links.forEach { put(it) }; b.closures.forEach { put(it) }
                b.reminders.forEach { old ->
                    val r = old.copy(generation = UUID.randomUUID().toString(), delivered = null)
                    val past = !Rules.reminderInstant(b.tasks.first { it.id == r.taskId }, r, ZoneId.systemDefault())!!.isAfter(repo.clock.instant())
                    put(if (past || old.delivered != null) r.copy(delivered = r.generation) else r)
                }
            }
        }
    }
}
