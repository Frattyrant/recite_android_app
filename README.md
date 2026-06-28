# MIearn

MIearn 是一款面向制造业专业英语的 Android 10+ 原生离线背词应用。V2 使用 Kotlin、
Jetpack Compose、Room、MVVM、DataStore、Media3 与 WorkManager，包名为
`com.miearn.app`。

## V2 使用方式

- 底部仅保留“学习 / 测试”两个入口，首页与词库合并。
- 首页保留今日新学、到期复习、连续天数、总进度，以及词库、收藏、错题和学习数据入口。
- 64dp 高的“开始学习”按钮固定在底部导航上方，打开应用即可单手开始当天任务。
- 新词第一遍只浏览：左右滑动，点击卡片查看音标、中文、双语例句和备注，可随时发音或收藏。
- 第二遍固定为英文选择中文；首次答错会显示答案并在本轮末尾再出现一次。
- 到期复习和新词第二遍按首次客观答案自动写入 SM-2：答对质量 5，答错质量 2，无主观熟悉度按钮。
- 学习数据提供 7/30 天趋势、首次正确率、未来 7 天待复习量和预计保持率曲线。
- 学习提醒默认关闭；用户主动开启后默认每天 10:00 提醒，可按 15 分钟调整。
- 测试保留英选中、中选英、拼写、听音选词和例句填空五种模式。

## 离线内容与发音

应用内置机械 1,227、电气 970、客户评审 251、会议口语 58、商务句子 198，共
2,704 条。数据、进度、统计和任务会话完全保存在本机，Manifest 不申请互联网权限。

每条内容都打包一个 Piper `en_US-lessac-medium` 美式英语 Ogg/Opus 音频。播放优先级：

1. Media3 播放内置 Ogg；
2. 文件缺失或解码失败时自动回退 Android TTS；
3. TTS 不可用时显示“启用英语语音”系统入口。

初始化期间的播放请求会在 TTS 就绪后补播；快速滑卡只保留当前请求；TTS 失败会重试
一次。完整音频校验会检查清单绑定、逐文件解码、时长和静音阈值。

## 安装

1. 将 `app/build/outputs/apk/debug/app-debug.apk` 复制到 Android 10 或更高版本设备。
2. 允许当前文件管理器安装未知来源应用。
3. 安装并打开 `MIearn`。首次启动会把内置 JSON 事务导入 Room。

Debug APK 使用 Android 默认调试证书，只用于本地安装和测试。

## 构建与验证

项目锁定 Gradle 9.1、AGP 9.0、compile/target SDK 36、min SDK 29。

```powershell
$env:JAVA_HOME = "D:\Android_Studio\jbr"
$env:ANDROID_HOME = (Resolve-Path ".\.android-sdk").Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = (Join-Path (Get-Location) ".gradle-user-home")
.\gradlew.bat clean testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug lintDebug --offline
```

内容与音频验证：

```powershell
& "C:\Users\LENOVO\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" `
  -m unittest discover -s tools\tests -v
& "C:\Users\LENOVO\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" `
  tools\validate_audio.py --all
```

完整 V2 验收记录见 `output/reports/v2_verification_report.md`，第三方模型与许可见
`THIRD_PARTY_NOTICES.md`。
