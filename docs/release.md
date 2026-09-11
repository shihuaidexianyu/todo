# todo 1.1.1

[下载 APK / AAB](https://github.com/shihuaidexianyu/todo/releases/tag/v1.1.1) · versionCode 4，可覆盖原 release 安装。

- 完成和恢复任务不再弹出提示条。
- 加入轻微震动与短促完成提示音，在设置中可分别关闭；静音与勿扰时不播放声音。
- “已完成”移至底部导航，直接查看与恢复任务。

反馈仅在任务保存成功后触发。振动使用 [Android 触觉反馈接口](https://developer.android.com/develop/ui/views/haptics/haptic-feedback)，声音使用 [ToneGenerator](https://developer.android.com/reference/android/media/ToneGenerator)。

验证范围见 [验证记录](verification.md)。

---

# todo 1.1.0

[下载 APK / AAB](https://github.com/shihuaidexianyu/todo/releases/tag/v1.1.0) · versionCode 3，沿用原 release 签名，可覆盖安装。

## 修改

- 右上角直接进入设置；已完成入口放在设置页。
- “结束一天”仅隐藏今天列表，不修改任务、日期或提醒。
- “重新展开今天”恢复列表；当天收尾状态在重启后保留，换日后正常显示。
- 有备注的任务显示图标、文字标记和一行预览。
- 收紧页头与任务行，弱化分隔，简化新建任务、当天截止与保存操作。
- 包含此前版本的输入焦点、标签当页展开和导航崩溃修复。

## 验证

28 项单元测试、6 项界面工作流测试通过；Lint 0 错误、28 警告。最终 release 已完成覆盖安装及 UI 回归，详见 [验证记录](verification.md)。

数据库结构未变，旧备份仍兼容。校验和见 Release 附件 `SHA256SUMS.txt`。
