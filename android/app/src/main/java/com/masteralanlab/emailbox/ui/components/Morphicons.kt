package com.masteralanlab.emailbox.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A small native subset of Lucide's 24dp, round-stroke icon family. */
object Morphicons {
    val Inbox = build("Inbox") {
        strokePath { moveTo(22f, 12f); horizontalLineTo(16f); lineTo(14f, 15f); horizontalLineTo(10f); lineTo(8f, 12f); horizontalLineTo(2f) }
        strokePath { moveTo(5.45f, 5.11f); lineTo(2f, 12f); verticalLineTo(18f); curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f); horizontalLineTo(20f); curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f); verticalLineTo(12f); lineTo(18.55f, 5.11f); curveTo(18.21f, 4.43f, 17.52f, 4f, 16.76f, 4f); horizontalLineTo(7.24f); curveTo(6.48f, 4f, 5.79f, 4.43f, 5.45f, 5.11f) }
    }

    val Dashboard = build("LayoutDashboard") {
        strokePath { moveTo(3f, 3f); horizontalLineTo(10f); verticalLineTo(10f); horizontalLineTo(3f); close() }
        strokePath { moveTo(14f, 3f); horizontalLineTo(21f); verticalLineTo(10f); horizontalLineTo(14f); close() }
        strokePath { moveTo(3f, 14f); horizontalLineTo(10f); verticalLineTo(21f); horizontalLineTo(3f); close() }
        strokePath { moveTo(14f, 14f); horizontalLineTo(21f); verticalLineTo(21f); horizontalLineTo(14f); close() }
    }

    val FileText = build("FileText") {
        strokePath { moveTo(14f, 2f); horizontalLineTo(6f); curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f); verticalLineTo(20f); curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f); horizontalLineTo(18f); curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f); verticalLineTo(8f); close() }
        strokePath { moveTo(14f, 2f); verticalLineTo(8f); horizontalLineTo(20f); moveTo(8f, 13f); horizontalLineTo(16f); moveTo(8f, 17f); horizontalLineTo(16f); moveTo(8f, 9f); horizontalLineTo(10f) }
    }

    val Wallet = build("WalletCards") {
        strokePath { moveTo(20f, 7f); verticalLineTo(6f); curveTo(20f, 4.9f, 19.1f, 4f, 18f, 4f); horizontalLineTo(5f); curveTo(3.34f, 4f, 2f, 5.34f, 2f, 7f); verticalLineTo(18f); curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f); horizontalLineTo(20f); curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f); verticalLineTo(10f); curveTo(22f, 8.9f, 21.1f, 8f, 20f, 8f); horizontalLineTo(15f); curveTo(13.9f, 8f, 13f, 8.9f, 13f, 10f); curveTo(13f, 11.1f, 13.9f, 12f, 15f, 12f); horizontalLineTo(22f) }
    }

    val Search = build("Search") {
        strokePath { moveTo(19f, 11f); curveTo(19f, 15.42f, 15.42f, 19f, 11f, 19f); curveTo(6.58f, 19f, 3f, 15.42f, 3f, 11f); curveTo(3f, 6.58f, 6.58f, 3f, 11f, 3f); curveTo(15.42f, 3f, 19f, 6.58f, 19f, 11f); moveTo(20f, 20f); lineTo(16.65f, 16.65f) }
    }

    val Settings = build("Settings") {
        addPath(
            pathData = PathParser().parsePathString(
                "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"
            ).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        addPath(
            pathData = PathParser().parsePathString("M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    val User = build("UserRound") {
        strokePath { moveTo(16f, 8f); curveTo(16f, 10.21f, 14.21f, 12f, 12f, 12f); curveTo(9.79f, 12f, 8f, 10.21f, 8f, 8f); curveTo(8f, 5.79f, 9.79f, 4f, 12f, 4f); curveTo(14.21f, 4f, 16f, 5.79f, 16f, 8f); moveTo(4f, 21f); curveTo(4.6f, 16.9f, 7.3f, 15f, 12f, 15f); curveTo(16.7f, 15f, 19.4f, 16.9f, 20f, 21f) }
    }

    val Users = build("UsersRound") {
        strokePath { moveTo(15f, 8f); curveTo(15f, 10.21f, 13.21f, 12f, 11f, 12f); curveTo(8.79f, 12f, 7f, 10.21f, 7f, 8f); curveTo(7f, 5.79f, 8.79f, 4f, 11f, 4f); curveTo(13.21f, 4f, 15f, 5.79f, 15f, 8f); moveTo(2f, 21f); curveTo(2.6f, 16.9f, 5.3f, 15f, 10f, 15f); curveTo(14.7f, 15f, 17.4f, 16.9f, 18f, 21f); moveTo(17f, 5f); curveTo(20.4f, 5.4f, 22f, 7.5f, 22f, 10.5f); moveTo(18f, 15.3f); curveTo(20.8f, 16.1f, 22.2f, 18f, 23f, 21f) }
    }

    val Key = build("KeyRound") {
        strokePath { moveTo(10.5f, 13f); lineTo(19f, 4.5f); moveTo(16f, 7.5f); lineTo(18.5f, 10f); moveTo(13.5f, 10f); lineTo(16f, 12.5f); moveTo(7.5f, 19f); curveTo(9.43f, 19f, 11f, 17.43f, 11f, 15.5f); curveTo(11f, 13.57f, 9.43f, 12f, 7.5f, 12f); curveTo(5.57f, 12f, 4f, 13.57f, 4f, 15.5f); curveTo(4f, 17.43f, 5.57f, 19f, 7.5f, 19f) }
    }

    val Activity = build("Activity") {
        strokePath { moveTo(3f, 12f); horizontalLineTo(7f); lineTo(10f, 4f); lineTo(14f, 20f); lineTo(17f, 12f); horizontalLineTo(21f) }
    }

    val Database = build("Database") {
        strokePath { moveTo(4f, 5f); curveTo(4f, 3.34f, 7.58f, 2f, 12f, 2f); curveTo(16.42f, 2f, 20f, 3.34f, 20f, 5f); verticalLineTo(19f); curveTo(20f, 20.66f, 16.42f, 22f, 12f, 22f); curveTo(7.58f, 22f, 4f, 20.66f, 4f, 19f); close() }
        strokePath { moveTo(4f, 5f); curveTo(4f, 6.66f, 7.58f, 8f, 12f, 8f); curveTo(16.42f, 8f, 20f, 6.66f, 20f, 5f); moveTo(4f, 12f); curveTo(4f, 13.66f, 7.58f, 15f, 12f, 15f); curveTo(16.42f, 15f, 20f, 13.66f, 20f, 12f) }
    }

    private fun build(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()
}

private fun ImageVector.Builder.strokePath(block: PathBuilder.() -> Unit) = path(
    fill = null,
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = block,
)
