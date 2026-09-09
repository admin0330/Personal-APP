package com.masteralanlab.emailbox.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val zone: ZoneId get() = ZoneId.systemDefault()

/** 后端输出 RFC3339（可能带纳秒），这里容错解析。 */
fun parseInstant(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return try {
        Instant.parse(raw)
    } catch (_: Throwable) {
        try {
            // 去掉超出纳秒的精度再试一次
            val fixed = raw.replace(Regex("\\.(\\d{9})\\d+"), ".$1")
            Instant.parse(fixed)
        } catch (_: Throwable) {
            null
        }
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val dateFmt = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val fullFmt = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)

/** 列表用：今天显示时间，昨天显示「昨天」，更早显示日期。 */
fun formatShortTime(raw: String?): String {
    val instant = parseInstant(raw) ?: return ""
    val zoned = instant.atZone(zone)
    val now = java.time.ZonedDateTime.now(zone)
    return when {
        ChronoUnit.DAYS.between(zoned.toLocalDate(), now.toLocalDate()) == 0L -> timeFmt.format(zoned)
        ChronoUnit.DAYS.between(zoned.toLocalDate(), now.toLocalDate()) == 1L -> "昨天"
        zoned.year == now.year -> dateFmt.format(zoned)
        else -> DateTimeFormatter.ofPattern("yyyy/M/d", Locale.CHINA).format(zoned)
    }
}

fun formatFullTime(raw: String?): String {
    val instant = parseInstant(raw) ?: return ""
    return fullFmt.format(instant.atZone(zone))
}

fun formatBytes(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = size.toDouble()
    var idx = 0
    while (value >= 1024 && idx < units.lastIndex) {
        value /= 1024
        idx++
    }
    return if (idx == 0) "$size B" else String.format(Locale.CHINA, "%.1f %s", value, units[idx])
}

/** 取发件人显示名：没有尖括号前缀时直接返回邮箱。 */
fun displayName(from: String): String {
    val trimmed = from.trim()
    val idx = trimmed.indexOf('<')
    if (idx > 0) {
        val name = trimmed.substring(0, idx).trim().trim('"', '\'')
        if (name.isNotBlank()) return name
    }
    return trimmed.trim('<', '>', ' ', '"')
}

fun emailAddress(from: String): String {
    val s = from.trim()
    val start = s.indexOf('<')
    val end = s.indexOf('>')
    return if (start >= 0 && end > start) s.substring(start + 1, end).trim() else s
}

/**
 * 列表与账号头像提取：
 * 自动获取邮箱名首字母大写，如果是数字则保持为数字。
 */
fun accountInitialOf(from: String): String {
    val clean = emailAddress(from).ifBlank { from }.trim()
    val username = clean.substringBefore('@').trim().ifBlank { clean }
    val ch = username.firstOrNull { it.isLetterOrDigit() }
        ?: clean.firstOrNull { it.isLetterOrDigit() }
        ?: return "?"
    return if (ch.isDigit()) ch.toString() else ch.uppercaseChar().toString()
}

/** 列表头像用：取邮箱首字母大写或数字。 */
fun initialOf(from: String): String = accountInitialOf(from)
