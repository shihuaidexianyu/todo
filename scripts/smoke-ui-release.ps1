param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference = 'Stop'
$taskAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$taskEvidence = Join-Path (Split-Path -Parent $PSScriptRoot) 'artifacts/release-validation/1.2.0'
function Device { & $taskAdb -s $Serial @args }
function Tree {
    Device shell uiautomator dump /sdcard/todo-a.xml | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect device UI' }
    [xml]$xml = Device shell cat /sdcard/todo-a.xml
    return $xml
}
function Find([string]$XPath) {
    for ($attempt=0;$attempt -lt 5;$attempt++) {
        $node = (Tree).SelectSingleNode($XPath)
        if ($node) { return $node }
    }
    throw "Missing UI: $XPath"
}
function Tap([string]$XPath) {
    $node = Find $XPath
    $coords = [regex]::Matches($node.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    Device shell input tap ([int](($coords[0]+$coords[2])/2)) ([int](($coords[1]+$coords[3])/2))
}
function HideKeyboard {
    $ime = Device shell dumpsys input_method
    if ($ime | Select-String 'mInputShown=true') { Device shell input keyevent KEYCODE_BACK; Start-Sleep -Milliseconds 600 }
}
function Capture([string]$name) {
    Device shell screencap -p /sdcard/todo-a.png
    Device pull /sdcard/todo-a.png (Join-Path $taskEvidence "$name.png") | Out-Null
}
Device logcat -c
Device shell am start -W -n app.todo.local/.MainActivity
Tap '//node[@content-desc="添加任务"]'
$null = Find '//node[@class="android.widget.EditText"]'
Start-Sleep -Seconds 1
Device shell input text ReleaseNote
HideKeyboard
Tap '//node[@text="提醒与备注"]'
Tap '(//node[@class="android.widget.EditText"])[last()]'
Device shell input text NoteDetails
HideKeyboard
Tap '//node[@text="保存"]'
$null = Find '//node[@content-desc="完成 ReleaseNote"]'
$null = Find '//node[contains(@content-desc,"有备注：ReleaseNote")]'
$null = Find '//node[@text="NoteDetails"]'
Capture 'release-note-row'
Write-Output 'PASS: note marker and preview visible on saved task.'
Tap '//node[@text="结束一天"]'
$null = Find '//node[@text="今天已收尾"]'
if ((Tree).SelectSingleNode('//node[@content-desc="完成 ReleaseNote"]')) { throw 'Today task remains visible after close' }
Capture 'release-today-closed'
Device shell am force-stop app.todo.local
Device shell am start -W -n app.todo.local/.MainActivity
$null = Find '//node[@text="重新展开今天"]'
Tap '//node[@text="标签"]'
Tap '//node[@text="全部待办"]'
$null = Find '//node[@content-desc="完成 ReleaseNote"]'
Tap '//node[@text="今天"]'
Tap '//node[@text="重新展开今天"]'
$null = Find '//node[@content-desc="完成 ReleaseNote"]'
$null = Find '//node[@text="NoteDetails"]'
Write-Output 'PASS: close persists across restart; task remains in tags and reappears on reopen.'
Tap '//node[@content-desc="完成 ReleaseNote"]'
$null = Find '//node[@text="今天完成 1 项"]'
if ((Tree).SelectSingleNode('//node[@text="撤销"]')) { throw 'Unexpected completion banner' }
Tap '//node[@text="已完成"]'
$null = Find '//node[@content-desc="恢复 ReleaseNote"]'
Capture 'release-completed'
Tap '//node[@content-desc="恢复 ReleaseNote"]'
$null = Find '//node[@text="还没有已完成的任务。"]'
if ((Tree).SelectSingleNode('//node[@text="已恢复"]')) { throw 'Unexpected restore banner' }
Tap '//node[@text="今天"]'
$null = Find '//node[@content-desc="完成 ReleaseNote"]'
Write-Output 'PASS: complete and restore without banners through bottom navigation.'
Tap '//node[@content-desc="设置"]'
$null = Find '//node[@text="外观"]'
Tap '//node[@text="已完成"]'
$null = Find '//node[@text="还没有已完成的任务。"]'
Write-Output 'PASS: direct settings navigation and completed history.'
Device logcat -d -b crash | Out-File (Join-Path $taskEvidence 'crash-buffer.txt')
Write-Output 'PASS: release UI workflow finished.'
