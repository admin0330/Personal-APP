package com.masteralanlab.emailbox

import com.masteralanlab.emailbox.data.remote.parseHttpFailure
import com.masteralanlab.emailbox.data.remote.presentableErrorMessage
import com.masteralanlab.emailbox.data.remote.presentableSseMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 与后端约定的错误语义（AGENTS §5.1）：
 * 401 只表示「本次请求的调用方没通过认证」；502 / code=1005 是上游邮箱错误，
 * 绝不能把用户踢回登录页。
 */
class HttpErrorMappingTest {

    @Test
    fun `401 without code field is session expiry in session mode`() {
        val f = parseHttpFailure(401, """{"message":"用户未认证"}""", apiKeyMode = false)
        assertTrue(f.sessionExpired)
        assertEquals(401, f.httpStatus)
        assertNull(f.code)
        assertEquals("用户未认证", f.message)
    }

    @Test
    fun `401 without code field asks rebind in api key mode`() {
        val f = parseHttpFailure(401, """{"message":"API Key 无效"}""", apiKeyMode = true)
        assertTrue(f.sessionExpired)
        assertEquals("API Key 无效", f.message)
    }

    @Test
    fun `401 with business code is upstream trouble not session expiry`() {
        val f = parseHttpFailure(
            401,
            """{"code":1005,"message":"上游失败","data":{"error_kind":"auth_failed"}}""",
            apiKeyMode = false,
        )
        assertFalse(f.sessionExpired)
        assertTrue(f.message!!.startsWith("上游邮箱读取失败"))
        assertEquals(1005, f.code)
    }

    @Test
    fun `403 maps to permission denied and never session expiry`() {
        val session = parseHttpFailure(403, """{"message":"权限不足"}""", apiKeyMode = false)
        val apiKey = parseHttpFailure(403, """{"message":"权限不足"}""", apiKeyMode = true)
        assertFalse(session.sessionExpired)
        assertFalse(apiKey.sessionExpired)
        // 后端自带 message 时优先显示原文
        assertEquals("权限不足", session.message)
        assertEquals("权限不足", apiKey.message)
        // 无 body 时按状态码与凭据模式给出兜底文案
        assertEquals("没有权限执行该操作", parseHttpFailure(403, null, apiKeyMode = false).message)
        assertEquals(
            "登录密钥未获此权限；仅支持邮件只读及已授权的个人账本",
            parseHttpFailure(403, null, apiKeyMode = true).message,
        )
    }

    @Test
    fun `404 maps to tenant or resource missing`() {
        val f = parseHttpFailure(404, null, apiKeyMode = false)
        assertFalse(f.sessionExpired)
        assertEquals("资源不存在或不属于当前工作空间", f.message)
    }

    @Test
    fun `502 without json body still maps to upstream failure`() {
        val f = parseHttpFailure(502, null, apiKeyMode = false)
        assertFalse(f.sessionExpired)
        assertTrue(f.message!!.startsWith("上游邮箱读取失败"))
    }

    @Test
    fun `upstream error_kind drives the action hint`() {
        val authFailed = parseHttpFailure(
            502,
            """{"code":1005,"message":"x","data":{"error_kind":"auth_failed"}}""",
            apiKeyMode = false,
        )
        assertTrue(authFailed.message!!.contains("重新授权"))

        val proxy = parseHttpFailure(
            502,
            """{"code":1005,"message":"x","data":{"error_kind":"proxy_failed"}}""",
            apiKeyMode = false,
        )
        assertTrue(proxy.message!!.contains("代理"))

        val banned = parseHttpFailure(
            200,
            """{"code":1005,"message":"x","data":{"error_kind":"banned"}}""",
            apiKeyMode = false,
        )
        assertTrue(banned.message!!.contains("封禁"))
    }

    @Test
    fun `quota business code maps to quotaExceeded`() {
        val f = parseHttpFailure(200, """{"code":1001,"message":"今日取件已达上限"}""", apiKeyMode = false)
        assertTrue(f.quotaExceeded)
        assertFalse(f.sessionExpired)
        assertEquals(1001, f.code)
    }

    @Test
    fun `malformed body does not crash mapping`() {
        val f = parseHttpFailure(500, "not-json", apiKeyMode = false)
        assertFalse(f.sessionExpired)
        assertEquals("服务端暂时不可用，请稍后重试", f.message)
    }

    @Test
    fun `unknown status falls back to generic message`() {
        val f = parseHttpFailure(418, null, apiKeyMode = false)
        assertEquals("请求失败（HTTP 418）", f.message)
        assertNull(f.upstream)
        assertNotNull(f)
    }

    @Test
    fun `raw connection details are sanitized before reaching UI`() {
        val mapped = parseHttpFailure(
            500,
            """{"message":"Failed to connect to /127.0.0.1:9"}""",
            apiKeyMode = false,
        )
        assertEquals("网络连接暂时不可用，请稍后重试", mapped.message)
        assertFalse(mapped.message.orEmpty().contains("127.0.0.1"))
        assertFalse(mapped.message.orEmpty().contains(":9"))
        assertEquals("网络连接暂时不可用，请稍后重试", presentableErrorMessage("Failed to connect to /127.0.0.1:9"))
    }

    @Test
    fun `sse payload details are sanitized before reaching UI`() {
        assertEquals(
            "网络连接暂时不可用，请稍后重试",
            presentableSseMessage("java.net.ConnectException: /10.0.0.2:443 refused", "任务失败"),
        )
        assertEquals("任务失败", presentableSseMessage("", "任务失败"))
    }
}
