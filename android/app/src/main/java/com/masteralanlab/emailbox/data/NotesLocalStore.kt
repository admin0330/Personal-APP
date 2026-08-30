package com.masteralanlab.emailbox.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.masteralanlab.emailbox.data.remote.AppJson
import com.masteralanlab.emailbox.data.remote.Note
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 待同步到服务器的笔记操作，按队列顺序回放。 */
@Serializable
data class PendingNoteOp(
    val kind: String,               // create / update / delete
    val localId: String,            // 本地条目 id（create 为客户端生成的临时 id）
    val serverId: String? = null,   // 已知的服务器 id（update/delete 用）
    val title: String = "",
    val content: String = "",
    val pinned: Boolean = false,
)

@Serializable
data class NotesLocalData(
    val tenant: String,
    val notes: List<Note> = emptyList(),
    val pendingOps: List<PendingNoteOp> = emptyList(),
)

/**
 * 笔记本地优先存储（Keystore AES-GCM 加密，与 LedgerLocalStore 同一套模式）。
 * 所有增删改先落本地（秒级反馈），队列化后按顺序同步服务器；断网/失败不丢数据。
 */
object NotesLocalStore {
    private const val FILE_PREFIX = "notes-cache-"
    private const val KEY_ALIAS = "ym1r_notes_cache_v1"
    private const val IV_BYTES = 12
    private lateinit var root: File

    fun init(context: Context) { root = context.noBackupFilesDir }

    @Synchronized
    fun load(tenant: String): NotesLocalData {
        val file = file(tenant)
        if (!file.exists()) return NotesLocalData(tenant)
        return runCatching {
            val raw = file.readBytes()
            require(raw.size > IV_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOf(IV_BYTES)))
            cipher.updateAAD(tenant.toByteArray(StandardCharsets.UTF_8))
            AppJson.decodeFromString<NotesLocalData>(
                cipher.doFinal(raw.copyOfRange(IV_BYTES, raw.size)).toString(StandardCharsets.UTF_8),
            )
        }.getOrNull()?.takeIf { it.tenant == tenant } ?: NotesLocalData(tenant)
    }

    @Synchronized
    fun save(data: NotesLocalData) {
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(data.tenant.toByteArray(StandardCharsets.UTF_8))
            val encoded = AppJson.encodeToString(data).toByteArray(StandardCharsets.UTF_8)
            val file = file(data.tenant)
            val tmp = File(root, "${file.name}.tmp")
            tmp.writeBytes(cipher.iv + cipher.doFinal(encoded))
            if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
        }
    }

    fun newLocalId(): String = "local-" + UUID.randomUUID().toString()

    @Synchronized
    fun putNote(tenant: String, note: Note) {
        val data = load(tenant)
        save(
            data.copy(
                notes = (data.notes.filterNot { it.id == note.id } + note).sortedWith(
                    compareByDescending<Note> { it.is_pinned }.thenByDescending { it.updated_at },
                ),
            ),
        )
    }

    @Synchronized
    fun removeNote(tenant: String, id: String) {
        val data = load(tenant)
        save(data.copy(notes = data.notes.filterNot { it.id == id }))
    }

    @Synchronized
    fun enqueueOp(tenant: String, op: PendingNoteOp) {
        val data = load(tenant)
        save(data.copy(pendingOps = data.pendingOps + op))
    }

    /** create 成功后把临时 id 替换为服务器 id，并把后续指向临时 id 的操作重定向。 */
    @Synchronized
    fun promote(tenant: String, localId: String, serverNote: Note) {
        val data = load(tenant)
        save(
            data.copy(
                notes = (data.notes.filterNot { it.id == localId } + serverNote)
                    .sortedWith(compareByDescending<Note> { it.is_pinned }.thenByDescending { it.updated_at }),
                pendingOps = data.pendingOps.map {
                    when {
                        it.localId == localId -> it.copy(localId = serverNote.id, serverId = serverNote.id)
                        it.serverId == localId -> it.copy(serverId = serverNote.id)
                        else -> it
                    }
                },
            ),
        )
    }

    @Synchronized
    fun popOp(tenant: String, op: PendingNoteOp) {
        val data = load(tenant)
        save(data.copy(pendingOps = data.pendingOps.filterNot { it == op }))
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

    @Synchronized
    fun clearAll() {
        root.listFiles()?.forEach { if (it.name.startsWith(FILE_PREFIX)) it.delete() }
    }

    private fun file(tenant: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(tenant.toByteArray(StandardCharsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(root, "$FILE_PREFIX$digest.bin")
    }
}
