package com.miearn.app.ui.importing

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.miearn.app.data.local.ImportConflictPolicy
import com.miearn.app.data.local.ImportJobEntity
import com.miearn.app.data.local.ImportJobStatus
import com.miearn.app.importing.ColumnRole
import com.miearn.app.importing.ImportColumnMapping
import com.miearn.app.importing.ImportMappingCodec
import com.miearn.app.importing.ImportSanitizer
import com.miearn.app.ui.canRetryImport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWizardScreen(
    job: ImportJobEntity?,
    localError: String?,
    onBack: () -> Unit,
    onCancel: () -> Unit = onBack,
    onUseSource: (String) -> Unit = {},
    onReselect: () -> Unit = onBack,
    onFileSelected: (Uri, String) -> Unit,
    onTextSelected: (String, String) -> Unit = { _, _ -> },
    onMapping: (ImportColumnMapping) -> Unit,
    onCommit: (ImportConflictPolicy) -> Unit,
    onClearError: () -> Unit,
    onRetry: () -> Unit = {},
) {
    var sourceName by rememberSaveable { mutableStateOf("") }
    var pasteMode by rememberSaveable { mutableStateOf(false) }
    var pastedText by rememberSaveable { mutableStateOf("") }
    var pickerMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerLaunching by rememberSaveable { mutableStateOf(false) }
    val contentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickerLaunching = false
        if (uri != null) {
            pickerMessage = null
            onFileSelected(uri, sourceName)
        } else {
            pickerMessage = importPickerResultMessage(hasUri = false)
        }
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickerLaunching = false
        if (uri != null) {
            pickerMessage = null
            onFileSelected(uri, sourceName)
        } else {
            pickerMessage = importPickerResultMessage(hasUri = false)
        }
    }
    fun launchPicker(mode: ImportPickerMode) {
        pickerLaunching = true
        pickerMessage = "正在打开文件选择器…"
        try {
            when (mode) {
                ImportPickerMode.GET_CONTENT -> contentPicker.launch("*/*")
                ImportPickerMode.OPEN_DOCUMENT -> documentPicker.launch(arrayOf("*/*"))
            }
        } catch (_: ActivityNotFoundException) {
            nextImportPicker(mode)?.let(::launchPicker) ?: run {
                pickerLaunching = false
                pickerMessage = "系统文件选择器不可用，请直接粘贴文本。"
            }
        } catch (_: SecurityException) {
            nextImportPicker(mode)?.let(::launchPicker) ?: run {
                pickerLaunching = false
                pickerMessage = "系统文件选择器没有访问权限，请直接粘贴文本。"
            }
        } catch (_: IllegalStateException) {
            pickerLaunching = false
            pickerMessage = "当前页面暂时无法打开文件选择器，请稍后重试或直接粘贴文本。"
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入词库") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ImportStepIndicator(job)
            if (localError != null) {
                Text(localError, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onClearError) { Text("知道了") }
            }
            if (job?.status == ImportJobStatus.FAILED.name) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "导入失败${job.errorCode?.let { "（$it）" }.orEmpty()}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(job.errorMessage ?: "文件无法解析，请检查格式后重试。")
                    job.recoveryHint?.takeIf(String::isNotBlank)?.let { hint ->
                        Text(hint, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canRetryImport(job)) {
                            Button(onClick = onRetry) { Text("重试") }
                        }
                        OutlinedButton(onClick = onReselect) { Text("重新选择") }
                    }
                }
            }
            when {
                job == null -> {
                    Text("导入词库文件", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "第一列可直接放英文；也支持“英文、中文、音标、例句、例句翻译、备注”等表头。数据只在本机处理。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = sourceName,
                        onValueChange = { sourceName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("词库名称（可稍后修改）") },
                        placeholder = { Text("例如：考研英语") },
                    )
                    if (pickerMessage != null) {
                        Text(
                            pickerMessage.orEmpty(),
                            color = if (pickerLaunching) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = { launchPicker(ImportPickerMode.GET_CONTENT) },
                        enabled = !pickerLaunching,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (pickerLaunching) "正在打开…" else "选择 .xlsx、.csv、.tsv 或 .txt 文件")
                    }
                    OutlinedButton(
                        onClick = { launchPicker(ImportPickerMode.OPEN_DOCUMENT) },
                        enabled = !pickerLaunching,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("系统文件选择器（兼容模式）")
                    }
                    TextButton(onClick = { pasteMode = !pasteMode }) {
                        Text(if (pasteMode) "收起直接粘贴" else "无法选择文件？直接粘贴文本")
                    }
                    if (pasteMode) {
                        OutlinedTextField(
                            value = pastedText,
                            onValueChange = { pastedText = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5,
                            maxLines = 10,
                            label = { Text("粘贴词库内容") },
                            placeholder = { Text("每行一个单词，也支持：英文<Tab>中文") },
                        )
                        Button(
                            onClick = { onTextSelected(pastedText, sourceName) },
                            enabled = pastedText.isNotBlank() && !pickerLaunching,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("开始导入粘贴内容")
                        }
                    }
                    Text(
                        "限制：文件不超过 20 MB，最多 20,000 行。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                job.status == ImportJobStatus.COPYING.name ||
                    job.status == ImportJobStatus.PREPARING.name -> {
                    Text("正在校验词库", style = MaterialTheme.typography.headlineSmall)
                    if (job.totalRows > 0) {
                        val progress = job.processedRows.toFloat() / job.totalRows
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("正在校验第 ${job.processedRows}/${job.totalRows} 个词…")
                    } else {
                        CircularProgressIndicator()
                        Text("正在读取 ${job.originalFileName}…")
                    }
                    Text(
                        "可以离开此页面，导入任务会继续运行。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("取消导入")
                    }
                }

                job.status == ImportJobStatus.AWAITING_MAPPING.name -> {
                    ImportMappingEditor(job = job, onConfirm = onMapping)
                }

                job.status == ImportJobStatus.AWAITING_CONFIRMATION.name -> {
                    Text("校验完成", style = MaterialTheme.typography.headlineSmall)
                    ImportSummary(job)
                    HorizontalDivider()
                    Text("遇到已经存在的单词时：")
                    Button(
                        onClick = { onCommit(ImportConflictPolicy.KEEP_EXISTING) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("保留现有内容并加入新词库") }
                    OutlinedButton(
                        onClick = { onCommit(ImportConflictPolicy.UPDATE_NON_EMPTY) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("用导入文件的非空内容更新") }
                }

                job.status == ImportJobStatus.COMMITTING.name -> {
                    Text("正在保存词库…", style = MaterialTheme.typography.headlineSmall)
                    CircularProgressIndicator()
                }

                job.status == ImportJobStatus.COMPLETED.name -> {
                    Text("导入完成", style = MaterialTheme.typography.headlineSmall)
                    Text("“${job.sourceName}”已加入 ${job.validRows} 个有效词条。")
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onUseSource(job.sourceId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("开始学习这个词库")
                    }
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("稍后再学")
                    }
                }

                job.status == ImportJobStatus.CANCELLED.name -> {
                    Text("导入已取消", style = MaterialTheme.typography.headlineSmall)
                    Text("临时文件和未提交的词条已清理，现有学习进度不受影响。")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
                }

                job.status == ImportJobStatus.FAILED.name -> Unit

                else -> {
                    Text("导入未完成", style = MaterialTheme.typography.headlineSmall)
                    Text(job.errorMessage ?: "文件无法解析，请检查格式后重试。")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onBack) { Text("关闭") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportMappingEditor(
    job: ImportJobEntity,
    onConfirm: (ImportColumnMapping) -> Unit,
) {
    val headers = remember(job.headersJson) {
        ImportMappingCodec.decodeHeaders(job.headersJson)
    }
    val preview = remember(job.previewRowsJson) {
        ImportMappingCodec.decodePreview(job.previewRowsJson)
    }
    var roles by remember(job.jobId, job.headersJson) {
        mutableStateOf(
            headers.indices.associateWith { index ->
                ImportSanitizer.detectHeader(headers[index])
            },
        )
    }

    Text("确认每一列的含义", style = MaterialTheme.typography.headlineSmall)
    Text("必须指定一列为“英文”；不需要的列选择“忽略”。")
    headers.forEachIndexed { index, header ->
        var expanded by remember(index) { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                header.ifBlank { "第 ${index + 1} 列" },
                style = MaterialTheme.typography.titleMedium,
            )
            val samples = preview.mapNotNull { it.cells.getOrNull(index) }
                .filter(String::isNotBlank)
                .take(2)
                .joinToString(" / ")
            if (samples.isNotBlank()) {
                Text(samples, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { expanded = true }) {
                Text((roles[index] ?: ColumnRole.IGNORE).label)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                ColumnRole.entries.forEach { role ->
                    DropdownMenuItem(
                        text = { Text(role.label) },
                        onClick = {
                            roles = roles
                                .mapValues { (otherIndex, oldRole) ->
                                    if (
                                        otherIndex != index &&
                                        role != ColumnRole.IGNORE &&
                                        oldRole == role
                                    ) {
                                        ColumnRole.IGNORE
                                    } else {
                                        oldRole
                                    }
                                }
                                .toMutableMap()
                                .apply { put(index, role) }
                            expanded = false
                        },
                    )
                }
            }
        }
    }
    val valid = roles.values.count { it == ColumnRole.ENGLISH } == 1
    Button(
        onClick = {
            onConfirm(
                ImportColumnMapping(
                    roles.filterValues { it != ColumnRole.IGNORE },
                ),
            )
        },
        enabled = valid,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("按此映射继续校验")
    }
}

@Composable
private fun ImportSummary(job: ImportJobEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("有效词条：${job.validRows}")
        Text("格式错误：${job.invalidRows}")
        Text("文件内重复：${job.duplicateRows}")
    }
}

private val ColumnRole.label: String
    get() = when (this) {
        ColumnRole.ENGLISH -> "英文"
        ColumnRole.CHINESE -> "中文释义"
        ColumnRole.PHONETIC -> "音标"
        ColumnRole.EXAMPLE_EN -> "英文例句"
        ColumnRole.EXAMPLE_ZH -> "例句翻译"
        ColumnRole.NOTE -> "备注"
        ColumnRole.IGNORE -> "忽略"
    }
