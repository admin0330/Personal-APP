package com.masteralanlab.emailbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.masteralanlab.emailbox.ui.theme.LocalYm1rColors

/**
 * X (Twitter) 风格紧凑型顶栏规范：
 * - 紧凑高度 (52dp)，内容向上吸顶，留足列表浏览视野；
 * - 左侧：头像入口（最小 48dp 点击热区）；
 * - 中间：业务主标题与上下文副标题（或业务内置分页分段）；
 * - 右侧：最关键快捷动作区（次要功能收拢进菜单）；
 * - 底部：0.5dp 微弱发丝分割线，区分顶栏与内容层。
 */
@Composable
fun XTopAppBar(
    title: String,
    subtitle: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalYm1rColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 左侧头像入口
            if (onAvatarClick != null) {
                UserAvatarButton(
                    onClick = onAvatarClick,
                    contentDescription = "打开账户与导航菜单",
                )
            } else {
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.width(8.dp))

            // 中间标题区
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // 右侧快捷操作区
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                actions()
            }
        }

        // 底部细分隔线
        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.separator,
        )
    }
}
