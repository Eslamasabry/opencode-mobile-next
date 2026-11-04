package io.github.eslamasabry.oc.feature.search.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.WebSearchGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.WebSearchProvider
import io.github.eslamasabry.oc.core.model.WebSourceSelection
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.composer.drafts.DraftLocation
import io.github.eslamasabry.oc.engine.composer.drafts.DraftState
import io.github.eslamasabry.oc.engine.composer.web.WebSourcesReview
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.WebSourcesRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

public enum class WebSourcesProblem {
    Changed,
    Invalid,
    Duplicate,
    Full,
    Failed,
}

public data class WebSourceRow(val source: WebSourceSelection, val selected: Boolean)

public data class WebSourcesUi(
    val available: Boolean = false,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val changed: Boolean = false,
    val searched: Boolean = false,
    val providers: ImmutableList<WebSearchProvider> = persistentListOf(),
    val provider: String? = null,
    val results: ImmutableList<WebSourceSelection> = persistentListOf(),
    val sources: ImmutableList<WebSourceRow> = persistentListOf(),
    val omitted: Int = 0,
    val problem: WebSourcesProblem? = null,
)

public interface WebSourcesActions {
    public fun back()

    public fun discover()

    public fun chooseProvider(id: String)

    public fun search(query: String)

    public fun addResult(url: String)

    public fun addLink(title: String, url: String, excerpt: String)

    public fun select(url: String, on: Boolean)

    public fun remove(url: String)

    public fun confirm(draftLabel: String)
}

/** B-CHAT-124: no search on discovery and no prompt or page fetch on confirmation. */
@AssistedInject
public class WebSourcesViewModel(
    @Assisted private val route: WebSourcesRoute,
    private val registry: ConnectionRegistry,
    drafts: Set<ConversationDrafts>,
    private val nav: AppNavigator,
) : ViewModel(), WebSourcesActions {
    private val mutable = MutableStateFlow(WebSourcesUi())
    public val state: StateFlow<WebSourcesUi> = mutable.asStateFlow()
    private val graph = registry.graphFor(route.ref)
    private val gateway = graph?.session?.server
    private val location = gateway?.location?.value
    // This is the app's canonical session, shared with the underlying composer. Closing it when
    // this route pops would disable that composer; profile deletion retires it in
    // ConversationDrafts.
    private val draft = drafts.singleOrNull()?.open(route.ref)
    private var original: DraftState? =
        draft?.state?.value?.takeIf { it.ready && it.failure == null }
    private var targetReady = false
    private var committed = false
    private var invalidated = false
    private val review =
        WebSourcesReview(
            gateway as? WebSearchGateway,
            { gateway?.capabilities?.value?.webSearch == true },
            { if (current()) graph else null },
        )

    private fun current(): Boolean =
        !invalidated &&
            graph != null &&
            registry.graphFor(route.ref) === graph &&
            gateway?.location?.value == location

    init {
        viewModelScope.launch {
            val detail = gateway?.session(route.ref.sessionId)
            if (detail !is Outcome.Ok || draft == null || !current()) {
                publish(if (current()) WebSourcesProblem.Failed else WebSourcesProblem.Changed)
                return@launch
            }
            draft.bindLocation(DraftLocation(detail.value.directory, detail.value.workspaceId?.raw))
            val snapshot =
                withTimeoutOrNull(5_000) {
                    draft.state.first { it.ready || it.failure != null }
                }
            if (snapshot?.ready == true && snapshot.failure == null && current()) {
                if (original == null) original = snapshot
                targetReady = true
            }
            publish(if (original == null) WebSourcesProblem.Failed else null)
        }
        viewModelScope.launch { registry.live.collect { if (!current()) changed() } }
        if (gateway != null) {
            viewModelScope.launch { gateway.location.collect { if (!current()) changed() } }
            viewModelScope.launch {
                gateway.events.project.collect { envelope ->
                    val event = envelope.event
                    if (event is GatewayEvent.RefreshHint && event.kind.raw == "websearch") {
                        review.invalidateProviders()
                        publish()
                    }
                }
            }
            viewModelScope.launch {
                gateway.capabilities.collect { caps ->
                    if (!caps.webSearch) {
                        review.invalidateProviders()
                        publish()
                    }
                }
            }
        }
        publish()
        discover()
    }

    private fun changed() {
        invalidated = true
        // Reading sources checks the engine's captured scope and discards pending state.
        review.sources
        mutable.value = WebSourcesUi(changed = true, problem = WebSourcesProblem.Changed)
    }

    private fun publish(problem: WebSourcesProblem? = null, saving: Boolean = false) {
        if (!current()) {
            changed()
            return
        }
        mutable.value =
            WebSourcesUi(
                available = review.supportsSearch,
                ready = targetReady && original != null && !committed,
                busy = saving || review.discovering || review.searching,
                changed = review.scopeChanged,
                searched = review.searched,
                providers = review.providers,
                provider = review.providerId,
                results = review.results,
                sources =
                    review.sources
                        .map { WebSourceRow(it, review.isSelected(it)) }
                        .toImmutableList(),
                omitted = review.omittedResults,
                problem =
                    problem ?: if (review.searchFailure != null) WebSourcesProblem.Failed else null,
            )
    }

    override fun back() = nav.back()

    override fun discover() {
        if (!current() || committed) return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            review.discoverProviders()
            publish()
        }
        publish()
    }

    override fun chooseProvider(id: String) {
        review.chooseProvider(id)
        publish()
    }

    override fun search(query: String) {
        if (
            !current() || committed || query.isBlank() || query.length > WebSourcesReview.MAX_QUERY
        ) {
            publish(WebSourcesProblem.Invalid)
            return
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            review.search(query.trim())
            publish()
        }
        publish()
    }

    private fun add(source: WebSourceSelection) {
        val problem =
            when (review.add(source)) {
                null -> null
                WebSourcesReview.AddProblem.ScopeChanged -> WebSourcesProblem.Changed
                WebSourcesReview.AddProblem.Duplicate -> WebSourcesProblem.Duplicate
                WebSourcesReview.AddProblem.TooMany -> WebSourcesProblem.Full
            }
        publish(problem)
    }

    override fun addResult(url: String) {
        if (committed) return
        review.results.firstOrNull { it.url == url }?.let(::add)
    }

    override fun addLink(title: String, url: String, excerpt: String) {
        if (committed) return
        when (val built = WebSourceSelection.of(title, url, excerpt)) {
            is WebSourceSelection.Built.Ok -> add(built.source)
            is WebSourceSelection.Built.Refused -> publish(WebSourcesProblem.Invalid)
        }
    }

    override fun select(url: String, on: Boolean) {
        review.sources.firstOrNull { it.url == url }?.let { review.select(it, on) }
        publish()
    }

    override fun remove(url: String) {
        review.sources.firstOrNull { it.url == url }?.let(review::remove)
        publish()
    }

    override fun confirm(draftLabel: String) {
        if (committed || !targetReady || state.value.busy) return
        val before = original ?: return
        val target = draft ?: return
        val sources = review.reviewedSelection() ?: return changed()
        if (sources.isEmpty()) return
        val now = target.state.value
        if (
            !current() ||
                !now.ready ||
                now.editRevision != before.editRevision ||
                now.text != before.text ||
                now.attachments != before.attachments
        ) {
            publish(WebSourcesProblem.Changed)
            return
        }
        val appendix =
            JsonArray(
                    sources.map { source ->
                        buildJsonObject {
                            put("title", source.title)
                            put("url", source.url)
                            put("excerpt", source.excerpt?.let(::JsonPrimitive) ?: JsonNull)
                        }
                    }
                )
                .toString()
        val addition = "$draftLabel\n$appendix"
        val text = if (before.text.isEmpty()) addition else "${before.text}\n\n$addition"
        if (!target.replaceReviewedText(before.editRevision, text)) {
            publish(WebSourcesProblem.Changed)
            return
        }
        committed = true
        publish(saving = true)
        viewModelScope.launch {
            val result = target.flush()
            if (result is Outcome.Ok && current()) nav.openChat(route.ref, focusComposer = true)
            else publish(if (current()) WebSourcesProblem.Failed else WebSourcesProblem.Changed)
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: WebSourcesRoute): WebSourcesViewModel
    }
}
