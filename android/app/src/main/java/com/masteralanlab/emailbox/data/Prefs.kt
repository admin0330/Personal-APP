package com.masteralanlab.emailbox.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import java.io.File
import javax.crypto.spec.GCMParameterSpec

/**
 * 轻量同步 preferences。CookieJar 需要在同步线程里读写，所以不用 DataStore。
 * API Key 例外：只把 AES-GCM 密文放进 preferences，密钥始终留在 Android Keystore。
 */
object Prefs {

    private const val FILE = "emailbox"
    const val DEFAULT_SERVER = "https://ym3861.cn/emailbox"

    private const val K_SERVER = "server_url"
    private const val K_SESSION = "session_token"
    private const val K_API_KEY_LEGACY = "api_key"
    private const val K_API_KEY_CIPHERTEXT = "api_key_ciphertext"
    private const val K_API_KEY_MODE = "api_key_mode"
    private const val K_TENANT = "tenant_id"
    private const val K_TENANT_NAME = "tenant_name"
    private const val K_USER_ID = "user_id"
    private const val K_USERNAME = "username"
    private const val K_USER_EMAIL = "user_email"
    private const val K_PLATFORM_ROLE = "platform_role"
    private const val K_THEME = "theme_mode"
    private const val K_DYNAMIC = "dynamic_color"
    private const val K_BLOCK_IMAGES = "block_remote_images"
    private const val K_ACCOUNT_CATEGORY = "account_category"
    private const val K_PULL_MODE = "pull_mode"
    private const val K_SHOW_ACCOUNT_DOMAIN = "show_account_domain"
    private const val K_BIOMETRIC_UNLOCK = "biometric_unlock"
    private const val K_UPDATE_CHANNEL = "update_channel"
    private const val K_OFFLINE_CACHE = "offline_cache"
    private const val K_CACHE_DAYS = "cache_days"
    private const val K_NAV_ORDER = "nav_order"
    private const val K_CATEGORY_RULES = "category_rules"
    private const val K_LAST_CHECK = "last_update_check"
    private const val K_SKIPPED_VERSION = "skipped_version"
    private const val API_KEY_ALIAS = "emailbox_api_key"
    private const val KEYSTORE = "AndroidKeyStore"

    const val ACCOUNT_CATEGORY_OFF = "off"
    const val ACCOUNT_CATEGORY_DOMAIN = "domain"
    const val PULL_MODE_LOCAL = "local"
    const val PULL_MODE_SERVER = "server"

    private lateinit var sp: SharedPreferences

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        migrateLegacyApiKey()
    }

    // ---- 服务器 ----
    var serverUrl: String
        get() = sp.getString(K_SERVER, DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(v) = sp.edit().putString(K_SERVER, v).apply()

    // ---- 会话 ----
    var sessionToken: String?
        get() = sp.getString(K_SESSION, null)
        set(v) = sp.edit().putString(K_SESSION, v).apply()

    var apiKey: String?
        get() = sp.getString(K_API_KEY_CIPHERTEXT, null)?.let(::decryptApiKey)
        set(v) {
            val edit = sp.edit().remove(K_API_KEY_LEGACY)
            if (v.isNullOrBlank()) {
                edit.remove(K_API_KEY_CIPHERTEXT)
            } else {
                edit.putString(K_API_KEY_CIPHERTEXT, encryptApiKey(v.trim()))
            }
            edit.apply()
        }

    /** true = 用 API Key（只读）模式访问，false = 用户名密码会话。 */
    var apiKeyMode: Boolean
        get() = sp.getBoolean(K_API_KEY_MODE, false)
        set(v) = sp.edit().putBoolean(K_API_KEY_MODE, v).apply()

    // ---- 工作空间 ----
    var tenantId: String?
        get() = sp.getString(K_TENANT, null)
        set(v) = sp.edit().putString(K_TENANT, v).apply()

    var tenantName: String?
        get() = sp.getString(K_TENANT_NAME, null)
        set(v) = sp.edit().putString(K_TENANT_NAME, v).apply()

    // ---- 当前用户 ----
    var userId: String?
        get() = sp.getString(K_USER_ID, null)
        set(v) = sp.edit().putString(K_USER_ID, v).apply()

    var username: String?
        get() = sp.getString(K_USERNAME, null)
        set(v) = sp.edit().putString(K_USERNAME, v).apply()

    var userEmail: String?
        get() = sp.getString(K_USER_EMAIL, null)
        set(v) = sp.edit().putString(K_USER_EMAIL, v).apply()

    var platformRole: String?
        get() = sp.getString(K_PLATFORM_ROLE, null)
        set(v) = sp.edit().putString(K_PLATFORM_ROLE, v).apply()

    val isPlatformAdmin: Boolean get() = platformRole == "admin"

    // ---- 外观 ----
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    var themeMode: String
        get() = sp.getString(K_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        set(v) = sp.edit().putString(K_THEME, v).apply()

    var dynamicColor: Boolean
        get() = sp.getBoolean(K_DYNAMIC, true)
        set(v) = sp.edit().putBoolean(K_DYNAMIC, v).apply()

    // ---- 邮件正文 ----
    var blockRemoteImages: Boolean
        get() = sp.getBoolean(K_BLOCK_IMAGES, true)
        set(v) = sp.edit().putBoolean(K_BLOCK_IMAGES, v).apply()

    // ---- 账号展示与拉取 ----
    var accountCategory: String
        get() = sp.getString(K_ACCOUNT_CATEGORY, ACCOUNT_CATEGORY_OFF) ?: ACCOUNT_CATEGORY_OFF
        set(v) = sp.edit().putString(K_ACCOUNT_CATEGORY, v).apply()

    /** 按域名分类时是否在行标题显示完整邮箱；关闭则只显示 @ 之前的本地部分。 */
    var showAccountDomain: Boolean
        get() = sp.getBoolean(K_SHOW_ACCOUNT_DOMAIN, true)
        set(v) = sp.edit().putBoolean(K_SHOW_ACCOUNT_DOMAIN, v).apply()

    var pullMode: String
        get() = sp.getString(K_PULL_MODE, PULL_MODE_SERVER) ?: PULL_MODE_SERVER
        set(v) = sp.edit().putString(K_PULL_MODE, v).apply()

    // ---- App 解锁 ----
    var biometricUnlockEnabled: Boolean
        get() = sp.getBoolean(K_BIOMETRIC_UNLOCK, false)
        set(v) = sp.edit().putBoolean(K_BIOMETRIC_UNLOCK, v).apply()

    // ---- 离线缓存与本地智能 ----
    var offlineCacheEnabled: Boolean
        get() = sp.getBoolean(K_OFFLINE_CACHE, true)
        set(v) = sp.edit().putBoolean(K_OFFLINE_CACHE, v).apply()

    var cacheDays: Int
        get() = sp.getInt(K_CACHE_DAYS, 30).coerceIn(7, 90)
        set(v) = sp.edit().putInt(K_CACHE_DAYS, v.coerceIn(7, 90)).apply()

    var navOrder: List<String>
        get() {
            val known = listOf("mail", "ledger", "notes")
            val saved = sp.getString(K_NAV_ORDER, null)?.split(',').orEmpty()
                .map { if (it == "tokens") "notes" else it }
            return (saved.filter { it in known } + known).distinct()
        }
        set(v) = sp.edit().putString(K_NAV_ORDER, v.distinct().joinToString(",")).apply()

    fun categoryOverride(from: String): String? {
        val sender = senderAddress(from)
        val domain = sender.substringAfter('@', "")
        val rules = sp.getStringSet(K_CATEGORY_RULES, emptySet()).orEmpty()
        return rules.firstNotNullOfOrNull { raw ->
            val parts = raw.split('\t', limit = 3)
            if (parts.size != 3) null
            else if ((parts[0] == "sender" && parts[1] == sender) || (parts[0] == "domain" && parts[1] == domain)) parts[2]
            else null
        }
    }

    fun setCategoryRule(kind: String, value: String, category: String) {
        require(kind == "sender" || kind == "domain")
        val normalized = value.trim().lowercase()
        val next = sp.getStringSet(K_CATEGORY_RULES, emptySet()).orEmpty()
            .filterNot { it.startsWith("$kind\t$normalized\t") }
            .toMutableSet()
            .apply { add("$kind\t$normalized\t$category") }
        sp.edit().putStringSet(K_CATEGORY_RULES, next).apply()
    }

    fun setSenderCategoryRule(from: String, category: String) = setCategoryRule("sender", senderAddress(from), category)

    private fun senderAddress(from: String): String =
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+")
            .find(from)?.value?.lowercase() ?: from.trim().lowercase()

    /** 只返回是否存在本地会话，不向 UI 暴露任何凭据内容。 */
    val hasSession: Boolean
        get() = if (apiKeyMode) !apiKey.isNullOrBlank() else !sessionToken.isNullOrBlank()

    // ---- 更新渠道 ----
    const val UPDATE_CHANNEL_STABLE = "stable"
    const val UPDATE_CHANNEL_TEST = "test"

    var updateChannel: String
        get() = when (sp.getString(K_UPDATE_CHANNEL, UPDATE_CHANNEL_STABLE)) {
            UPDATE_CHANNEL_TEST -> UPDATE_CHANNEL_TEST
            else -> UPDATE_CHANNEL_STABLE
        }
        set(v) = sp.edit().putString(
            K_UPDATE_CHANNEL,
            if (v == UPDATE_CHANNEL_TEST) UPDATE_CHANNEL_TEST else UPDATE_CHANNEL_STABLE,
        ).apply()

    // ---- 应用内更新 ----
    var lastUpdateCheck: Long
        get() = sp.getLong(K_LAST_CHECK, 0L)
        set(v) = sp.edit().putLong(K_LAST_CHECK, v).apply()

    var skippedVersion: Int
        get() = sp.getInt(K_SKIPPED_VERSION, 0)
        set(v) = sp.edit().putInt(K_SKIPPED_VERSION, v).apply()

    fun rememberSession(auth: com.masteralanlab.emailbox.data.remote.AuthResponse) {
        sp.edit()
            .putString(K_USER_ID, auth.user.id)
            .putString(K_USERNAME, auth.user.username)
            .putString(K_USER_EMAIL, auth.user.email)
            .putString(K_PLATFORM_ROLE, auth.user.platform_role)
            .apply()
        auth.active_tenant_id?.let { tenantId = it }
    }

    fun clearSession() {
        sp.edit()
            .remove(K_SESSION)
            .remove(K_API_KEY_LEGACY)
            .remove(K_API_KEY_CIPHERTEXT)
            .remove(K_USER_ID)
            .remove(K_USERNAME)
            .remove(K_USER_EMAIL)
            .remove(K_PLATFORM_ROLE)
            .remove(K_TENANT)
            .remove(K_TENANT_NAME)
            .putBoolean(K_BIOMETRIC_UNLOCK, false)
            .putBoolean(K_API_KEY_MODE, false)
            .apply()
            // 所有本地数据跟着账号走：退出/切换登录即清空各类磁盘缓存，
            // 换账号登录时永远不会看到上一个账号的数据残留
            appContext?.let { ctx ->
                runCatching {
                    File(ctx.cacheDir, "accounts_cache.json").delete()
                    File(ctx.cacheDir, "tokens_cache.json").delete()
                    ctx.noBackupFilesDir.listFiles()?.forEach { f ->
                        if (f.name.startsWith("ledger-cache-")) f.delete()
                    }
                }
            }
    }

    /** 初始版本把 API Key 写进了明文 preferences；升级时只迁移一次并立即删掉旧字段。 */
    private fun migrateLegacyApiKey() {
        val legacy = sp.getString(K_API_KEY_LEGACY, null)?.trim().orEmpty()
        if (legacy.isBlank()) {
            sp.edit().remove(K_API_KEY_LEGACY).apply()
            return
        }
        apiKey = legacy
        check(sp.edit().remove(K_API_KEY_LEGACY).commit()) { "无法清理旧版 API Key" }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(API_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                API_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encryptApiKey(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decryptApiKey(value: String): String {
        val raw = Base64.decode(value, Base64.NO_WRAP)
        require(raw.size > GCM_IV_BYTES) { "API Key 密文无效" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(GCM_TAG_BITS, raw.copyOf(GCM_IV_BYTES)),
        )
        return cipher.doFinal(raw.copyOfRange(GCM_IV_BYTES, raw.size))
            .toString(StandardCharsets.UTF_8)
    }

    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
}
