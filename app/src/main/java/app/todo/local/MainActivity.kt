package app.todo.local

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
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
    private val sharedText = mutableStateOf<String?>(null)
    private val newTaskRequests = mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(); handleIntent(intent)
        setContent {
            TodoApp(requestedTask.value, sharedText.value, newTaskRequests.intValue,
                consumed = { requestedTask.value = null; intent.removeExtra("taskId") },
                sharedConsumed = { sharedText.value = null })
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { sharedText.value = it }
            ACTION_NEW_TASK -> newTaskRequests.intValue++
            else -> requestedTask.value = intent.getStringExtra("taskId")
        }
    }
    companion object { const val ACTION_NEW_TASK = "app.todo.local.action.NEW_TASK" }
}

private val light = lightColorScheme(
    primary = Color(0xFF202020), onPrimary = Color.White, primaryContainer = Color(0xFFE8E8E8), onPrimaryContainer = Color(0xFF202020), inversePrimary = Color(0xFFE8E8E8),
    secondary = Color(0xFF5C5C5C), onSecondary = Color.White, secondaryContainer = Color(0xFFEDEDED), onSecondaryContainer = Color(0xFF292929),
    tertiary = Color(0xFF606060), onTertiary = Color.White, tertiaryContainer = Color(0xFFEAEAEA), onTertiaryContainer = Color(0xFF252525),
    background = Color(0xFFFAFAFA), onBackground = Color(0xFF1A1A1A), surface = Color(0xFFFAFAFA), onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE5E5E5), onSurfaceVariant = Color(0xFF626262),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F5F5), surfaceContainer = Color(0xFFF0F0F0), surfaceContainerHigh = Color(0xFFE9E9E9), surfaceContainerHighest = Color(0xFFE2E2E2),
    surfaceDim = Color(0xFFD6D6D6), surfaceBright = Color.White, surfaceTint = Color(0xFF202020),
    inverseSurface = Color(0xFF252525), inverseOnSurface = Color(0xFFF5F5F5),
    error = Color(0xFFA94442), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF858585), outlineVariant = Color(0xFFE0E0E0), scrim = Color.Black)
private val dark = darkColorScheme(
    primary = Color(0xFFE8E8E8), onPrimary = Color(0xFF181818), primaryContainer = Color(0xFF303030), onPrimaryContainer = Color(0xFFFAFAFA), inversePrimary = Color(0xFF202020),
    secondary = Color(0xFFBDBDBD), onSecondary = Color(0xFF242424), secondaryContainer = Color(0xFF333333), onSecondaryContainer = Color(0xFFE5E5E5),
    tertiary = Color(0xFFC6C6C6), onTertiary = Color(0xFF262626), tertiaryContainer = Color(0xFF353535), onTertiaryContainer = Color(0xFFEAEAEA),
    background = Color(0xFF141414), onBackground = Color(0xFFEDEDED), surface = Color(0xFF141414), onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF393939), onSurfaceVariant = Color(0xFFB5B5B5),
    surfaceContainerLowest = Color(0xFF1C1C1C), surfaceContainerLow = Color(0xFF222222), surfaceContainer = Color(0xFF282828), surfaceContainerHigh = Color(0xFF303030), surfaceContainerHighest = Color(0xFF393939),
    surfaceDim = Color(0xFF141414), surfaceBright = Color(0xFF3D3D3D), surfaceTint = Color(0xFFE8E8E8),
    inverseSurface = Color(0xFFEDEDED), inverseOnSurface = Color(0xFF252525),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF909090), outlineVariant = Color(0xFF3D3D3D), scrim = Color.Black)
private val TodoTypography = Typography(
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp))
private val TodoShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(24.dp))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodoApp(requestedTask: String? = null, sharedText: String? = null, newTaskRequest: Int = 0, consumed: () -> Unit = {}, sharedConsumed: () -> Unit = {}) {
    val vm: TodoViewModel = viewModel()
    val context = LocalContext.current
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val settings = remember { context.getSharedPreferences("settings", 0) }
    var appearance by remember { mutableStateOf(settings.getString("appearance", "跟随系统")!!) }
    var reduce by remember { mutableStateOf(settings.getBoolean("reduce", false)) }
    var lockTitle by remember { mutableStateOf(settings.getBoolean("lock_title", false)) }
    var haptics by remember { mutableStateOf(settings.getBoolean("completion_haptics", true)) }
    var sound by remember { mutableStateOf(settings.getBoolean("completion_sound", true)) }
    var swipeRight by remember { mutableStateOf(settings.getString("swipe_right", "完成或恢复")!!) }
    var swipeLeft by remember { mutableStateOf(settings.getString("swipe_left", "安排到今天")!!) }
    val feedback = remember(context) { CompletionFeedback(context) }
    val feedbackView = LocalView.current
    val currentHaptics by rememberUpdatedState(haptics)
    val currentSound by rememberUpdatedState(sound)
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    LaunchedEffect(vm, feedback, feedbackView) {
        vm.completionFeedback.collect { completed ->
            if (feedbackView.hasWindowFocus()) feedback.play(feedbackView, completed, currentHaptics, currentSound)
        }
    }
    val isDark = appearance == "深色" || appearance == "跟随系统" && isSystemInDarkTheme()
    val reducedMotion = reduce || Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val window = (context as? android.app.Activity)?.window
    SideEffect { window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView).apply { isAppearanceLightStatusBars = !isDark; isAppearanceLightNavigationBars = !isDark } } }
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
    MaterialTheme(colorScheme = if (isDark) dark else light, typography = TodoTypography, shapes = TodoShapes) {
        val storedTasks by vm.tasks.collectAsState(); val tags by vm.tags.collectAsState()
        val presentation = rememberTaskPresentation(storedTasks, reducedMotion)
        val tasks = presentation.tasks
        val loaded by vm.loaded.collectAsState(); val error by vm.readError.collectAsState(); val busy by vm.busy.collectAsState(); val draft by vm.editor.collectAsState()
        var page by rememberSaveable { mutableStateOf("今天") }
        var listTab by rememberSaveable { mutableStateOf("全部") }
        var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
        var listQuery by rememberSaveable { mutableStateOf("") }
        // A selected tag may be deleted (or merged on rename); drop the filter instead of sticking on a missing id.
        LaunchedEffect(tags, selectedTag) { if (selectedTag != null && selectedTag != "none" && tags.none { it.id == selectedTag }) selectedTag = null }
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
        var tagMenu by remember { mutableStateOf(false) }
        var hiddenToday by remember { mutableStateOf(settings.getString("hidden_today", null)) }
        val todayHidden = hiddenToday == now.toLocalDate().toString()
        fun finishToday() { hiddenToday = now.toLocalDate().toString(); settings.edit().putString("hidden_today", hiddenToday).apply() }
        fun reopenToday() { hiddenToday = null; settings.edit().remove("hidden_today").apply() }
        val primary = page in listOf("今天", "收件箱", "清单")
        BackHandler(!primary && draft == null) { page = "今天" }
        fun add() { vm.draft(EditorDraft(Task(scheduleDate = if (page == "今天") now.toLocalDate().toString() else null), if (page == "清单" && selectedTag != null && selectedTag != "none") tags.filter { it.id == selectedTag }.map { it.name } else emptyList(), null, true)) }
        // Launcher shortcut ("新建任务"): reuse add() so the draft matches the current page; never clobber an open draft.
        LaunchedEffect(newTaskRequest) { if (newTaskRequest > 0 && draft == null) add() }
        // System share sheet: first line becomes the title, the rest becomes the note; never clobber an open draft.
        LaunchedEffect(sharedText, loaded) {
            val text = sharedText ?: return@LaunchedEffect
            if (!loaded || draft != null) return@LaunchedEffect
            val lines = text.trim().lines()
            vm.draft(EditorDraft(Task(title = lines.first().take(300), note = lines.drop(1).joinToString("\n").trim().take(5000)), emptyList(), null, true))
            sharedConsumed()
        }
        val title = page
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = { Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!primary) IconButton(onClick = { page = "今天" }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                    // 今天 keeps the hero title; other pages get a compact bar so list content starts higher.
                    Text(title, Modifier.weight(1f).padding(start = 8.dp, top = if (page == "今天") 12.dp else 2.dp, bottom = if (page == "今天") 8.dp else 2.dp), style = if (page == "今天") MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
                    if (page == "清单") Box {
                        IconButton(onClick = { tagMenu = true }) { Icon(Icons.Outlined.MoreHoriz, "管理标签") }
                        DropdownMenu(expanded = tagMenu, onDismissRequest = { tagMenu = false }) {
                            if (tags.isEmpty()) DropdownMenuItem(text = { Text("还没有标签") }, enabled = false, onClick = {})
                            tags.forEach { tag -> DropdownMenuItem(text = { Text(tag.name) }, onClick = { tagMenu = false; tagDialog = tag }) }
                        }
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
            bottomBar = { NavigationBar { listOf("今天" to Icons.Outlined.WbSunny, "收件箱" to Icons.Outlined.Inbox, "清单" to Icons.Outlined.Checklist).forEach { (name, icon) ->
                NavigationBarItem(selected = page == name, onClick = { page = name }, icon = { Icon(icon, null) }, label = { Text(name) })
            } } },
            floatingActionButton = { if (page != "设置" && !(page == "清单" && listTab == "已完成") && !(page == "今天" && todayHidden) && loaded && !error) FloatingActionButton(onClick = { add() }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Outlined.Add, "添加任务") } }
        ) { padding ->
            val body = Modifier.padding(padding).fillMaxSize()
            if (error) Column(body.padding(24.dp)) { Text("无法读取本地数据库，数据未被清除。"); Button(onClick = { vm.observe() }) { Text("重试") } }
            else if (!loaded) Box(body, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (page == "设置") SettingsPage(body, appearance, { appearance = it; settings.edit().putString("appearance", it).apply() }, reduce, { reduce = it; settings.edit().putBoolean("reduce", it).apply() }, lockTitle, { lockTitle = it; settings.edit().putBoolean("lock_title", it).apply() }, vm, statusTick, haptics, { haptics = it; settings.edit().putBoolean("completion_haptics", it).apply() }, sound, { sound = it; settings.edit().putBoolean("completion_sound", it).apply() }, swipeRight, { swipeRight = it; settings.edit().putString("swipe_right", it).apply() }, swipeLeft, { swipeLeft = it; settings.edit().putString("swipe_left", it).apply() })
            else if (page == "今天" && todayHidden) Column(body.padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.NightsStay, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                Text("今天已收尾", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { reopenToday() }, modifier = Modifier.padding(top = 12.dp)) { Text("重新展开今天") }
            }
            else if (page == "清单") {
                val list = remember(tasks, listTab, selectedTag, listQuery) {
                    tasks.filter { r ->
                        val t = r.task
                        (if (listTab == "全部") t.completedAt == null else t.completedAt != null) &&
                            (selectedTag == null || (selectedTag == "none" && r.tags.isEmpty()) || r.tags.any { it.id == selectedTag }) &&
                            (listQuery.isBlank() || t.title.contains(listQuery.trim(), true) || t.note.contains(listQuery.trim(), true) || r.tags.any { it.name.contains(listQuery.trim(), true) })
                    }.sortedWith(if (listTab == "全部") compareBy<TaskRecord> { it.task.scheduleDate ?: "9999" }.thenBy { it.task.createdAt }.thenBy { it.task.id } else compareByDescending<TaskRecord> { it.task.completedAt }.thenBy { it.task.id })
                }
                LazyColumn(body, contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 88.dp)) {
                    item { OutlinedTextField(listQuery, { listQuery = it }, placeholder = { Text("搜索标题、备注或标签") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp), leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (listQuery.isNotEmpty()) IconButton(onClick = { listQuery = "" }) { Icon(Icons.Outlined.Close, "清空搜索") } }) }
                    item { TabRow(selectedTabIndex = if (listTab == "全部") 0 else 1, modifier = Modifier.padding(top = 8.dp)) { Tab(selected = listTab == "全部", onClick = { listTab = "全部" }, text = { Text("全部") }); Tab(selected = listTab == "已完成", onClick = { listTab = "已完成" }, text = { Text("已完成") }) } }
                    item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag -> FilterChip(selected = selectedTag == tag.id, onClick = { selectedTag = if (selectedTag == tag.id) null else tag.id }, label = { Text(tag.name) }) }
                        FilterChip(selected = selectedTag == "none", onClick = { selectedTag = if (selectedTag == "none") null else "none" }, label = { Text("无标签") })
                        AssistChip(onClick = { creatingTag = true }, label = { Text("新建标签") }, leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)) })
                    } }
                    if (list.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Checklist, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(when { listQuery.isNotBlank() -> "没有找到相关任务"; listTab == "已完成" -> "还没有已完成的任务。"; selectedTag == "none" -> "还没有无标签的任务。"; selectedTag != null -> "这个标签下还没有待办。"; else -> "还没有待办。" }, Modifier.padding(top = 12.dp))
                    } }
                    items(list, key = { it.task.id }) { row -> Column(if (reducedMotion) Modifier else Modifier.animateItem(fadeInSpec = tween(160), placementSpec = tween(280, easing = FastOutSlowInEasing), fadeOutSpec = tween(100))) { SwipeableTaskRow(row, now, !busy, { vm.complete(row.task, row.task.completedAt == null) }, { vm.draft(EditorDraft(row.task, row.tags.map(Tag::name), row.reminder, false)) }, presentation.targets[row.task.id], scheduleToday = { vm.scheduleToday(row) }, reschedule = { vm.scheduleTomorrow(row) }, startAction = swipeRight, endAction = swipeLeft) } }
                }
            } else {
                val filtered = remember(tasks, page, now) {
                    val list = tasks.filter { r -> val t = r.task; when {
                        t.completedAt != null -> false
                        page == "今天" -> Rules.today(t, now.toLocalDate())
                        page == "收件箱" -> t.scheduleDate == null && r.tags.isEmpty()
                        page == "之后" -> !Rules.today(t, now.toLocalDate()) && Rules.futureDate(t, now.toLocalDate()) != null
                        else -> true
                    } }
                    when (page) { "今天" -> list.sortedWith(Rules.todaySort(now)); "收件箱" -> list.sortedWith(compareByDescending<TaskRecord> { it.task.createdAt }.thenBy { it.task.id }); "之后" -> list.sortedWith(compareBy<TaskRecord> { Rules.futureDate(it.task, now.toLocalDate()) }.thenBy { it.task.createdAt }.thenBy { it.task.id }); else -> list }
                }
                var completedOpen by rememberSaveable { mutableStateOf(false) }
                var inboxHint by remember { mutableStateOf(!settings.getBoolean("inbox_hint_dismissed", false)) }
                val listStates = rememberSaveableStateHolder()
                // Build groups in the same composition as filtering. Lazy content can outlive
                // a navigation change; it must never regroup old rows using the new page.
                val groups = filtered.groupBy { when (page) { "今天" -> Rules.group(it.task, now); "之后" -> Rules.futureDate(it.task, now.toLocalDate()).orEmpty(); else -> "" } }
                listStates.SaveableStateProvider(page) { LazyColumn(body, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp)) {
                    if (page == "今天" && compactHeight) item {
                        Text(now.format(DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINA)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            
                            
                            TextButton(onClick = { page = "之后" }) { Text("之后"); Icon(Icons.Outlined.ChevronRight, null) }
                        }
                    }
                    if (page == "收件箱" && inboxHint) item { Row(verticalAlignment = Alignment.CenterVertically) { Text("还没决定哪天做的事，先放这里。", Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); IconButton(onClick = { inboxHint = false; settings.edit().putBoolean("inbox_hint_dismissed", true).apply() }) { Icon(Icons.Outlined.Close, "关闭说明") } } }
                    if (filtered.isEmpty()) item {
                        // An emptied Today after finishing work is a win, not a blank list; acknowledge it and lead to 结束一天 below.
                        val doneToday = tasks.count { it.task.completedOn == now.toLocalDate().toString() }
                        val celebrated = page == "今天" && doneToday > 0
                        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(when { page == "收件箱" -> Icons.Outlined.Inbox; celebrated -> Icons.Outlined.Celebration; else -> Icons.Outlined.Checklist }, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(when { celebrated -> "今天的都完成了。"; page == "今天" -> "今天没有安排。"; page == "收件箱" -> "还没有未安排且未打标签的事。"; page == "之后" -> "暂时没有未来安排。"; else -> "这里还没有任务。" }, Modifier.padding(top = 12.dp))
                        if (celebrated) Text("剩下的时间归你。", Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (page == "今天" && !celebrated) { TextButton(onClick = { add() }) { Text("添加任务") }; TextButton(onClick = { page = "收件箱" }) { Text("查看收件箱") } }
                    } }
                    groups.forEach { (group, rows) ->
                        if (group.isNotEmpty() && !(page == "今天" && groups.size == 1 && group == "今天")) item(key = "group:$group") { Text(group, Modifier.padding(top = 20.dp, bottom = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (group == "已过截止") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(rows, key = { it.task.id }) { row -> Column(if (reducedMotion) Modifier else Modifier.animateItem(fadeInSpec = tween(160), placementSpec = tween(280, easing = FastOutSlowInEasing), fadeOutSpec = tween(100))) { SwipeableTaskRow(row, now, !busy, { vm.complete(row.task, row.task.completedAt == null) }, { vm.draft(EditorDraft(row.task, row.tags.map(Tag::name), row.reminder, false)) }, presentation.targets[row.task.id], scheduleToday = { vm.scheduleToday(row) }, reschedule = { vm.scheduleTomorrow(row) }, startAction = swipeRight, endAction = swipeLeft) } }
                    }
                    if (page == "今天") {
                        val completed = tasks.filter { it.task.completedOn == now.toLocalDate().toString() }.sortedByDescending { it.task.completedAt }
                        if (completed.isNotEmpty()) item { TextButton(onClick = { completedOpen = !completedOpen }, modifier = Modifier.padding(top = 12.dp)) { Icon(if (completedOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null); Text("今天完成 ${completed.size} 项") } }
                        if (completedOpen) items(completed, key = { "completed:${it.task.id}" }) { row -> SwipeableTaskRow(row, now, !busy, { vm.complete(row.task, false) }, { vm.draft(EditorDraft(row.task, row.tags.map(Tag::name), row.reminder, false)) }, presentation.targets[row.task.id], scheduleToday = { vm.scheduleToday(row) }, reschedule = { vm.scheduleTomorrow(row) }, startAction = swipeRight, endAction = swipeLeft) }
                        item { TextButton(onClick = { finishToday() }, modifier = Modifier.padding(top = 12.dp)) { Icon(Icons.Outlined.NightsStay, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("结束一天") } }
                    }
                } }
            }
        }
        if (draft != null) EditorSheet(draft!!, tags, busy, vm)
        if (creatingTag || tagDialog != null) TagDialog(tagDialog, { creatingTag = false; tagDialog = null }, vm)
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TaskRow(record: TaskRecord, now: LocalDateTime, enabled: Boolean, complete: () -> Unit, edit: () -> Unit, completionTarget: Boolean? = null) {
    val t = record.task
    val checked = completionTarget ?: (t.completedAt != null)
    val reduced = LocalReducedMotion.current
    val opacity by animateFloatAsState(if (completionTarget == true) .45f else 1f, tween(if (reduced) 0 else 160), label = "completion fade")
    val scale by animateFloatAsState(if (completionTarget == true) .94f else 1f, tween(if (reduced) 0 else 120, easing = FastOutSlowInEasing), label = "check settle")
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).graphicsLayer { alpha = opacity }, verticalAlignment = Alignment.Top) {
        Checkbox(checked, { complete() }, enabled = enabled && completionTarget == null,
            modifier = Modifier.padding(top = 12.dp).graphicsLayer { scaleX = scale; scaleY = scale }.semantics { contentDescription = if (t.completedAt == null) "完成 ${t.title}" else "恢复 ${t.title}" })
        Column(Modifier.weight(1f).clickable(enabled = completionTarget == null, onClick = edit).padding(top = 24.dp, bottom = 24.dp)) {
            Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, textDecoration = if (checked) TextDecoration.LineThrough else null)
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
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SwipeableTaskRow(record: TaskRecord, now: LocalDateTime, enabled: Boolean, complete: () -> Unit, edit: () -> Unit, completionTarget: Boolean? = null, scheduleToday: () -> Unit, reschedule: () -> Unit, startAction: String, endAction: String) {
    val completed = record.task.completedAt != null
    // The dismiss state is remembered per item key while lambdas/row are recreated on every emission;
    // without rememberUpdatedState a swipe could act on a stale revision and fail the optimistic-lock check.
    val currentComplete by rememberUpdatedState(complete)
    val currentScheduleToday by rememberUpdatedState(scheduleToday)
    val currentReschedule by rememberUpdatedState(reschedule)
    val currentActive by rememberUpdatedState(enabled && completionTarget == null)
    val currentCompleted by rememberUpdatedState(completed)
    val currentStart by rememberUpdatedState(startAction)
    val currentEnd by rememberUpdatedState(endAction)
    val currentScheduleDate by rememberUpdatedState(record.task.scheduleDate)
    val currentToday by rememberUpdatedState(now.toLocalDate().toString())
    val currentTomorrow by rememberUpdatedState(now.toLocalDate().plusDays(1).toString())
    // Scheduling actions never apply to a completed task or one already on the target day; 关闭 disarms the direction.
    fun actionFor(direction: SwipeToDismissBoxValue): String? = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> currentStart
        SwipeToDismissBoxValue.EndToStart -> currentEnd
        else -> null
    }?.takeUnless { it == "关闭" || (it != "完成或恢复" && currentCompleted) || (it == "安排到今天" && currentScheduleDate == currentToday) || (it == "安排到明天" && currentScheduleDate == currentTomorrow) }
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.Settled) true else {
            if (currentActive) when (actionFor(value)) {
                "完成或恢复" -> currentComplete()
                "安排到今天" -> currentScheduleToday()
                "安排到明天" -> currentReschedule()
            }
            false
        }
    })
    val view = LocalView.current
    // A light tick the moment the drag crosses the action threshold (progress ≈ 0.5 at the 50% positional threshold).
    LaunchedEffect(state) {
        var armed = false
        snapshotFlow { state.dismissDirection.let { it != SwipeToDismissBoxValue.Settled && state.progress >= .5f && actionFor(it) != null } }.collect { isArmed ->
            if (isArmed && !armed) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            armed = isArmed
        }
    }
    SwipeToDismissBox(state, backgroundContent = {
        val action = actionFor(state.dismissDirection)
        if (action != null) {
            val completing = action == "完成或恢复"
            // progress ≈ 0 at rest, 0.5 at the 50% positional threshold: the badge grows and fades in as the action arms.
            val ramp = (state.progress * 2f).coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize().clearAndSetSemantics { }, contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd) {
                Box(Modifier.padding(horizontal = 16.dp).size(44.dp)
                    .graphicsLayer { alpha = ramp; val s = .5f + .5f * ramp; scaleX = s; scaleY = s }
                    .background(if (completing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(if (completing) { if (currentCompleted) Icons.AutoMirrored.Outlined.Undo else Icons.Outlined.Check } else if (action == "安排到今天") Icons.Outlined.Today else Icons.Outlined.Event,
                        if (completing) { if (currentCompleted) "恢复" else "完成" } else action,
                        Modifier.size(22.dp),
                        tint = if (completing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }) {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) { TaskRow(record, now, enabled, complete, edit, completionTarget) }
    }
}
@Composable fun Meta(text: String, error: Boolean = false) { Text(text, Modifier.padding(top = 4.dp), fontSize = 13.sp, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
fun dateLabel(date: String, today: LocalDate = LocalDate.now()) = when (date) { today.toString() -> "今天"; today.plusDays(1).toString() -> "明天"; else -> LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyy年M月d日")) }
