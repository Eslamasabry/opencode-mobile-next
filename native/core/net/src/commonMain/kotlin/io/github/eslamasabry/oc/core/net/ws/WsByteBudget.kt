package io.github.eslamasabry.oc.core.net.ws

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Undelivered bytes are charged until the downstream collector has consumed a message. */
internal class WsByteBudget(private val limit: Int) {
    private val lock = Mutex()
    private var used = 0
    private var changed = CompletableDeferred<Unit>()

    suspend fun acquire(bytes: Int) {
        while (true) {
            val wait =
                lock.withLock {
                    if (bytes <= limit - used) {
                        used += bytes
                        null
                    } else changed
                } ?: return
            wait.await()
        }
    }

    suspend fun release(bytes: Int) {
        lock.withLock {
            used -= bytes
            val previous = changed
            changed = CompletableDeferred()
            previous.complete(Unit)
        }
    }
}
