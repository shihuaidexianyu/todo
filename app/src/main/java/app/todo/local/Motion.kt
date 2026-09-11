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
