package com.miearn.app.ui.importing

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWizardScreen(
    job: ImportJobEntity?,
    localError: String?,
    onBack: () -> Unit,
    onFileSelected: (Uri, String) -> Unit,
    onMapping: (ImportColumnMapping) -> Unit,
    onCommit: (ImportConflictPolicy) -> Unit,
    onClearError: () -> Unit,
) {
    var sourceName by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onFileSelected(uri, sourceName)
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
            if (localError != null) {
                Text(localError, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onClearError) { Text("知道了") }
            }
            when {
                job == null -> {
                    Text("从 CSV 或 Excel 导入", style = MaterialTheme.typography.headlineSmall)
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
                    Button(
                        onClick = {
                            picker.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/octet-stream",
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("选择 .csv 或 .xlsx 文件")
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
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("完成") }
                }

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
