package com.miearn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    SoftPageBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("自定义词库") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            if (custom.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    SoftEmptyState(
                        title = "还没有自定义词库",
                        message = "在学习首页点击“导入”，即可加入 CSV 或 Excel 词库。",
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "${custom.size} 个来源",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(custom, key = SourceEntity::sourceId) { source ->
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(15.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(source.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${source.wordCount} 个词 · 本地导入",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { renameSource = source }) {
                                    Icon(Icons.Default.Edit, contentDescription = "重命名")
                                }
                                IconButton(onClick = { deleteSource = source }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
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
                        if (name.isNotBlank()) onRename(source.sourceId, name.trim())
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
                ) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteSource = null }) { Text("取消") } },
        )
    }
}
