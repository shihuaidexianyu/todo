package app.todo.local

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.time.*
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val requestedTask = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(); requestedTask.value = intent.getStringExtra("taskId")
        setContent { TodoApp(requestedTask.value) { requestedTask.value = null; intent.removeExtra("taskId") } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); requestedTask.value = intent.getStringExtra("taskId") }
}

private val light = lightColorScheme(
    primary = Color(0xFF405E75), onPrimary = Color.White, primaryContainer = Color(0xFFDCE6ED), onPrimaryContainer = Color(0xFF203A4D),
    secondary = Color(0xFF405E75), secondaryContainer = Color(0xFFE2EAF0), onSecondaryContainer = Color(0xFF203A4D),
    background = Color(0xFFFAFAF8), onBackground = Color(0xFF202124), surface = Color(0xFFFAFAF8), onSurface = Color(0xFF202124),
    onSurfaceVariant = Color(0xFF596169), surfaceContainer = Color(0xFFF0F1ED), surfaceContainerLow = Color(0xFFF5F6F3), surfaceContainerLowest = Color.White, surfaceContainerHigh = Color(0xFFECEEEB), surfaceContainerHighest = Color(0xFFE2E6E5), surfaceTint = Color(0xFF405E75), outline = Color(0xFF747D84),
    outlineVariant = Color(0xFFD5DAD9), inverseSurface = Color(0xFF202528), inverseOnSurface = Color(0xFFECEEED), inversePrimary = Color(0xFFB3CBDC))
private val dark = darkColorScheme(
    primary = Color(0xFFB3CBDC), onPrimary = Color(0xFF203A4D), primaryContainer = Color(0xFF30434F), onPrimaryContainer = Color(0xFFECEEED),
    secondary = Color(0xFFB3CBDC), secondaryContainer = Color(0xFF30434F), onSecondaryContainer = Color(0xFFECEEED),
    background = Color(0xFF141617), onBackground = Color(0xFFECEEED), surface = Color(0xFF141617), onSurface = Color(0xFFECEEED),
    onSurfaceVariant = Color(0xFFC2C8CB), surfaceContainer = Color(0xFF202528), surfaceContainerLow = Color(0xFF191D1F), surfaceContainerLowest = Color(0xFF101213), surfaceContainerHigh = Color(0xFF292F32), surfaceContainerHighest = Color(0xFF333A3E), surfaceTint = Color(0xFFB3CBDC), outline = Color(0xFF8B949A),
    outlineVariant = Color(0xFF454D52), inverseSurface = Color(0xFFECEEED), inverseOnSurface = Color(0xFF202124), inversePrimary = Color(0xFF405E75))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodoApp(requestedTask: String? = null, consumed: () -> Unit = {}) {
    val vm: TodoViewModel = viewModel()
    val context = LocalContext.current
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val settings = remember { context.getSharedPreferences("settings", 0) }
    var appearance by remember { mutableStateOf(settings.getString("appearance", "跟随系统")!!) }
    var reduce by remember { mutableStateOf(settings.getBoolean("reduce", false)) }
    var lockTitle by remember { mutableStateOf(settings.getBoolean("lock_title", false)) }
    val isDark = appearance == "深色" || appearance == "跟随系统" && isSystemInDarkTheme()
    val reducedMotion = reduce || Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val window = (context as? android.app.Activity)?.window
    SideEffect { window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView).apply { isAppearanceLightStatusBars = !isDark; isAppearanceLightNavigationBars = !isDark } } }
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
    MaterialTheme(colorScheme = if (isDark) dark else light, typography = Typography(titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold))) {
        val tasks by vm.tasks.collectAsState(); val tags by vm.tags.collectAsState()
        val loaded by vm.loaded.collectAsState(); val error by vm.readError.collectAsState(); val busy by vm.busy.collectAsState(); val draft by vm.editor.collectAsState()
        var page by rememberSaveable { mutableStateOf("今天") }
        var expandedTag by rememberSaveable { mutableStateOf<String?>(null) }
        var query by rememberSaveable { mutableStateOf("") }; var includeCompleted by rememberSaveable { mutableStateOf(false) }
        var now by remember { mutableStateOf(LocalDateTime.now()) }; var statusTick by remember { mutableIntStateOf(0) }
        val lifecycle = LocalLifecycleOwner.current
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { now = LocalDateTime.now(); statusTick++; vm.resume() } }
            lifecycle.lifecycle.addObserver(observer); onDispose { lifecycle.lifecycle.removeObserver(observer) }
        }
        // Listen to system clock ticks only while visible; no application polling loop.
        DisposableEffect(lifecycle) {
            val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context, intent: Intent) { now = LocalDateTime.now(); statusTick++ } }
            var registered = false
            fun start() { if (!registered) { androidx.core.content.ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED); registered = true } }
            fun stop() { if (registered) { context.unregisterReceiver(receiver); registered = false } }
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) start() else if (event == Lifecycle.Event.ON_STOP) stop() }
            lifecycle.lifecycle.addObserver(observer)
            if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
            onDispose { stop(); lifecycle.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(requestedTask, loaded) { if (requestedTask != null && loaded) { tasks.firstOrNull { it.task.id == requestedTask }?.let { vm.draft(EditorDraft(it.task, it.tags.map(Tag::name), it.reminder, false)) } ?: vm.message("任务已不存在"); consumed() } }
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { vm.messages.collectLatest { (message, undo) -> if (snack.showSnackbar(message, if (undo != null) "撤销" else null, withDismissAction = true) == SnackbarResult.ActionPerformed && undo != null) vm.perform(block = { vm.repo.complete(undo, false) }) } }
        var tagDialog by remember { mutableStateOf<Tag?>(null) }; var creatingTag by remember { mutableStateOf(false) }
        var hiddenToday by remember { mutableStateOf(settings.getString("hidden_today", null)) }
        val todayHidden = hiddenToday == now.toLocalDate().toString()
        fun finishToday() { hiddenToday = now.toLocalDate().toString(); settings.edit().putString("hidden_today", hiddenToday).apply() }
        fun reopenToday() { hiddenToday = null; settings.edit().remove("hidden_today").apply() }
        var exportConfirm by remember { mutableStateOf(false) }; var importBackup by remember { mutableStateOf<Backup?>(null) }
        val scope = rememberCoroutineScope()
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) scope.launch { try { withContext(Dispatchers.IO) { val text = BackupCodec.encode(BackupCodec.snapshot(vm.repo)); context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(text) } }; vm.message("备份已导出") } catch (_: Exception) { vm.message("导出失败，请重新选择文件位置") } }
        }
        val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch { try { importBackup = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { reader ->
                val buffer = CharArray(8192); val text = StringBuilder(); while (true) { val n = reader.read(buffer); if (n < 0) break; require(text.length + n <= 30_000_000); text.append(buffer, 0, n) }; BackupCodec.decode(text.toString()) } }
            } catch (_: Exception) { vm.message("无法恢复：备份损坏、引用不完整或版本不兼容；现有数据未改变") } }
        }
        val primary = page in listOf("今天", "收件箱", "标签")
        BackHandler(!primary && draft == null) { page = if (page.startsWith("tag:") || page in listOf("全部待办", "无标签")) "标签" else "今天" }
        fun add() { vm.draft(EditorDraft(Task(scheduleDate = if (page == "今天") now.toLocalDate().toString() else null), if (page == "标签") tags.filter { it.id == expandedTag }.map { it.name } else emptyList(), null, true)) }
        val title = if (page.startsWith("tag:")) tags.firstOrNull { it.id == page.removePrefix("tag:") }?.name ?: "标签" else page
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = { Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!primary) IconButton(onClick = { page = if (page.startsWith("tag:")) "标签" else "今天" }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                    if (page == "搜索") {
                        OutlinedTextField(query, { query = it }, placeholder = { Text("搜索标题、备注或标签") }, singleLine = true,
                            shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "清空搜索") } })
                    } else {
                        Text(title, Modifier.weight(1f).padding(start = 8.dp, top = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { page = "搜索" }) { Icon(Icons.Outlined.Search, "搜索") }
                    }
                    if (page != "设置") IconButton(onClick = { page = "设置" }) { Icon(Icons.Outlined.Settings, "设置") }
                }
                if (page == "今天" && !compactHeight) {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(now.format(DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINA)), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        TextButton(onClick = { page = "之后" }) { Text("之后"); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp)) }
                    }

                }
            } },
            bottomBar = { NavigationBar { listOf("今天" to Icons.Outlined.WbSunny, "收件箱" to Icons.Outlined.Inbox, "标签" to Icons.Outlined.Label).forEach { (name, icon) ->
                NavigationBarItem(selected = page == name || name == "标签" && (page.startsWith("tag:") || page == "全部待办" || page == "无标签"), onClick = { page = name }, icon = { Icon(icon, null) }, label = { Text(name) })
            } } },
            floatingActionButton = { if (page !in listOf("设置", "搜索", "已完成") && !(page == "今天" && todayHidden) && loaded && !error) FloatingActionButton(onClick = { add() }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.Add, "添加任务") } }
        ) { padding ->
            val body = Modifier.padding(padding).fillMaxSize()
            if (error) Column(body.padding(24.dp)) { Text("无法读取本地数据库，数据未被清除。"); Button(onClick = { vm.observe() }) { Text("重试") } }
            else if (!loaded) Box(body, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (page == "设置") SettingsPage(body, appearance, { appearance = it; settings.edit().putString("appearance", it).apply() }, reduce, { reduce = it; settings.edit().putBoolean("reduce", it).apply() }, lockTitle, { lockTitle = it; settings.edit().putBoolean("lock_title", it).apply() }, vm, statusTick, { exportConfirm = true }, { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, { page = "已完成" })
            else if (page == "今天" && todayHidden) Column(body.padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.NightsStay, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                Text("今天已收尾", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { reopenToday() }, modifier = Modifier.padding(top = 12.dp)) { Text("重新展开今天") }
            }
            else if (page == "标签") LazyColumn(body, contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 88.dp)) {
                item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("按标签整理", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick = { creatingTag = true }) { Text("新建标签") } } }
                (listOf("all" to "全部待办", "none" to "无标签") + tags.map { it.id to it.name }).forEach { (id, name) ->
                    val rows = tasks.filter { it.task.completedAt == null && (id == "all" || id == "none" && it.tags.isEmpty() || it.tags.any { tag -> tag.id == id }) }
                        .sortedWith(compareBy<TaskRecord> { it.task.scheduleDate ?: "9999" }.thenBy { it.task.createdAt }.thenBy { it.task.id })
                    item(key = "tag:$id") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { SectionLink(name, rows.size, if (id == "all") Icons.Outlined.Checklist else Icons.Outlined.Label, expandedTag == id) { expandedTag = if (expandedTag == id) null else id } }
                            if (id != "all" && id != "none") IconButton(onClick = { tagDialog = tags.firstOrNull { it.id == id } }) { Icon(Icons.Outlined.MoreHoriz, "管理标签 $name") }
                        }
                    }
                    if (expandedTag == id) {
                        if (rows.isEmpty()) item(key = "empty:$id") { Text("这里还没有待办", Modifier.padding(start = 40.dp, bottom = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(rows, key = { "tag:$id:task:${it.task.id}" }) { row ->
                            Box(if (reducedMotion) Modifier else Modifier.animateItem()) { TaskRow(row, now, !busy, { vm.complete(row.task, true) }, { vm.draft(EditorDraft(row.task, row.tags.map(Tag::name), row.reminder, false)) }) }
                        }
                    }
                }
                if (tags.isEmpty()) item { Text("用标签，把相关的事情放在一起。", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val filtered = remember(tasks, page, query, includeCompleted, now) {
                    val list = tasks.filter { r -> val t = r.task; when {
                        page == "已完成" -> t.completedAt != null
                        page == "搜索" -> query.isNotBlank() && (includeCompleted || t.completedAt == null) && (t.title.contains(query.trim(), true) || t.note.contains(query.trim(), true) || r.tags.any { it.name.contains(query.trim(), true) })
                        t.completedAt != null -> false
                        page == "今天" -> Rules.today(t, now.toLocalDate())
                        page == "收件箱" -> t.scheduleDate == null
                        page == "之后" -> !Rules.today(t, now.toLocalDate()) && Rules.futureDate(t, now.toLocalDate()) != null
                        page == "无标签" -> r.tags.isEmpty()
                        page.startsWith("tag:") -> r.tags.any { it.id == page.removePrefix("tag:") }
                        else -> true
                    } }
                    when (page) { "今天" -> list.sortedWith(Rules.todaySort(now)); "收件箱", "搜索" -> list.sortedWith(compareByDescending<TaskRecord> { it.task.createdAt }.thenBy { it.task.id }); "已完成" -> list.sortedByDescending { it.task.completedAt }; "之后" -> list.sortedWith(compareBy<TaskRecord> { Rules.futureDate(it.task, now.toLocalDate()) }.thenBy { it.task.createdAt }.thenBy { it.task.id }); else -> list.sortedWith(compareBy<TaskRecord> { it.task.scheduleDate ?: "9999" }.thenBy { it.task.createdAt }.thenBy { it.task.id }) }
                }
                var completedOpen by rememberSaveable { mutableStateOf(false) }
                var inboxHint by remember { mutableStateOf(!settings.getBoolean("inbox_hint_dismissed", false)) }
                val listStates = rememberSaveableStateHolder()
                // Build groups in the same composition as filtering. Lazy content can outlive
                // a navigation change; it must never regroup old rows using the new page.
                val groups = filtered.groupBy { when (page) { "今天" -> Rules.group(it.task, now); "之后" -> Rules.futureDate(it.task, now.toLocalDate()).orEmpty(); "已完成" -> it.task.completedOn.orEmpty(); else -> "" } }
                listStates.SaveableStateProvider(page) { LazyColumn(body, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp)) {
                    if (page == "今天" && compactHeight) item {
                        Text(now.format(DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINA)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            
                            
                            TextButton(onClick = { page = "之后" }) { Text("之后"); Icon(Icons.Outlined.ChevronRight, null) }
                        }
                    }
                    if (page == "搜索") item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(!includeCompleted, { includeCompleted = false }, label = { Text("待办") })
                            FilterChip(includeCompleted, { includeCompleted = true }, label = { Text("包含已完成") })
                            Spacer(Modifier.weight(1f))
                            if (query.isNotBlank()) Text("${filtered.size} 项结果", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (query.isBlank() && tags.isNotEmpty()) {
                            Text("按标签查找", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tags.take(8).forEach { tag -> AssistChip(onClick = { query = tag.name }, label = { Text("#${tag.name}") }) } }
                        }
                    }
                    if (page == "收件箱" && inboxHint) item { Row(verticalAlignment = Alignment.CenterVertically) { Text("还没决定哪天做的事，先放这里。", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); IconButton(onClick = { inboxHint = false; settings.edit().putBoolean("inbox_hint_dismissed", true).apply() }) { Icon(Icons.Outlined.Close, "关闭说明") } } }
                    if (filtered.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(20.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (page == "收件箱") Icons.Outlined.Inbox else Icons.Outlined.Checklist, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(when (page) { "今天" -> "今天没有安排。"; "收件箱" -> "暂时没有未安排的任务。"; "搜索" -> if (query.isBlank()) "从一个关键词开始" else "没有找到相关任务"; "已完成" -> "还没有已完成的任务。"; "之后" -> "暂时没有未来安排。"; else -> "这里还没有任务。" }, Modifier.padding(top = 12.dp))
                        if (page == "今天") { TextButton(onClick = { add() }) { Text("添加任务") }; TextButton(onClick = { page = "收件箱" }) { Text("查看收件箱") } }
                    } }
                    groups.forEach { (group, rows) ->
                        if (group.isNotEmpty() && !(page == "今天" && groups.size == 1 && group == "今天")) item(key = "group:$group") { Text(group, Modifier.padding(top = 20.dp, bottom = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (group == "已过截止") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(rows, key = { it.task.id }) { row -> Column(if (reducedMotion) Modifier else Modifier.animateItem(fadeInSpec = tween(180), placementSpec = tween(220), fadeOutSpec = tween(180))) { TaskRow(row, now, !busy, { vm.complete(row.task, row.task.completedAt == null) }, { vm.draft(EditorDraft(row.task, row.tags.map(Tag::name), row.reminder, false)) }) } }
                    }
                    if (page == "今天") {
                        val completed = tasks.filter { it.task.completedOn == now.toLocalDate().toString() }.sortedByDescending { it.task.completedAt }
                        if (completed.isNotEmpty()) item { TextButton(onClick = { completedOpen = !completedOpen }, modifier = Modifier.padding(top = 12.dp)) { Icon(if (completedOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null); Text("今天完成 ${completed.size} 项") } }
                        if (completedOpen) items(completed, key = { "completed:${it.task.id}" }) { row -> TaskRow(row, now, !busy, { vm.complete(row.task, false) }, { vm.draft(EditorDraft(row.task, row.tags.map(Tag::name), row.reminder, false)) }) }
                        item { TextButton(onClick = { finishToday() }, modifier = Modifier.padding(top = 12.dp)) { Icon(Icons.Outlined.NightsStay, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("结束一天") } }
                    }
                } }
            }
        }
        if (draft != null) EditorSheet(draft!!, tags, busy, vm)
        if (creatingTag || tagDialog != null) TagDialog(tagDialog, { creatingTag = false; tagDialog = null }, vm)
        if (exportConfirm) AlertDialog(onDismissRequest = { exportConfirm = false }, title = { Text("导出本地备份") }, text = { Text("备份是明文文件，包含任务与备注。请选择安全的位置并自行保管。") }, confirmButton = { TextButton(onClick = { exportConfirm = false; export.launch("todo-${now.toLocalDate()}.json") }) { Text("选择保存位置") } }, dismissButton = { TextButton(onClick = { exportConfirm = false }) { Text("取消") } })
        importBackup?.let { backup -> AlertDialog(onDismissRequest = { importBackup = null }, title = { Text("替换全部本地数据？") }, text = { Text("已校验备份：${backup.tasks.size} 项任务，${backup.tags.size} 个标签。恢复将覆盖当前全部任务与收尾记录，无法撤销。") }, confirmButton = { TextButton(enabled = !busy, onClick = { vm.perform("备份已恢复", { BackupCodec.restore(vm.repo, backup); vm.scheduler.cancelAll() }, { vm.draft(null); importBackup = null }) }) { Text("确认覆盖") } }, dismissButton = { TextButton(onClick = { importBackup = null }) { Text("取消") } }) }
    }
    }
}

@Composable private fun SectionLink(name: String, count: Int, icon: ImageVector, expanded: Boolean, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = click).semantics { stateDescription = if (expanded) "已展开" else "已收起" }, verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(name, Modifier.weight(1f).padding(horizontal = 12.dp)); Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant); Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.padding(start = 8.dp)) }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun TaskRow(record: TaskRecord, now: LocalDateTime, enabled: Boolean, complete: () -> Unit, edit: () -> Unit) {
    val t = record.task
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.Top) {
        val view = LocalView.current
        Checkbox(t.completedAt != null, { complete(); view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY) }, enabled = enabled,
            modifier = Modifier.padding(top = 4.dp).semantics { contentDescription = if (t.completedAt == null) "完成 ${t.title}" else "恢复 ${t.title}" })
        Column(Modifier.weight(1f).clickable(onClick = edit).padding(top = 12.dp, bottom = 12.dp)) {
            Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, textDecoration = if (t.completedAt != null) TextDecoration.LineThrough else null)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (t.dueDate != null) Meta("${if (Rules.overdue(t, now)) "已过截止 · " else ""}截止 ${dateLabel(t.dueDate, now.toLocalDate())}${t.dueTime?.let { " $it" } ?: ""}", Rules.overdue(t, now))
            if (t.scheduleDate != null && (t.scheduleDate != now.toLocalDate().toString() || t.scheduleTime != null)) Meta("${if (t.completedAt == null && t.scheduleDate < now.toLocalDate().toString()) "此前安排 · " else ""}安排 ${dateLabel(t.scheduleDate, now.toLocalDate())}${t.scheduleTime?.let { " $it" } ?: ""}")
            if (record.reminder != null) Meta("提醒 ${Rules.reminderLocal(t, record.reminder!!)?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) ?: "未设置"}")
            if (t.note.isNotBlank()) Row(Modifier.padding(top = 4.dp).semantics(mergeDescendants = true) { contentDescription = "有备注：${t.title}" }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Notes, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text("备注", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (record.tags.isNotEmpty()) Meta(record.tags.take(2).joinToString("  ") { "#${it.name}" } + if (record.tags.size > 2) "  +${record.tags.size - 2}" else "")
            }
            if (t.note.isNotBlank()) Text(t.note.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty(), Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .22f))
}
@Composable fun Meta(text: String, error: Boolean = false) { Text(text, Modifier.padding(top = 4.dp), fontSize = 13.sp, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
fun dateLabel(date: String, today: LocalDate = LocalDate.now()) = when (date) { today.toString() -> "今天"; today.plusDays(1).toString() -> "明天"; else -> LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyy年M月d日")) }
