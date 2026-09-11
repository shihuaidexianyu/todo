# 构建与签名

使用 JDK 17+、Android SDK Platform 36、Build Tools 36.0.0。依赖版本见 Gradle 配置。

Windows 执行 `scripts/build-release.ps1`，输出到 `artifacts/`：

- `todo-1.1.0-release.apk`
- `todo-1.1.0-release.aab`
- `release-validation/1.1.0/`：签名校验、SHA-256 与 R8 混淆映射。

首次运行发布脚本会在 `.signing/` 生成本机独立密钥和 `keystore.properties`，后续复用。整个目录已被 Git 忽略，应单独安全备份；后续覆盖更新需要原签名。

自行构建使用新密钥时，不能覆盖 GitHub Release 的安装。其他系统可自行配置 `.signing/keystore.properties` 的 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`，然后执行 `./gradlew assembleRelease bundleRelease`。未配置时输出未签名包。

Release 启用 R8 混淆、代码优化和资源裁剪。发布 APK、AAB 放在 GitHub Releases，密钥与本地构建日志不提交仓库。
