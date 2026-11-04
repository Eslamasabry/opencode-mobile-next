package io.github.eslamasabry.oc.engine.composer.web

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.WebSearchGateway
import io.github.eslamasabry.oc.core.model.WebSearchProvider
import io.github.eslamasabry.oc.core.model.WebSourceSelection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Flutter `WebSourcesOverview` (B-CHAT-124): search and review are transient, and only an explicit
 * confirmation ([reviewedSelection]) returns draft context. Discovery never searches; a search
 * needs a chosen provider; a scope change ([scope] differs from the value at creation: profile,
 * location or gateway) discards every pending result and the local review; a provider invalidation
 * (`websearch.updated`, [invalidateProviders]) supersedes an in-flight search and rediscovers
 * without another query.
 *
 * One serial lane: call from one coroutine context (the owning engine/view model), like the Dart
 * ChangeNotifier.
 */
public class WebSourcesReview(
    private val gateway: WebSearchGateway?,
    private val searchEnabled: () -> Boolean,
    private val scope: () -> Any?,
) {
    private val origin = scope()
    private val staged = mutableListOf<WebSourceSelection>()
    private val selected = mutableSetOf<String>()
    private var searchRevision = 0
    private var providerRevision = 0
    private var discoverAgain = false

    public var scopeChanged: Boolean = false
        private set

    public var providers: ImmutableList<WebSearchProvider> = persistentListOf()
        private set

    public var results: ImmutableList<WebSourceSelection> = persistentListOf()
        private set

    public var providerId: String? = null
        private set

    public var discovering: Boolean = false
        private set

    public var searching: Boolean = false
        private set

    public var searched: Boolean = false
        private set

    public var omittedResults: Int = 0
        private set

    public var searchFailure: Failure? = null
        private set

    /** An immutable snapshot of the review list. */
    public val sources: ImmutableList<WebSourceSelection>
        get() {
            checkScope()
            return staged.toImmutableList()
        }

    public val supportsSearch: Boolean
        get() = gateway != null && searchEnabled()

    public fun isSelected(source: WebSourceSelection): Boolean = source.url in selected

    private fun checkScope() {
        if (scopeChanged || scope() == origin) return
        scopeChanged = true
        searchRevision++
        providerRevision++
        providers = persistentListOf()
        results = persistentListOf()
        searching = false
        discovering = false
        staged.clear()
        selected.clear()
    }

    private fun canEdit(): Boolean {
        checkScope()
        return !scopeChanged
    }

    /**
     * Lists providers; never submits a query. A burst while one runs coalesces into one more
     * discovery.
     */
    public suspend fun discoverProviders() {
        if (!canEdit() || !supportsSearch) return
        if (discovering) {
            providerRevision++
            searchRevision++
            discoverAgain = true
            return
        }
        val revision = ++providerRevision
        searchRevision++
        searching = false
        discovering = true
        results = persistentListOf()
        providers = persistentListOf()
        searched = false
        searchFailure = null
        val found = gateway!!.webSearchProviders()
        if (canEdit() && revision == providerRevision) {
            when (found) {
                is Outcome.Ok -> {
                    providers = found.value
                    if (providers.none { it.id == providerId })
                        providerId = providers.singleOrNull()?.id
                }
                is Outcome.Failed -> {
                    searchFailure = found.failure
                    providerId = null
                }
            }
        }
        if (canEdit()) {
            discovering = false
            if (discoverAgain) {
                discoverAgain = false
                discoverProviders()
            }
        }
    }

    /** The `websearch.updated` / reconnect signal: drop the current answer and rediscover. */
    public suspend fun invalidateProviders() {
        searchRevision++
        results = persistentListOf()
        providers = persistentListOf()
        providerId = null
        searching = false
        searched = false
        staged.clear()
        selected.clear()
        discoverProviders()
    }

    public fun chooseProvider(id: String?) {
        if (!canEdit() || providers.none { it.id == id }) return
        searchRevision++
        providerId = id
        searching = false
        searched = false
        results = persistentListOf()
        searchFailure = null
    }

    /**
     * Explicit search with the chosen provider. Results are only shown for review; nothing is added
     * or sent.
     */
    public suspend fun search(query: String) {
        val chosen = providerId
        if (
            !canEdit() ||
                !supportsSearch ||
                discovering ||
                searching ||
                query.isBlank() ||
                query.length > MAX_QUERY ||
                chosen == null ||
                providers.none { it.id == chosen }
        )
            return
        val revision = ++searchRevision
        searching = true
        searched = false
        results = persistentListOf()
        omittedResults = 0
        searchFailure = null
        val reply = gateway!!.searchWeb(query, chosen)
        if (!canEdit() || revision != searchRevision) return
        when (reply) {
            is Outcome.Ok ->
                if (reply.value.providerId != chosen) {
                    searchFailure = Failure.UnreadableReply()
                } else {
                    val safe = mutableListOf<WebSourceSelection>()
                    val seen = mutableSetOf<String>()
                    for (result in reply.value.results.take(100)) {
                        when (
                            val built =
                                WebSourceSelection.of(
                                    result.title ?: "",
                                    result.url,
                                    result.content,
                                )
                        ) {
                            is WebSourceSelection.Built.Ok ->
                                if (seen.add(built.source.url)) safe += built.source
                            is WebSourceSelection.Built.Refused -> omittedResults++
                        }
                    }
                    results = safe.toImmutableList()
                    searched = true
                }
            is Outcome.Failed -> {
                searchFailure = reply.failure
                if (reply.failure == Failure.Unavailable(FeatureId("webSearch"))) {
                    providers = persistentListOf()
                    providerId = null
                }
            }
        }
        searching = false
    }

    /**
     * Why [add] refused; :ui:words owns the sentences ("Connection changed…", "already in your
     * review list", …).
     */
    public enum class AddProblem {
        ScopeChanged,
        Duplicate,
        TooMany,
    }

    /**
     * Stages [source] for review (selected). Null on success; a failure keeps every staged source.
     */
    public fun add(source: WebSourceSelection): AddProblem? {
        if (!canEdit()) return AddProblem.ScopeChanged
        if (staged.any { it.url == source.url }) return AddProblem.Duplicate
        if (staged.size >= MAX_SOURCES) return AddProblem.TooMany
        staged += source
        selected += source.url
        return null
    }

    public fun select(source: WebSourceSelection, on: Boolean) {
        if (!canEdit() || source !in staged) return
        if (on) selected += source.url else selected -= source.url
    }

    public fun remove(source: WebSourceSelection) {
        if (!canEdit()) return
        staged -= source
        selected -= source.url
    }

    /**
     * The explicit confirmation: the selected sources, or null when the original scope is no longer
     * valid.
     */
    public fun reviewedSelection(): ImmutableList<WebSourceSelection>? =
        if (!canEdit()) null else staged.filter { it.url in selected }.toImmutableList()

    public companion object {
        public const val MAX_SOURCES: Int = 10
        public const val MAX_QUERY: Int = 1000
    }
}
