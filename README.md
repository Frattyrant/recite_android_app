# MIearn

MIearn 是一款面向制造业专业英语的 Android 10+ 原生离线背词应用，包名为
`com.miearn.app`。项目使用 Kotlin、Jetpack Compose、Room、DataStore、Media3
和 WorkManager；核心学习、词库、音频与统计均可离线使用。

## 主要功能

- 首页保留今日新学、到期复习、连续天数、总进度和底部大号“开始学习”按钮。
- 底部导航为“学习 / 测试 / 我的”，全局搜索位于首页顶部。
- 新词第一遍浏览，第二遍英选中；复习使用 SM-2，并带错题强化和学习数据。
- 每日新词可在 5–200 间按 5 调节，提醒时间可精确到分钟。
- 测试包含英选中、中选英、拼写、听音选词和例句填空。

## 离线内容与发音

内置机械 1,227、电气 970、客户评审 251、会议口语 58、商务句子 198，共
2,704 条内容。

- 单词型词条按分号、斜杠、反斜杠和空格切分为可单独点击的表达。
- 句子型词条按完整句切分，不会把一句话拆成单个单词。
- 完整播放时各表达间停顿 500 ms；点击某个表达只播放该段。
- 内置音频使用 Piper `en_US-lessac-medium` 的 Ogg/Opus；资源缺失时回退到
  Android 英语 TTS。
- 发给 TTS 的文本会移除 `/` 和 `\`，不会把斜杠读成 “slash”。

## 导入自定义词库

首页顶部的导入按钮支持 `.csv` 和 `.xlsx`，单个文件上限 20 MB。

推荐表头：

```text
英文,中文,音标,备注,英文例句,例句翻译
fixture,夹具,/ˈfɪkstʃər/,制造业术语,Inspect the fixture.,检查夹具。
```

- CSV 支持 UTF-8、UTF-8 BOM 与常见中文编码；XLSX 读取首个非空工作表。
- 无表头文件默认把第一列作为英文；无法可靠识别表头时可手动指定各列。
- 导入前会清洗空格、校验英文、统计无效行和文件内重复项。
- 内置精简 ECDICT 会离线补全已有词的音标和中文释义，不生成虚构例句。
- 冲突策略可选“保留已有内容”或“用导入的非空字段更新”；学习进度不会被覆盖。
- 自定义词库可在“我的 → 词库管理”重命名或删除。删除来源时，仅删除不再属于
  任何其他词库的自定义词及其学习记录。
- 自定义词条不打包音频，学习时使用系统英语 TTS；应用不申请网络权限。

## 安装

将 `app/build/outputs/apk/debug/app-debug.apk` 复制到 Android 10 或更高版本设备，
允许文件管理器安装未知来源应用后安装。Debug APK 使用 Android 调试证书，仅用于
本地安装和测试。

## 构建

```powershell
$env:JAVA_HOME = "D:\Android_Studio\jbr"
$env:ANDROID_HOME = "D:\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = "D:\Android\Gradle"

.\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin lintDebug verifyDebugApkSize
```

重新构建精简 ECDICT：

```powershell
Invoke-WebRequest `
  -Uri "https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv" `
  -OutFile "tools\vendor\ecdict.csv"

python tools\build_compact_ecdict.py `
  --source tools\vendor\ecdict.csv `
  --output app\src\main\assets\dictionaries\ecdict_compact.db.gz.bin `
  --manifest app\src\main\assets\dictionaries\ecdict_compact_manifest.json `
  --limit 120000
```

原始 `ecdict.csv` 是临时构建输入，已被 `.gitignore` 排除。第三方模型、词典与许可
见 `THIRD_PARTY_NOTICES.md`。
