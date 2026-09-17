$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr' }
    $taskKeytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
    if (-not (Test-Path -LiteralPath $taskKeytool)) { throw '请配置 JAVA_HOME（JDK 17 或以上）。' }
    $taskSdk = $env:ANDROID_HOME
    if (-not $taskSdk) { $taskSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    if (-not (Test-Path -LiteralPath 'local.properties')) {
        Set-Content -LiteralPath 'local.properties' -Value ('sdk.dir=' + $taskSdk.Replace('\', '/').Replace(':', '\:')) -NoNewline
    }
    New-Item -ItemType Directory -Force -Path '.signing','artifacts/release-validation/1.2.0' | Out-Null
    $taskKeyFile = Join-Path $taskRoot '.signing\todo-release.p12'
    $taskKeyConfig = Join-Path $taskRoot '.signing\keystore.properties'
    if ((Test-Path -LiteralPath $taskKeyConfig) -and -not (Test-Path -LiteralPath $taskKeyFile)) {
        throw '签名配置已存在但密钥缺失；请恢复原密钥，避免破坏后续版本升级。'
    }
    if (-not (Test-Path -LiteralPath $taskKeyConfig)) {
        if (Test-Path -LiteralPath $taskKeyFile) { throw '签名密钥已存在但配置缺失；请恢复原配置，不要重新生成密钥。' }
        $taskRandomBytes = New-Object byte[] 32
        $taskRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $taskRng.GetBytes($taskRandomBytes) } finally { $taskRng.Dispose() }
        $taskPassword = [Convert]::ToBase64String($taskRandomBytes)
        $taskConfigText = "storeFile=.signing/todo-release.p12`nstorePassword=$taskPassword`nkeyAlias=todo`nkeyPassword=$taskPassword`n"
        [IO.File]::WriteAllText($taskKeyConfig, $taskConfigText, [Text.UTF8Encoding]::new($false))
    }
    # Keep this folder private to the current Windows user. Never print its contents.
    $taskIdentity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls (Join-Path $taskRoot '.signing') /inheritance:r /grant:r "${taskIdentity}:(OI)(CI)F" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '无法保护本地签名目录权限。' }
    if (-not (Test-Path -LiteralPath $taskKeyFile)) {
        $taskConfig = Get-Content -Raw -LiteralPath $taskKeyConfig | ConvertFrom-StringData
        $env:TODO_RELEASE_KEY_PASSWORD = $taskConfig.storePassword
        try {
            & $taskKeytool -genkeypair -keystore $taskKeyFile -storetype PKCS12 -storepass:env TODO_RELEASE_KEY_PASSWORD -alias todo -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=todo' -noprompt
            if ($LASTEXITCODE -ne 0) { throw 'Release 签名密钥生成失败。' }
        } finally { Remove-Item Env:TODO_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue }
    }
    & .\gradlew.bat assembleRelease bundleRelease testReleaseUnitTest lintRelease --console=plain --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Release 构建或检查失败。' }
    Copy-Item -LiteralPath app/build/outputs/apk/release/app-release.apk -Destination artifacts/todo-1.2.0-release.apk
    Copy-Item -LiteralPath app/build/outputs/bundle/release/app-release.aab -Destination artifacts/todo-1.2.0-release.aab
    Copy-Item -LiteralPath app/build/outputs/mapping/release/mapping.txt -Destination artifacts/release-validation/1.2.0/mapping.txt
    $taskSigner = Join-Path $taskSdk 'build-tools\36.0.0\apksigner.bat'
    & $taskSigner verify --verbose --print-certs artifacts/todo-1.2.0-release.apk | Tee-Object -FilePath artifacts/release-validation/1.2.0/signature.txt
    if ($LASTEXITCODE -ne 0) { throw 'APK 签名校验失败。' }
    Get-FileHash -Algorithm SHA256 -LiteralPath artifacts/todo-1.2.0-release.apk,artifacts/todo-1.2.0-release.aab | Format-List | Out-File artifacts/release-validation/1.2.0/sha256.txt
    Write-Output 'Release APK 和 AAB 已生成。请自行备份 .signing 目录，用于后续版本更新。'
} finally { Pop-Location }


