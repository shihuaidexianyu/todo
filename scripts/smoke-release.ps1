param([Parameter(Mandatory = $true)][string]$Serial)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskSdk = $env:ANDROID_HOME
if (-not $taskSdk) { $taskSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$taskAdb = Join-Path $taskSdk 'platform-tools\adb.exe'
function Invoke-Device { & $taskAdb -s $Serial @args }
function Find-UiNode([string]$XPath) {
    for ($attempt = 0; $attempt -lt 8; $attempt++) {
        Invoke-Device shell uiautomator dump /sdcard/todo-release-ui.xml | Out-Null
        $xmlText = Invoke-Device shell cat /sdcard/todo-release-ui.xml
        try { [xml]$tree = $xmlText; $node = $tree.SelectSingleNode($XPath); if ($null -ne $node) { return $node } } catch { }
        Start-Sleep -Milliseconds 400
    }
    throw "未找到界面控件：$XPath"
}
function Tap-UiNode([string]$XPath) {
    $node = Find-UiNode $XPath
    $coords = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    Invoke-Device shell input tap ([int](($coords[0] + $coords[2]) / 2)) ([int](($coords[1] + $coords[3]) / 2))
}
Push-Location $taskRoot
try {
    New-Item -ItemType Directory -Force -Path artifacts/release-validation | Out-Null
    Invoke-Device shell am start -W -n app.todo.local/.MainActivity
    Tap-UiNode '//node[@content-desc="添加任务"]'
    Tap-UiNode '//node[@class="android.widget.EditText"]'
    Invoke-Device shell input text ReleaseSmoke
    Start-Sleep -Seconds 1
    Invoke-Device shell input keyevent KEYCODE_BACK
    Start-Sleep -Seconds 1
    Tap-UiNode '//node[@text="保存"]'
    $null = Find-UiNode '//node[@content-desc="完成 ReleaseSmoke"]'
    Write-Output 'PASS: optimized release starts and saves a task.'
    Invoke-Device shell am force-stop app.todo.local
    Invoke-Device shell am start -W -n app.todo.local/.MainActivity
    $null = Find-UiNode '//node[@content-desc="完成 ReleaseSmoke"]'
    Write-Output 'PASS: task persists across process restart.'
    Invoke-Device shell screencap -p /sdcard/todo-release.png
    Invoke-Device pull /sdcard/todo-release.png artifacts/release-validation/installed-release.png
    Tap-UiNode '//node[@content-desc="完成 ReleaseSmoke"]'
    $null = Find-UiNode '//node[@text="今天没有安排。"]'
    Tap-UiNode '//node[@content-desc="更多"]'
    Tap-UiNode '//node[@text="已完成"]'
    $null = Find-UiNode '//node[@text="ReleaseSmoke"]'
    Write-Output 'PASS: completion moves the task to completed history.'
    $packageInfo = Invoke-Device shell dumpsys package app.todo.local
    if ($packageInfo | Select-String 'flags=\[.*DEBUGGABLE') { throw '安装的应用仍可调试。' }
    Write-Output 'PASS: installed application is not debuggable.'
} finally { Pop-Location }
