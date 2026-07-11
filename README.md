# MIearn

一款面向制造业专业英语的 Android 离线背词应用。

MIearn 内置 2,704 条专业词汇与句子，无需注册、无需联网，安装后即可学习。

[下载最新版本](https://github.com/Frattyrant/recite_android_app/releases/latest)

## 主要功能

- 机械、电气、客户评审、会议口语和商务句子五类内置词库。
- 新词先浏览、再做英选中巩固，减少首次学习的认知负担。
- SM-2 复习、错题强化、收藏和学习进度统计。
- 英选中、中选英、拼写、听音选词和例句填空五种测试。
- 内置离线发音，多表达词条支持分别点击播放。
- 月度学习日历、周摘要和每日学习详情。
- 支持导入 `.csv` 与 `.xlsx` 自定义词库。
- 跟随系统切换浅色或深色界面。

## 安装

1. 打开 [Releases](https://github.com/Frattyrant/recite_android_app/releases)。
2. 下载最新版 `MIearn-v2.2.apk`。
3. 在 Android 手机上打开 APK 并按系统提示完成安装。

系统要求：Android 10 或更高版本。

> 如果系统阻止安装，请仅为当前文件管理器开启“允许安装未知应用”，安装结束后可再次关闭。

## 快速开始

1. 打开 MIearn，在首页顶部选择要学习的词库。
2. 点击底部上方的“开始学习”。
3. 第一遍浏览卡片，点击卡片查看音标、释义和例句。
4. 第二遍完成英选中巩固。
5. 之后按首页显示的到期数量进行复习。

每日新词数量、自动发音和学习提醒可在首页右上角的设置中调整。

## 导入自己的词库

首页点击“导入”，选择 CSV 或 Excel 文件。最简单的 CSV 格式如下：

```csv
英文,中文
fixture,夹具
limit switch,限位开关
```

也支持以下可选列：

```text
英文,中文,音标,备注,英文例句,例句翻译
```

- 单个文件不超过 20 MB、最多 20,000 行。
- 所有解析、清洗和字典补全均在本机完成。
- 自定义词条在学习时使用系统英语 TTS 发音。

## 离线与隐私

- 应用不申请网络权限。
- 不包含账号、广告、云同步或用户追踪。
- 词库、收藏、错题和学习记录均保存在手机本地。

## 从源码构建

环境要求：JDK 21、Android SDK 36、Android Build Tools 36。

```powershell
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

项目使用 Kotlin、Jetpack Compose、Room、MVVM、DataStore、Media3 和 WorkManager。

第三方资源与许可信息见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
