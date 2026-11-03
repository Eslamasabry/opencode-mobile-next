package io.github.eslamasabry.oc.feature.chats.inventory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * In-flight work shared by key: the first caller starts [block] in [scope] and every caller with the same key, while
 * it runs, awaits the same result. Running in the scope means a cancelled caller never strands the others.
 * Confined to one dispatcher, like the inventory that uses it.
 */
internal class SharedWork<K : Any, T : Any>(private val scope: CoroutineScope) {
    private val running = HashMap<K, MutableStateFlow<T?>>()

    fun isRunning(key: K): Boolean = key in running

    suspend fun join(key: K, block: suspend () -> T): T {
        val existing = running[key]
        if (existing != null) return existing.first { it != null }!!
        val result = MutableStateFlow<T?>(null)
        running[key] = result
        scope.launch {
            try {
                result.value = block()
            } finally {
                if (running[key] === result) running.remove(key)
            }
        }
        return result.first { it != null }!!
    }
}

/**
 * Turns taken in admission order (a fair, cancellation-safe serial lane without a mutex). A turn waits until every
 * turn admitted before it was released; a turn cancelled while waiting releases itself at once, since it never ran.
 */
internal class TurnLane {
    private data class State(val next: Long = 0, val released: Set<Long> = emptySet(), val lowest: Long = 0)

    private val state = MutableStateFlow(State())

    suspend fun <T> run(block: suspend () -> T): T {
        val ticket = state.getAndUpdate { it.copy(next = it.next + 1) }.next
        try {
            state.first { it.lowest >= ticket }
            return block()
        } finally {
            state.update { s ->
                val released = s.released + ticket
                var lowest = s.lowest
                while (lowest in released) lowest++
                s.copy(released = released.filterTo(HashSet()) { it >= lowest }, lowest = lowest)
            }
        }
    }

    /** True while a turn runs or waits. */
    val busy: Boolean get() = state.value.let { it.lowest < it.next }
}
