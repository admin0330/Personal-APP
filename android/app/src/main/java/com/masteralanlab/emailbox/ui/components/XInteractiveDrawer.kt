package com.masteralanlab.emailbox.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * X (Twitter) 风格交互式侧栏抽屉状态控制器
 */
@Stable
class XDrawerState(
    initialOpen: Boolean = false,
    private val scope: CoroutineScope,
) {
    internal val animOffset = Animatable(if (initialOpen) 0f else -1000f)
    internal var maxDrawerWidthPx by mutableFloatStateOf(1000f)

    val isOpen: Boolean by derivedStateOf {
        maxDrawerWidthPx > 0f && animOffset.value > -maxDrawerWidthPx * 0.45f
    }

    val progress: Float by derivedStateOf {
        if (maxDrawerWidthPx <= 0f) 0f
        else ((animOffset.value + maxDrawerWidthPx) / maxDrawerWidthPx).coerceIn(0f, 1f)
    }

    val springSpec: SpringSpec<Float> = spring(
        dampingRatio = 0.84f,
        stiffness = 650f,
    )

    fun open() {
        scope.launch {
            animOffset.animateTo(0f, springSpec)
        }
    }

    fun close() {
        scope.launch {
            animOffset.animateTo(-maxDrawerWidthPx, springSpec)
        }
    }

    internal suspend fun snapTo(value: Float) {
        animOffset.snapTo(value.coerceIn(-maxDrawerWidthPx, 0f))
    }

    internal suspend fun settle(velocity: Float) {
        val target = when {
            velocity > 800f -> 0f // 快速向右滑开
            velocity < -800f -> -maxDrawerWidthPx // 快速向左关上
            progress > 0.40f -> 0f // 拖过 40% 自动吸附展开
            else -> -maxDrawerWidthPx // 回弹关闭
        }
        animOffset.animateTo(
            targetValue = target,
            animationSpec = springSpec,
            initialVelocity = velocity,
        )
    }
}

@Composable
fun rememberXDrawerState(initialOpen: Boolean = false): XDrawerState {
    val scope = rememberCoroutineScope()
    return remember { XDrawerState(initialOpen, scope) }
}

/**
 * X App 交互规范的抽屉容器：
 * 1. 交互式连续右拉：手指从主界面向右拖动时，抽屉逐像素跟手展开；
 * 2. 精准防误触与手势消歧：
 *    - 严格遵从 touchSlop 阈值判定；
 *    - 当垂直位移占优时（|dy| > |dx|），无条件释放给垂直列表滚动，绝不触发抽屉；
 *    - 底部 Dock 区域触摸优先给 Dock，不与 Dock 拖动抢手势；
 * 3. 释放时结合距离与速度物理弹簧收敛，支持二次触摸随时打断；
 * 4. 彻底消除顶层透明遮罩层，头像按钮等所有控件触控区 100% 原始透传。
 */
@Composable
fun XInteractiveDrawerLayout(
    drawerState: XDrawerState,
    drawerWidth: Dp = 320.dp,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val drawerWidthPx = with(density) { drawerWidth.toPx() }
    val viewConfig = LocalViewConfiguration.current
    val touchSlop = viewConfig.touchSlop
    val scope = rememberCoroutineScope()

    LaunchedEffect(drawerWidthPx) {
        drawerState.maxDrawerWidthPx = drawerWidthPx
        if (!drawerState.isOpen) {
            drawerState.animOffset.snapTo(-drawerWidthPx)
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        drawerState.close()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val dockExclusionHeightPx = with(density) { 96.dp.toPx() } // 预留底部 dock 交互区

        val gestureModifier = if (!gesturesEnabled) Modifier else Modifier.pointerInput(drawerState) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val startPos = down.position
                // 若按在底部 Dock 区域，让位给 Dock 交互
                if (!drawerState.isOpen && startPos.y > (screenHeightPx - dockExclusionHeightPx)) {
                    return@awaitEachGesture
                }

                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                var isDragging = false
                var totalDx = 0f
                var totalDy = 0f
                val initialOffset = drawerState.animOffset.value

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        // Pointer up: 计算速度并结算
                        if (isDragging) {
                            change.consume()
                            val velocity = velocityTracker.calculateVelocity().x
                            scope.launch {
                                drawerState.settle(velocity)
                            }
                        }
                        break
                    }

                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val posChange = change.positionChange()
                    totalDx += posChange.x
                    totalDy += posChange.y

                    if (!isDragging) {
                        val absDx = abs(totalDx)
                        val absDy = abs(totalDy)
                        if (absDx > touchSlop || absDy > touchSlop) {
                            if (absDy >= absDx) {
                                // 纵向滚动占优，退出手势循环让位给列表滚动
                                break
                            }
                            if (!drawerState.isOpen && totalDx <= 0f) {
                                // 抽屉处于关闭状态且向左滑动，不消费
                                break
                            }
                            // 判定为横向右拉或打开态左推，激活拖拽
                            isDragging = true
                            change.consume()
                        }
                    } else {
                        change.consume()
                        val newOffset = initialOffset + totalDx
                        scope.launch {
                            drawerState.snapTo(newOffset)
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier),
        ) {
            // 1. 主内容层：随抽屉展开轻微视差平移 (X App 经典视差反馈)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = drawerState.progress * (drawerWidthPx * 0.12f)
                    },
            ) {
                content()
            }

            // 2. 遮罩层 (Scrim)
            if (drawerState.progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = drawerState.progress * 0.52f
                        }
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { drawerState.close() },
                        ),
                )
            }

            // 3. 抽屉视图层 (从左侧滑出)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        translationX = drawerState.animOffset.value
                    },
            ) {
                drawerContent()
            }
        }
    }
}
