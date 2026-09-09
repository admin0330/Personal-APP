package com.masteralanlab.emailbox.data.remote

import com.masteralanlab.emailbox.data.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

internal fun presentableSseMessage(raw: String?, fallback: String): String =
    raw?.takeIf { it.isNotBlank() }?.let(::presentableErrorMessage)?.ifBlank { fallback } ?: fallback

/**
 * 任务进度事件。与后端 `started/progress/item/finished/error` 五种 SSE 事件一一对应。
 */
sealed interface SseEvent {

    data class Started(val total: Int, val type: String) : SseEvent

    data class Progress(
        val total: Int,
        val success: Int,
        val failed: Int,
        val done: Int,
        val current: String,
    ) : SseEvent

    data class Item(
        val accountId: String,
        val email: String,
        val status: String,
        val errorKind: String,
        val error: String,
    ) : SseEvent

    data class Finished(
        val status: String,
        val total: Int,
        val success: Int,
        val failed: Int,
        val skipped: Int,
        val errorSummary: String,
    ) : SseEvent

    data class Error(val msg: String) : SseEvent
}

sealed interface MailNotificationEvent {
    object Ready : MailNotificationEvent
    data class Mail(val accountId: String, val newCount: Int) : MailNotificationEvent
    data class Error(val status: Int, val message: String) : MailNotificationEvent
}

/**
 * 非 2xx 响应。任务不存在时后端直接返回 404，不会给一个 200 空流，这里必须当成硬错误上报，
 * 否则会退化成「一直重连但永远收不到 finished」。
 */
private class SseHttpException(val status: Int) : IOException(
    if (status == 404) "任务不存在或已过期" else "任务流连接失败（HTTP $status）"
)

/**
 * 任务进度 SSE 客户端。
 *
 * 帧格式由后端固定为三行加一个空行：
 * ```
 * id: <long>
 * event: started|progress|item|finished|error
 * data: <单行 JSON>
 *
 * ```
 * 另有一种心跳帧——以冒号开头的注释行 `: keepalive` 加空行，必须整帧忽略。
 *
 * 断线（流结束但还没收到 finished/error）时带 `last_event_id` 重连，指数退避 1/2/4/8/16 秒，
 * 最多 5 次；收到 finished 或 error 后发射该事件并关闭流。
 */
object SseClient {

    private const val MAX_RETRY = 5
    private val BACKOFF_MS = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L)

    fun jobStream(
        tenantId: String,
        jobId: String,
        lastEventId: Long? = null,
    ): Flow<SseEvent> = callbackFlow {
        val scope = this@callbackFlow

        // 读循环跑在 IO 线程上；取消时要立刻关掉 response，才能让阻塞中的 readLine 抛异常退出。
        var current: Response? = null

        val worker = scope.launch(Dispatchers.IO) {
            var lastId = lastEventId
            var attempt = 0

            while (isActive) {
                if (attempt > 0) {
                    delay(BACKOFF_MS[(attempt - 1).coerceAtMost(BACKOFF_MS.lastIndex)])
                }

                var response: Response? = null
                var terminated = false
                try {
                    response = open(tenantId, jobId, lastId)
                    current = response
                    terminated = pump(response, scope) { lastId = it }
                } catch (_: CancellationException) {
                    break
                } catch (e: SseHttpException) {
                    scope.send(SseEvent.Error(presentableSseMessage(e.message, "任务流连接失败")))
                    break
                } catch (e: Exception) {
                    // 连接中断 / 读超时 / 解析异常：交由下面的重连逻辑处理
                    if (e !is IOException && e !is SerializationException) {
                        scope.send(SseEvent.Error(presentableSseMessage(e.message, "读取任务流失败，请稍后重试")))
                        break
                    }
                } finally {
                    response?.close()
                    if (current === response) current = null
                }

                if (terminated) break

                if (attempt >= MAX_RETRY) {
                    scope.send(
                        SseEvent.Error(
                            presentableSseMessage(null, "与服务器的连接已断开，重试 $MAX_RETRY 次仍未完成"),
                        ),
                    )
                    break
                }
                attempt++
            }

            scope.close()
        }

        awaitClose {
            worker.cancel()
            current?.close()
        }
    }

    /** 服务器拉取模式的通知流；401/403/404 立即停止，502 只做有限重试且不触发登录失效。 */
    fun notificationStream(tenantId: String): Flow<MailNotificationEvent> = callbackFlow {
        val scope = this@callbackFlow
        var current: Response? = null

        val worker = scope.launch(Dispatchers.IO) {
            var attempt = 0
            while (isActive) {
                if (attempt > 0) {
                    delay(BACKOFF_MS[(attempt - 1).coerceAtMost(BACKOFF_MS.lastIndex)])
                }

                var response: Response? = null
                var endedNormally = false
                try {
                    response = openNotifications(tenantId)
                    current = response
                    pumpNotifications(response, scope)
                    endedNormally = true
                } catch (_: CancellationException) {
                    break
                } catch (e: NotificationSseHttpException) {
                    scope.send(MailNotificationEvent.Error(e.status, presentableSseMessage(e.message, "通知流连接失败")))
                    if (e.status in setOf(401, 403, 404)) break
                    attempt++
                    if (attempt > MAX_RETRY) break
                } catch (e: IOException) {
                    attempt++
                    if (attempt > MAX_RETRY) {
                        scope.send(MailNotificationEvent.Error(0, presentableSseMessage(null, "通知流连接已断开")))
                        break
                    }
                } catch (e: Exception) {
                    scope.send(MailNotificationEvent.Error(0, presentableSseMessage(null, "读取通知流失败")))
                    break
                } finally {
                    response?.close()
                    if (current === response) current = null
                }

                if (endedNormally) {
                    // EOF 也要走有限退避，避免服务器主动断开时形成忙循环。
                    attempt++
                    if (attempt > MAX_RETRY) {
                        scope.send(MailNotificationEvent.Error(0, presentableSseMessage(null, "通知流连接已断开")))
                        break
                    }
                }
            }
            scope.close()
        }

        awaitClose {
            worker.cancel()
            current?.close()
        }
    }

    private fun open(tenantId: String, jobId: String, lastEventId: Long?): Response {
        val url = buildString {
            append(apiBaseUrl(Prefs.serverUrl))
            append("tenants/").append(tenantId)
            append("/mail/jobs/").append(jobId).append("/stream")
            if (lastEventId != null) append("?last_event_id=").append(lastEventId)
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .get()
            .build()
        val response = ApiClient.sseClient().newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw SseHttpException(code)
        }
        return response
    }

    private class NotificationSseHttpException(val status: Int) : IOException(
        "通知流连接失败（HTTP $status）"
    )

    private fun openNotifications(tenantId: String): Response {
        val url = "${apiBaseUrl(Prefs.serverUrl)}tenants/$tenantId/mail/notifications/stream"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .get()
            .build()
        val response = ApiClient.sseClient().newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw NotificationSseHttpException(code)
        }
        return response
    }

    private suspend fun pumpNotifications(
        response: Response,
        scope: ProducerScope<MailNotificationEvent>,
    ) {
        val reader = (response.body ?: throw IOException("响应体为空")).charStream().buffered()
        var event: String? = null
        val data = StringBuilder()

        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) {
                val name = event
                val payload = data.toString()
                event = null
                data.setLength(0)
                if (name != null && payload.isNotBlank()) {
                    when (name) {
                        "ready" -> scope.send(MailNotificationEvent.Ready)
                        "mail" -> runCatching {
                            AppJson.decodeFromString<MailNotificationPayload>(payload)
                        }.getOrNull()?.let {
                            if (it.account_id.isNotBlank()) {
                                scope.send(MailNotificationEvent.Mail(it.account_id, it.new_count))
                            }
                        }
                    }
                }
                continue
            }
            if (line.startsWith(":")) continue
            val colon = line.indexOf(':')
            val field = if (colon < 0) line else line.substring(0, colon)
            val value = if (colon < 0) "" else line.substring(colon + 1).removePrefix(" ")
            when (field) {
                "event" -> event = value.trim()
                "data" -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(value)
                }
            }
        }
    }

    /** 逐行读取并按空行切帧。返回 true 表示已收到 finished/error，流应终止。 */
    private suspend fun pump(
        response: Response,
        scope: ProducerScope<SseEvent>,
        onId: (Long) -> Unit,
    ): Boolean {
        val reader = (response.body ?: throw IOException("响应体为空")).charStream().buffered()

        var terminated = false
        var id: Long? = null
        var event: String? = null
        val data = StringBuilder()

        while (true) {
            val line = reader.readLine() ?: break

            if (line.isEmpty()) {
                // 帧结束
                val name = event
                val payload = data.toString()
                event = null
                data.setLength(0)
                if (name == null && payload.isEmpty()) continue

                id?.let { onId(it) }
                id = null

                val ev = decode(name ?: "message", payload) ?: continue
                scope.send(ev)
                if (ev is SseEvent.Finished || ev is SseEvent.Error) {
                    terminated = true
                    break
                }
                continue
            }

            // 心跳注释行（`: keepalive`），整行忽略
            if (line.startsWith(":")) continue

            val colon = line.indexOf(':')
            val field: String
            val value: String
            if (colon < 0) {
                field = line
                value = ""
            } else {
                field = line.substring(0, colon)
                value = line.substring(colon + 1).removePrefix(" ")
            }

            when (field) {
                "id" -> id = value.trim().toLongOrNull()
                "event" -> event = value.trim()
                "data" -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(value)
                }
                // retry / 其它字段：后端未使用
            }
        }

        return terminated
    }

    private fun decode(name: String, payload: String): SseEvent? = try {
        when (name) {
            "started" -> AppJson.decodeFromString<SseStarted>(payload).let {
                SseEvent.Started(it.total, it.type)
            }

            "progress" -> AppJson.decodeFromString<SseProgress>(payload).let {
                SseEvent.Progress(it.total, it.success, it.failed, it.done, it.current)
            }

            "item" -> AppJson.decodeFromString<SseItem>(payload).let {
                SseEvent.Item(
                    it.account_id,
                    it.email,
                    it.status,
                    it.error_kind,
                    presentableSseMessage(it.error, ""),
                )
            }

            "finished" -> AppJson.decodeFromString<SseFinished>(payload).let {
                SseEvent.Finished(
                    status = it.status,
                    total = it.total,
                    success = it.success,
                    failed = it.failed,
                    skipped = it.skipped,
                    errorSummary = presentableSseMessage(it.error_summary, ""),
                )
            }

            "error" -> SseEvent.Error(
                presentableSseMessage(
                    AppJson.decodeFromString<SseErrorPayload>(payload).error,
                    "任务执行失败",
                )
            )

            else -> null
        }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
