package com.masteralanlab.emailbox.ui.components

import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.masteralanlab.emailbox.ui.theme.LocalDarkTheme
import com.masteralanlab.emailbox.ui.theme.LocalYm1rColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.masteralanlab.emailbox.ui.theme.IosContinuousCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Legacy names delegate to the active semantic palette; no separate fixed colors. */
object AppleColors {
    val Blue: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.accent
    val Green: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.success
    val Indigo: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.accent
    val Orange: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.warning
    val Pink: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.accent
    val Purple: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.accent
    val Red: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.danger
    val MusicRed: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.danger
    val Teal: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.accent
    val Yellow: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.warning
    val Gray: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.textSecondary
    val Gray2: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.textSecondary
    val Gray3: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.separator
    val Gray4: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.disabled
    val Gray5: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.surfaceRaised
    val Gray6: Color
        @Composable @ReadOnlyComposable get() = LocalYm1rColors.current.background
}

/**
 * Apple iOS 设置经典的圆角微图标（Squircle）。
 * 尺寸默认为 28x28dp，圆角为 7dp，搭配纯白矢量图标。
 */
@Composable
fun AppleIconSquircle(
    icon: ImageVector,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    tint: Color = backgroundColor,
    size: Dp = 28.dp,
    iconSize: Dp = 18.dp,
    shape: Shape = IosContinuousCornerShape(7.dp),
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(backgroundColor.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Apple SwiftUI 标准 Toggle 开关。
 * 开启为 iOS 经典薄荷绿 (#34C759)，关闭为原生浅灰，无边缘突兀描边。
 */
@Composable
fun AppleToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isDark = LocalDarkTheme.current
    val ym1rColors = LocalYm1rColors.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = ym1rColors.accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
            uncheckedTrackColor = ym1rColors.surfaceRaised,
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedTrackColor = ym1rColors.disabled,
            disabledUncheckedTrackColor = ym1rColors.disabled,
        ),
    )
}

/**
 * SwiftUI 风格的 Inset Grouped 分组容器。
 * 支持大写小字 Header 与底部 Footnote 说明文本。
 */
@Composable
fun AppleListSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val ym1rColors = LocalYm1rColors.current
    val cardColor = ym1rColors.surface
    val borderColor = ym1rColors.separator

    Column(modifier = modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank() || titleTrailing != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 0.2.sp,
                        ),
                        color = AppleColors.Gray,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                titleTrailing?.invoke()
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = cardColor,
            border = BorderStroke(0.5.dp, borderColor),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        }
        if (!footer.isNullOrBlank()) {
            Text(
                text = footer,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                ),
                color = AppleColors.Gray,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

/**
 * Apple Inset Grouped 列表行。
 * 支持左侧图标 Squircle、主标题、副标题、右侧自定义控件（Switch、Detail、Chevron）与智能缩进分割线。
 */
@Composable
fun AppleListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    control: (@Composable () -> Unit)? = null,
) {
    val isDark = LocalDarkTheme.current
    val ym1rColors = LocalYm1rColors.current
    val dividerColor = ym1rColors.separator
    val hasIcon = icon != null

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .appleListRowClickable(onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontFamily = FontFamily.SansSerif,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif,
                        ),
                        color = AppleColors.Gray,
                    )
                }
            }
            val effectiveTrailing: (@Composable () -> Unit)? = trailing ?: if (onClick != null) {
                {
                    Icon(
                        Ym1rIcons.ChevronRight,
                        contentDescription = null,
                        tint = AppleColors.Gray.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else null

            if (effectiveTrailing != null) {
                Spacer(modifier = Modifier.width(12.dp))
                effectiveTrailing()
            }
        }
        if (control != null) {
            Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                control()
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (hasIcon) 56.dp else 16.dp),
                thickness = 0.5.dp,
                color = dividerColor,
            )
        }
    }
}

/**
 * Apple iOS 原生分段控制器 (Segmented Control / Picker)。
 * 由 selectedIndex 驱动的连续平滑弹簧滑动指示器，具备跟手物理反馈与多主题色彩适配。
 */
@Composable
fun AppleSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return

    val isDark = LocalDarkTheme.current
    val ym1rColors = LocalYm1rColors.current

    val containerColor = ym1rColors.surfaceRaised
    val selectedPillColor = ym1rColors.surface

    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.coerceIn(0, options.lastIndex).toFloat(),
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = 750f,
        ),
        label = "segment_indicator",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(maxOf(48f, 20f * LocalDensity.current.fontScale + 24f).dp)
            .clip(IosContinuousCornerShape(9.dp))
            .background(containerColor)
            .padding(2.dp),
    ) {
        val count = options.size
        val segmentWidth = maxWidth / count

        // 连续滑动选中指示器 (Continuous spring sliding indicator)
        Box(
            modifier = Modifier
                .width(segmentWidth)
                .fillMaxHeight()
                .offset(x = segmentWidth * animatedIndex)
                .shadow(elevation = 2.dp, shape = IosContinuousCornerShape(7.dp), clip = false)
                .background(selectedPillColor, IosContinuousCornerShape(7.dp)),
        )

        // 选项文字层与点击触发
        Row(
            modifier = Modifier.fillMaxSize().selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(IosContinuousCornerShape(7.dp))
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = FontFamily.SansSerif,
                        ),
                        color = when {
                            isSelected -> ym1rColors.textPrimary
                            else -> ym1rColors.textSecondary
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Apple iOS 原生胶囊搜索栏。
 * 极简微灰半透明底色、居中放大镜、右侧清除按钮与丝滑占位符。
 */
@Composable
fun AppleSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索",
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val isDark = LocalDarkTheme.current
    val ym1rColors = LocalYm1rColors.current
    val bgColor = ym1rColors.surfaceRaised

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(maxOf(48f, 20f * LocalDensity.current.fontScale + 24f).dp),
        shape = IosContinuousCornerShape(10.dp),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(8.dp))
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                cursorBrush = SolidColor(ym1rColors.accent),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.SansSerif,
                ),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = AppleColors.Gray,
                                fontFamily = FontFamily.SansSerif,
                            ),
                        )
                    }
                    innerTextField()
                },
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Ym1rIcons.X, contentDescription = "清除搜索", tint = ym1rColors.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * iOS 27 Beta 标准胶囊状态徽章。
 */
@Composable
fun AppleBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = AppleColors.Blue.copy(alpha = 0.12f),
    contentColor: Color = AppleColors.Blue,
) {
    Surface(
        modifier = modifier,
        shape = IosContinuousCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(0.5.dp, contentColor.copy(alpha = 0.25f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = contentColor,
            ),
        )
    }
}

/**
 * Apple Music 风格浮动液态玻璃操作按钮（FAB）。
 */
@Composable
fun AppleFloatingActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    containerColor: Color = AppleColors.Blue,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    size: Dp = 56.dp,
    iconSize: Dp = 26.dp,
) {
    val isDark = LocalDarkTheme.current
    val ym1rColors = LocalYm1rColors.current
    val shadowColor = if (isDark) Color.Black.copy(alpha = 0.5f) else containerColor.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 8.dp,
                shape = androidx.compose.foundation.shape.CircleShape,
                ambientColor = shadowColor,
                spotColor = shadowColor,
            )
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(containerColor)
            .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.35f)), androidx.compose.foundation.shape.CircleShape)
            .appleClickable(pressedScale = 0.92f, pressedAlpha = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Apple Music 杂志展台风格的 Hero 卡片。
 */
@Composable
fun AppleHeroCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    tag: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val isDark = LocalDarkTheme.current
    val ym1rColors = LocalYm1rColors.current
    val cardBg = ym1rColors.surface
    val borderColor = ym1rColors.separator

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = IosContinuousCornerShape(20.dp),
        color = cardBg,
        border = BorderStroke(0.5.dp, borderColor),
        shadowElevation = if (isDark) 0.dp else 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (tag != null) {
                        Text(
                            text = tag.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = AppleColors.Blue,
                                letterSpacing = 0.5.sp,
                            ),
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.4).sp,
                        ),
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = AppleColors.Gray,
                            ),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                trailing?.invoke()
            }
            if (content != null) {
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}
