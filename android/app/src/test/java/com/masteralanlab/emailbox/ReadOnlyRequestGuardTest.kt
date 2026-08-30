package com.masteralanlab.emailbox

import com.masteralanlab.emailbox.data.remote.ReadOnlyBlockedException
import com.masteralanlab.emailbox.data.remote.isValidBearerValue
import com.masteralanlab.emailbox.data.remote.prepareRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * API Key 只读模式的最后一道客户端防线：写请求必须在出网前被拦下，
 * Bearer 头只补不覆盖。后端权限矩阵才是主防线，这里挡的是「本不该发出去的请求」。
 */
class ReadOnlyRequestGuardTest {

    init {
        // 真机回归钉住的崩溃：em-dash 等「排版字符」混进 Key 后，
        // okhttp 的 header 校验会在调度线程抛 IllegalArgumentException 直接炸进程。
        // 本类的 invalid-key 用例保证闸门永不把这类值递给 okhttp。
    }

    private fun get(path: String = "/mail/groups") =
        Request.Builder().url("https://ym3861.cn/emailbox/api/v1/tenants/t$path").get().build()

    private fun post() = Request.Builder()
        .url("https://ym3861.cn/emailbox/api/v1/tenants/t/mail/accounts")
        .post("{}".toRequestBody("application/json".toMediaType()))
        .build()

    @Test
    fun `api key mode rejects non-get methods`() {
        listOf("POST", "PATCH", "PUT", "DELETE").forEach { method ->
            val request = Request.Builder()
                .url("https://ym3861.cn/emailbox/api/v1/tenants/t/mail/accounts")
                .method(method, if (method == "DELETE") null else "{}".toRequestBody("application/json".toMediaType()))
                .build()
            assertThrows(ReadOnlyBlockedException::class.java) {
                prepareRequest(request, apiKeyMode = true, apiKey = "ebx_k")
            }
        }
    }

    @Test
    fun `api key mode allows get and adds bearer header`() {
        val prepared = prepareRequest(get(), apiKeyMode = true, apiKey = "ebx_secret")
        assertEquals("GET", prepared.method)
        assertEquals("Bearer ebx_secret", prepared.header("Authorization"))
    }

    @Test
    fun `api key mode only allows exact ledger writes`() {
        val body = "{}".toRequestBody("application/json".toMediaType())
        val allowed = listOf(
            Request.Builder().url("https://ym3861.cn/emailbox/api/v1/tenants/t/ledger/transactions").post(body).build(),
            Request.Builder().url("https://ym3861.cn/emailbox/api/v1/tenants/t/ledger/transactions/id").patch(body).build(),
            Request.Builder().url("https://ym3861.cn/emailbox/api/v1/tenants/t/ledger/transactions/id").delete().build(),
        )
        allowed.forEach { assertEquals(it.method, prepareRequest(it, true, "ebx_secret").method) }
        val lookalike = Request.Builder().url("https://ym3861.cn/emailbox/api/v1/tenants/t/ledger/transactions/id/extra").post(body).build()
        assertThrows(ReadOnlyBlockedException::class.java) { prepareRequest(lookalike, true, "ebx_secret") }
    }

    @Test
    fun `api key mode does not overwrite an explicit authorization header`() {
        val request = Request.Builder()
            .url("https://ym3861.cn/emailbox/api/v1/tenants/t/mail/groups")
            .get()
            .header("Authorization", "Bearer user_provided")
            .build()
        val prepared = prepareRequest(request, apiKeyMode = true, apiKey = "ebx_secret")
        assertEquals("Bearer user_provided", prepared.header("Authorization"))
    }

    @Test
    fun `session mode never adds bearer header`() {
        val prepared = prepareRequest(get(), apiKeyMode = false, apiKey = "ebx_secret")
        assertNull(prepared.header("Authorization"))
    }

    @Test
    fun `session mode allows post without interception`() {
        assertEquals("POST", prepareRequest(post(), apiKeyMode = false, apiKey = null).method)
    }

    @Test
    fun `api key mode with blank key still blocks writes`() {
        assertThrows(ReadOnlyBlockedException::class.java) {
            prepareRequest(post(), apiKeyMode = true, apiKey = null)
        }
    }

    @Test
    fun `key with typographic characters is never attached as a header`() {
        // 0x2014 em-dash：真机崩溃的实际触发字符（网页复制 Key 的现实污染路径）
        val prepared = prepareRequest(get(), apiKeyMode = true, apiKey = "ebx\u2014inv\u00a0alid")
        assertNull(prepared.header("Authorization"))
        assertEquals("GET", prepared.method)
    }

    @Test
    fun `key with space or control characters is rejected by the validator`() {
        assertFalse(isValidBearerValue("ebx key"))
        assertFalse(isValidBearerValue("ebx\tkey"))
        assertFalse(isValidBearerValue("ebx\nkey"))
        assertFalse(isValidBearerValue("ebx全角"))
        assertFalse(isValidBearerValue(""))
        assertTrue(isValidBearerValue("ebx_abc-DEF.123~_=+/"))
    }

    @Test
    fun `blocked request keeps its method readable in the exception path`() {
        // 防线拒绝的是动作而不是资源：拦截发生在发出之前，请求体不外发
        val request = post()
        val thrown = runCatching {
            prepareRequest(request, apiKeyMode = true, apiKey = "ebx_k")
        }.exceptionOrNull()
        assertEquals(ReadOnlyBlockedException::class.java, thrown?.javaClass)
    }
}
