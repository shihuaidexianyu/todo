package app.todo.local

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareCaptureTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<TodoApplication>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun clean() {
        app.getSharedPreferences("drafts", 0).edit().clear().commit()
        app.getSharedPreferences("settings", 0).edit().remove("hidden_today").apply()
        runBlocking { app.repository.gate.lock(); try { app.database.clearAllTables() } finally { app.repository.gate.unlock() } }
    }
    @After fun tearDown() {
        scenario?.close()
        app.getSharedPreferences("drafts", 0).edit().clear().commit()
    }

    @Test fun sharedTextPrefillsEditorAndSaves() {
        scenario = ActivityScenario.launch(Intent(app, MainActivity::class.java)
            .setAction(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "分享来的标题\n备注第二行"))
        compose.waitUntil(10_000) { compose.onAllNodesWithText("新建任务").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("分享来的标题").assertExists()
        compose.onNodeWithText("保存").assertIsDisplayed().performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("新建任务").fetchSemanticsNodes().isEmpty() }
        val saved = runBlocking { app.repository.dao.tasks().single() }
        Assert.assertEquals("分享来的标题", saved.title)
        Assert.assertEquals("备注第二行", saved.note)
        Assert.assertNull(saved.scheduleDate)
    }

    @Test fun newTaskShortcutOpensBlankEditor() {
        scenario = ActivityScenario.launch(Intent(app, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_TASK))
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction() and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("标题").assertExists()
        Assert.assertTrue(runBlocking { app.repository.dao.tasks().isEmpty() })
    }
}
