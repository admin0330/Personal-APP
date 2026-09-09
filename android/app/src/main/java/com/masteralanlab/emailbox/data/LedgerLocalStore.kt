package com.masteralanlab.emailbox.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.masteralanlab.emailbox.data.remote.AppJson
import com.masteralanlab.emailbox.data.remote.CreateLedgerTransactionRequest
import com.masteralanlab.emailbox.data.remote.LedgerSummary
import com.masteralanlab.emailbox.data.remote.LedgerTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class PendingLedgerUpdate(
    val serverId: String,
    val posted: Boolean? = null,
    val occurredAt: String? = null,
    val type: String? = null,
    val amountMinor: Long? = null,
    val currency: String? = null,
    val category: String? = null,
    val merchant: String? = null,
    val note: String? = null,
)

@Serializable
data class LedgerLocalData(
    val tenant: String,
    val month: String = "",
    val transactions: List<LedgerTransaction> = emptyList(),
    val summary: LedgerSummary? = null,
    val pendingCreates: List<CreateLedgerTransactionRequest> = emptyList(),
    val pendingUpdates: List<PendingLedgerUpdate> = emptyList(),
    val pendingDeletes: List<String> = emptyList(),
)

object LedgerLocalStore {
    private const val FILE_PREFIX = "ledger-cache-"
    private const val KEY_ALIAS = "ym1r_ledger_cache_v1"
    private const val IV_BYTES = 12
    private lateinit var root: File

    fun init(context: Context) { root = context.noBackupFilesDir }

    @Synchronized
    fun load(tenant: String): LedgerLocalData {
        val file = file(tenant)
        if (!file.exists()) return LedgerLocalData(tenant)
        return runCatching {
            val raw = file.readBytes()
            require(raw.size > IV_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOf(IV_BYTES)))
            cipher.updateAAD(tenant.toByteArray(StandardCharsets.UTF_8))
            AppJson.decodeFromString<LedgerLocalData>(cipher.doFinal(raw.copyOfRange(IV_BYTES, raw.size)).toString(StandardCharsets.UTF_8))
        }.getOrNull()?.takeIf { it.tenant == tenant } ?: LedgerLocalData(tenant)
    }

    @Synchronized
    fun save(data: LedgerLocalData) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(data.tenant.toByteArray(StandardCharsets.UTF_8))
        val encoded = AppJson.encodeToString(data).toByteArray(StandardCharsets.UTF_8)
        val file = file(data.tenant)
        val tmp = File(root, "${file.name}.tmp")
        tmp.writeBytes(cipher.iv + cipher.doFinal(encoded))
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
    }

    @Synchronized
    fun enqueue(tenant: String, request: CreateLedgerTransactionRequest) {
        val data = load(tenant)
        if (data.pendingCreates.none { it.client_id == request.client_id }) {
            save(data.copy(pendingCreates = data.pendingCreates + request))
        }
    }

    @Synchronized
    fun complete(tenant: String, clientId: String, transaction: LedgerTransaction) {
        val data = load(tenant)
        save(
            data.copy(
                transactions = (data.transactions.filterNot { it.client_id == clientId } + transaction)
                    .sortedByDescending { it.occurred_at },
                pendingCreates = data.pendingCreates.filterNot { it.client_id == clientId },
            ),
        )
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
    fun putTransaction(tenant: String, t: LedgerTransaction) {
        val data = load(tenant)
        save(data.copy(transactions = data.transactions.filterNot { it.id == t.id } + t))
    }

    @Synchronized
    fun removeTransaction(tenant: String, id: String) {
        val data = load(tenant)
        save(data.copy(transactions = data.transactions.filterNot { it.id == id }))
    }

    @Synchronized
    fun enqueueUpdate(tenant: String, op: PendingLedgerUpdate) {
        val data = load(tenant)
        save(data.copy(pendingUpdates = data.pendingUpdates.filterNot { it.serverId == op.serverId } + op))
    }

    @Synchronized
    fun enqueueDelete(tenant: String, serverId: String) {
        val data = load(tenant)
        if (serverId !in data.pendingDeletes) save(data.copy(pendingDeletes = data.pendingDeletes + serverId))
    }

    @Synchronized
    fun popPendingUpdate(tenant: String, op: PendingLedgerUpdate) {
        val data = load(tenant)
        save(data.copy(pendingUpdates = data.pendingUpdates.filterNot { it == op }))
    }

    @Synchronized
    fun popPendingDelete(tenant: String, serverId: String) {
        val data = load(tenant)
        save(data.copy(pendingDeletes = data.pendingDeletes.filterNot { it == serverId }))
    }

    private fun file(tenant: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(tenant.toByteArray(StandardCharsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(root, "$FILE_PREFIX$digest.bin")
    }
}
