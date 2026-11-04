package io.github.eslamasabry.oc.engine.attention.activity

import kotlinx.coroutines.CompletableDeferred

/** The disk under the activity history: refuses, throws, or holds a write at [gate]; survives "restart". */
class FakeAttentionKeyStore : AttentionKeyStore {
    val data = mutableMapOf<String, String>()
    var refuse = false
    var throwOnWrite = false
    var throwOnRead = false
    var gate: CompletableDeferred<Unit>? = null
    var writeStarted: CompletableDeferred<Unit>? = null
    var writes = 0

    override suspend fun read(key: String): String? {
        if (throwOnRead) error("Synthetic read failure")
        return data[key]
    }

    override suspend fun write(key: String, value: String): Boolean {
        writeStarted?.complete(Unit)
        gate?.await()
        if (throwOnWrite) error("Synthetic storage failure")
        if (refuse) return false
        writes++
        data[key] = value
        return true
    }

    override suspend fun remove(key: String): Boolean {
        if (refuse) return false
        data.remove(key)
        return true
    }
}
