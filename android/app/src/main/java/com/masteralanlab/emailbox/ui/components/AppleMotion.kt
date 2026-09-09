package com.masteralanlab.emailbox.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.masteralanlab.emailbox.ui.theme.LocalDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset

/**
 * Apple iOS 物理弹簧阻尼规范体系
 */
object AppleSpring {
    // 默认触控回弹：轻微弹性，干脆爽利，完全还原 iPhone Taptic 物理回弹手感
    val Bouncy: SpringSpec<Float> = spring(dampingRatio = 0.72f, stiffness = 420f)
    // 快速按下压缩：高刚度阻尼，跟手极快
    val PressCompress: SpringSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 1200f)
    // 页面滑动转场弹簧
    val NavigationSlide: SpringSpec<IntOffset> = spring(dampingRatio = 0.85f, stiffness = 420f)
    val NavigationFade: SpringSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 420f)
}

/**
 * Apple iOS 原生点击动效：
 * 1. 彻底禁用 Android 放射状圆形水波纹（Ripple）；
 * 2. 按下瞬间伴随细腻触觉脉冲（Taptic Feedback）；
 * 3. 元素整体微缩放（默认 0.96f）与轻微呼吸透明度；
 * 4. 释放时以 iOS 阻尼弹簧迅速弹性回位。
 */
fun Modifier.appleClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    pressedAlpha: Float = 0.90f,
    hapticFeedback: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isPressed) {
        if (isPressed && hapticFeedback && enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = if (isPressed) AppleSpring.PressCompress else AppleSpring.Bouncy,
        label = "apple_scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedAlpha else 1f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 600f),
        label = "apple_alpha",
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

/**
 * 支持长按的 Apple iOS 动效（用于记账列表项、笔记卡片、邮箱条目等）。
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.appleCombinedClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    pressedAlpha: Float = 0.92f,
    hapticFeedback: Boolean = true,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isPressed) {
        if (isPressed && hapticFeedback && enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = if (isPressed) AppleSpring.PressCompress else AppleSpring.Bouncy,
        label = "apple_combined_scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedAlpha else 1f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 600f),
        label = "apple_combined_alpha",
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/**
 * iOS Inset Grouped 设置列表行专用的触控反馈：
 * 点按呈现柔和半透明灰阶高光背景与轻微微缩（0.985f），释放平滑褪去。
 */
fun Modifier.appleListRowClickable(
    enabled: Boolean = true,
    hapticFeedback: Boolean = true,
    onClick: (() -> Unit)?,
): Modifier = composed {
    if (onClick == null) return@composed this

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val isDark = LocalDarkTheme.current

    LaunchedEffect(isPressed) {
        if (isPressed && hapticFeedback && enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.985f else 1f,
        animationSpec = if (isPressed) AppleSpring.PressCompress else AppleSpring.Bouncy,
        label = "apple_row_scale",
    )

    val highlightColor = if (isDark) Color(0x33FFFFFF) else Color(0x18000000)
    val bgColor by animateColorAsState(
        targetValue = if (isPressed && enabled) highlightColor else Color.Transparent,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 600f),
        label = "apple_row_bg",
    )

    this
        .background(bgColor)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
}

/**
 * 纯按压动效 Modifier，用于已具有自带手势或复杂子视图的容器。
 */
fun Modifier.applePressEffect(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    pressedAlpha: Float = 0.90f,
    hapticFeedback: Boolean = true,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isPressed) {
        if (isPressed && hapticFeedback && enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = if (isPressed) AppleSpring.PressCompress else AppleSpring.Bouncy,
        label = "apple_press_scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedAlpha else 1f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 600f),
        label = "apple_press_alpha",
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}
