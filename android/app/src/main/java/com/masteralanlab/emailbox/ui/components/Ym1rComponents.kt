package com.masteralanlab.emailbox.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masteralanlab.emailbox.ui.theme.LocalYm1rColors
import com.masteralanlab.emailbox.ui.theme.Ym1rShapes
import com.masteralanlab.emailbox.ui.theme.Ym1rTypography

/**
 * Ym1r 统一标准顶部导航栏
 */
@Composable
fun Ym1rTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
) {
    val colors = LocalYm1rColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Ym1rIconButton(
                icon = Ym1rIcons.ArrowLeft,
                contentDescription = "返回",
                onClick = onBack,
            )
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = Ym1rTypography.NavTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = Ym1rTypography.Caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (actions != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
    }
}

/**
 * Ym1r 统一标准分组区块容器 (Grouped Section)
 * 采用 16dp 水平边距、Ym1rShapes.Card (16dp 平滑圆角) 与可自适应高度行组
 */
@Composable
fun Ym1rSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalYm1rColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = Ym1rTypography.Caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 14.dp, bottom = 6.dp, top = 2.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Ym1rShapes.Card)
                .background(colors.surface),
        ) {
            content()
        }

        if (!footer.isNullOrBlank()) {
            Text(
                text = footer,
                style = Ym1rTypography.Footnote,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 2.dp),
            )
        }
    }
}

/**
 * Ym1r 统一列表行 (Grouped Row)
 * 支持普通行、导航行、开关行与危险操作行，保证最小 48dp 触摸高度与规范分隔线缩进
 */
@Composable
fun Ym1rRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalYm1rColors.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val rowModifier = if (onClick != null && enabled) {
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .background(if (isPressed) colors.selection else Color.Transparent)
    } else {
        Modifier.fillMaxWidth()
    }

    Column(modifier = modifier) {
        Row(
            modifier = rowModifier
                .heightIn(min = 50.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = Ym1rTypography.Body,
                    color = when {
                        !enabled -> colors.disabled
                        destructive -> colors.danger
                        else -> colors.textPrimary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = Ym1rTypography.Subheadline,
                        color = if (enabled) colors.textSecondary else colors.disabled,
                    )
                }
            }

            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (icon != null) 58.dp else 16.dp),
                thickness = 0.5.dp,
                color = colors.separator,
            )
        }
    }
}

/**
 * 导航型列表行（带右侧 ChevronRight）
 */
@Composable
fun Ym1rNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    icon: (@Composable () -> Unit)? = null,
    showDivider: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalYm1rColors.current
    Ym1rRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = onClick,
        showDivider = showDivider,
        enabled = enabled,
        modifier = modifier,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!value.isNullOrBlank()) {
                    Text(
                        text = value,
                        style = Ym1rTypography.Callout,
                        color = colors.textSecondary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    imageVector = Ym1rIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.textSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}

/**
 * 开关型列表行
 */
@Composable
fun Ym1rSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    showDivider: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalYm1rColors.current
    val haptic = LocalHapticFeedback.current
    Ym1rRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        showDivider = showDivider,
        enabled = enabled,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(it)
                },
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = colors.accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = colors.separator,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        },
    )
}

/**
 * 统一圆角微图标底座 (Squircle)
 */
@Composable
fun Ym1rIconSquircle(
    icon: ImageVector,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    iconColor: Color = Color.White,
    size: Dp = 30.dp,
    iconSize: Dp = 17.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(Ym1rShapes.Icon)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * 统一标准按钮组件，支持触控弹性微缩放反馈与最小 48dp 触摸区域
 */
@Composable
fun Ym1rButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSecondary: Boolean = false,
    isDestructive: Boolean = false,
    icon: ImageVector? = null,
) {
    val colors = LocalYm1rColors.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 750f),
        label = "buttonScale",
    )

    val containerColor = when {
        !enabled -> colors.disabled
        isDestructive -> colors.danger
        isSecondary -> colors.surfaceRaised
        else -> colors.accent
    }

    val contentColor = when {
        !enabled -> colors.textSecondary
        isSecondary -> colors.textPrimary
        else -> Color.White
    }

    Box(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(Ym1rShapes.Button)
            .background(containerColor)
            .then(
                if (isSecondary) Modifier.border(0.5.dp, colors.separator, Ym1rShapes.Button)
                else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .semantics {
                this.contentDescription = text
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = Ym1rTypography.Callout,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
        }
    }
}

/**
 * 统一标准图标按钮，具备 48dp 无障碍触控热区
 */
@Composable
fun Ym1rIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true,
) {
    val colors = LocalYm1rColors.current
    val haptic = LocalHapticFeedback.current
    val iconTint = tint ?: (if (enabled) colors.textPrimary else colors.disabled)

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(Ym1rShapes.Row)
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * 状态标签徽章 (Badge)
 */
@Composable
fun Ym1rBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val colors = LocalYm1rColors.current
    val effectiveColor = color ?: colors.accent

    Box(
        modifier = modifier
            .clip(Ym1rShapes.Icon)
            .background(effectiveColor.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Ym1rTypography.Caption,
            color = effectiveColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 状态反馈通用视图 (空状态 / 错误状态 / 加载状态)
 */
@Composable
fun Ym1rStateView(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    isLoading: Boolean = false,
) {
    val colors = LocalYm1rColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(42.dp),
                color = colors.accent,
                strokeWidth = 3.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(Ym1rShapes.Card)
                    .background(colors.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = title,
            style = Ym1rTypography.Title,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = Ym1rTypography.Callout,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Ym1rButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(0.6f),
            )
        }
    }
}
