package io.github.eslamasabry.oc.feature.search.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.WebSearchGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.WebSearchProvider
import io.github.eslamasabry.oc.core.model.WebSearchResult
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.ui.routes.SearchRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

public data class WebSearchUi(
    val available: Boolean = false,
    val open: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val invalidQuery: Boolean = false,
    val searched: Boolean = false,
    val providers: ImmutableList<WebSearchProvider> = persistentListOf(),
    val provider: String? = null,
    val results: ImmutableList<WebSearchResult> = persistentListOf(),
    val review: WebLinkReview? = null,
)

public interface WebSearchActions {
    public fun toggle()

    public fun chooseProvider(id: String)

    public fun discover()

    public fun search(query: String)

    public fun openResult(url: String)

    public fun discardReview()

    public fun confirmOpen()
}

/**
 * Explicit web searches have a separate field and never enter conversation-search history or send a
 * prompt.
 */
@AssistedInject
public class WebSearchViewModel(
    @Assisted private val route: SearchRoute.Conversations,
    private val registry: ConnectionRegistry,
    private val openers: Set<WebResultOpener>,
) : ViewModel(), WebSearchActions {
    private val mutable = MutableStateFlow(WebSearchUi())
    public val state: StateFlow<WebSearchUi> = mutable.asStateFlow()
    private var selected = route.profile
    private var graph: ConnectionGraph? = null
    private var generation = 0L
    private var cancelObserve: () -> Unit = {}
    private var cancelRead: () -> Unit = {}

    init {
        viewModelScope.launch { registry.live.collectLatest { observe() } }
    }

    public fun chooseServer(profile: ProfileId?) {
        if (profile == selected) return
        selected = profile
        observe()
    }

    private fun observe() {
        cancelObserve()
        cancelRead()
        generation++
        graph = selected?.let { registry.live.value[it] }
        val next = graph
        discardReview()
        mutable.value = WebSearchUi(open = state.value.open)
        if (next == null) return
        val job = viewModelScope.launch {
            next.session.server.location.collectLatest {
                discardReview()
                cancelRead()
                generation++
                mutable.value = WebSearchUi(open = state.value.open)
                coroutineScope {
                    launch {
                        next.session.capabilities.collectLatest { capabilities ->
                            val available =
                                capabilities.webSearch && next.session.server is WebSearchGateway
                            mutable.value = state.value.copy(available = available)
                            if (available && state.value.open) discover()
                        }
                    }
                    next.session.server.events.project.collect { envelope ->
                        val event = envelope.event
                        if (event is GatewayEvent.RefreshHint && event.kind.raw == "websearch") {
                            discardReview()
                            cancelRead()
                            generation++
                            mutable.value =
                                state.value.copy(
                                    results = persistentListOf(),
                                    searched = false,
                                    loading = false,
                                )
                            if (state.value.open) discover()
                        }
                    }
                }
            }
        }
        cancelObserve = { job.cancel() }
    }

    override fun toggle() {
        val next = !state.value.open
        mutable.value = state.value.copy(open = next)
        if (next && state.value.available) discover()
        if (!next) {
            cancelRead()
            generation++
            discardReview()
            mutable.value = state.value.copy(loading = false)
        }
    }

    override fun chooseProvider(id: String) {
        if (state.value.loading || state.value.providers.none { it.id == id }) return
        mutable.value =
            state.value.copy(provider = id, results = persistentListOf(), searched = false)
    }

    override fun discover() {
        val expected = graph ?: return
        val gateway = expected.session.server as? WebSearchGateway ?: return
        if (!state.value.available) return
        cancelRead()
        val mine = ++generation
        val location = expected.session.server.location.value
        mutable.value = state.value.copy(loading = true, failed = false)
        val job = viewModelScope.launch {
            val answer = gateway.webSearchProviders()
            if (
                generation != mine ||
                    selected?.let { registry.live.value[it] } !== expected ||
                    expected.session.server.location.value != location
            )
                return@launch
            mutable.value =
                when (answer) {
                    is Outcome.Failed ->
                        state.value.copy(
                            loading = false,
                            failed = true,
                            providers = persistentListOf(),
                            provider = null,
                        )
                    is Outcome.Ok ->
                        state.value.copy(
                            loading = false,
                            providers = answer.value,
                            provider =
                                state.value.provider?.takeIf { id ->
                                    answer.value.any { it.id == id }
                                } ?: answer.value.firstOrNull()?.id,
                        )
                }
        }
        cancelRead = { job.cancel() }
    }

    override fun search(query: String) {
        val text = query.trim()
        if (state.value.loading || !state.value.available) return
        if (text.isEmpty() || query.length > 1000) {
            mutable.value = state.value.copy(invalidQuery = true)
            return
        }
        val expected = graph ?: return
        val provider = state.value.provider ?: return
        val gateway = expected.session.server as? WebSearchGateway ?: return
        cancelRead()
        val mine = ++generation
        val location = expected.session.server.location.value
        mutable.value =
            state.value.copy(
                loading = true,
                failed = false,
                invalidQuery = false,
                results = persistentListOf(),
                searched = false,
            )
        val job = viewModelScope.launch {
            val answer = gateway.searchWeb(text, provider)
            if (
                generation != mine ||
                    selected?.let { registry.live.value[it] } !== expected ||
                    expected.session.server.location.value != location
            )
                return@launch
            mutable.value =
                when (answer) {
                    is Outcome.Failed -> state.value.copy(loading = false, failed = true)
                    is Outcome.Ok ->
                        state.value.copy(
                            loading = false,
                            results = answer.value.results,
                            searched = true,
                        )
                }
        }
        cancelRead = { job.cancel() }
    }

    override fun openResult(url: String) {
        if (state.value.results.none { it.url == url }) return
        discardReview()
        val review = openers.singleOrNull()?.review(url)
        mutable.value = state.value.copy(review = review, failed = review == null)
    }

    override fun discardReview() {
        state.value.review?.let { openers.singleOrNull()?.discard(it.token) }
        mutable.value = state.value.copy(review = null)
    }

    override fun confirmOpen() {
        val review = state.value.review ?: return
        mutable.value = state.value.copy(review = null)
        viewModelScope.launch {
            val result = openers.singleOrNull()?.open(review.token)
            if (result !is Outcome.Ok) mutable.value = state.value.copy(failed = true)
        }
    }

    override fun onCleared() {
        discardReview()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: SearchRoute.Conversations): WebSearchViewModel
    }
}
