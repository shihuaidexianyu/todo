package app.todo.local

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

data class EditorDraft(val task: Task, val names: List<String>, val reminder: Reminder?, val isNew: Boolean) {
    fun encode() = JSONObject().put("task", BackupCodec.taskJson(task)).put("names", org.json.JSONArray(names))
        .put("reminder", reminder?.let(BackupCodec::reminderJson) ?: JSONObject.NULL).put("new", isNew).toString()
    companion object {
        fun decode(text: String): EditorDraft {
            val j = JSONObject(text); val a = j.getJSONArray("names")
            return EditorDraft(BackupCodec.parseTask(j.getJSONObject("task")), (0 until a.length()).map(a::getString),
                if (j.isNull("reminder")) null else BackupCodec.parseReminder(j.getJSONObject("reminder")), j.getBoolean("new"))
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
    fun complete(t: Task, value: Boolean) = perform(block = { repo.complete(t.id, value) }, after = { messages.tryEmit((if (value) "已完成" else "已恢复") to if (value) t.id else null) })
    fun resume() { viewModelScope.launch(Dispatchers.IO) { runCatching { scheduler.reconcile() }.onFailure { message("提醒恢复失败，请在设置中重试") } } }
}
