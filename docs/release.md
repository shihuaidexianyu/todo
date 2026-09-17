# todo 1.2.0

[下载 APK / AAB](https://github.com/shihuaidexianyu/todo/releases/tag/v1.2.0) · versionCode 6，沿用原 release 签名，可覆盖安装。

- 列表手势：右滑完成或恢复，左滑安排到明天；两个方向可在「设置 → 手势与反馈」分别改为完成或恢复、安排到明天或关闭。滑动时浮现圆形图标，滑过触发点有轻微震动。
- 今天的任务全部完成后，空状态变为庆祝提示。
- 支持从系统分享文本直接新建任务（首行为标题、余下为备注），长按桌面图标可快速新建。
- 任务行加高，创建页改为近全屏弹层，保存按钮固定在底部操作栏。
- 设置页重组为外观、手势与反馈、提醒三组，删除冗余说明。
- 移除应用内 JSON 备份与恢复功能；系统级自动备份保持关闭，卸载会清除本地数据。

验证范围见 [验证记录](verification.md)。

---

# todo 1.1.2

[下载 APK / AAB](https://github.com/shihuaidexianyu/todo/releases/tag/v1.1.2) · versionCode 5，可覆盖原 release 安装。

- 完成音换为原创玻璃拨弦短音，快速起音、165 ms 收尾，替换原系统蜂鸣音。
- 保存成功后先呈现勾选，再淡出任务，列表缓动补位；统一今天与标签列表的节奏。
- 减少动画模式直接更新，完成与恢复仍不弹提示条。

音效源文件由 `scripts/generate-completion-sound.py` 生成，使用 [SoundPool](https://developer.android.com/reference/android/media/SoundPool) 预加载。沿用静音、勿扰和应用内声音开关。

验证范围见 [验证记录](verification.md)。

---

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
