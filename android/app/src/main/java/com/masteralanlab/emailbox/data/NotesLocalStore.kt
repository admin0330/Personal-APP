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
    val completed: Boolean = false,
)

@Serializable
data class NotesLocalData(
    val tenant: String,
    val notes: List<Note> = emptyList(),
    val pendingOps: List<PendingNoteOp> = emptyList(),
)

class NotesStorageException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * Remove the operation that just completed before redirecting later operations to the server id.
 * Keeping the completed create in the queue makes every flush create another copy forever.
 */
internal fun promotePendingOps(
    pendingOps: List<PendingNoteOp>,
    completedOp: PendingNoteOp,
    serverId: String,
): List<PendingNoteOp> {
    var removed = false
    return pendingOps.mapNotNull { op ->
        if (!removed && op == completedOp) {
            removed = true
            null
        } else {
            op.copy(
                localId = if (op.localId == completedOp.localId) serverId else op.localId,
                serverId = if (op.serverId == completedOp.localId) serverId else op.serverId,
            )
        }
    }
}

/**
 * 应用一次批量删除：本地笔记只写一次，服务器笔记各追加一个删除操作。
 * 未同步的 local-* 笔记直接移除其 create/update 队列，避免把本地草稿发到服务器。
 */
internal fun applyBulkNoteDeletion(data: NotesLocalData, notes: List<Note>): NotesLocalData {
    val selected = notes.associateBy { it.id }
    if (selected.isEmpty()) return data
    val selectedIds = selected.keys
    val localIds = selectedIds.filter { it.startsWith("local-") }.toSet()
    val existingDeleteIds = data.pendingOps
        .asSequence()
        .filter { it.kind == "delete" }
        .map { it.serverId ?: it.localId }
        .toSet()
    val deleteOps = selected.values
        .filter { !it.id.startsWith("local-") && it.id !in existingDeleteIds }
        .map { note ->
            PendingNoteOp(kind = "delete", localId = note.id, serverId = note.id, title = note.title)
        }

    return data.copy(
        notes = data.notes.filterNot { it.id in selectedIds },
        pendingOps = data.pendingOps.filterNot { it.localId in localIds } + deleteOps,
    )
}

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
        return try {
            val raw = file.readBytes()
            require(raw.size > IV_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOf(IV_BYTES)))
            cipher.updateAAD(tenant.toByteArray(StandardCharsets.UTF_8))
            val data = AppJson.decodeFromString<NotesLocalData>(
                cipher.doFinal(raw.copyOfRange(IV_BYTES, raw.size)).toString(StandardCharsets.UTF_8),
            )
            if (data.tenant != tenant) throw NotesStorageException("本地笔记所属空间不匹配，请重试")
            data
        } catch (e: NotesStorageException) {
            throw e
        } catch (e: Exception) {
            throw NotesStorageException("本地笔记读取失败，请检查设备存储空间后重试", e)
        }
    }

    @Synchronized
    fun save(data: NotesLocalData) {
        val file = file(data.tenant)
        val tmp = File(root, "${file.name}.tmp")
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(data.tenant.toByteArray(StandardCharsets.UTF_8))
            val encoded = AppJson.encodeToString(data).toByteArray(StandardCharsets.UTF_8)
            tmp.writeBytes(cipher.iv + cipher.doFinal(encoded))
            if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
        } catch (e: Exception) {
            tmp.delete()
            throw NotesStorageException("本地笔记保存失败，请检查设备存储空间后重试", e)
        }
    }

    fun newLocalId(): String = "local-" + UUID.randomUUID().toString()

    @Synchronized
    fun putNote(tenant: String, note: Note) {
        val data = load(tenant)
        save(
            data.copy(
                notes = (data.notes.filterNot { it.id == note.id } + note).sortedWith(
                    compareBy<Note> { it.is_completed }.thenByDescending { it.is_pinned }.thenByDescending { it.updated_at },
                ),
            ),
        )
    }

    @Synchronized
    fun removeNote(tenant: String, id: String) {
        val data = load(tenant)
        save(data.copy(notes = data.notes.filterNot { it.id == id }))
    }

    /** 批量删除只做一次解密/加密，避免长列表逐条写 Keystore 快照导致卡顿或进程被杀。 */
    @Synchronized
    fun removeNotesAndQueueDeletes(tenant: String, notes: List<Note>): NotesLocalData {
        val updated = applyBulkNoteDeletion(load(tenant), notes)
        save(updated)
        return updated
    }

    @Synchronized
    fun enqueueOp(tenant: String, op: PendingNoteOp) {
        val data = load(tenant)
        save(data.copy(pendingOps = data.pendingOps + op))
    }

    /** create 成功后移除已完成操作、替换临时 id，并把后续操作重定向到服务器 id。 */
    @Synchronized
    fun promote(tenant: String, completedOp: PendingNoteOp, serverNote: Note) {
        val data = load(tenant)
        save(
            data.copy(
                notes = (data.notes.filterNot { it.id == completedOp.localId } + serverNote)
                    .sortedWith(compareBy<Note> { it.is_completed }.thenByDescending { it.is_pinned }.thenByDescending { it.updated_at }),
                pendingOps = promotePendingOps(data.pendingOps, completedOp, serverNote.id),
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
