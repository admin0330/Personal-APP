package com.masteralanlab.emailbox.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.masteralanlab.emailbox.data.Prefs
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 规范化用户填写的服务器地址。 */
fun normalizeServerUrl(raw: String): String {
    var s = raw.trim()
    if (s.isEmpty()) return Prefs.DEFAULT_SERVER
    s = when {
        s.startsWith("http://", true) -> "https://${s.substring(7)}"
        s.startsWith("https://", true) -> s
        else -> "https://$s"
    }.trimEnd('/')

    val url = s.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("服务器地址无效")
    require(url.username.isEmpty() && url.password.isEmpty()) { "服务器地址不能包含账号或密码" }
    require(url.query == null && url.fragment == null) { "服务器地址不能包含查询参数" }

    val path = url.encodedPath.trimEnd('/')
    val normalizedPath = if (url.host.equals("example.com", ignoreCase = true) && path.isEmpty()) {
        "/emailbox"
    } else {
        path
    }
    return url.newBuilder()
        .encodedPath(normalizedPath.ifEmpty { "/" })
        .build()
        .toString()
        .trimEnd('/')
}

/** 服务器地址 → API 基址，必须以 / 结尾，Retrofit 才不会截断路径。 */
fun apiBaseUrl(server: String): String = "${normalizeServerUrl(server)}/api/v1/"

/**
 * 只托管后端下发的 session_token。
 * 后端登录响应不含 token 字段，凭据全在 Set-Cookie 里，所以这里负责把 Cookie 落盘并在
 * 后续请求里带回去；退出登录时后端下发 Max-Age=-1，这里同步清空。
 */
class SessionCookieJar : CookieJar {

    @Volatile
    var serverHost: String = ""

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (c in cookies) {
            if (c.name != "session_token") continue
            if (c.expiresAt <= System.currentTimeMillis()) {
                if (Prefs.sessionToken == c.value) Prefs.sessionToken = null
            } else {
                Prefs.sessionToken = c.value
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val token = Prefs.sessionToken ?: return emptyList()
        if (serverHost.isNotBlank() && !url.host.equals(serverHost, ignoreCase = true)) {
            return emptyList()
        }
        return try {
            listOf(
                Cookie.Builder()
                    .name("session_token")
                    .value(token)
                    .domain(url.host)
                    .path("/")
                    .build()
            )
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

/**
 * API Key 只读模式下试图发出写请求时抛出。
 * 单独一个类型而不是复用 IOException：调用方要把它显示成「权限」语义，
 * 而不是「网络异常」——UI 层已隐藏写入口，能走到这里说明防线被绕过了。
 */
class ReadOnlyBlockedException(message: String) : IOException(message)

/**
 * Bearer 值只允许「可见 ASCII」字符（0x21~0x7E，无空格无控制符）。
 * 真实场景：从网页复制 Key 时混进排版字符（em-dash、全角、零宽空格），
 * okhttp 的 header 校验一旦收到这类值会直接抛 IllegalArgumentException——
 * 且异常发生在 OkHttp 调度线程上，必然炸掉整个进程。
 */
private val SAFE_BEARER = Regex("[!-~]+")

internal fun isValidBearerValue(value: String): Boolean = value.matches(SAFE_BEARER)

/**
 * 请求出网前的统一闸门，纯函数便于直接单测：
 * 1. API Key 模式下拒绝一切非安全方法（写操作防线在客户端再拦一层，后端权限矩阵是主防线）；
 * 2. API Key 模式下补 `Authorization: Bearer`（请求已显式带头时不覆盖）；
 *    含非法字符的 Key 不随请求发送——服务端会以 401 拒绝并走重新绑定路径，
 *    好过在拦截器里抛异常把进程带走；
 * 3. 会话模式下不加该头，凭据由 CookieJar 托管。
 */
fun prepareRequest(original: Request, apiKeyMode: Boolean, apiKey: String?): Request {
    if (apiKeyMode) {
        val safeMethod = original.method in setOf("GET", "HEAD", "OPTIONS")
        if (!safeMethod && !isAllowedLedgerWrite(original)) {
            throw ReadOnlyBlockedException("登录密钥仅允许读取邮件与操作个人账本")
        }
        if (!apiKey.isNullOrBlank() && original.header("Authorization") == null) {
            val token = apiKey.trim()
            if (isValidBearerValue(token)) {
                return original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }
        }
    }
    return original
}

private val LEDGER_COLLECTION = Regex(".*/api/v1/tenants/[^/]+/ledger/transactions$")
private val LEDGER_ITEM = Regex(".*/api/v1/tenants/[^/]+/ledger/transactions/[^/]+$")

internal fun isAllowedLedgerWrite(request: Request): Boolean = when (request.method) {
    "POST" -> LEDGER_COLLECTION.matches(request.url.encodedPath)
    "PATCH", "DELETE" -> LEDGER_ITEM.matches(request.url.encodedPath)
    else -> false
}

object ApiClient {

    private const val SESSION_COOKIE = "session_token"

    private val cookieJar = SessionCookieJar()

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedService: ApiService? = null

    @Volatile
    private var cachedClient: OkHttpClient? = null

    /** 应用内更新等场景需要绕过会话 Cookie 的干净客户端，配备境内高速源站 DNS 智能优先直连。 */
    fun plainClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .dns(OriginAcceleratedDns)
            .build()

    fun okHttp(): OkHttpClient {
        val key = currentKey()
        if (cachedKey == key) cachedClient?.let { return it }
        val client = buildClient()
        synchronized(this) {
            cachedKey = key
            cachedClient = client
        }
        return client
    }

    fun service(): ApiService {
        val key = currentKey()
        if (cachedKey == key) cachedService?.let { return it }
        val base = apiBaseUrl(Prefs.serverUrl)
        val host = runCatching { java.net.URL(base).host }.getOrDefault("")
        cookieJar.serverHost = host
        val svc = Retrofit.Builder()
            .baseUrl(base)
            .client(okHttp())
            .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
        synchronized(this) {
            cachedKey = key
            cachedService = svc
        }
        return svc
    }

    /** 服务器地址或凭据变更後调用，强制重建 Retrofit。 */
    fun invalidate() {
        synchronized(this) {
            cachedKey = null
            cachedService = null
            cachedClient = null
        }
    }

    private fun currentKey(): String =
        "${normalizeServerUrl(Prefs.serverUrl)}|${Prefs.apiKeyMode}|${Prefs.apiKey.orEmpty()}"

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)

        builder.addInterceptor { chain ->
            val request = prepareRequest(
                chain.request(),
                Prefs.apiKeyMode,
                Prefs.apiKey,
            )
            chain.proceed(request)
        }

        if (BuildConfigDebug.enabled) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
        return builder.build()
    }

    /** SSE 长连接：禁用读超时，避免心跳间隙被掐断。 */
    fun sseClient(): OkHttpClient = okHttp().newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    const val COOKIE_NAME = SESSION_COOKIE
}

/** 日志开关，release 关闭。 */
internal object BuildConfigDebug {
    val enabled: Boolean get() = false
}


/** Public builds resolve hosts using the system DNS configuration. */
object OriginAcceleratedDns : okhttp3.Dns {
    override fun lookup(hostname: String): List<java.net.InetAddress> =
        okhttp3.Dns.SYSTEM.lookup(hostname)
}
