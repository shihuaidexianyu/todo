package app.todo.local

import android.app.Activity
import android.view.Window
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

val LocalReducedMotion = staticCompositionLocalOf { false }

data class TaskPresentation(val tasks: List<TaskRecord>, val targets: Map<String, Boolean> = emptyMap())

/** Keep the saved row briefly visible so the check resolves before the list closes its gap. */
@Composable
fun rememberTaskPresentation(records: List<TaskRecord>, reduced: Boolean): TaskPresentation {
    var shown by remember { mutableStateOf(TaskPresentation(records)) }
    LaunchedEffect(records, reduced) {
        val previous = shown.tasks.associateBy { it.task.id }
        val changed = records.mapNotNull { row ->
            previous[row.task.id]?.let { old ->
                val checked = row.task.completedAt != null
                if ((old.task.completedAt != null) != checked) row.task.id to checked else null
            }
        }.toMap()
        if (!reduced && changed.isNotEmpty()) {
            shown = TaskPresentation(records.map { if (it.task.id in changed) previous.getValue(it.task.id) else it }, changed)
            kotlinx.coroutines.delay(180)
        }
        shown = TaskPresentation(records)
    }
    return if (reduced) TaskPresentation(records) else shown
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoBottomSheet(onDismissRequest: () -> Unit, sheetState: SheetState,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() }, content: @Composable ColumnScope.() -> Unit) {
    if (!LocalReducedMotion.current) ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState, dragHandle = dragHandle, content = content)
    else Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setWindowAnimations(0) }
        Box(Modifier.fillMaxWidth().fillMaxHeight(.94f).statusBarsPadding(), contentAlignment = Alignment.BottomCenter) {
            Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding()) { dragHandle?.invoke(); content() }
            }
        }
    }
}
