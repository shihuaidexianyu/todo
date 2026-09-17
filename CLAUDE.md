# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`todo` — a lightweight, fully offline Android todo app (Kotlin, Jetpack Compose Material 3, Room, AlarmManager). No accounts, no network permission. Package `app.todo.local`, minSdk 29, targetSdk 36, JDK 17+. The product spec (`todo-product-spec.md`, Chinese) is the acceptance baseline; its stated priority order when things conflict: **don't lose data > correct time/reminder semantics > direct operation > clear UI > polished animation**. Note the spec predates three shipped changes: "结束一天" is now collapse-today-only (no forced rescheduling), search/tags/completed all live in the single 「清单」 bottom-nav page, and the in-app JSON backup/restore was removed (2026-09, owner decision: less maintenance) — only the OS-level backup rules remain (`android:allowBackup="false"` + `res/xml/backup_rules.xml`/`data_extraction_rules.xml`), so uninstalling loses all data.

All user-facing strings, validation messages, and docs are Simplified Chinese — keep new strings in Chinese. `TodoViewModel.perform()` decides whether an exception message is user-displayable by checking for non-Latin-1 characters; deliberate validation errors must be Chinese `require`/`check` messages to surface.

## Commands (Windows PowerShell; use `./gradlew` equivalents elsewhere)

Run the `.ps1` scripts with **PowerShell 7 (`pwsh`)** — they are UTF-8 *without* BOM, and Windows PowerShell 5.1 (`powershell.exe`) misreads the Chinese strings and fails with parser errors.

```powershell
.\scripts\build.ps1                  # assembleDebug + testDebugUnitTest + lintDebug
.\scripts\build-release.ps1          # release APK/AAB + tests + lint + signature/hash verification
.\scripts\test-device.ps1            # instrumented tests on a connected device/emulator
python scripts\generate-completion-sound.py   # regenerate app/src/main/res/raw/complete.wav
```

Gradle direct:

```powershell
.\gradlew.bat testDebugUnitTest                                              # all JVM unit tests
.\gradlew.bat testDebugUnitTest --tests "app.todo.local.RulesTest"           # one class
.\gradlew.bat testDebugUnitTest --tests "app.todo.local.RulesTest.dstGapUsesFirstValidMinute"  # one method
.\gradlew.bat connectedDebugAndroidTest                                      # all instrumented tests
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.todo.local.WorkflowTest.addCompleteUndoRescheduleAndCloseDay
```

Instrumented tests **wipe the app's data and toggle its notification/exact-alarm permissions** — run only on a dedicated emulator/test device. `scripts\smoke-release.ps1 -Serial <adb-serial>` and `scripts\regression-release.ps1 -Serial <adb-serial>` drive an installed release build via uiautomator.

Release flow notes: `build-release.ps1` auto-creates the signing key in `.signing/` on first run (git-ignored, ACL-locked) — never regenerate it, or upgrades over GitHub releases break; back it up separately. `versionCode`/`versionName` live in `app/build.gradle.kts`, and the release scripts + `README.md` + `docs/` hardcode the current version (e.g. `artifacts/release-validation/1.1.2/`) — bump all together.

## Architecture

Single module, seven Kotlin files under `app/src/main/java/app/todo/local/`, no subpackages. The layering that matters:

**Data.kt — entities, DAO, `Repository`, and `Rules`.** Room DB `TodoDatabase` (version 1, schema exported to `app/schemas/` — regenerate when entities change). All multi-step writes go through `Repository` methods, which serialize on a single `Mutex` (`gate`); `ReminderScheduler` takes the same mutex while delivering alarms, so a notification can never race a mutation. `Repository` does optimistic concurrency via the `revision` column — stale edits fail with a Chinese `check` message. It takes an injectable `Clock` (fixed clocks in tests). `Rules` is a pure-function object holding all date semantics: dates are ISO `yyyy-MM-dd` strings, times `HH:mm` (seconds must be 0, time requires a date). Three independent time concepts — schedule （打算什么时候做）, due （最晚什么时候完成）, reminder — never conflate them. `Rules.resolve()` defines DST behavior (gap → first valid instant after; overlap → first occurrence); user-facing times are local wall-clock, reminders re-resolve after timezone changes.

**Reminders.kt — reconcile-driven, at-most-once delivery.** `ReminderScheduler.reconcile()` is the single place that recomputes every alarm from DB state; it's invoked after each mutation (from `TodoViewModel.perform`), on app resume, and by `ReconcileReceiver` (boot, package replaced, time/timezone/alarm-permission changes). Never schedule alarms ad hoc. Invariants to preserve: the scheduled-id registry in `reminder_registry` SharedPreferences is persisted *before* scheduling (crash can't orphan alarms); a reminder is marked `delivered = generation` in the DB *before* the notification posts (at-most-once); stale generations and stale `at` timestamps in incoming intents are ignored; missed reminders (< 24 h, notifications were on) produce one summary notification, not per-task catch-up.

**TodoViewModel.kt — one mutation funnel.** `perform(success, block, after)` gates on `busy`, runs `block` on IO, then always reconciles reminders and emits "任务已保存，提醒未能启用" if that fails (task stays saved). The editor draft (`EditorDraft`) is JSON-encoded into SharedPreferences so it survives process death; saving clears it.

**UI — MainActivity.kt, Forms.kt, Motion.kt, CompletionFeedback.kt.** Single activity, no navigation library: a `page` string state switches among 今天 / 收件箱 / 清单 / 之后 / 设置. Inbox = unscheduled **and** untagged tasks (a due date alone doesn't remove a task from inbox). "结束一天" only persists `hidden_today` in SharedPreferences — it must not mutate any task (there's a UI test asserting record equality before/after). The spec's original reschedule-at-close flow survives as `EndDaySheet` (Forms.kt, no longer wired into the UI) and `Repository.closeDay` (still covered by `RepositoryTest`) — treat them as legacy unless the feature is revived. `Motion.kt`: `LocalReducedMotion` composition local; `TodoBottomSheet` renders a Dialog with window animations off when reduced; `rememberTaskPresentation` holds the previous row ~180 ms after a completion toggle so the check animation resolves before the list closes the gap. `CompletionFeedback` plays haptics + a SoundPool chime only after a successful save, only when the window has focus, ringer is normal, system volume > 0, and DND is off.

Task rows in lists are wrapped in `SwipeableTaskRow` (M3 `SwipeToDismissBox`): the action per direction is user-configurable in 设置 → 手势与反馈 (`swipe_right` / `swipe_left` prefs; values 完成或恢复 / 安排到今天 / 安排到明天 / 关闭； defaults right=完成或恢复， left=安排到今天 via `TodoViewModel.scheduleToday` — quiet, tags/reminder untouched). Scheduling actions never fire on an already-completed task or one already on the target day, and 关闭 disarms the direction (row drags but snaps back with no badge). `confirmValueChange` always returns false for actions — rows leave via the data change, never via dismissal — and callbacks, the configured actions and the task's current `scheduleDate` go through `rememberUpdatedState` so a swipe never acts on a stale `revision`, setting or date. The reveal is a floating circular icon badge (44dp, no text labels): complete = filled `primary` circle, schedule = `surfaceContainerHigh` (Today icon for 安排到今天， Event for 安排到明天）; its alpha/scale ramp with `state.progress * 2` so it's fully grown exactly when the 50% positional threshold arms the action (`progress` ≈ 0.5 there), and crossing the threshold on an applicable direction fires a `KEYBOARD_TAP` haptic. The background is `clearAndSetSemantics`-clean so TalkBack never announces it. Today's empty state becomes a celebration ("今天的都完成了。") when the list is empty because everything was completed today.

Two extra entry points into `MainActivity`: the system share sheet (`ACTION_SEND` text/plain → editor draft, first line = title ≤300 chars, remainder = note ≤5000) and the launcher shortcut `app.todo.local.action.NEW_TASK` (`res/xml/shortcuts.xml`, reuses the page-aware `add()`). Both refuse to clobber an open draft.

## Tests

- `app/src/test/` (JVM, no device): `RulesTest` (date/DST/sorting semantics — extend this when touching `Rules`).
- `app/src/androidTest/`: `RepositoryTest` (in-memory Room + fixed `Clock`), `ReminderPlatformTest` (real AlarmManager/NotificationManager; grants itself permissions via shell), `WorkflowTest` (Compose end-to-end; asserts no snackbars on complete/restore/save, writes screenshots to external files which `test-device.ps1` pulls into `artifacts/screenshots/`).
