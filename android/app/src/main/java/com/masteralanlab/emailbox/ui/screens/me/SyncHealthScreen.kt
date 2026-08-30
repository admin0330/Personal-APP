package com.masteralanlab.emailbox.ui.screens.me

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.ProxyGroupStatus
import com.masteralanlab.emailbox.data.remote.SwitchProxyNodeRequest
import com.masteralanlab.emailbox.data.remote.SyncHealth
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.data.remote.apiCallUnit
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.EmptyBox
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncHealthState(
    val status: SyncHealth? = null,
    val latencyMs: Long? = null,
    val loading: Boolean = true,
    val switching: Boolean = false,
)

class SyncHealthViewModel : ViewModel() {
    val state = MutableStateFlow(SyncHealthState())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun load() = viewModelScope.launch {
        state.update { it.copy(loading = true) }
        val started = SystemClock.elapsedRealtime()
        when (val result = apiCall { syncHealth() }) {
            is ApiResult.Success -> state.value = SyncHealthState(result.data, SystemClock.elapsedRealtime() - started, false)
            is ApiResult.Failure -> {
                state.update { it.copy(loading = false) }
                _messages.tryEmit(result.message)
            }
        }
    }

    fun switch(group: ProxyGroupStatus, node: String, done: () -> Unit) = viewModelScope.launch {
        state.update { it.copy(switching = true) }
        when (val result = apiCallUnit { switchProxyNode(SwitchProxyNodeRequest(group.name, node)) }) {
            is ApiResult.Success -> {
                done()
                _messages.tryEmit("${group.name} 已切换到 $node")
                load()
            }
            is ApiResult.Failure -> {
                state.update { it.copy(switching = false) }
                _messages.tryEmit(result.message)
            }
        }
    }
}

@Composable
fun SyncHealthScreen(onBack: () -> Unit, vm: SyncHealthViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var selected by remember { mutableStateOf<ProxyGroupStatus?>(null) }

    LaunchedEffect(Unit) {
        vm.load()
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "同步健康中心",
                onBack = onBack,
                actions = {
                    IconButton(onClick = vm::load, enabled = !state.loading) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.status?.let { health ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CloudDone, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("服务器同步链路正常", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "响应 ${state.latencyMs ?: 0} ms · ${health.groups.size} 个可切换策略",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f),
                            )
                        }
                    }
                }
                Text(
                    "服务器节点",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(health.groups, key = { it.name }) { group ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selected = group },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(group.name, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        group.current,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(Icons.Outlined.ChevronRight, contentDescription = "切换")
                            }
                        }
                    }
                }
            }
            if (!state.loading && state.status == null) {
                EmptyBox("暂时无法取得同步状态")
            }
        }
    }

    selected?.let { group ->
        AlertDialog(
            onDismissRequest = { if (!state.switching) selected = null },
            title = { Text("切换 ${group.name}") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().height(400.dp)) {
                    items(group.choices) { node ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !state.switching) {
                                vm.switch(group, node) { selected = null }
                            }.padding(vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(node, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (node == group.current) Icon(Icons.Outlined.CheckCircle, contentDescription = "当前节点")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { selected = null }, enabled = !state.switching) { Text("取消") } },
        )
    }
}
