# MIearn 自定义词库导入设计

**日期：** 2026-06-28  
**状态：** 已确认设计，待实施计划  
**目标版本：** V2.2

## 1. 目标与边界

MIearn V2.2 在保持离线优先、无账号、无云同步、无联网权限的前提下，允许用户从手机存储导入 `.xlsx` 或 `.csv` 词库。导入内容可以通过精简 ECDICT 补全音标、中文释义和词形，并直接进入现有学习、复习、搜索和测试流程。

首版明确不包含：

- 联网词典或在线 AI 补全。
- Edge TTS、批量 MP3 下载或网络音频缓存。
- 自动生成例句。
- 自定义词库导出、分享、商店或云同步。
- 对内置词库的删除操作。

体积门禁：

- Release APK 不超过 45 MB。
- Debug APK 不超过 65 MB。
- 保持 Manifest 无 `INTERNET` 权限。

## 2. 首页与导入流程

首页继续保持极简。在顶部当前词库下拉框右侧增加一个小型导入图标，不新增大卡片。

点击导入图标后：

1. 底部面板说明支持 `.xlsx` 和 `.csv`，并展示推荐列名。
2. 用户点击“选择文件”，通过 Android Storage Access Framework 选择文件。
3. 应用立即把文件复制到内部临时目录，避免后续 URI 权限失效。
4. 全屏导入向导显示词库名称、列识别结果和前几行预览。
5. 自动识别失败时才显示手动列映射。
6. 后台完成全量解析、清洗和 ECDICT 补全。
7. 显示有效行、空行、格式错误和重复词统计。
8. 用户统一选择“保留现有内容”或“用导入内容更新”。
9. 后台以原子事务提交。
10. 完成页只显示“开始学习”和“返回首页”。

导入进行时用户可以离开向导。首页显示一条紧凑的进度信息，例如“正在校验 50/100”，但不发送系统通知。

首页词库下拉列表分为“内置词库”和“自定义词库”。“我的”增加“词库管理”，支持重命名和删除自定义词库。

## 3. 数据模型

Room 数据库从 v2 升级到 v3。

### WordEntity

保留现有字段，新增：

- `isCustom: Boolean`：表示该规范词条最初是否由用户导入创建。

`isCustom` 不代表词库归属。一个内置词条也可以被关联到自定义词库。

### SourceEntity

- `sourceId: String`
- `displayName: String`
- `type: BUILTIN | CUSTOM`
- `originalFileName: String?`
- `createdAtEpochMillis: Long`
- `updatedAtEpochMillis: Long`
- `wordCount: Int`

现有五类内置词库在迁移时生成固定 Source，ID 继续使用当前分类键，避免破坏用户当前词库设置。

### WordSourceCrossRef

- `sourceId: String`
- `wordId: String`
- `importOrder: Int`

主键为 `(sourceId, wordId)`。它允许一个词同时属于内置词库和多个自定义词库，且只维护一份学习进度。

### ImportJobEntity 与 ImportDraftEntity

`ImportJobEntity` 保存文件名、词库名、状态、进度、列映射、统计、错误信息和临时文件路径。`ImportDraftEntity` 保存校验后的待导入行及 ECDICT 补全结果。

导入完成、取消或失败清理时删除 Draft 和内部临时文件。

## 4. 重复词与进度规则

词条规范键由英文执行 Unicode 规范化、去首尾空白、合并连续空白并转为小写后得到。

导入时遇到已存在词条：

- “保留现有内容”：不修改规范词条，但仍将它关联到新自定义词库。
- “用导入内容更新”：用户文件中的非空字段覆盖对应内容，空字段不清空现有数据；随后关联到新词库。
- ECDICT 只填补仍为空的字段，不覆盖用户文件或已有内容。

收藏、错题次数、SM-2、掌握状态和学习事件始终保留并跨词库共享。

删除自定义词库时：

- 删除 Source 和 CrossRef。
- 仅当某个自定义词条不再属于任何 Source 时，才删除词条及其进度。
- 内置词条即使曾出现在自定义词库中也不会被删除。
- 删除前必须二次确认。

## 5. 文件解析与清洗

依赖 `org.dhatim:fastexcel-reader:0.20.x` 流式读取 XLSX。CSV 使用项目内轻量解析器，避免引入大型表格依赖。SimpleMagic 不加入 APK；文件类型由 SAF MIME、扩展名和文件签名联合判断。

支持的列：

- 英文（必需）
- 中文
- 音标
- 英文例句
- 例句翻译
- 备注

常见中英文列名自动映射。识别不确定时由用户手动选择。

清洗规则：

- Unicode 规范化。
- 去首尾空格并合并连续空白。
- 允许单词、短语、连字符、撇号和分号表达。
- 拒绝没有拉丁字母的英文值。
- CSV 支持 UTF-8、UTF-8 BOM，并在检测失败时回退 GB18030。
- XLSX 读取第一个非空工作表；公式只读取缓存值，不执行公式。
- 文件限制为 20 MB、20,000 行。

空行被忽略；无效行进入结果摘要但不提交。缺少英文列、损坏文件、没有有效词条或超限时整次准备流程失败。

## 6. ECDICT 与发音

使用 ECDICT 的精简派生库，目标为 8–12 万常用词，仅保留：

- `word`
- `phonetic`
- `translation`
- `exchange`

ECDICT 官方基础版约 76 万词，`detail` 例句和 `audio` 字段仍标记为待添加，因此首版不依赖它们：

- https://github.com/skywind3000/ECDICT

构建阶段生成只读 SQLite，并压缩为 `ecdict_compact.db.gz` 放入 assets。首次需要时解压到 `noBackupFilesDir/dictionaries`，校验哈希后以只读模式打开。目标压缩体积为 6–12 MB。

字段优先级：

1. 用户文件的非空字段。
2. 已有规范词条的非空字段。
3. ECDICT 音标、中文释义和词形。
4. 留空。

ECDICT 不可用时导入继续完成，只是不做本地补全。

自定义词条不生成内置 Ogg 或 MP3。学习、预览和测试时复用现有 Android TTS 回退链路。例句缺失时界面不显示例句区域。

## 7. WorkManager 流程

导入拆为两个 Worker，避免后台任务阻塞等待用户：

### PrepareImportWorker

- 流式解析文件。
- 更新“正在校验第 N/M 个词”进度。
- 执行清洗、规范化和 ECDICT 查询。
- 将结果写入 ImportDraft。
- 计算冲突和错误摘要。
- 将任务状态设为 `AWAITING_CONFIRMATION`。

### CommitImportWorker

- 读取用户选择的冲突策略。
- 在 Room 事务内创建 Source、合并 Word、写入 CrossRef。
- 更新 Source 词数。
- 清理 ImportDraft 和临时文件。
- 将任务状态设为 `COMPLETED`。

Worker 必须幂等。相同 `jobId` 的重复提交不会产生重复 Source、Word 或 CrossRef。

取消任务会取消 WorkManager、删除 Draft 和临时文件。进程被系统杀死后，WorkManager 和 Room 中的任务状态允许恢复 UI。

## 8. 错误与降级

- 文件无法读取：显示文件名和可操作原因，允许重新选择。
- 列识别失败：进入手动映射，而不是直接失败。
- ECDICT 解压或查询失败：继续导入，记录降级信息。
- 单行内容无效：跳过并计入摘要。
- 数据库提交失败：事务回滚，不创建半个词库。
- 自定义词库词量不足：学习照常；测试选项不足时从全词库补充干扰项。
- 系统 TTS 不可用：沿用现有稳定的语音帮助提示。

## 9. 测试与验收

### 单元测试

- CSV 引号、逗号、换行、UTF-8 BOM 和 GB18030。
- XLSX 首工作表、空表、公式缓存值和常见列名。
- Unicode、空白、大小写和重复词规范化。
- 20 MB 与 20,000 行边界。
- 字段优先级和 ECDICT 缺失降级。
- 冲突两种策略。

### Room 与 Worker 测试

- v2→v3 迁移保留全部进度。
- 内置 Source 建立正确。
- 同一 Word 关联多个 Source。
- Prepare/Commit 进度、重试、取消和幂等。
- 提交失败完整回滚。
- 删除自定义 Source 的孤儿清理规则。

### Compose 与设备测试

- 首页导入入口和极简布局。
- 自动映射与手动映射。
- 冲突摘要、进度、后台恢复和完成页。
- 自定义词库选择、重命名和删除。
- API 29 与 API 36 的 SAF 导入。
- 断网导入、学习、搜索和测试。
- 10,000 行性能样本和进程重启。

### 构建门禁

- `testDebugUnitTest`
- Room migration tests
- Compose instrumentation tests
- `lintDebug`
- `assembleDebug` 和 `assembleRelease`
- Manifest 不含 `INTERNET`
- Release APK ≤45 MB
- Debug APK ≤65 MB

## 10. APK 体积优化

当前 Debug APK 实测约 95.55 MB，其中 DEX 约 70.61 MB，离线音频约 22.08 MB。实施导入前先：

- 移除 `material-icons-extended`，将实际使用的少量图标改为本地 Vector/ImageVector。
- Release 启用 R8 与资源裁剪。
- 检查 Debug 的裁剪策略，确保不影响 Compose 测试。
- 将 ECDICT 作为 gzip 资产，运行时解压。

在不降低现有音频质量的前提下，现实目标为 Release 35–45 MB；约 30 MB 是接近理论下限。

## 11. 已批准的项目清理

已批准删除：

- 根目录全部 `.v21-*.patch`。
- `apply_patch_probe.txt`。
- Python `__pycache__`。
- 空 stage/tmp 目录。
- 旧音频生成过程日志。
- 无引用的旧版 `LearningHomeScreen.kt` 和 `SettingsDialog.kt`。

保留：

- 最新 APK 和验证报告。
- Android SDK 与 Gradle 缓存。
- Piper 环境与模型。
- 原始 Excel、PDF、图像及全部音频资产。
- 内容和音频构建脚本。
