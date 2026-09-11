$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    if (-not $env:JAVA_HOME) {
        $studioJbr = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
        if (Test-Path -LiteralPath $studioJbr) { $env:JAVA_HOME = $studioJbr }
    }
    if (-not (Test-Path -LiteralPath 'local.properties')) {
        $taskSdk = $env:ANDROID_HOME
        if (-not $taskSdk) { $taskSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
        if (-not (Test-Path -LiteralPath $taskSdk)) { throw '请安装 Android SDK 并配置 ANDROID_HOME。' }
        Set-Content -LiteralPath 'local.properties' -Value ('sdk.dir=' + $taskSdk.Replace('\', '/').Replace(':', '\:')) -NoNewline
    }
    & .\gradlew.bat assembleDebug testDebugUnitTest lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw '构建或检查失败，请查看 Gradle 输出。' }
    New-Item -ItemType Directory -Force -Path 'artifacts' | Out-Null
    Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination 'artifacts\todo-1.0.0-debug.apk'
    Get-FileHash -Algorithm SHA256 -LiteralPath 'artifacts\todo-1.0.0-debug.apk'
} finally { Pop-Location }
