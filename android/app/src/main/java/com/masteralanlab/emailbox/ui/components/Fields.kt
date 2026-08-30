package com.masteralanlab.emailbox.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.masteralanlab.emailbox.data.remote.MailGroup

/**
 * 通用下拉选择框。options 为 (值, 显示名) 列表。
 * 下拉里的 value 允许为 null，用于「不限 / 自动」这类选项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    options: List<Pair<T?, String>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            supportingText = if (supporting.isNullOrBlank()) null else ({ Text(supporting) }),
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, text) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** 单选列表，用于「凭证刷新范围」这类一组互斥选项。 */
@Composable
fun <T> RadioOptionList(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** 可选：某个选项行尾部的自定义内容（如「按域名分类」行右侧的显示域名开关）。 */
    optionTrailing: (@Composable (T) -> Unit)? = null,
) {
    options.forEachIndexed { index, (value, text) ->
        if (index > 0) HorizontalDivider()
        ListItem(
            headlineContent = { Text(text, style = MaterialTheme.typography.bodyLarge) },
            leadingContent = {
                RadioButton(selected = value == selected, onClick = { onSelect(value) })
            },
            trailingContent = optionTrailing?.let { trailing -> { trailing(value) } },
            modifier = modifier.clickable { onSelect(value) },
        )
    }
}

/**
 * 分组选择器。后端不会返回明文代理地址，只有 *_masked，下拉里直接展示分组名即可。
 * 允许为空——后端会把账号落到系统默认分组。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupPicker(
    groups: List<MailGroup>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allowEmpty: Boolean = true,
) {
    val options = buildList {
        if (allowEmpty) add(null to "默认分组（不指定）")
        groups.forEach { add(it.id to it.name) }
    }
    DropdownField(
        label = "分组",
        options = options,
        selected = selectedId?.takeIf { groups.any { g -> g.id == it } },
        onSelect = onSelect,
        modifier = modifier,
        enabled = enabled,
    )
}
