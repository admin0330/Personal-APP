package com.masteralanlab.emailbox.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 会话失效广播。
 * 只有「HTTP 401 且响应体没有 code 字段」才会触发——上游邮件错误（如 refresh_token 失效）
 * 也会返回 4xx，但那不是 App 自己的会话问题，不能把用户踢回登录页。
 */
object SessionBus {

    private val _expired = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val expired: SharedFlow<String> = _expired.asSharedFlow()

    fun emitExpired(reason: String) {
        _expired.tryEmit(reason)
    }
}
