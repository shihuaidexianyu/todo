package app.todo.local

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.time.LocalDate

// Draft persistence only needs Task/Reminder (de)serialization; the backup codec that used to host these is gone.
private object DraftJson {
    private fun JSONObject.nullString(key: String) = if (isNull(key)) null else getString(key)
    private fun JSONObject.nullLong(key: String) = if (isNull(key)) null else getLong(key)
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
}
data class EditorDraft(val task: Task, val names: List<String>, val reminder: Reminder?, val isNew: Boolean) {
    fun encode() = JSONObject().put("task", DraftJson.taskJson(task)).put("names", org.json.JSONArray(names))
        .put("reminder", reminder?.let(DraftJson::reminderJson) ?: JSONObject.NULL).put("new", isNew).toString()
    companion object {
        fun decode(text: String): EditorDraft {
            val j = JSONObject(text); val a = j.getJSONArray("names")
            return EditorDraft(DraftJson.parseTask(j.getJSONObject("task")), (0 until a.length()).map(a::getString),
                if (j.isNull("reminder")) null else DraftJson.parseReminder(j.getJSONObject("reminder")), j.getBoolean("new"))
        }
    }
}
class TodoViewModel(app: Application) : AndroidViewModel(app) {
    val application = app as TodoApplication
    val repo = application.repository
    val scheduler = application.reminders
    val tasks = MutableStateFlow<List<TaskRecord>>(emptyList())
    val tags = MutableStateFlow<List<Tag>>(emptyList())
    val closures = MutableStateFlow<List<DayClosure>>(emptyList())
    val loaded = MutableStateFlow(false)
    val readError = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val messages = MutableSharedFlow<Pair<String, String?>>(extraBufferCapacity = 10)
    val completionFeedback = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private val drafts = app.getSharedPreferences("drafts", 0)
    val editor = MutableStateFlow(runCatching { drafts.getString("editor", null)?.let(EditorDraft::decode) }.getOrNull())
    init { observe() }
    fun observe() {
        viewModelScope.launch {
            try { combine(repo.dao.observeTasks(), repo.dao.observeTags(), repo.dao.observeClosures()) { t, g, c -> Triple(t, g, c) }
                .collect { (t, g, c) -> tasks.value = t; tags.value = g; closures.value = c; loaded.value = true; readError.value = false }
            } catch (_: Exception) { readError.value = true }
        }
    }
    fun draft(value: EditorDraft?) {
        editor.value = value
        drafts.edit().apply { if (value == null) remove("editor") else putString("editor", value.encode()) }.apply()
    }
    fun message(text: String) { messages.tryEmit(text to null) }
    fun perform(success: String? = null, block: suspend () -> Unit, after: () -> Unit = {}) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            val saved = runCatching { withContext(Dispatchers.IO) { block() } }
            busy.value = false
            saved.fold(onSuccess = {
                after()
                if (success != null) message(success)
                val ok = withContext(Dispatchers.IO) { runCatching { scheduler.reconcile() }.getOrDefault(false) }
                if (!ok) message("任务已保存，提醒未能启用")
            }, onFailure = { e -> message(e.message?.takeIf { it.any { c -> c.code > 255 } } ?: "保存失败，请重试；草稿已保留") })
        }
    }
    fun save() { val d = editor.value ?: return; perform(block = { repo.save(d.task, d.names, d.reminder, d.isNew) }, after = { draft(null) }) }
    fun complete(t: Task, value: Boolean) = perform(block = { repo.complete(t.id, value) }, after = { completionFeedback.tryEmit(value) })
    // Swipe gesture entry: keep tags/reminder untouched, move only the schedule date. Quiet like complete().
    fun scheduleTomorrow(record: TaskRecord) {
        val t = record.task
        if (t.completedAt != null) return
        val tomorrow = LocalDate.now(repo.clock).plusDays(1).toString()
        if (t.scheduleDate == tomorrow) return
        perform(block = { repo.save(t.copy(scheduleDate = tomorrow), record.tags.map(Tag::name), record.reminder, false) })
    }
    fun resume() { viewModelScope.launch(Dispatchers.IO) { runCatching { scheduler.reconcile() }.onFailure { message("提醒恢复失败，请在设置中重试") } } }
}
