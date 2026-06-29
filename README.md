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

点击release页下载MIearn(v2.1),目前仅支持APK

## Release 签名

仓库不会保存发布私钥或密码。Copy `key.properties.example` to `key.properties`，
并填写新生成密钥的本地路径和凭据。Git 已忽略该文件、
`.signing/` 和 `*.jks`；没有本地签名配置时，Release APK 会保持未签名。

本地发布密钥默认放在 `.signing/miearn-release.jks`。不要提交 keystore、
`key.properties`、密码、私钥内容或 keystore 的 Base64，也不要把它们粘贴到
终端输出、Issue、Actions 日志或聊天记录中。

GitHub Actions 的签名发布工作流需要在仓库 Secrets 中配置：

- `MIEARN_KEYSTORE_BASE64`
- `MIEARN_KEYSTORE_PASSWORD`
- `MIEARN_KEY_ALIAS`
- `MIEARN_KEY_PASSWORD`

在 Windows 上可将 keystore 的 Base64 直接复制到剪贴板，避免写入中间文件：

```powershell
[Convert]::ToBase64String(
    [IO.File]::ReadAllBytes(".signing/miearn-release.jks")
) | Set-Clipboard
```

将剪贴板内容保存为 `MIEARN_KEYSTORE_BASE64` Secret；其余三个 Secret 从本地
`key.properties` 手动复制。配置时不要共享或截图 Secret 值。普通 Push/PR 只
执行测试、lint 和 Debug 构建；`v*` 标签或手动触发才会使用 Secrets 构建签名
Release APK。
Recreate the audio toolchain from `tools/requirements-audio.txt`. Do not commit
`tools/.venv` or `tools/.piper-models`.
