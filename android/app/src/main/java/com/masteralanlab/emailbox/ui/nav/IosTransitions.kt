package com.masteralanlab.emailbox.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset

/**
 * 纯正 iOS 页面转场规范 (iOS UINavigationController 风格)：
 * 1. 紧致弹簧：DampingRatio = 0.86f, Stiffness = 750f；
 * 2. Push 进场：新页从右侧 100% 推入，底层向左退至 -30% 视差位移并伴随 0.25 黑色遮罩；
 * 3. Pop 返回：当前页面右滑至 100%，底层自 -30% 视差归正至 0%，遮罩淡出。
 */
object IosTransitions {
    val NavSpring = spring<IntOffset>(
        dampingRatio = 0.86f,
        stiffness = 750f,
    )

    val FadeSpring = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = 750f,
    )

    // Push 进场：新页面从右边 100% 推进
    val PushEnter: EnterTransition = slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = NavSpring,
    )

    // Push 离开：底层页面向左收回 30%（视差 Parallax），伴随 25% 遮罩衰减
    val PushExit: ExitTransition = slideOutHorizontally(
        targetOffsetX = { fullWidth -> -(fullWidth * 0.30f).toInt() },
        animationSpec = NavSpring,
    ) + fadeOut(
        targetAlpha = 0.75f,
        animationSpec = FadeSpring,
    )

    // Pop 进入：底层页面从 -30% 视差位移推回 0%
    val PopEnter: EnterTransition = slideInHorizontally(
        initialOffsetX = { fullWidth -> -(fullWidth * 0.30f).toInt() },
        animationSpec = NavSpring,
    ) + fadeIn(
        initialAlpha = 0.75f,
        animationSpec = FadeSpring,
    )

    // Pop 退出：当前页面向右滑出到 100%
    val PopExit: ExitTransition = slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = NavSpring,
    )
}
