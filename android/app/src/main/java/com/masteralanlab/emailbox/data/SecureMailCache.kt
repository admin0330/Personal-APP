package com.masteralanlab.emailbox.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.masteralanlab.emailbox.data.remote.AppJson
import com.masteralanlab.emailbox.data.remote.MessageDetail
import com.masteralanlab.emailbox.data.remote.MessageItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.masteralanlab.emailbox.widget.MailStatusWidget

data class CachedMail(
    val tenant: String,
    val accountId: String,
    val item: MessageItem,
    val detail: MessageDetail?,
    val category: String,
    val otp: String?,
)

data class MailSearchFilters(
    val query: String = "",
    val accountId: String? = null,
    val category: String? = null,
    val unreadOnly: Boolean = false,
    val attachmentsOnly: Boolean = false,
)

data class MailCacheStats(
    val messages: Int,
    val accounts: Int,
    val unread: Int,
    val bytes: Long,
    val lastSyncAt: Long,
)

object SecureMailCache {
    private const val KEY_ALIAS = "ym1r_mail_cache_v1"
    private const val DB_NAME = "mail-cache.db"
    private const val MAX_PER_ACCOUNT = 500
    private const val MAX_GLOBAL = 1000
    private const val MAX_BYTES = 200L * 1024L * 1024L
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    @Serializable
    private data class Payload(val item: MessageItem, val detail: MessageDetail? = null)

    private lateinit var db: SQLiteDatabase
    private lateinit var appContext: Context

    @Synchronized
    fun init(context: Context) {
        if (::db.isInitialized) return
        appContext = context.applicationContext
        val file = File(appContext.noBackupFilesDir, DB_NAME)
        db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS mail_cache (
                tenant TEXT NOT NULL,
                account_id TEXT NOT NULL,
                folder TEXT NOT NULL,
                id_mode TEXT NOT NULL,
                message_id TEXT NOT NULL,
                received_epoch INTEGER NOT NULL,
                unread INTEGER NOT NULL,
                has_attachments INTEGER NOT NULL,
                category TEXT NOT NULL,
                otp TEXT,
                payload BLOB NOT NULL,
                byte_size INTEGER NOT NULL,
                cached_at INTEGER NOT NULL,
                PRIMARY KEY (tenant, account_id, folder, id_mode, message_id)
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS mail_cache_recent ON mail_cache(tenant, received_epoch DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS mail_cache_category ON mail_cache(tenant, category, received_epoch DESC)")
    }

    @Synchronized
    fun putMessages(tenant: String, accountId: String, items: List<MessageItem>) {
        if (!Prefs.offlineCacheEnabled || items.isEmpty()) return
        db.beginTransaction()
        try {
            items.forEach { item -> put(tenant, accountId, item, null) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        prune(tenant)
        MailStatusWidget.updateAll(appContext)
    }

    @Synchronized
    fun putDetail(tenant: String, accountId: String, detail: MessageDetail) {
        if (!Prefs.offlineCacheEnabled) return
        val item = MessageItem(
            id = detail.id, id_mode = detail.id_mode, folder = detail.folder,
            subject = detail.subject, from = detail.from, to = detail.to, cc = detail.cc,
            received_at = detail.received_at, is_read = detail.is_read,
            has_attachments = detail.has_attachments, body_preview = detail.body_preview,
        )
        put(tenant, accountId, item, detail)
        prune(tenant)
        MailStatusWidget.updateAll(appContext)
    }

    @Synchronized
    fun messages(tenant: String, accountId: String, folder: String, limit: Int = 100): List<MessageItem> =
        queryRows(
            "tenant=? AND account_id=? AND folder=?",
            arrayOf(tenant, accountId, folder),
            limit,
        ).map { it.item }

    @Synchronized
    fun detail(tenant: String, accountId: String, folder: String, idMode: String, messageId: String): MessageDetail? =
        if (idMode.isBlank()) {
            queryRows(
                "tenant=? AND account_id=? AND folder=? AND message_id=?",
                arrayOf(tenant, accountId, folder, messageId),
                1,
            ).firstOrNull()?.detail
        } else {
            queryRows(
                "tenant=? AND account_id=? AND folder=? AND id_mode=? AND message_id=?",
                arrayOf(tenant, accountId, folder, idMode, messageId),
                1,
            ).firstOrNull()?.detail
        }

    @Synchronized
    fun search(tenant: String, filters: MailSearchFilters, limit: Int = 100): List<CachedMail> {
        val clauses = mutableListOf("tenant=?")
        val args = mutableListOf(tenant)
        filters.accountId?.let { clauses += "account_id=?"; args += it }
        filters.category?.let { clauses += "category=?"; args += it }
        if (filters.unreadOnly) clauses += "unread=1"
        if (filters.attachmentsOnly) clauses += "has_attachments=1"
        val query = filters.query.trim().lowercase()
        return queryRows(clauses.joinToString(" AND "), args.toTypedArray(), MAX_GLOBAL)
            .asSequence()
            .filter { cached ->
                query.isBlank() || listOf(
                    cached.item.from, cached.item.to, cached.item.subject,
                    cached.item.body_preview, cached.detail?.body.orEmpty(),
                ).any { it.lowercase().contains(query) }
            }
            .take(limit.coerceIn(1, 100))
            .toList()
    }

    @Synchronized
    fun stats(tenant: String): MailCacheStats {
        db.rawQuery(
            "SELECT COUNT(*), COUNT(DISTINCT account_id), COALESCE(SUM(unread),0), COALESCE(SUM(byte_size),0), COALESCE(MAX(cached_at),0) FROM mail_cache WHERE tenant=?",
            arrayOf(tenant),
        ).use { c ->
            if (!c.moveToFirst()) return MailCacheStats(0, 0, 0, 0, 0)
            return MailCacheStats(c.getInt(0), c.getInt(1), c.getInt(2), c.getLong(3), c.getLong(4))
        }
    }

    @Synchronized
    fun clear(tenant: String? = null) {
        if (tenant == null) db.delete("mail_cache", null, null)
        else db.delete("mail_cache", "tenant=?", arrayOf(tenant))
        MailStatusWidget.updateAll(appContext)
    }

    private fun put(tenant: String, accountId: String, item: MessageItem, detail: MessageDetail?) {
        val override = Prefs.categoryOverride(item.from)
        val insight = MailIntelligence.analyze(item, detail?.body.orEmpty(), override)
        val aad = aad(tenant, accountId, item.folder, item.id_mode, item.id)
        val payload = encrypt(AppJson.encodeToString(Payload(item, detail)), aad)
        val values = ContentValues().apply {
            put("tenant", tenant); put("account_id", accountId); put("folder", item.folder)
            put("id_mode", item.id_mode); put("message_id", item.id)
            put("received_epoch", parseEpoch(item.received_at)); put("unread", if (item.is_read) 0 else 1)
            put("has_attachments", if (item.has_attachments) 1 else 0); put("category", insight.category)
            if (insight.otp == null) putNull("otp") else put("otp", insight.otp)
            put("payload", payload); put("byte_size", payload.size); put("cached_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("mail_cache", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun queryRows(where: String, args: Array<String>, limit: Int): List<CachedMail> {
        val out = mutableListOf<CachedMail>()
        db.query(
            "mail_cache",
            arrayOf("tenant", "account_id", "folder", "id_mode", "message_id", "category", "otp", "payload"),
            where, args, null, null, "received_epoch DESC", limit.toString(),
        ).use { c ->
            while (c.moveToNext()) {
                val tenant = c.getString(0); val account = c.getString(1)
                val folder = c.getString(2); val idMode = c.getString(3); val id = c.getString(4)
                val payload = runCatching {
                    val json = decrypt(c.getBlob(7), aad(tenant, account, folder, idMode, id))
                    AppJson.decodeFromString<Payload>(json)
                }.getOrNull() ?: continue
                out += CachedMail(tenant, account, payload.item, payload.detail, c.getString(5), c.getString(6))
            }
        }
        return out
    }

    private fun prune(tenant: String) {
        val cutoff = Instant.now().minusSeconds(Prefs.cacheDays.coerceIn(7, 90) * 86_400L).epochSecond
        db.delete("mail_cache", "tenant=? AND received_epoch<?", arrayOf(tenant, cutoff.toString()))
        db.rawQuery("SELECT DISTINCT account_id FROM mail_cache WHERE tenant=?", arrayOf(tenant)).use { c ->
            while (c.moveToNext()) {
                db.execSQL(
                    "DELETE FROM mail_cache WHERE rowid IN (SELECT rowid FROM mail_cache WHERE tenant=? AND account_id=? ORDER BY received_epoch DESC LIMIT -1 OFFSET ?)",
                    arrayOf(tenant, c.getString(0), MAX_PER_ACCOUNT),
                )
            }
        }
        db.execSQL(
            "DELETE FROM mail_cache WHERE rowid IN (SELECT rowid FROM mail_cache WHERE tenant=? ORDER BY received_epoch DESC LIMIT -1 OFFSET ?)",
            arrayOf(tenant, MAX_GLOBAL),
        )
        while (stats(tenant).bytes > MAX_BYTES) {
            db.execSQL(
                "DELETE FROM mail_cache WHERE rowid IN (SELECT rowid FROM mail_cache WHERE tenant=? ORDER BY received_epoch ASC LIMIT 50)",
                arrayOf(tenant),
            )
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(value: String, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(aad)
        return cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decrypt(value: ByteArray, aad: ByteArray): String {
        require(value.size > IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, value.copyOf(IV_BYTES)))
        cipher.updateAAD(aad)
        return cipher.doFinal(value.copyOfRange(IV_BYTES, value.size)).toString(StandardCharsets.UTF_8)
    }

    private fun aad(tenant: String, account: String, folder: String, idMode: String, id: String) =
        "$tenant\u0000$account\u0000$folder\u0000$idMode\u0000$id".toByteArray(StandardCharsets.UTF_8)

    private fun parseEpoch(value: String): Long = runCatching { Instant.parse(value).epochSecond }.getOrDefault(0L)
}
