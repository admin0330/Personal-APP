package com.masteralanlab.emailbox.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.masteralanlab.emailbox.ui.components.RollingNumber
import androidx.compose.ui.unit.dp
import com.masteralanlab.emailbox.data.CachedMail
import com.masteralanlab.emailbox.data.MailCategory
import com.masteralanlab.emailbox.data.MailSearchFilters
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.SecureMailCache
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.ProductField
import com.masteralanlab.emailbox.util.displayName
import com.masteralanlab.emailbox.util.formatShortTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailSearchScreen(
    onBack: () -> Unit,
    onOpen: (CachedMail) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var unreadOnly by remember { mutableStateOf(false) }
    var attachmentsOnly by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyList<CachedMail>()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchGeneration by remember { mutableLongStateOf(0L) }
    val tenant = Prefs.tenantId.orEmpty()

    LaunchedEffect(query, category, unreadOnly, attachmentsOnly, tenant) {
        val currentGen = ++searchGeneration
        isSearching = true
        delay(200)
        val res = withContext(Dispatchers.IO) {
            if (tenant.isBlank()) emptyList()
            else SecureMailCache.search(tenant, MailSearchFilters(query, category = category, unreadOnly = unreadOnly, attachmentsOnly = attachmentsOnly))
        }
        if (currentGen == searchGeneration) {
            results = res
            isSearching = false
        }
    }

    Scaffold(
        topBar = { AppTopBar("离线缓存搜索", onBack = onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ProductField(
                value = query,
                onValueChange = { query = it },
                label = "搜索缓存邮件",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                leading = { androidx.compose.material3.Icon(Ym1rIcons.Search, null) },
                placeholder = "发件人、主题、摘要或已缓存正文",
                singleLine = true,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { androidx.compose.material3.Text("全部") })
                MailCategory.all.forEach { item ->
                    FilterChip(selected = category == item, onClick = { category = item }, label = { androidx.compose.material3.Text(item) })
                }
            }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = unreadOnly, onClick = { unreadOnly = !unreadOnly }, label = { androidx.compose.material3.Text("未读") })
                FilterChip(selected = attachmentsOnly, onClick = { attachmentsOnly = !attachmentsOnly }, label = { androidx.compose.material3.Text("含附件") })
            }
            if (!isSearching && results.isEmpty()) {
                EmptyBox(if (query.isBlank() && category == null && !unreadOnly && !attachmentsOnly) "输入关键词搜索本机加密缓存" else "离线缓存中没有匹配邮件")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RollingNumber(
                                value = results.size.toLong(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            androidx.compose.material3.Text(
                                " 条离线缓存结果",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(results, key = { "${it.accountId}:${it.item.folder}:${it.item.id_mode}:${it.item.id}" }) { result ->
                        ListItem(
                            headlineContent = { Text(result.item.subject.ifBlank { "（无主题）" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text("${displayName(result.item.from)} · ${result.category} · 邮箱 ${result.accountId.take(8)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = { Text(formatShortTime(result.item.received_at), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.clickable { onOpen(result) },
                        )
                    }
                }
            }
        }
    }
}
