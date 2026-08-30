package com.masteralanlab.emailbox.data.remote

import kotlinx.serialization.json.Json

/**
 * 全局 JSON 配置。
 * - ignoreUnknownKeys：后端新增字段时老版本 App 不应崩溃
 * - explicitNulls=false：Kotlin 的 null 一律不写入请求体，满足后端指针语义
 *   （字段缺失/为 null = 保持原值，显式 "" = 清空）
 * - coerceInputValues：后端偶尔把数值字段写成 null，回落到默认值而不是抛异常
 */
val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
}
