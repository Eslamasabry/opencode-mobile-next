package io.github.eslamasabry.oc.feature.chat.handoff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.engine.agents.handoff.*
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

public data class StateHandoffUi(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val links: List<HandoffLink> = emptyList(),
    val destinations: List<HandoffDestination> = emptyList(),
    val draft: HandoffDraft? = null,
    val failure: Failure? = null,
    val transferFailed: Boolean = false,
    val retryLink: Boolean = false,
)

public interface StateHandoffActions {
    public fun open()

    public fun close()

    public fun prepare(destination: HandoffDestination)

    public fun send(prompt: String)

    public fun retryLink()

    public fun openCounterpart(link: HandoffLink)
}

@AssistedInject
public class StateHandoffViewModel(
    @Assisted private val route: ChatRoute,
    sources: Set<StateHandoffSource>,
    private val navigator: AppNavigator,
) : ViewModel(), StateHandoffActions {
    private val source = sources.singleOrNull()
    private val mutable = MutableStateFlow(StateHandoffUi())
    public val state: StateFlow<StateHandoffUi> = mutable.asStateFlow()
    private var epoch = 0L
    private var engine: HandoffEngine? = null

    override fun open() {
        if (mutable.value.loading) return
        val generation = ++epoch
        mutable.value = StateHandoffUi(visible = true, loading = true)
        viewModelScope.launch {
            val owner = source
            if (owner == null) {
                mutable.value =
                    mutable.value.copy(
                        loading = false,
                        failure = Failure.Unavailable(FeatureId("agentHandoffFiles")),
                    )
                return@launch
            }
            val links = owner.links(route.ref)
            val destinations = owner.destinations(route.ref)
            if (generation != epoch) return@launch
            mutable.value =
                mutable.value.copy(
                    loading = false,
                    links = links.getOrNull().orEmpty(),
                    destinations = destinations.getOrNull().orEmpty(),
                    failure = links.failureOrNull() ?: destinations.failureOrNull(),
                )
        }
    }

    override fun close() {
        epoch++
        engine = null
        mutable.value = StateHandoffUi()
    }

    override fun prepare(destination: HandoffDestination) {
        if (mutable.value.loading || engine != null) return
        val owner = source ?: return
        val generation = epoch
        mutable.value = mutable.value.copy(loading = true, failure = null, transferFailed = false)
        viewModelScope.launch {
            val created =
                owner.attempt(route.ref, destination) {
                    generation == epoch && mutable.value.visible
                }
            if (generation != epoch) return@launch
            val attempt = created.getOrNull()
            if (attempt == null) {
                mutable.value =
                    mutable.value.copy(loading = false, failure = created.failureOrNull())
                return@launch
            }
            engine = attempt
            val prepared = attempt.prepare("")
            if (generation != epoch) return@launch
            val draft = (prepared.getOrNull() as? HandoffPrepared.Ready)?.draft
            mutable.value =
                mutable.value.copy(
                    loading = false,
                    draft = draft,
                    failure = prepared.failureOrNull(),
                    transferFailed = draft == null,
                )
        }
    }

    override fun send(prompt: String) {
        val attempt = engine ?: return
        if (mutable.value.loading || mutable.value.draft == null) return
        val generation = epoch
        mutable.value = mutable.value.copy(loading = true, failure = null, transferFailed = false)
        viewModelScope.launch {
            val result = attempt.send(prompt)
            if (generation != epoch) return@launch
            finish(result)
        }
    }

    override fun retryLink() {
        val attempt = engine ?: return
        if (mutable.value.loading || !mutable.value.retryLink) return
        val generation = epoch
        mutable.value = mutable.value.copy(loading = true)
        viewModelScope.launch {
            val result = attempt.retryLink()
            if (generation != epoch) return@launch
            finish(result)
        }
    }

    private fun finish(result: Outcome<HandoffSent>) {
        val sent = result.getOrNull() as? HandoffSent.Sent
        if (sent != null) {
            val ref =
                route.ref.copy(
                    sessionId =
                        io.github.eslamasabry.oc.core.common.ids.SessionId(sent.navigationSessionId)
                )
            close()
            navigator.openChat(ref)
        } else {
            mutable.value =
                mutable.value.copy(
                    loading = false,
                    failure = result.failureOrNull(),
                    transferFailed = true,
                    retryLink = engine?.state?.value?.failure == HandoffFailureKind.LinkFailed,
                )
        }
    }

    override fun openCounterpart(link: HandoffLink) {
        val owner = source ?: return
        if (mutable.value.loading) return
        val generation = epoch
        mutable.value = mutable.value.copy(loading = true, failure = null)
        viewModelScope.launch {
            val result = owner.counterpart(route.ref, link)
            if (generation != epoch) return@launch
            val ref = result.getOrNull()
            if (ref != null) {
                close()
                navigator.openChat(ref)
            } else
                mutable.value =
                    mutable.value.copy(loading = false, failure = result.failureOrNull())
        }
    }

    override fun onCleared() {
        epoch++
        engine = null
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatRoute): StateHandoffViewModel
    }
}
