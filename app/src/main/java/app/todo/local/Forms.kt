package app.todo.local

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorSheet(draft: EditorDraft, tags: List<Tag>, busy: Boolean, vm: TodoViewModel) {
    var abandon by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var tagPicker by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(draft.task.id) { mutableStateOf(!draft.isNew) }
    var timePicker by remember { mutableStateOf<String?>(null) }
    var permissionExplain by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val t = draft.task
    val original by vm.tasks.collectAsState()
    val old = original.firstOrNull { it.task.id == t.id }
    val dirty = if (draft.isNew) t.title.isNotEmpty() || t.note.isNotEmpty() || t.dueDate != null || draft.reminder != null || draft.names.isNotEmpty()
        else old == null || old.task != t || old.tags.map { it.name }.toSet() != draft.names.toSet() || old.reminder != draft.reminder
    fun dismiss() { if (!busy) { if (dirty) abandon = true else vm.draft(null) } }
    fun update(task: Task = t, names: List<String> = draft.names, r: Reminder? = draft.reminder) { vm.draft(draft.copy(task = task, names = names, reminder = r)) }
    val requestNotification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.resume() }
    BackHandler { dismiss() }
    val latestDirty = rememberUpdatedState(dirty)
    val latestBusy = rememberUpdatedState(busy)
    val confirmSheetChange: (SheetValue) -> Boolean = remember {
        { value -> if (value == SheetValue.Hidden && latestDirty.value) { abandon = true; false } else !latestBusy.value }
    }
    val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = confirmSheetChange)
    TodoBottomSheet(onDismissRequest = { dismiss() }, sheetState = editorSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (draft.isNew) "新建任务" else "任务详情", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { dismiss() }, enabled = !busy) { Icon(Icons.Outlined.Close, "关闭编辑") }
            }
            TextField(t.title, { if (it.length <= 300) update(task = t.copy(title = it)) }, label = { Text("标题") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focus), minLines = 1, maxLines = 8,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
                colors = TextFieldDefaults.colors(focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
            if (t.title.length > 260) Text("${t.title.length}/300", style = MaterialTheme.typography.labelSmall)
            if (!draft.isNew) OutlinedTextField(t.note, { if (it.length <= 5000) update(task = t.copy(note = it)) }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 8)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { tagPicker = !tagPicker }, label = { Text(if (draft.names.isEmpty()) "标签" else draft.names.take(2).joinToString("、") + if (draft.names.size > 2) " +${draft.names.size - 2}" else "", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, leadingIcon = { Icon(Icons.Outlined.Label, null, Modifier.size(16.dp)) }, trailingIcon = { Icon(if (tagPicker) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(16.dp)) })
                AssistChip(onClick = { timePicker = "schedule" }, label = { Text("安排${t.scheduleDate?.let { "：${dateLabel(it)}${t.scheduleTime?.let { time -> " $time" } ?: ""}" } ?: ""}") }, leadingIcon = { Icon(Icons.Outlined.Event, null) })
                if (t.scheduleDate != null) IconButton(onClick = {
                    update(task = t.copy(scheduleDate = null, scheduleTime = null), r = draft.reminder?.takeUnless { it.mode == "schedule" })
                    if (draft.reminder?.mode == "schedule") vm.message("保存后将取消跟随安排的提醒")
                }) { Icon(Icons.Outlined.Close, "取消安排") }
            }
            if (tagPicker) InlineTagPicker(tags, draft.names, { update(names = it) }, { tagPicker = false })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = t.dueDate != null, onClick = { if (t.dueDate == null) update(task = t.copy(dueDate = LocalDate.now().toString())) else timePicker = "due" }, label = { Text(t.dueDate?.let { "截止：${dateLabel(it)}${t.dueTime?.let { time -> " $time" } ?: ""}" } ?: "今天截止") }, leadingIcon = { Icon(Icons.Outlined.Flag, null, Modifier.size(16.dp)) })
                if (t.dueDate == null) TextButton(onClick = { timePicker = "due" }) { Text("其他日期") }
            }
            if (draft.isNew) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起更多选项" else "提醒与备注"); Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
            if (expanded) {
                Text("提醒", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("none" to "不提醒", "schedule" to "按安排时间", "due" to "按截止时间", "custom" to "自定义").forEach { (mode, label) ->
                        FilterChip(selected = (draft.reminder?.mode ?: "none") == mode,
                            enabled = mode != "schedule" && mode != "due" || mode == "schedule" && t.scheduleTime != null || mode == "due" && t.dueTime != null,
                            onClick = {
                                if (mode == "custom") timePicker = "custom" else update(r = if (mode == "none") null else Reminder(t.id, mode))
                                if (mode != "none" && !vm.scheduler.notificationsAllowed()) permissionExplain = true
                            }, label = { Text(label) })
                    }
                }
                if (draft.reminder != null) {
                    Text(vm.scheduler.status(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    val local = Rules.reminderLocal(t, draft.reminder)
                    if (local != null) {
                        val resolved = Rules.resolve(local, ZoneId.systemDefault())
                        Meta("${if (draft.reminder.mode == "custom") "独立提醒保留在 " else "提醒时间 "}${resolved.format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))}")
                        if (resolved.toLocalDateTime() != local) Meta("所选钟点因夏令时不存在，使用上方实际提醒时间")
                        if (!resolved.toInstant().isAfter(Instant.now())) Meta("提醒时间已过，不会立即补发", true)
                    }
                    if (draft.reminder.mode == "custom") TextButton(onClick = { timePicker = "custom" }) { Text("修改独立提醒时间") }
                    if (!vm.scheduler.notificationsAllowed()) TextButton(onClick = { permissionExplain = true }) { Text("开启通知") }
                    else if (!vm.scheduler.exactAllowed()) TextButton(onClick = { openExactSettings(context) }) { Text("授权精确提醒（否则可能延迟）") }
                }
                if (draft.isNew) OutlinedTextField(t.note, { if (it.length <= 5000) update(task = t.copy(note = it)) }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), minLines = 2, maxLines = 8)
            }
            if (Rules.conflict(t)) Meta("安排晚于截止", true)
            if (old?.reminder?.mode == "schedule" && draft.reminder == null && t.scheduleTime == null) Meta("保存后将取消跟随安排的提醒")
            if (old?.reminder?.mode == "due" && draft.reminder == null && t.dueTime == null) Meta("保存后将取消跟随截止的提醒")
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = { vm.save() }, enabled = !busy && t.title.isNotBlank(), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(if (busy) "保存中…" else "保存"); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) }
            }
            if (!draft.isNew) TextButton(onClick = { delete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("删除任务", color = MaterialTheme.colorScheme.error) }
        }
        val reduced = LocalReducedMotion.current
        LaunchedEffect(t.id) {
            if (draft.isNew) {
                if (!reduced) snapshotFlow { editorSheetState.currentValue }.first { it == SheetValue.Expanded }
                focus.requestFocus()
            }
        }
    }
    if (abandon) AlertDialog(onDismissRequest = { abandon = false }, title = { Text("保留这些修改？") }, text = { Text("尚未保存的内容只保留在草稿中。") }, confirmButton = { TextButton(enabled = t.title.isNotBlank() && !busy, onClick = { abandon = false; vm.save() }) { Text("保存") } }, dismissButton = { Row { TextButton(onClick = { vm.draft(null) }) { Text("放弃") }; TextButton(onClick = { abandon = false }) { Text("继续编辑") } } })
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("删除任务？") }, text = { Text("任务与提醒将被删除，标签会保留。此操作无法撤销。") }, confirmButton = { TextButton(enabled = !busy, onClick = { vm.perform("任务已删除", { vm.repo.delete(t.id) }, { vm.draft(null) }) }) { Text("删除") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("取消") } })
    timePicker?.let { field ->
        val date = when (field) { "schedule" -> t.scheduleDate; "due" -> t.dueDate; else -> draft.reminder?.date }
        val time = when (field) { "schedule" -> t.scheduleTime; "due" -> t.dueTime; else -> draft.reminder?.time }
        DateTimePanel(when (field) { "schedule" -> "安排：打算什么时候做"; "due" -> "截止：最晚什么时候完成"; else -> "提醒：独立的具体时间" }, date, time, field == "custom", { timePicker = null }) { d, tm ->
            when (field) {
                "schedule" -> { update(task = t.copy(scheduleDate = d, scheduleTime = tm), r = draft.reminder?.takeUnless { it.mode == "schedule" && tm == null }); if (tm == null && draft.reminder?.mode == "schedule") vm.message("保存后将取消跟随安排的提醒") }
                "due" -> { update(task = t.copy(dueDate = d, dueTime = tm), r = draft.reminder?.takeUnless { it.mode == "due" && tm == null }); if (tm == null && draft.reminder?.mode == "due") vm.message("保存后将取消跟随截止的提醒") }
                else -> update(r = if (d != null && tm != null) Reminder(t.id, "custom", d, tm) else null)
            }
            timePicker = null
        }
    }
    if (permissionExplain) AlertDialog(onDismissRequest = { permissionExplain = false }, title = { Text("允许 todo 发送提醒") }, text = { Text("通知用于你明确设置的待办提醒。拒绝后仍可保存任务。准确钟点还需要单独授权精确提醒，未授权时可能延迟。") }, confirmButton = { TextButton(onClick = {
        permissionExplain = false
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        else openNotificationSettings(context)
    }) { Text("继续") } }, dismissButton = { TextButton(onClick = { permissionExplain = false }) { Text("暂不开启") } })
}

@Composable fun TimeField(title: String, date: String?, time: String?, click: () -> Unit) {
    OutlinedButton(onClick = click, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 56.dp)) {
        Column(Modifier.weight(1f)) { Text(title); if (date != null) Text("${dateLabel(date)}${time?.let { " $it" } ?: " · 仅日期"}", fontSize = 14.sp) }
        Icon(Icons.Outlined.EditCalendar, null)
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun DateTimePanel(title: String, initialDate: String?, initialTime: String?, requireTime: Boolean = false, dismiss: () -> Unit, save: (String?, String?) -> Unit) {
    var date by rememberSaveable { mutableStateOf(initialDate) }; var time by rememberSaveable { mutableStateOf(initialTime) }
    val context = LocalContext.current
    TodoBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("今天" to LocalDate.now(), "明天" to LocalDate.now().plusDays(1)).forEach { (label, d) -> FilterChip(selected = date == d.toString(), onClick = { date = d.toString() }, label = { Text(label) }) }
                AssistChip(onClick = { val d = date?.let(LocalDate::parse) ?: LocalDate.now(); DatePickerDialog(context, { _, y, m, day -> date = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show() }, label = { Text("选择日期") })
            }
            if (date != null) {
                Text(dateLabel(date!!), Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { val tm = time?.let(LocalTime::parse) ?: LocalTime.now(); TimePickerDialog(context, { _, h, m -> time = String.format(java.util.Locale.ROOT, "%02d:%02d", h, m) }, tm.hour, tm.minute, true).show() }) { Text(time ?: "添加时间") }
                    if (time != null) TextButton(onClick = { time = null }) { Text("清除钟点") }
                }
            }
            if (requireTime) Meta("请选择具体钟点；自定义提醒不会随安排或截止移动。") else Meta("钟点可不填，仅日期不会自动添加午夜提醒。")
            Button(onClick = { save(date, time) }, enabled = date != null && (!requireTime || time != null), modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("确定") }
            TextButton(onClick = { save(null, null) }, modifier = Modifier.fillMaxWidth()) { Text(if (title.startsWith("安排")) "取消安排" else if (title.startsWith("截止")) "清除截止" else "不提醒") }
            TextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) { Text("返回") }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun InlineTagPicker(tags: List<Tag>, selected: List<String>, update: (List<String>) -> Unit, dismiss: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            OutlinedTextField(search, { if (it.length <= 30) search = it }, label = { Text("搜索或创建标签") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (tags.map { it.name } + selected).distinctBy(::normalize).filter { it.contains(search, true) }.forEach { name ->
                    FilterChip(selected = name in selected, onClick = { update(if (name in selected) selected - name else selected + name) }, label = { Text(name) })
                }
            }
            if (search.isNotBlank() && (tags.map { it.name } + selected).none { normalize(it) == normalize(search) }) {
                TextButton(onClick = { update(selected + search.trim()); search = "" }) { Icon(Icons.Outlined.Add, null); Text("创建标签“${search.trim()}”") }
            }
            TextButton(onClick = dismiss, modifier = Modifier.align(Alignment.End)) { Text("完成选择") }
        }
    }
}
@Composable fun TagDialog(tag: Tag?, dismiss: () -> Unit, vm: TodoViewModel) {
    var name by rememberSaveable(tag?.id) { mutableStateOf(tag?.name ?: "") }; var delete by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsState()
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (tag == null) "新建标签" else "管理标签") }, text = { Column {
        OutlinedTextField(name, { if (it.length <= 30) name = it }, label = { Text("名称") }, singleLine = true)
        if (tag != null) TextButton(onClick = { delete = true }) { Text("删除标签", color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(enabled = name.isNotBlank() && !busy, onClick = { vm.perform("标签已保存", { vm.repo.tag(name, tag?.id) }, dismiss) }) { Text("保存") } }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
    if (delete && tag != null) AlertDialog(onDismissRequest = { delete = false }, title = { Text("删除标签？") }, text = { Text("只移除标签，任务会保留。安排、截止与提醒不会改变。") }, confirmButton = { TextButton(enabled = !busy, onClick = { vm.perform("标签已删除", { vm.repo.deleteTag(tag.id) }, dismiss) }) { Text("删除") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("取消") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EndDaySheet(date: LocalDate, records: List<TaskRecord>, vm: TodoViewModel, dismiss: () -> Unit) {
    var changesJson by rememberSaveable(date.toString()) { mutableStateOf("{}") }
    val changes = remember(changesJson) { val j = JSONObject(changesJson); j.keys().asSequence().associateWith { id -> val c = j.getJSONObject(id); ScheduleChange(c.getLong("revision"), if (c.isNull("date")) null else c.getString("date"), if (c.isNull("time")) null else c.getString("time")) } }
    fun change(t: Task, d: String?, time: String?) { val j = JSONObject(changesJson); j.put(t.id, JSONObject().put("revision", t.revision).put("date", d ?: JSONObject.NULL).put("time", time ?: JSONObject.NULL)); changesJson = j.toString() }
    fun keep(id: String) { val j = JSONObject(changesJson); j.remove(id); changesJson = j.toString() }
    var abandon by remember { mutableStateOf(false) }; var selecting by remember { mutableStateOf<Task?>(null) }; var success by rememberSaveable { mutableStateOf(false) }
    var completedOpen by rememberSaveable { mutableStateOf(false) }
    val busy by vm.busy.collectAsState(); val view = LocalView.current
    fun leave() { if (!busy) { if (changes.isNotEmpty() && !success) abandon = true else dismiss() } }
    BackHandler { leave() }
    TodoBottomSheet(onDismissRequest = { leave() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { if (it == SheetValue.Hidden && changes.isNotEmpty() && !success) { abandon = true; false } else !busy })) {
        if (success) Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("今天就到这里。", style = MaterialTheme.typography.titleLarge); Text("${dateLabel(date.toString())}已收尾", Modifier.padding(vertical = 20.dp)); Button(onClick = dismiss) { Text("回到今天") }
        } else {
            val active = records.filter { Rules.today(it.task, date) }.sortedWith(Rules.todaySort(date.atTime(23, 59)))
            val completed = records.filter { it.task.completedOn == date.toString() }
            LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 24.dp)) {
                item { Text("结束一天", style = MaterialTheme.typography.titleLarge); Text(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), Modifier.padding(vertical = 12.dp)); Text("未完成的事情可以保持原样，也可以重新安排。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { completedOpen = !completedOpen }) { Icon(if (completedOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null); Text("今天完成 ${completed.size} 项") }
                }
                if (completedOpen) items(completed, key = { "done:${it.task.id}" }) { Text(it.task.title, Modifier.padding(vertical = 8.dp)) }
                item { Text("还有 ${active.size} 项未完成", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.titleMedium) }
                items(active, key = { it.task.id }) { record ->
                    val t = record.task; val c = changes[t.id]; var menu by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(t.title); t.dueDate?.let { Meta("截止 ${dateLabel(it)} ${t.dueTime ?: ""}") }
                        Box { OutlinedButton(onClick = { menu = true }) { Text(if (c == null) "保持不变" else if (c.date == null) "取消安排" else "安排 ${dateLabel(c.date, date)} ${c.time ?: ""}"); Icon(Icons.Outlined.ExpandMore, null) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("保持不变") }, onClick = { keep(t.id); menu = false })
                                DropdownMenuItem(text = { Text("安排到明天") }, onClick = { change(t, date.plusDays(1).toString(), t.scheduleTime); menu = false })
                                DropdownMenuItem(text = { Text("选择其他日期") }, onClick = { selecting = t; menu = false })
                                DropdownMenuItem(text = { Text("取消安排") }, onClick = { change(t, null, null); menu = false })
                            }
                        }
                        if (c != null) {
                            val preview = t.copy(scheduleDate = c.date, scheduleTime = c.time)
                            if (Rules.conflict(preview)) Meta("安排晚于截止", true)
                            if (preview.dueDate?.let { it <= date.toString() } == true) Meta("截止不变，任务仍会出现在今天")
                            if (record.reminder?.mode == "custom") Meta("独立提醒保持 ${Rules.reminderLocal(t, record.reminder!!)}")
                            if (record.reminder?.mode == "schedule" && c.time == null) Meta("将取消跟随安排的提醒")
                            if (c.revision != t.revision) Meta("任务已被修改，请重新选择安排", true)
                        }
                    }
                    HorizontalDivider()
                }
                item { Button(enabled = !busy, onClick = { vm.perform(block = { vm.repo.closeDay(date, changes) }, after = { success = true; view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY) }) }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text(if (busy) "保存中…" else "结束今天") }; TextButton(enabled = !busy, onClick = { leave() }, modifier = Modifier.fillMaxWidth()) { Text("返回") } }
            }
        }
    }
    if (abandon) AlertDialog(onDismissRequest = { abandon = false }, title = { Text("放弃这次安排调整？") }, text = { Text("预览尚未写入，任务与收尾状态会保持原样。") }, confirmButton = { TextButton(onClick = dismiss) { Text("放弃调整") } }, dismissButton = { TextButton(onClick = { abandon = false }) { Text("继续整理") } })
    selecting?.let { t -> DateTimePanel("安排：打算什么时候做", changes[t.id]?.date ?: t.scheduleDate, changes[t.id]?.time ?: t.scheduleTime, dismiss = { selecting = null }) { d, time -> change(t, d, time); selecting = null } }
}

fun openNotificationSettings(context: android.content.Context) { runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) } }
fun openExactSettings(context: android.content.Context) { if (Build.VERSION.SDK_INT >= 31) runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) } }
@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsPage(modifier: Modifier, appearance: String, setAppearance: (String) -> Unit, reduce: Boolean, setReduce: (Boolean) -> Unit, lockTitle: Boolean, setLockTitle: (Boolean) -> Unit, vm: TodoViewModel, tick: Int, export: () -> Unit, import: () -> Unit, haptics: Boolean, setHaptics: (Boolean) -> Unit, sound: Boolean, setSound: (Boolean) -> Unit) {
    val context = LocalContext.current
    val status = remember(tick) { vm.scheduler.status() }
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("外观", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("跟随系统", "浅色", "深色").forEach { FilterChip(appearance == it, { setAppearance(it) }, label = { Text(it) }) } }
        SettingSwitch("减少动画", "默认跟随系统；开启后直接呈现状态变化。", reduce, setReduce)
        SettingSwitch("操作震动", "完成和恢复任务时轻触反馈", haptics, setHaptics)
        SettingSwitch("完成提示音", "静音或勿扰时不播放", sound, setSound)
        SettingSwitch("锁屏显示任务标题", "默认隐藏任务内容，并尊重系统隐私设置。", lockTitle, setLockTitle)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("提醒状态", style = MaterialTheme.typography.titleMedium)
        Text(status)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        Meta("通知通道：${if (manager.getNotificationChannel(ReminderScheduler.CHANNEL)?.importance == android.app.NotificationManager.IMPORTANCE_NONE) "已关闭" else "已开启"}")
        Meta("精确提醒：${if (vm.scheduler.exactAllowed()) "已授权" else "未授权，可能延迟"}")
        if (vm.scheduler.failed()) Meta("任务已保存，提醒未能启用", true)
        TextButton(onClick = { openNotificationSettings(context) }) { Text("通知设置") }
        if (Build.VERSION.SDK_INT >= 31) TextButton(onClick = { openExactSettings(context) }) { Text("精确提醒授权") }
        TextButton(onClick = { vm.resume(); vm.message("已请求重新检查提醒") }) { Text("重新检查提醒") }
        Text("关机、强行停止或系统省电限制可能影响提醒。再次打开后会重新协调，不保证任何情况下都准时。", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("本地数据", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = export, modifier = Modifier.fillMaxWidth()) { Text("导出备份") }
        OutlinedButton(onClick = import, modifier = Modifier.fillMaxWidth()) { Text("恢复备份") }
        Text("数据只保存在此设备。卸载或设备丢失可能导致数据丢失，请自行保管手动备份文件。自动云备份已禁用。", fontSize = 14.sp)
        Text("日期和钟点跟随手机所在地的本地时间。更换时区后保留填写的钟点，并重新计算提醒。", fontSize = 14.sp)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("todo", style = MaterialTheme.typography.titleLarge); Text("版本 ${BuildConfig.VERSION_NAME}"); Text("随手记下，安排好，再放下。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
    }
}
@Composable private fun SettingSwitch(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f).padding(end = 12.dp)) { Text(title); Meta(detail) }; Switch(checked, change, modifier = Modifier.semantics { contentDescription = title }) }
}
