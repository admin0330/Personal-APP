package com.masteralanlab.emailbox.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 底部液态玻璃 Dock 栏专属图标库（严格冻结保护）：
 * 本文件中的图标轮廓、尺寸、端点与表现形式永久冻结，专属供应 LiquidGlassDock 使用。
 * 全局其他界面的图标重构严禁修改本文件中的定义，确保 Dock 栏图标的连续性与稳定性。
 */
object DockIcons {
    val Inbox = build("DockInbox") {
        strokePath { moveTo(22f, 12f); horizontalLineTo(16f); lineTo(14f, 15f); horizontalLineTo(10f); lineTo(8f, 12f); horizontalLineTo(2f) }
        strokePath { moveTo(5.45f, 5.11f); lineTo(2f, 12f); verticalLineTo(18f); curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f); horizontalLineTo(20f); curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f); verticalLineTo(12f); lineTo(18.55f, 5.11f); curveTo(18.21f, 4.43f, 17.52f, 4f, 16.76f, 4f); horizontalLineTo(7.24f); curveTo(6.48f, 4f, 5.79f, 4.43f, 5.45f, 5.11f) }
    }

    val Dashboard = build("DockDashboard") {
        strokePath { moveTo(3f, 3f); horizontalLineTo(10f); verticalLineTo(10f); horizontalLineTo(3f); close() }
        strokePath { moveTo(14f, 3f); horizontalLineTo(21f); verticalLineTo(10f); horizontalLineTo(14f); close() }
        strokePath { moveTo(3f, 14f); horizontalLineTo(10f); verticalLineTo(21f); horizontalLineTo(3f); close() }
        strokePath { moveTo(14f, 14f); horizontalLineTo(21f); verticalLineTo(21f); horizontalLineTo(14f); close() }
    }

    val FileText = build("DockFileText") {
        strokePath { moveTo(14f, 2f); horizontalLineTo(6f); curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f); verticalLineTo(20f); curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f); horizontalLineTo(18f); curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f); verticalLineTo(8f); close() }
        strokePath { moveTo(14f, 2f); verticalLineTo(8f); horizontalLineTo(20f); moveTo(8f, 13f); horizontalLineTo(16f); moveTo(8f, 17f); horizontalLineTo(16f); moveTo(8f, 9f); horizontalLineTo(10f) }
    }

    val Wallet = build("DockWallet") {
        strokePath { moveTo(20f, 7f); verticalLineTo(6f); curveTo(20f, 4.9f, 19.1f, 4f, 18f, 4f); horizontalLineTo(5f); curveTo(3.34f, 4f, 2f, 5.34f, 2f, 7f); verticalLineTo(18f); curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f); horizontalLineTo(20f); curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f); verticalLineTo(10f); curveTo(22f, 8.9f, 21.1f, 8f, 20f, 8f); horizontalLineTo(15f); curveTo(13.9f, 8f, 13f, 8.9f, 13f, 10f); curveTo(13f, 11.1f, 13.9f, 12f, 15f, 12f); horizontalLineTo(22f) }
    }

    private fun build(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.strokePath(block: PathBuilder.() -> Unit) = path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )
}
