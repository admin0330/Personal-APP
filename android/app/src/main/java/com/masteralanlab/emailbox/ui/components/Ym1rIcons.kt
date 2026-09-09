package com.masteralanlab.emailbox.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Ym1r 全局功能图标库：
 * 统一采用 Lucide 官方 24x24 视口、线宽 2f、圆头线帽 (Round) 与圆角连接 (Round) 规范，
 * 彻底替换原各界面散落的 Material 实心图标与不规范手绘轮廓。
 * （注：底部液态玻璃 Dock 图标已在 DockIcons 中独立冻结保护，不属于本库范畴）。
 */
object Ym1rIcons {

    val ArrowLeft = buildIcon("ArrowLeft", "M19 12H5M12 19l-7-7 7-7")
    val ArrowRight = buildIcon("ArrowRight", "M5 12h14M12 5l7 7-7 7")
    val ArrowUp = buildIcon("ArrowUp", "M12 19V5M5 12l7-7 7 7")
    val ArrowDown = buildIcon("ArrowDown", "M12 5v14M19 12l-7 7-7-7")

    val ChevronLeft = buildIcon("ChevronLeft", "M15 18l-6-6 6-6")
    val ChevronRight = buildIcon("ChevronRight", "M9 18l6-6-6-6")
    val ChevronDown = buildIcon("ChevronDown", "M6 9l6 6 6-6")
    val ChevronUp = buildIcon("ChevronUp", "M18 15l-6-6-6 6")

    val Search = buildIcon("Search", "M19 11 A8 8 0 0 1 3 11 A8 8 0 0 1 19 11 Z M21 21l-4.35-4.35")
    val X = buildIcon("X", "M18 6L6 18M6 6l12 12")
    val Plus = buildIcon("Plus", "M12 5v14M5 12h14")
    val Minus = buildIcon("Minus", "M5 12h14")
    val Check = buildIcon("Check", "M20 6L9 17l-5-5")
    val CheckCheck = buildIcon("CheckCheck", "M18 6L7 17l-5-5M22 10l-7.5 7.5-1.5-1.5")

    val Pencil = buildIcon("Pencil", "M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5ZM15 5l4 4")
    val Trash2 = buildIcon(
        "Trash2",
        "M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M10 11v6M14 11v6"
    )
    val Copy = buildIcon("Copy", "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2zM4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2")
    val MoreVertical = buildIcon("MoreVertical", "M12 12a1 1 0 1 0 0 .01M12 5a1 1 0 1 0 0 .01M12 19a1 1 0 1 0 0 .01")
    val MoreHorizontal = buildIcon("MoreHorizontal", "M12 12a1 1 0 1 0 0 .01M19 12a1 1 0 1 0 0 .01M5 12a1 1 0 1 0 0 .01")

    val RefreshCw = buildIcon(
        "RefreshCw",
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8M21 3v5h-5M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16M8 16H3v5"
    )

    val Mail = buildIcon("Mail", "M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2zM22 6l-10 7L2 6")
    val MailSearch = buildIcon(
        "MailSearch",
        "M22 12.5V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v12c0 1.1.9 2 2 2h7.5M22 6l-10 7L2 6M18 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM22 22l-1.9-1.9"
    )
    val Inbox = buildIcon("Inbox", "M22 12h-6l-2 3h-4l-2-3H2M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z")
    val Paperclip = buildIcon(
        "Paperclip",
        "M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"
    )

    val Eye = buildIcon("Eye", "M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z")
    val EyeOff = buildIcon(
        "EyeOff",
        "M9.88 9.88a3 3 0 1 0 4.24 4.24M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61M2 2l20 20"
    )

    val Key = buildIcon("Key", "M2 15.5a5.5 5.5 0 1 0 11 0a5.5 5.5 0 1 0-11 0M21 2l-9.6 9.6M15.5 7.5l2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L19 4")
    val Lock = buildIcon("Lock", "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2zM7 11V7a5 5 0 0 1 10 0v4")
    val Unlock = buildIcon("Unlock", "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2zM7 11V7a5 5 0 0 1 9.9-1")
    val Shield = buildIcon("Shield", "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z")

    val Settings = buildIcon(
        "Settings",
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"
    )

    val User = buildIcon("User", "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z")
    val Users = buildIcon("Users", "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75")

    val Folder = buildIcon("Folder", "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2z")
    val FolderPlus = buildIcon("FolderPlus", "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2zM12 10v6M9 13h6")

    val Download = buildIcon("Download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3")
    val Upload = buildIcon("Upload", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12")

    val FileText = buildIcon("FileText", "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7zM14 2v4a2 2 0 0 0 2 2h4M10 9H8M16 13H8M16 17H8")
    val Wallet = buildIcon("Wallet", "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4")
    val Activity = buildIcon("Activity", "M22 12h-4l-3 9L9 3l-3 9H2")
    val Archive = buildIcon("Archive", "M3 3h18v5H3zM4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8M10 12h4")
    val LogOut = buildIcon("LogOut", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9")
    val AlertCircle = buildIcon("AlertCircle", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 8v4M12 16h.01")
    val ExternalLink = buildIcon("ExternalLink", "M15 3h6v6M10 14L21 3M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6")
    val Globe = buildIcon("Globe", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20M2 12h20")
    val Server = buildIcon("Server", "M4 2h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM4 14h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2zM6 6h.01M6 18h.01")
    val Sliders = buildIcon("Sliders", "M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6")
    val Share2 = buildIcon("Share2", "M18 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM6 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM18 22a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM8.59 13.51l6.83 3.98M15.41 6.51l-6.82 3.98")
    val Tag = buildIcon("Tag", "M12 2H2v10l9.29 9.29a2.4 2.4 0 0 0 3.42 0l6.58-6.58a2.4 2.4 0 0 0 0-3.42L12 2zM7 7h.01")
    val CreditCard = buildIcon("CreditCard", "M3 5h18a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM2 10h20")
    val Filter = buildIcon("Filter", "M22 3H2l8 9.46V19l4 2v-8.54L22 3z")
    val Image = buildIcon("Image", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM9 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM21 15l-5-5L5 21")
    val Translate = buildIcon("Translate", "M5 8l6 6M4 14l6-6 2-3M2 5h12M7 2v3M22 22l-5-10-5 10M14 18h6")
    val CheckCircle = buildIcon("CheckCircle", "M22 11.08V12a10 10 0 1 1-5.93-9.14M22 4L12 14.01l-3-3")
    val Cloud = buildIcon("Cloud", "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z")
    val CloudCheck = buildIcon("CloudCheck", "M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242M8 15l2 2 4-4")
    val Ban = buildIcon("Ban", "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm-7.07 14.93L16.93 4.93")

    private fun buildIcon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            name = name,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
}
