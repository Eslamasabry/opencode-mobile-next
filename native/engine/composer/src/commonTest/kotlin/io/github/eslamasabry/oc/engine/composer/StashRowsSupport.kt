package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import io.github.eslamasabry.oc.engine.composer.store.PrefsEdit
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import io.github.eslamasabry.oc.engine.composer.vault.PayloadEntry
import io.github.eslamasabry.oc.engine.composer.vault.PayloadStore
import kotlinx.coroutines.CompletableDeferred

/** A payload store that counts I/O and can hold a write (Flutter's `_Vault` test double). */
class CountingPayloadStore(val inner: MemoryPayloadStore = MemoryPayloadStore()) : PayloadStore {
    var writes = 0
    var lists = 0
    var reads = 0
    var failLists = false
    var writeEntered: CompletableDeferred<Unit>? = null
    var writeGate: CompletableDeferred<Unit>? = null
    var beforeWrite: (suspend (ownerKey: String) -> Unit)? = null

    val ioCount: Int get() = writes + lists + reads

    override suspend fun list(ownerKey: String?): List<PayloadEntry> {
        lists++
        if (failLists) throw IllegalStateException("synthetic list failure")
        return inner.list(ownerKey)
    }

    override suspend fun length(ownerKey: String, id: String): Long? = inner.length(ownerKey, id)

    override suspend fun read(ownerKey: String, id: String, expectedBytes: Long): ByteArray {
        reads++
        return inner.read(ownerKey, id, expectedBytes)
    }

    override suspend fun write(ownerKey: String, id: String, bytes: ByteArray) {
        beforeWrite?.invoke(ownerKey)
        writeEntered?.complete(Unit)
        writeGate?.await()
        writes++
        inner.write(ownerKey, id, bytes)
    }

    override suspend fun delete(ownerKey: String, id: String) = inner.delete(ownerKey, id)
}

/** Preferences that refuse any commit touching one of [refused] (Flutter's selective disk refusals). */
class SelectivePrefs(val inner: MemoryComposerPrefs = MemoryComposerPrefs()) : ComposerPrefs by inner {
    val refused = mutableSetOf<String>()

    override suspend fun commit(edit: PrefsEdit): Boolean {
        if (edit.entries.keys.any { it in refused }) return false
        return inner.commit(edit)
    }
}
