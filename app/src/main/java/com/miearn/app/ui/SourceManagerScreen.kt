package com.miearn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.miearn.app.data.local.SourceEntity
import com.miearn.app.data.local.SourceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceManagerScreen(
    sources: List<SourceEntity>,
    onBack: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val custom = sources.filter { it.type == SourceType.CUSTOM.name }
    var renameSource by remember { mutableStateOf<SourceEntity?>(null) }
    var deleteSource by remember { mutableStateOf<SourceEntity?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自定义词库") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        if (custom.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("还没有自定义词库", style = MaterialTheme.typography.titleMedium)
                Text("可在学习首页点击“导入”添加 CSV 或 Excel 词库。")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(custom, key = SourceEntity::sourceId) { source ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(source.displayName, style = MaterialTheme.typography.titleMedium)
                                Text("${source.wordCount} 个词", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { renameSource = source }) { Text("重命名") }
                            TextButton(onClick = { deleteSource = source }) { Text("删除") }
                        }
                    }
                }
            }
        }
    }

    renameSource?.let { source ->
        var name by remember(source.sourceId) { mutableStateOf(source.displayName) }
        AlertDialog(
            onDismissRequest = { renameSource = null },
            title = { Text("重命名词库") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) onRename(source.sourceId, name)
                        renameSource = null
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameSource = null }) { Text("取消") } },
        )
    }

    deleteSource?.let { source ->
        AlertDialog(
            onDismissRequest = { deleteSource = null },
            title = { Text("删除“${source.displayName}”？") },
            text = { Text("仅属于该词库的自定义词条及其学习记录会被删除，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(source.sourceId)
                        deleteSource = null
                    },
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { deleteSource = null }) { Text("取消") } },
        )
    }
}
