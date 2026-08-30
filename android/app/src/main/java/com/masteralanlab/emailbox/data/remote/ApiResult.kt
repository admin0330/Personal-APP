package com.masteralanlab.emailbox.data.remote

/** 统一的调用结果。UI 层只关心成功数据与可展示的错误。 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>

    data class Failure(
        val httpStatus: Int,
        /** 业务码；中间件抛出的 401/403 没有 code，此时为 null。 */
        val code: Int?,
        val message: String,
        /** code=1005 时的上游错误分类，用于决定引导重新授权还是换代理。 */
        val upstream: UpstreamError? = null,
        /** 会话失效，需要清理本地状态并跳登录。 */
        val sessionExpired: Boolean = false,
        val quotaExceeded: Boolean = false,
        val cause: Throwable? = null,
    ) : ApiResult<Nothing>
}

val ApiResult.Failure.isUpstream: Boolean get() = code == 1005

fun ApiResult.Failure.isNoPermission(): Boolean = httpStatus == 403 && code != 1001
