package com.masteralanlab.emailbox.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.UnevenRoundedRectangle

/**
 * 连续曲率平滑圆角形状 (Continuous Curvature Rounded Shape)
 * 继承自 Compose 标准 CornerBasedShape，兼容 MaterialTheme.shapes、Surface 与 clip。
 * 委托基于严格高阶贝塞尔平滑算法的 UnevenRoundedRectangle，
 * 保证直边到圆弧的曲率自然过渡，消除突变、斜切感与边缘缺口，并支持 RTL 与单侧圆角。
 */
class IosContinuousCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(topStart: Dp = 0.dp, topEnd: Dp = 0.dp, bottomEnd: Dp = 0.dp, bottomStart: Dp = 0.dp) : this(
        CornerSize(topStart),
        CornerSize(topEnd),
        CornerSize(bottomEnd),
        CornerSize(bottomStart),
    )

    constructor(cornerRadius: Dp) : this(cornerRadius, cornerRadius, cornerRadius, cornerRadius)

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = IosContinuousCornerShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect.Zero)
        }
        val maxR = minOf(size.width, size.height) / 2f
        val rTS = topStart.coerceIn(0f, maxR)
        val rTE = topEnd.coerceIn(0f, maxR)
        val rBE = bottomEnd.coerceIn(0f, maxR)
        val rBS = bottomStart.coerceIn(0f, maxR)

        if (rTS <= 0f && rTE <= 0f && rBE <= 0f && rBS <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }

        val delegate = UnevenRoundedRectangle(
            topStart = rTS.dp,
            topEnd = rTE.dp,
            bottomEnd = rBE.dp,
            bottomStart = rBS.dp,
            style = RoundedCornerStyle.Continuous,
        )
        // 使用 Density(1f, 1f) 保证传入的像素值在委托计算中 1:1 精确映射
        return delegate.createOutline(size, layoutDirection, Density(1f, 1f))
    }

    override fun toString(): String =
        "IosContinuousCornerShape(topStart=$topStart, topEnd=$topEnd, bottomEnd=$bottomEnd, bottomStart=$bottomStart)"
}

// 常用平滑圆角规格预设
val IosIconCorner = IosContinuousCornerShape(8.dp)
val IosSegmentCorner = IosContinuousCornerShape(8.dp)
val IosRowCorner = IosContinuousCornerShape(10.dp)
val IosButtonCorner = IosContinuousCornerShape(12.dp)
val IosPillCorner = IosContinuousCornerShape(14.dp)
val IosCardCorner = IosContinuousCornerShape(16.dp)
val IosHeroCorner = IosContinuousCornerShape(20.dp)
val IosDialogCorner = IosContinuousCornerShape(20.dp)
val IosSheetCorner = IosContinuousCornerShape(topStart = 24.dp, topEnd = 24.dp)
