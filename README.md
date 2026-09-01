# MIearn

MIearn 是一款面向制造业专业英语的离线背词应用。内置 2,698 条经过清洗的专业词汇与句子，无需注册、无需联网，安装后即可学习。

[下载 Android 最新版](https://github.com/Frattyrant/recite_android_app/releases/latest)

## 发布平台

- **Android：正式支持。** GitHub Releases 当前只提供 Android APK。
- **iPhone / iPad：尚未发布。** iOS 设备不能安装 APK；仓库中的 iOS 工程仅用于源码开发与模拟器构建验证，目前没有 IPA、TestFlight 或 App Store 版本。

## v2.33 更新

- 首页采用“一张主任务卡”，连续学习火苗根据连续天数呈现静态/动态分级效果。
- 多表达词条采用“一主多辅”：学习与测试聚焦首个表达，其他表达可展开并单独播放。
- 修正句子切分、导入词条主表达和工程符号校验，避免把完整句子或 `C++` 等内容误判为无效。
- 自定义词库导入支持 `.xlsx`、`.csv`、`.tsv`、`.txt`，失败会显示错误原因、恢复建议和重试入口。
- 导入中的复制、解析和校验阶段可取消，取消后清理临时文件且不影响已有学习记录。
- 内置术语提供两条例句并逐条显示中文翻译；历史单条例句和自定义词库格式保持兼容。
- 学习卡、详情和测试反馈支持逐条例句 TTS 播放，不增加联网或额外音频资源。

## 主要功能

- 机械、电气、客户评审、会议口语和商务句子五类内置词库。
- 新词先浏览、再做英选中巩固，第二遍作答后自动进入下一词。
- SM-2 复习、错题强化、收藏、学习日历和数据统计。
- 英选中、中选英、拼写、听音选词和例句填空五种测试。
- 内置美式音标和离线发音；多表达词条支持完整播放或单独播放。
- Android 正式版支持导入 XLSX、CSV、TSV 和 TXT 自定义词库。
- 跟随系统切换浅色或深色界面。

## v2.33.2 更新

- 修复部分手机点击导入文件无响应的问题，增加通用文件选择器与兼容模式入口。
- 文件选择取消、权限异常或系统选择器不可用时显示明确反馈，不再静默失败。
- 失败导入任务不会在重启后重复恢复；“重新选择”会先清理旧任务。
- 新增直接粘贴 TXT/CSV 内容的导入备用方式，并沿用原有校验与异步解析流程。

## Android 安装

1. 打开 [Releases](https://github.com/Frattyrant/recite_android_app/releases)。
2. 下载最新的 Android APK（Release 页面会标注对应版本）。
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

- Android：支持 `.xlsx`、`.csv`、`.tsv` 和 `.txt`；文本文件支持 UTF-8、UTF-16 和 GB18030，单文件不超过 20 MB、最多 20,000 行。
- 旧版二进制 `.xls`、启用宏的 `.xlsm`、普通 ZIP 和未知二进制文件不会被静默读取，界面会给出转换建议。
- 解析、清洗、字典补全和学习记录均保存在本机。
- 自定义词条在学习时使用系统英语 TTS 发音。
- 导入完成后可直接切换到该词库开始学习，也可以稍后从首页顶部词库菜单进入。

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

### iOS 源码说明

当前发布渠道仅提供 Android APK。仓库中的 `ios/` 目录仅作跨平台规则参考，不提供已签名 IPA、TestFlight 或 App Store 版本，也不属于 Android 构建依赖。

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
