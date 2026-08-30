package com.masteralanlab.emailbox.data.remote

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.SessionBus
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 把 Retrofit 调用统一收敛成 ApiResult。
 *
 * 两个关键约定来自后端实现，不能改：
 * 1. 中间件抛出的 401/403 响应体只有 message，没有 code 字段——因此只有「HTTP 401 且
 *    响应体没有 code」才判定为会话失效。上游邮件错误（auth_failed）也可能返回 401 之外的
 *    状态码，绝不能见 4xx 就登出。
 * 2. code=1005 表示上游邮件错误，真正的原因在 data.error_kind 里，不体现在 HTTP 状态码上。
 */
suspend fun <T : Any> apiCall(
    block: suspend ApiService.() -> ApiResp<T>,
): ApiResult<T> = withContext(Dispatchers.IO) {
    try {
        val resp = ApiClient.service().block()
        val code = resp.code
        if (code != null && code != 0) {
            return@withContext ApiResult.Failure(
                httpStatus = 200,
                code = code,
                message = resp.message ?: "请求失败",
                quotaExceeded = code == 1001,
            )
        }
        val data = resp.data
        if (data == null) {
            ApiResult.Failure(
                httpStatus = 200,
                code = code,
                message = resp.message?.takeIf { code != null && code != 0 } ?: "服务端未返回数据",
            )
        } else {
            ApiResult.Success(data)
        }
    } catch (e: HttpException) {
        parseHttpError(e)
    } catch (_: ReadOnlyBlockedException) {
        ApiResult.Failure(403, null, "登录密钥仅允许读取邮件与操作个人账本")
    } catch (e: UnknownHostException) {
        ApiResult.Failure(0, null, "无法连接服务器，请检查地址与网络", cause = e)
    } catch (e: SocketTimeoutException) {
        ApiResult.Failure(0, null, "请求超时，上游邮件服务可能较慢", cause = e)
    } catch (e: IOException) {
        ApiResult.Failure(0, null, "网络异常：${e.message ?: "连接中断"}", cause = e)
    } catch (e: SerializationException) {
        ApiResult.Failure(0, null, "服务端返回了无法解析的内容，请确认服务器地址指向 Emailbox", cause = e)
    } catch (e: Exception) {
        ApiResult.Failure(0, null, e.message ?: "未知错误", cause = e)
    }
}

/** 用于 data 恒为 null 的接口（登出、删除、排序等）。 */
suspend fun apiCallUnit(
    block: suspend ApiService.() -> ApiResp<Unit>,
): ApiResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val resp = ApiClient.service().block()
        val code = resp.code
        if (code != null && code != 0) {
            return@withContext ApiResult.Failure(200, code, resp.message ?: "请求失败", quotaExceeded = code == 1001)
        }
        ApiResult.Success(Unit)
    } catch (e: HttpException) {
        parseHttpError(e)
    } catch (_: ReadOnlyBlockedException) {
        ApiResult.Failure(403, null, "登录密钥仅允许读取邮件与操作个人账本")
    } catch (e: UnknownHostException) {
        ApiResult.Failure(0, null, "无法连接服务器，请检查地址与网络", cause = e)
    } catch (e: SocketTimeoutException) {
        ApiResult.Failure(0, null, "请求超时", cause = e)
    } catch (e: IOException) {
        ApiResult.Failure(0, null, "网络异常：${e.message ?: "连接中断"}", cause = e)
    } catch (e: SerializationException) {
        ApiResult.Failure(0, null, "服务端返回了无法解析的内容，请确认服务器地址指向 Emailbox", cause = e)
    } catch (e: Exception) {
        ApiResult.Failure(0, null, e.message ?: "未知错误", cause = e)
    }
}

private fun parseHttpError(e: HttpException): ApiResult.Failure {
    val status = e.code()
    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
    return parseHttpFailure(status, body, Prefs.apiKeyMode, e)
}

/** 将 Retrofit 以外的流式响应（附件等）也收敛到同一套错误语义。 */
fun requireSuccessful(response: Response<*>) {
    if (response.isSuccessful) return
    val body = runCatching { response.errorBody()?.string() }.getOrNull()
    val failure = parseHttpFailure(response.code(), body, Prefs.apiKeyMode)
    throw IOException(failure.message)
}

/**
 * HTTP 状态 + 响应体 → 统一错误语义。除 SessionBus 广播外是纯函数，
 * apiKeyMode 由调用方传入，便于在 JVM 单测里覆盖两种凭据模式。
 */
internal fun parseHttpFailure(
    status: Int,
    body: String?,
    apiKeyMode: Boolean,
    cause: Throwable? = null,
): ApiResult.Failure {

    var code: Int? = null
    var message: String? = null
    var upstream: UpstreamError? = null

    if (!body.isNullOrBlank()) {
        runCatching {
            val obj = AppJson.parseToJsonElement(body).jsonObject
            code = obj["code"]?.jsonPrimitive?.intOrNull
            message = obj["message"]?.jsonPrimitive?.contentOrNull
            val dataEl = obj["data"]
            if (dataEl != null && dataEl !is JsonNull) {
                upstream = runCatching {
                    AppJson.decodeFromJsonElement<UpstreamError>(dataEl)
                }.getOrNull()

            }
        }
    }

    if (status == 401 && code == null) {
        SessionBus.emitExpired(
            if (apiKeyMode) "API Key 无效或已重置，请重新绑定"
            else message ?: "登录已失效，请重新登录",
        )
    }

    val msg = when {
        status == 502 || code == 1005 -> upstreamMessage(message, upstream)
        message != null -> message!!
        else -> when (status) {
            401 -> if (apiKeyMode) "API Key 无效或已重置，请重新绑定" else "登录已失效，请重新登录"
            403 -> if (apiKeyMode) "登录密钥未获此权限；仅支持邮件只读及已授权的个人账本" else "没有权限执行该操作"
            404 -> "资源不存在或不属于当前工作空间"
            409 -> "与服务端数据冲突"
            413 -> "请求内容过大"
            429 -> "操作过于频繁，请稍后再试"
            500, 503, 504 -> "服务端暂时不可用，请稍后重试"
            else -> "请求失败（HTTP $status）"
        }
    }

    return ApiResult.Failure(
        httpStatus = status,
        code = code,
        message = msg,
        upstream = upstream,
        sessionExpired = status == 401 && code == null,
        quotaExceeded = code == 1001,
        cause = cause,
    )
}

private fun upstreamMessage(message: String?, upstream: UpstreamError?): String {
    val action = when (upstream?.error_kind) {
        "auth_failed", "consent_required" -> "邮箱授权失效，请在网页端重新授权"
        "proxy_failed", "network" -> "服务器代理或网络连接失败，请检查代理设置"
        "banned" -> "邮箱账号被服务商封禁"
        "rate_limited" -> "服务商限流，请稍后再试"
        "folder_unavailable" -> "邮件夹不存在或暂时不可用"
        else -> message?.takeIf { it.isNotBlank() } ?: "服务商暂时不可用，请稍后再试"
    }
    return "上游邮箱读取失败：$action"
}
