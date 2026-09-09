package com.masteralanlab.emailbox.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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
        ProductField(
            value = current,
            onValueChange = { },
            readOnly = true,
            enabled = enabled,
            label = label,
            trailing = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            supporting = supporting,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
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

/**
 * Emailbox 的输入控件：标签与内容共用一条阅读线，容器用 tonal surface + hairline，
 * 不用表单示例式的浮动 outline。56dp 高度是视觉节奏和无障碍触达的共同下限。
 */
@Composable
fun ProductField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    supporting: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    var focused by remember { mutableStateOf(false) }
    val border = when {
        isError -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val container = if (enabled) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val content = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = if (singleLine) 56.dp else 136.dp),
            shape = MaterialTheme.shapes.small,
            color = container,
            contentColor = content,
            border = BorderStroke(if (focused) 2.dp else 1.dp, border),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.let {
                    BoxedFieldIcon(content = it)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        readOnly = readOnly,
                        singleLine = singleLine,
                        minLines = minLines,
                        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                        visualTransformation = visualTransformation,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        textStyle = textStyle.copy(color = content),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (value.isBlank() && !placeholder.isNullOrBlank()) {
                                Text(
                                    placeholder,
                                    style = textStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused },
                    )
                }
                trailing?.invoke()
            }
        }
        if (!supporting.isNullOrBlank()) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 5.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun BoxedFieldIcon(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.size(28.dp),
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { content() }
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
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
