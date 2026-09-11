package app.todo.local

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WorkflowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Before fun ready() {
        val app = ui.activity.application as TodoApplication
        app.getSharedPreferences("settings", 0).edit().remove("hidden_today").apply()
        ui.activityRule.scenario.recreate()
        runBlocking { app.repository.gate.lock(); try { app.database.clearAllTables() } finally { app.repository.gate.unlock() } }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("今天没有安排。").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun add(title: String) {
        ui.onNodeWithContentDescription("添加任务").performClick()
        ui.onNodeWithText("标题").performTextInput(title)
        ui.onNodeWithText("保存").performScrollTo().performClick()
        ui.waitUntil(8_000) { ui.onAllNodesWithText("标题").fetchSemanticsNodes().isEmpty() }
    }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        Thread.sleep(500) // Allow system window/screenshot compositor transitions to settle.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val path = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap -> File(path, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
    @Test fun addCompleteUndoRescheduleAndCloseDay() {
        screenshot("01-today-empty-light")
        add("整理本周的阅读笔记")
        ui.onNodeWithText("整理本周的阅读笔记").assertIsDisplayed()
        screenshot("02-today-task-light")
        ui.onNodeWithContentDescription("完成 整理本周的阅读笔记").performClick()
        ui.waitUntil(8_000) { ui.onAllNodesWithText("撤销").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("撤销").performClick()
        ui.waitUntil(8_000) { ui.onAllNodesWithText("整理本周的阅读笔记").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("整理本周的阅读笔记").assertIsDisplayed()
        val repo = (ui.activity.application as TodoApplication).repository
        val before = runBlocking { repo.dao.records() }
        ui.onNodeWithText("结束一天").performScrollTo().performClick()
        ui.onNodeWithText("今天已收尾").assertIsDisplayed()
        ui.onNodeWithText("整理本周的阅读笔记").assertDoesNotExist()
        Assert.assertEquals(before, runBlocking { repo.dao.records() })
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("重新展开今天").assertIsDisplayed()
        ui.onNodeWithText("标签").performClick()
        ui.onNodeWithText("全部待办").performClick()
        ui.onNodeWithText("整理本周的阅读笔记").assertIsDisplayed()
        ui.onNodeWithText("今天").performClick()
        screenshot("03-today-closed")
        ui.onNodeWithText("重新展开今天").performClick()
        ui.onNodeWithText("整理本周的阅读笔记").assertIsDisplayed()
        Assert.assertEquals(before, runBlocking { repo.dao.records() })
        ui.onNodeWithText("之后").performClick()
        ui.onNodeWithText("暂时没有未来安排。").assertIsDisplayed()
    }

    @Test fun inboxTagDoesNotScheduleAndSearchFindsNotes() {
        ui.onNodeWithText("收件箱").performClick()
        add("买一盏阅读灯")
        ui.onNodeWithText("买一盏阅读灯").performClick()
        ui.onNodeWithText("备注").performTextInput("暖光，放在书桌旁")
        ui.onAllNodesWithText("标签").onLast().performClick()
        ui.onNodeWithText("搜索或创建标签").performTextInput("生活")
        ui.onNodeWithText("创建标签“生活”").performClick()
        ui.onNodeWithText("完成选择").performClick()
        ui.onNodeWithText("保存").performScrollTo().performClick()
        ui.waitUntil(8_000) { ui.onAllNodesWithText("标题").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("买一盏阅读灯").assertIsDisplayed()
        ui.onNodeWithText("#生活").assertIsDisplayed()
        ui.onNodeWithContentDescription("有备注：买一盏阅读灯", useUnmergedTree = true).assertExists()
        ui.onNodeWithText("暖光，放在书桌旁").assertIsDisplayed()
        screenshot("05-inbox-light")
        ui.onNodeWithContentDescription("搜索").performClick()
        ui.onNodeWithText("搜索标题、备注或标签").performTextInput("暖光")
        ui.onNodeWithText("买一盏阅读灯").assertIsDisplayed()
    }
    @Test fun discardEditorAndDarkTheme() {
        ui.onNodeWithContentDescription("添加任务").performClick()
        ui.onNodeWithText("标题").performTextInput("不应保存的草稿")
        ui.onNodeWithContentDescription("关闭编辑").performClick()
        ui.onNodeWithText("放弃").performClick()
        ui.onNodeWithText("不应保存的草稿").assertDoesNotExist()
        ui.onNodeWithContentDescription("设置").performClick()
        ui.onNodeWithText("深色").performClick()
        screenshot("06-settings-dark")
        ui.onNodeWithText("今天").performClick()
        add("留一点时间，读完手边的书")
        screenshot("07-today-dark")
        // Leave test settings as they were for repeatability.
        ui.onNodeWithContentDescription("设置").performClick(); ui.onNodeWithText("跟随系统").performClick()
    }
    @Test fun navigationWithMixedDatesAndCompletionHistory() {
        val repo = (ui.activity.application as TodoApplication).repository
        val today = java.time.LocalDate.now()
        val active = Task(title = "今天的任务", scheduleDate = today.toString())
        val future = Task(title = "明天的任务", scheduleDate = today.plusDays(1).toString())
        val done = Task(title = "已经完成的任务")
        runBlocking {
            repo.save(active, emptyList(), null, true)
            repo.save(future, emptyList(), null, true)
            repo.save(done, emptyList(), null, true)
            repo.complete(done.id, true)
        }
        ui.waitUntil(8_000) { ui.onAllNodesWithText(active.title).fetchSemanticsNodes().isNotEmpty() }
        repeat(6) {
            ui.onNodeWithText("之后").performClick()
            ui.onNodeWithText(future.title).assertIsDisplayed()
            ui.onNodeWithText(active.title).assertDoesNotExist()
            ui.onNodeWithText("今天").performClick()
            ui.onNodeWithContentDescription("设置").performClick()
            ui.onNodeWithText("已完成").performClick()
            ui.onNodeWithText(done.title).assertIsDisplayed()
            ui.onNodeWithText(active.title).assertDoesNotExist()
            ui.onNodeWithText("今天").performClick()
        }
        screenshot("11-navigation-regression")
    }
    @Test fun quickDueInlineTagsAndQuietSave() {
        ui.onNodeWithContentDescription("添加任务").performClick()
        ui.onNodeWithText("标题").performTextInput("快捷截止")
        ui.onNodeWithText("标题").assertIsFocused()
        ui.onNodeWithText("标题").performTextInput("任务")
        ui.onNodeWithText("标题").assertIsFocused()
        ui.onNodeWithText("今天截止").performScrollTo().performClick()
        ui.onAllNodesWithText("标签").onLast().performScrollTo().performClick()
        ui.onNodeWithText("搜索或创建标签").performScrollTo().performTextInput("工作")
        ui.onNodeWithText("创建标签“工作”").performScrollTo().performClick()
        ui.onNodeWithText("标题").assertExists()
        ui.onNodeWithText("完成选择").performScrollTo().performClick()
        ui.onNodeWithText("保存").performScrollTo().performClick()
        ui.waitUntil(8_000) { ui.onAllNodesWithText("标题").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("已保存").assertDoesNotExist()
        val repo = (ui.activity.application as TodoApplication).repository
        val saved = runBlocking { repo.dao.records().single() }
        Assert.assertEquals(java.time.LocalDate.now().toString(), saved.task.dueDate)
        ui.onNodeWithText("标签").performClick()
        ui.onNodeWithText("工作").performClick()
        ui.onNodeWithText("快捷截止任务").assertIsDisplayed()
        ui.onNodeWithText("按标签整理").assertIsDisplayed()
        screenshot("12-tags-inline")
        ui.onNodeWithText("工作").performClick()
        ui.onNodeWithText("快捷截止任务").assertDoesNotExist()
        ui.onNodeWithContentDescription("搜索").performClick()
        ui.onNodeWithText("从一个关键词开始").assertIsDisplayed()
        ui.onNodeWithText("搜索标题、备注或标签").performTextInput("快捷")
        ui.onNodeWithText("快捷截止任务").assertIsDisplayed()
        screenshot("13-search")
        ui.onNodeWithContentDescription("清空搜索").performClick()
        ui.onNodeWithText("从一个关键词开始").assertIsDisplayed()
    }
    @Test fun draftSurvivesRotationAndLargeTextKeepsSaveReachable() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) { automation.executeShellCommand(command).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } } }
        try {
            shell("settings put system font_scale 2.0")
            ui.waitForIdle()
            ui.onNodeWithContentDescription("添加任务").performClick()
            ui.onNodeWithText("标题").performTextInput("大字体下保存一条较长的待办任务")
            ui.activityRule.scenario.recreate()
            ui.onNodeWithText("大字体下保存一条较长的待办任务").assertExists()
            ui.onNodeWithText("保存").performScrollTo().assertIsDisplayed()
            screenshot("08-editor-200-percent")
            ui.onNodeWithText("保存").performClick()
            ui.waitUntil(8_000) { ui.onAllNodesWithText("标题").fetchSemanticsNodes().isEmpty() }
            screenshot("09-today-200-percent")
            ui.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ui.waitForIdle()
            ui.waitUntil(8_000) { ui.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            Thread.sleep(800)
            ui.onNodeWithContentDescription("添加任务").assertIsDisplayed()
            ui.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("大字体下保存一条较长的待办任务"))
            ui.onNodeWithText("大字体下保存一条较长的待办任务").assertIsDisplayed()
            screenshot("10-landscape-200-percent")
        } finally { shell("settings put system font_scale 1.0"); ui.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}
