package io.github.eslamasabry.oc.engine.runtime.setupinspection

import io.github.eslamasabry.oc.core.common.*
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Metadata only. A listing is never executed or followed, and search results are never persisted.
 */
public data class RegistryListing(
    val name: String,
    val version: String,
    val title: String,
    val description: String,
)

public enum class RegistryStatus {
    Disabled,
    Ready,
    Empty,
    Loading,
    Offline,
    Error,
}

public data class RegistrySnapshot(
    val optedIn: Boolean = false,
    val status: RegistryStatus = RegistryStatus.Disabled,
    val entries: List<RegistryListing> = emptyList(),
    val query: String? = null,
)

public interface RegistryCache {
    public suspend fun load(): Outcome<RegistrySnapshot?>

    /**
     * Refuses after profile deletion; profile writer lanes order this before the deletion sweep.
     */
    public suspend fun save(snapshot: RegistrySnapshot): Outcome<Unit>

    public fun exists(): Boolean
}

public fun interface RegistryFetcher {
    /**
     * Fixed unauthenticated registry origin; redirects and bodies over the cap must be refused by
     * the binding.
     */
    public suspend fun fetch(search: String?): Outcome<List<RegistryListing>>
}

/**
 * One per open profile view. Cancellation and a generation fence keep late refreshes out of cache
 * and presentation.
 */
public class SetupRegistry(private val cache: RegistryCache, private val fetcher: RegistryFetcher) {
    private val mutable = MutableStateFlow(RegistrySnapshot())
    public val state: StateFlow<RegistrySnapshot> = mutable.asStateFlow()
    private val lane = Mutex()
    private var generation = 0L
    private var pending: Job? = null
    private var saved = RegistrySnapshot()
    private var closed = false

    public suspend fun load(): Outcome<Unit> = lane.withLock {
        if (closed) return@withLock Failure.Refused(RefusalReason.ProfileClosing).failed()
        when (val loaded = cache.load()) {
            is Outcome.Failed -> {
                mutable.value = RegistrySnapshot(status = RegistryStatus.Error)
                loaded
            }
            is Outcome.Ok -> {
                saved =
                    loaded.value?.let {
                        it.copy(
                            status = resting(it.optedIn, it.entries),
                            entries = it.entries.toList(),
                            query = null,
                        )
                    } ?: RegistrySnapshot()
                mutable.value = saved
                Unit.ok()
            }
        }
    }

    public suspend fun consent(enabled: Boolean): Outcome<Unit> {
        generation++
        pending?.cancel()
        return lane.withLock {
            if (closed) return@withLock Failure.Refused(RefusalReason.ProfileClosing).failed()
            val next = saved.copy(optedIn = enabled, status = resting(enabled, saved.entries))
            mutable.value = next
            when (val result = cache.save(next)) {
                is Outcome.Failed -> {
                    mutable.value = next.copy(optedIn = false, status = RegistryStatus.Error)
                    result
                }
                is Outcome.Ok -> {
                    saved = next
                    Unit.ok()
                }
            }
        }
    }

    public fun offline() {
        generation++
        pending?.cancel()
        pending = null
        if (!closed)
            mutable.value =
                saved.copy(
                    optedIn = state.value.optedIn,
                    status =
                        if (state.value.optedIn) RegistryStatus.Offline
                        else RegistryStatus.Disabled,
                )
    }

    public fun showSaved() {
        generation++
        pending?.cancel()
        if (!closed) mutable.value = saved
    }

    public suspend fun refresh(query: String? = null): Outcome<Unit> {
        if (closed) return Failure.Refused(RefusalReason.ProfileClosing).failed()
        if (!state.value.optedIn) return Failure.Refused(RefusalReason.ConsentMissing).failed()
        generation++
        val ownGeneration = generation
        val ownJob = coroutineContext.job
        if (pending !== ownJob) pending?.cancel()
        pending = ownJob
        val search = query?.trim()?.takeIf { it.isNotEmpty() }
        mutable.value =
            saved.copy(
                status = RegistryStatus.Loading,
                query = search,
                entries = if (search == null) saved.entries else emptyList(),
            )
        val loaded = fetcher.fetch(search)
        return lane.withLock {
            if (closed || ownGeneration != generation || !cache.exists())
                return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
            pending = null
            when (loaded) {
                is Outcome.Failed -> {
                    mutable.value = saved.copy(status = RegistryStatus.Error, query = search)
                    loaded
                }
                is Outcome.Ok -> {
                    val next =
                        RegistrySnapshot(
                            true,
                            resting(true, loaded.value),
                            loaded.value.toList(),
                            search,
                        )
                    if (search != null) {
                        mutable.value = next
                        return@withLock Unit.ok()
                    }
                    when (val written = cache.save(next)) {
                        is Outcome.Failed -> {
                            mutable.value = saved.copy(status = RegistryStatus.Error)
                            written
                        }
                        is Outcome.Ok -> {
                            saved = next
                            mutable.value = next
                            Unit.ok()
                        }
                    }
                }
            }
        }
    }

    public fun close() {
        closed = true
        generation++
        pending?.cancel()
        pending = null
    }

    private fun resting(enabled: Boolean, entries: List<RegistryListing>): RegistryStatus =
        if (!enabled) RegistryStatus.Disabled
        else if (entries.isEmpty()) RegistryStatus.Empty else RegistryStatus.Ready
}
