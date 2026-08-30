package com.masteralanlab.emailbox.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masteralanlab.emailbox.util.formatBytes

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val info = state.info ?: return
    val busy = state.phase == UpdatePhase.Downloading || state.phase == UpdatePhase.Verifying

    AlertDialog(
        onDismissRequest = {
            // 强制更新不允许关闭；下载中也不允许关闭
            if (!busy && !info.isMandatory) onDismiss()
        },
        icon = {
            Icon(
                Icons.Outlined.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("发现新版本 ${info.versionName}") },
        text = {
            Column {
                if (info.isMandatory) {
                    Text(
                        "这是必须安装的更新版本。",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "版本码 ${info.versionCode}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (info.size > 0) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            formatBytes(info.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (info.changelog.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text("更新内容", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            info.changelog,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                when (state.phase) {
                    UpdatePhase.Downloading -> {
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "下载中 ${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    UpdatePhase.Verifying -> {
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("正在校验安装包…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    UpdatePhase.Failed -> {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            state.error ?: "更新失败",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    else -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    when (state.phase) {
                        UpdatePhase.Done -> onInstall()
                        else -> onDownload()
                    }
                },
            ) {
                Text(
                    when (state.phase) {
                        UpdatePhase.Failed -> "重试"
                        UpdatePhase.Done -> "安装"
                        else -> "立即更新"
                    }
                )
            }
        },
        dismissButton = {
            if (!info.isMandatory && !busy) {
                TextButton(onClick = onDismiss) { Text("以后再说") }
            }
        },
    )
}

/** 下载完成但用户没点安装时，下次进入前台再提示一次。 */
@Composable
fun RememberInstallPrompt(state: UpdateUiState) {
    LaunchedEffect(state.phase) {
        // 安装由用户点击触发，系统安装器会覆盖在前台，这里不需要额外处理
    }
}
