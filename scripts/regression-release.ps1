param([Parameter(Mandatory = $true)][string]$Serial)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$taskEvidence = Join-Path $taskRoot 'artifacts/release-validation/1.0.1'
New-Item -ItemType Directory -Force $taskEvidence | Out-Null
function Device { & $taskAdb -s $Serial @args }
function Node([string]$XPath) {
    for ($attempt = 0; $attempt -lt 5; $attempt++) {
        Device shell uiautomator dump /sdcard/regression-ui.xml | Out-Null
        if ($LASTEXITCODE -ne 0) { continue }
        [xml]$tree = Device shell cat /sdcard/regression-ui.xml
        $found = $tree.SelectSingleNode($XPath)
        if ($found) { return $found }
    }
    throw "Missing UI: $XPath"
}
function Tap([string]$XPath) {
    $target = Node $XPath
    $coords = [regex]::Matches($target.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    Device shell input tap ([int](($coords[0] + $coords[2]) / 2)) ([int](($coords[1] + $coords[3]) / 2))
}
function Capture([string]$Name) {
    Device shell screencap -p /sdcard/regression.png
    Device pull /sdcard/regression.png (Join-Path $taskEvidence "$Name.png") | Out-Null
}
Device logcat -c
Device shell am start -W -n app.todo.local/.MainActivity
Tap '//node[@text="今天"]'
$null = Node '//node[@content-desc="完成 UpgradeKeep"]'
Write-Output 'PASS: task from release 1.0.0 survives in-place upgrade.'
Tap '//node[@content-desc="添加任务"]'
$null = Node '//node[@class="android.widget.EditText"]'
Start-Sleep -Seconds 1
foreach ($part in @('Release', 'Typing', 'Stable')) {
    Device shell input text $part
    Start-Sleep -Milliseconds 350
    $ime = Device shell dumpsys input_method
    if (-not ($ime | Select-String 'mInputShown=true')) { throw "IME unexpectedly hidden after $part" }
}
Write-Output 'PASS: keyboard stays visible across successive input updates.'
Device shell input keyevent KEYCODE_BACK
Start-Sleep -Milliseconds 500
Tap '//node[@text="今天截止"]'
Capture 'editor-quick-due'
Tap '//node[@text="保存"]'
$null = Node '//node[@content-desc="完成 ReleaseTypingStable"]'
Capture 'today'
Tap '//node[@content-desc="添加任务"]'
$null = Node '//node[@class="android.widget.EditText"]'
Start-Sleep -Seconds 1
Device shell input text FutureTask
Device shell input keyevent KEYCODE_BACK
Start-Sleep -Milliseconds 500
Tap '//node[@text="安排：今天"]'
Tap '//node[@text="明天"]'
Tap '//node[@text="确定"]'
Tap '//node[@text="保存"]'
$null = Node '//node[@text="今天"]'
Tap '//node[@content-desc="完成 ReleaseTypingStable"]'
foreach ($round in 1..3) {
    Tap '//node[@text="之后"]'
    $null = Node '//node[@text="FutureTask"]'
    if ($round -eq 1) { Capture 'future' }
    Tap '//node[@text="今天"]'
    Tap '//node[@content-desc="更多"]'
    Tap '//node[@text="已完成"]'
    $null = Node '//node[@content-desc="恢复 ReleaseTypingStable"]'
    if ($round -eq 1) { Capture 'completed' }
    Tap '//node[@text="今天"]'
}
Write-Output 'PASS: repeated future and completed navigation with active, future and completed tasks.'
Device shell am force-stop app.todo.local
Device shell am start -W -n app.todo.local/.MainActivity
$null = Node '//node[@content-desc="完成 UpgradeKeep"]'
Write-Output 'PASS: persisted tasks survive process restart.'
$crashes = Device logcat -d -b crash
$crashes | Out-File (Join-Path $taskEvidence 'crash.txt')
if ($crashes | Select-String 'FATAL EXCEPTION') { throw 'Crash recorded' }
Write-Output 'PASS: no fatal crash in release regression run.'
