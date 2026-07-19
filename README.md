# MIearn

MIearn 是一款面向制造业专业英语的离线背词应用。内置 2,698 条经过清洗的专业词汇与句子，无需注册、无需联网，安装后即可学习。

[下载 Android 最新版](https://github.com/Frattyrant/recite_android_app/releases/latest)

## 发布平台

- **Android：正式支持。** GitHub Releases 当前只提供 Android APK。
- **iPhone / iPad：尚未发布。** iOS 设备不能安装 APK；仓库中的 iOS 工程仅用于源码开发与模拟器构建验证，目前没有 IPA、TestFlight 或 App Store 版本。

## v2.32 更新

- 全量重建美式英语发音：LJSpeech High 主声音 + Kokoro 定向纠音，3,741 个完整/分段音频均通过哈希、解码和 ASR 审计。
- 修正专业词条音标与多表达切分；点击紫色词块可只播放当前表达。
- 修复每日提醒只触发一次的问题，并增加系统状态与测试提醒入口。
- 强化本地数据安全、数据库迁移和自定义词库导入的临时文件保护。
- 新增实验性的 iOS 16+ SwiftUI 客户端源码，与 Android 共用词库、学习规则和离线音频；该源码不属于当前可下载安装的正式版本。

## 主要功能

- 机械、电气、客户评审、会议口语和商务句子五类内置词库。
- 新词先浏览、再做英选中巩固，第二遍作答后自动进入下一词。
- SM-2 复习、错题强化、收藏、学习日历和数据统计。
- 英选中、中选英、拼写、听音选词和例句填空五种测试。
- 内置美式音标和离线发音；多表达词条支持完整播放或单独播放。
- Android 正式版支持导入 CSV/XLSX 自定义词库；iOS 源码预览支持 CSV。
- 跟随系统切换浅色或深色界面。

## Android 安装

1. 打开 [Releases](https://github.com/Frattyrant/recite_android_app/releases)。
2. 下载 `MIearn-v2.32.apk`。
3. 在 Android 手机上打开 APK，并按系统提示完成安装。

系统要求：Android 10 或更高版本。

> 如果系统阻止安装，请仅为当前文件管理器开启“允许安装未知应用”，安装完成后可再次关闭。

## 快速开始

1. 打开 MIearn，在首页顶部选择学习词库。
2. 点击屏幕底部上方的“开始学习”。
3. 第一遍浏览卡片；点击卡片查看音标、释义和例句。
4. 第二遍完成英选中巩固，之后按首页到期数量复习。

每日新词数量、自动发音和学习提醒可在首页右上角的设置中调整。

## 导入自己的词库

最简单的 CSV 文件如下：

```csv
英文,中文
fixture,夹具
limit switch,限位开关
```

也支持以下可选列：

```text
英文,中文,音标,备注,英文例句,例句翻译
```

- Android：支持 `.csv` 和 `.xlsx`，单文件不超过 20 MB、最多 20,000 行。
- iOS 源码预览：支持 UTF-8 编码的 `.csv`，当前没有可供用户安装的 iOS 版本。
- 解析、清洗、字典补全和学习记录均保存在本机。
- 自定义词条在学习时使用系统英语 TTS 发音。

## 离线与隐私

- Android 应用不申请网络权限，也不申请广泛存储权限。
- 不包含账号、广告、云同步或用户追踪。
- 词库、收藏、错题和学习记录均保存在设备本地。
- Android 禁用应用数据备份和明文网络流量。

## 从源码构建

### Android

需要 JDK 21、Android SDK 36 和 Android Build Tools 36：

```powershell
./gradlew test
./gradlew lint
./gradlew assembleDebug verifyDebugApkSize
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`

### iOS

以下内容仅面向源码开发者，不代表仓库已经发布 iOS 安装包。构建需要受支持的 macOS、Xcode、XcodeGen 和 iOS 16+ SDK：

```bash
cd ios/MIearnCore
swift test

cd ../MIearnApp
xcodegen generate
xcodebuild \
  -project MIearn.xcodeproj \
  -scheme MIearn \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  build
```

当前发布渠道仅提供 Android APK。仓库不提供已签名 IPA、TestFlight 或 App Store 版本；如需研究 iOS 源码，请在 macOS 的 Xcode 中使用自己的 Apple Developer 签名运行或归档。

## 安全发布

本地签名时，将 `key.properties.example` 复制为 `key.properties`，并仅在本机填写真实签名信息。不要提交 `key.properties`、keystore 或密码。

命令行提示：Copy `key.properties.example` to `key.properties`，然后只在本机编辑副本。

GitHub Actions 使用以下 GitHub Secrets：

- `MIEARN_KEYSTORE_BASE64`
- `MIEARN_KEYSTORE_PASSWORD`
- `MIEARN_KEY_ALIAS`
- `MIEARN_KEY_PASSWORD`

`MIEARN_KEYSTORE_BASE64` 保存 keystore 文件的 Base64 编码内容；真实值只能写入 GitHub Secrets，不能出现在 README、工作流、提交或构建日志中。

项目使用 Kotlin、Jetpack Compose、Room、MVVM、DataStore、Media3、WorkManager 和 SwiftUI。第三方资源与许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
