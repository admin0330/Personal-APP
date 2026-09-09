package com.masteralanlab.emailbox.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.ui.theme.LocalDarkTheme
import com.masteralanlab.emailbox.ui.theme.LocalYm1rColors
import com.masteralanlab.emailbox.ui.theme.ThemeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * ============================================================================
 * 上游开源项目基线说明 (Upstream Open Source Origin & Attribution)
 * ============================================================================
 * - 原项目：Kyant0/AndroidLiquidGlass
 *   (https://github.com/Kyant0/AndroidLiquidGlass)
 * - 分支：kmp
 * - 提交 SHA: d49ff62d00bc0349eab94bd2dfc7ac0552261bbc (2026-02-15)
 * - 许可证：Apache License 2.0
 * - 对应上游源码文件：
 *   - app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt
 *   - app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTab.kt
 * - 本地适配差异说明 (Local Adaptations)：
 *   1. 彻底解决上游示例在真实页面路由中的状态断连与透镜滞留问题：
 *      - 业务层以全局权威的 `selectedTabId: String` 为单一真源，`selectedIndex` 严格按当前 `tabs` 顺序推导；
 *      - 废弃上游示例中容易导致状态丢失的 `remember(selectedTabIndex)` 与 `drop(1)` 监听；
 *      - 改用响应式 `LaunchedEffect(targetIndex, lensPosition)` 驱动弹簧物理动画，初始帧直接对齐当前页，消除闪烁；
 *      - 用户在 Dock 点击、侧边栏切页、系统返回或状态恢复时，透镜均 100% 同步滑入目标项；
 *   2. 拖拽手势与业务提交正交解耦：
 *      - 拖拽仅更新视觉预览与阻尼位移；释放时按有效悬停槽位提交一次业务切页；取消时平滑回弹当前页并不触发切页；
 *   3. 统一全局设计主题：
 *      - 淘汰组件内独立的 `isSystemInDarkTheme()`，统一从 `LocalDarkTheme.current` 与 `LocalYm1rColors.current` 取色；
 *   4. 严格保护 Dock 专属图标：
 *      - 继续复用 `DockIcons.kt` (Inbox, Dashboard, FileText, Wallet)，保持图标轮廓与语义清晰。
 * ============================================================================
 */

internal val LocalLiquidBottomTabScale =
    staticCompositionLocalOf { { 1f } }

/**
 * Ym1r 液态玻璃底部导航 Dock 栏
 */
@Composable
fun LiquidGlassDock(
    tabs: List<String>,
    selectedTab: String,
    compact: Boolean,
    onSelect: (String) -> Unit,
    backdrop: Backdrop,
    labelOf: (String) -> String,
    iconOf: (String) -> ImageVector,
    badgeOf: ((String) -> Int?)? = null,
    modifier: Modifier = Modifier,
) {
    val tabsCount = tabs.size.coerceAtLeast(1)
    val rawIndex = tabs.indexOf(selectedTab)
    val selectedIndex = if (rawIndex >= 0) rawIndex else 0

    LiquidBottomTabs(
        selectedIndex = selectedIndex,
        onTabSelected = { index ->
            if (index in tabs.indices) {
                onSelect(tabs[index])
            }
        },
        backdrop = backdrop,
        tabsCount = tabsCount,
        modifier = modifier,
    ) {
        tabs.forEachIndexed { index, id ->
            val isSelected = index == selectedIndex
            LiquidBottomTab(
                selected = isSelected,
                onClick = {
                    if (index in tabs.indices) {
                        onSelect(tabs[index])
                    }
                }
            ) {
                val badgeCount = badgeOf?.invoke(id)
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconOf(id),
                        contentDescription = labelOf(id),
                        modifier = Modifier.size(23.dp)
                    )
                    if (badgeCount != null && badgeCount > 0) {
                        RollingBadge(
                            count = badgeCount,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 10.dp, y = (-5).dp),
                        )
                    }
                }
                AnimatedVisibility(visible = !compact) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = labelOf(id),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                letterSpacing = (-0.2).sp
                            ),
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * 完整遵循 Kyant0/AndroidLiquidGlass 开源架构的 LiquidBottomTabs 实现
 */
@Composable
fun LiquidBottomTabs(
    selectedIndex: Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val isDark = LocalDarkTheme.current
    val ym1rColors = LocalYm1rColors.current
    val accentColor = ym1rColors.accent
    val containerColor = ym1rColors.surface.copy(alpha = 0.84f)
    val outlineColor = ym1rColors.separator
    val inactiveContentColor = ym1rColors.textSecondary
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)
    val latestOnTabSelected by rememberUpdatedState(onTabSelected)

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val targetIndex = selectedIndex.coerceIn(0, tabsCount - 1)
        val animationScope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        // 核心透镜位移动画控制器：初始帧严格对齐当前选中项，避免从 0 闪现
        val lensPosition = remember(tabsCount) {
            Animatable(targetIndex.toFloat())
        }

        // 外部选中项变化时（点击、侧边栏、系统返回、状态恢复），平滑驱动镜片移动
        LaunchedEffect(targetIndex, lensPosition) {
            lensPosition.animateTo(
                targetIndex.toFloat(),
                spring(dampingRatio = 0.86f, stiffness = 750f)
            )
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, constraints.maxWidth) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        var lastHapticIndex by remember { mutableIntStateOf(targetIndex) }

        // 阻尼拖拽状态管理
        var isDragging by remember { mutableStateOf(false) }
        var pressProgress by remember { mutableFloatStateOf(0f) }
        val pressProgressAnim = remember { Animatable(0f) }

        LaunchedEffect(pressProgress) {
            pressProgressAnim.animateTo(pressProgress, spring(1f, 1000f, 0.001f))
        }

        val interactiveHighlight = remember(animationScope, tabWidth, isLtr, lensPosition) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (lensPosition.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (lensPosition.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // 1. 底层静态 Row：承载所有 Tab 项（静态就位，绝对可见，不随拖拽漂移）
        Row(
            modifier = Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = pressProgressAnim.value
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = {
                        drawRect(containerColor)
                        drawOutline(
                            outline = Capsule().createOutline(size, layoutDirection, this),
                            color = outlineColor,
                            style = Stroke(0.5f.dp.toPx())
                        )
                    }
                )
                .then(interactiveHighlight.modifier)
                .height(64f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides inactiveContentColor) {
                content()
            }
        }

        // 2. 中间层 Row：通过 tabsBackdrop 捕捉强调色高亮图层（alpha 为 0 隐藏，用于透镜折射）
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, pressProgressAnim.value)
            }
        ) {
            Row(
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = pressProgressAnim.value
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(
                                24f.dp.toPx() * progress,
                                24f.dp.toPx() * progress
                            )
                        },
                        highlight = {
                            val progress = pressProgressAnim.value
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(Color.Transparent) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    content()
                }
            }
        }

        // 3. 顶层滑块 Box：纯物理流体阻尼液态玻璃滑动透镜（折射透视底层 Tab 并着色放大）
        Box(
            modifier = Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) lensPosition.value * tabWidth + panelOffset
                        else size.width - (lensPosition.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .pointerInput(tabsCount, tabWidth, isLtr) {
                    inspectDragGestures(
                        onDragStart = {
                            isDragging = true
                            pressProgress = 1f
                        },
                        onDragEnd = {
                            isDragging = false
                            pressProgress = 0f
                            val target = lensPosition.value.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                            if (target != lastHapticIndex) {
                                lastHapticIndex = target
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            animationScope.launch {
                                lensPosition.animateTo(
                                    target.toFloat(),
                                    spring(dampingRatio = 0.86f, stiffness = 750f)
                                )
                            }
                            animationScope.launch {
                                offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                            }
                            // 拖拽成功结束，提交一次业务切页
                            if (target != latestSelectedIndex) {
                                latestOnTabSelected(target)
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            pressProgress = 0f
                            // 拖拽取消，平滑回弹到当前业务选中项，不触发切页
                            animationScope.launch {
                                lensPosition.animateTo(
                                    latestSelectedIndex.toFloat(),
                                    spring(dampingRatio = 0.86f, stiffness = 750f)
                                )
                            }
                            animationScope.launch {
                                offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        val deltaFraction = dragAmount.x / tabWidth * (if (isLtr) 1f else -1f)
                        val newValue = (lensPosition.value + deltaFraction).fastCoerceIn(0f, (tabsCount - 1).toFloat())
                        animationScope.launch {
                            lensPosition.snapTo(newValue)
                        }
                        val hoverIndex = newValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                        if (hoverIndex != lastHapticIndex) {
                            lastHapticIndex = hoverIndex
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    }
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = pressProgressAnim.value
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = pressProgressAnim.value
                        Highlight.Default.copy(alpha = 0.25f + progress * 0.75f)
                    },
                    shadow = {
                        val progress = pressProgressAnim.value
                        Shadow(alpha = (0.15f + progress * 0.5f))
                    },
                    innerShadow = {
                        val progress = pressProgressAnim.value
                        InnerShadow(
                            radius = 8f.dp * (0.6f + progress * 0.4f),
                            alpha = (0.25f + progress * 0.5f)
                        )
                    },
                    layerBlock = {
                        val progress = pressProgressAnim.value
                        val pressedScale = 78f / 56f
                        val s = lerp(1f, pressedScale, progress)
                        scaleX = s
                        scaleY = s
                    },
                    onDrawSurface = {
                        val progress = pressProgressAnim.value
                        drawRect(
                            if (!isDark) Color.White.copy(alpha = 0.40f)
                            else Color.White.copy(alpha = 0.14f),
                            alpha = 1f - progress
                        )
                        drawRect(
                            if (!isDark) Color.White.copy(alpha = 0.55f)
                            else Color.White.copy(alpha = 0.22f),
                            alpha = progress
                        )
                        drawOutline(
                            outline = Capsule().createOutline(size, layoutDirection, this),
                            color = if (!isDark) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.20f),
                            style = Stroke(0.5f.dp.toPx())
                        )
                    }
                )
                .height(56f.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}

/**
 * 对应 Kyant0/AndroidLiquidGlass 开源项目的 LiquidBottomTab 项组件
 */
@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .clip(Capsule())
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .semantics {
                this.role = Role.Tab
                this.selected = selected
            }
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/** 交互手势与折射高光探测器 */
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {
    private val pressProgressAnimationSpec = spring<Float>(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring<Offset>(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value

    private val shader = if (isRuntimeShaderSupported()) {
        RuntimeShader(
            """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
        )
    } else {
        null
    }

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            if (shader != null) {
                drawRect(
                    Color.White.copy(0.08f * progress),
                    blendMode = BlendMode.Plus
                )
                shader.apply {
                    val pos = position(size, positionAnimation.value)
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", Color.White.copy(0.15f * progress))
                    setFloatUniform("radius", size.minDimension * 1.5f)
                    setFloatUniform(
                        "position",
                        pos.x.fastCoerceIn(0f, size.width),
                        pos.y.fastCoerceIn(0f, size.height)
                    )
                }
                drawRect(
                    ShaderBrush(shader.asComposeShader()),
                    blendMode = BlendMode.Plus
                )
            } else {
                drawRect(
                    Color.White.copy(0.20f * progress),
                    blendMode = BlendMode.Plus
                )
            }
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            }
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }
}

/** 拖拽手势检测扩展 */
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        val drag = initialDown

        onDragStart(down)
        onDrag(drag, Offset.Zero)
        val upEvent =
            drag(
                pointerId = drag.id,
                onDrag = { onDrag(it, it.positionChange()) }
            )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) return dragEvent else pointer = otherDown.id
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) return dragEvent
        }
    }
}
