package com.masteralanlab.emailbox.data

import com.masteralanlab.emailbox.data.remote.Note
import org.junit.Assert.assertEquals
import org.junit.Test

class NotesLocalStoreTest {
    @Test
    fun promotionRemovesCompletedCreateAndRedirectsLaterOps() {
        val create = PendingNoteOp(kind = "create", localId = "local-1", title = "待办")
        val update = create.copy(kind = "update", serverId = "local-1", completed = true)
        val delete = create.copy(kind = "delete", serverId = "local-1")

        assertEquals(
            listOf(
                update.copy(localId = "server-1", serverId = "server-1"),
                delete.copy(localId = "server-1", serverId = "server-1"),
            ),
            promotePendingOps(listOf(create, update, delete), create, "server-1"),
        )
    }

    @Test
    fun bulkDeletionWritesOneLogicalSnapshotAndQueuesOnlyServerDeletes() {
        val local = Note(id = "local-1", tenant_id = "tenant-1", title = "本地草稿")
        val server = Note(id = "server-1", tenant_id = "tenant-1", title = "服务器笔记")
        val keep = Note(id = "server-keep", tenant_id = "tenant-1", title = "保留")
        val localCreate = PendingNoteOp(kind = "create", localId = local.id, title = local.title)
        val localUpdate = localCreate.copy(kind = "update", completed = true)
        val keepUpdate = PendingNoteOp(kind = "update", localId = keep.id, serverId = keep.id)

        val result = applyBulkNoteDeletion(
            NotesLocalData(
                tenant = "tenant-1",
                notes = listOf(local, server, keep),
                pendingOps = listOf(localCreate, localUpdate, keepUpdate),
            ),
            listOf(local, server, server),
        )

        assertEquals(listOf(keep), result.notes)
        assertEquals(
            listOf(
                keepUpdate,
                PendingNoteOp(kind = "delete", localId = server.id, serverId = server.id, title = server.title),
            ),
            result.pendingOps,
        )
    }
}
