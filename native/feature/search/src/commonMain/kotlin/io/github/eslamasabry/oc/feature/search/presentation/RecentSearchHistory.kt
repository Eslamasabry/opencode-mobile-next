package io.github.eslamasabry.oc.feature.search.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Recent searches over one saved string list, for the app runtime to back with its preferences. [warm] waits for the
 * stores, [load] reads the saved list, [save] writes a new one (an empty list removes it). Restoration and every change
 * run one at a time under one lock, and a change restores first, so a search remembered before start-up restoration
 * has run is merged with the saved history instead of replacing it.
 */
public class RecentSearchHistory(
    scope: CoroutineScope,
    private val warm: suspend () -> Outcome<Unit>,
    private val load: () -> List<String>,
    private val save: suspend (List<String>) -> Outcome<Unit>,
) : RecentSearchStore {
    private val order = Mutex()
    private val mutable = MutableStateFlow<ImmutableList<String>>(persistentListOf())
    override val recent: StateFlow<ImmutableList<String>> = mutable.asStateFlow()

    /** Whether the saved list has been read into [mutable]; guarded by [order]. */
    private var restored = false

    init {
        scope.launch { order.withLock { restore() } }
    }

    /** Reads the saved list once; call under [order]. */
    private suspend fun restore(): Outcome<Unit> {
        if (restored) return Outcome.Ok(Unit)
        when (val ready = warm()) {
            is Outcome.Failed -> return ready
            is Outcome.Ok -> Unit
        }
        mutable.value = load().take(RecentSearchStore.RECENT_LIMIT).toImmutableList()
        restored = true
        return Outcome.Ok(Unit)
    }

    override suspend fun remember(query: String): Outcome<Unit> = write { SearchMatch.remember(it, query) }

    override suspend fun forget(query: String): Outcome<Unit> = write { list -> list.filterNot { it == query } }

    override suspend fun clear(): Outcome<Unit> = write { emptyList() }

    private suspend fun write(change: (List<String>) -> List<String>): Outcome<Unit> = order.withLock {
        when (val ready = restore()) {
            is Outcome.Failed -> return@withLock ready
            is Outcome.Ok -> Unit
        }
        val next = change(mutable.value)
        val saved = save(next)
        if (saved is Outcome.Ok) mutable.value = next.toImmutableList()
        saved
    }
}
