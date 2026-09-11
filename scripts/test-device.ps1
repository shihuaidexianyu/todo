$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    $taskSdk = $env:ANDROID_HOME
    if (-not $taskSdk) { $taskSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $taskAdb = Join-Path $taskSdk 'platform-tools\adb.exe'
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr' }
    & .\gradlew.bat assembleDebug assembleDebugAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw '测试包编译失败。' }
    & $taskAdb install -r app/build/outputs/apk/debug/app-debug.apk
    & $taskAdb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    New-Item -ItemType Directory -Force -Path artifacts/validation,artifacts/screenshots | Out-Null
    try {
        & $taskAdb shell am instrument -w -r app.todo.local.test/androidx.test.runner.AndroidJUnitRunner | Tee-Object -FilePath artifacts/validation/device-tests.txt
        & $taskAdb pull /sdcard/Android/data/app.todo.local/files/screenshots/. artifacts/screenshots
    } finally {
        & $taskAdb shell pm revoke app.todo.local android.permission.POST_NOTIFICATIONS
        & $taskAdb shell appops set app.todo.local SCHEDULE_EXACT_ALARM default
        & $taskAdb shell settings put system font_scale 1.0
    }
    if (-not (Select-String -LiteralPath artifacts/validation/device-tests.txt -Pattern 'OK \(\d+ tests\)' -Quiet)) { throw '设备测试未全部通过，请查看报告。' }
} finally { Pop-Location }
