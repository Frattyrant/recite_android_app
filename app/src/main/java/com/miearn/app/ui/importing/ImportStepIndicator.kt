package com.miearn.app.ui.importing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.miearn.app.data.local.ImportJobEntity
import com.miearn.app.data.local.ImportJobStatus

@Composable
fun ImportStepIndicator(job: ImportJobEntity?) {
    val activeStep = when (job?.status) {
        null -> 0
        ImportJobStatus.COPYING.name,
        ImportJobStatus.PREPARING.name,
        ImportJobStatus.AWAITING_MAPPING.name,
        -> 1
        ImportJobStatus.AWAITING_CONFIRMATION.name,
        ImportJobStatus.COMMITTING.name,
        -> 2
        ImportJobStatus.COMPLETED.name -> 3
        else -> 0
    }
    Row(Modifier.fillMaxWidth()) {
        listOf("选择文件", "确认列", "校验", "完成").forEachIndexed { index, label ->
            val active = index <= activeStep
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (active) "${index + 1}" else "·",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                        .padding(top = 4.dp),
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    label,
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
