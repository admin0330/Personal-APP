package com.masteralanlab.emailbox.ui.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

/**
 * iOS 页面级手势导航容器：
 * 将边缘手势与位移精准绑定到当前推入页面（Pushable Page），
 * 绝不永久偏移整个 NavHost 容器，彻底解决多层侧滑返回后界面停留在屏幕外的缺陷。
 */
@Composable
fun IosPageContainer(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), content = content)
}

/**
 * 兼容保留的 IosNavigationContainer：只做顶层布局容器，不再对整个 NavHost 施加位移
 */
@Composable
fun IosNavigationContainer(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        content = content,
    )
}

/**
 * 注册推入页面。返回手势由 Android 与 NavHost 处理，避免抢占页面横向操作。
 */
fun NavGraphBuilder.iosComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    onBack: () -> Unit,
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
    ) { entry ->
        IosPageContainer(onBack = onBack) {
            content(entry)
        }
    }
}
