package io.github.eslamasabry.oc.feature.setupreview.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.SetupReviewRoute
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Read-only owner: a changed profile, graph or location retires every earlier read. */
@AssistedInject
public class SetupReviewViewModel(
    @Assisted route: SetupReviewRoute,
    private val host: SetupReviewHost,
    private val nav: AppNavigator,
) : ViewModel(), SetupReviewActions {
    private val mutable = MutableStateFlow(SetupReviewUi())
    public val state: StateFlow<SetupReviewUi> = mutable.asStateFlow()
    private var context: SetupReviewContext? = null
    private var readJob: Job? = null
    private var revision = 0L

    init {
        viewModelScope.launch {
            host.contexts(route.profile).collectLatest { next ->
                val previous = context
                if (next == previous) return@collectLatest
                context = next
                revision++
                readJob?.cancel()
                val sameOwner =
                    previous != null &&
                        previous.owner === next.owner &&
                        previous.profile == next.profile &&
                        previous.directory == next.directory &&
                        previous.workspace == next.workspace
                val content = if (sameOwner) mutable.value.content else SetupReviewContent()
                val phase =
                    when {
                        !next.supported -> SetupReviewPhase.Unsupported
                        !next.online -> SetupReviewPhase.Offline
                        else -> SetupReviewPhase.Loading
                    }
                mutable.value =
                    SetupReviewUi(
                        phase,
                        content,
                        online = next.online,
                        guided = if (sameOwner) mutable.value.guided else null,
                    )
                if (next.supported && next.online) refresh()
            }
        }
    }

    override fun back() {
        if (mutable.value.guided != null) mutable.value = mutable.value.copy(guided = null)
        else nav.back()
    }

    override fun openGuided() {
        if (context != null) mutable.value = mutable.value.copy(guided = SetupGuidedUi())
    }

    override fun chooseIntent(intent: SetupGuidedIntent) {
        if (mutable.value.guided?.intent == intent) return
        mutable.value = mutable.value.copy(guided = SetupGuidedUi(intent))
    }

    override fun editInput(field: SetupGuidedField, value: String) {
        val guided = mutable.value.guided ?: return
        if (field !in guided.intent.fields()) return
        mutable.value =
            mutable.value.copy(
                guided =
                    guided.copy(
                        inputs = (guided.inputs + (field to value.take(4096))).toImmutableMap(),
                        reply = null,
                    )
            )
    }

    override fun prepareProposal() {
        val captured = context ?: return
        val guided = mutable.value.guided ?: return
        val reply = host.plan(captured, guided.intent, guided.inputs)
        if (context == captured)
            mutable.value = mutable.value.copy(guided = guided.copy(reply = reply))
    }

    override fun signIn(): Unit = nav.open(ServersRoute.Home)

    override fun refresh() {
        val captured = context ?: return
        if (!captured.online || !captured.supported || readJob?.isActive == true) return
        val generation = revision
        mutable.value = mutable.value.copy(refreshing = true)
        readJob = viewModelScope.launch {
            val result = host.read(captured)
            if (generation != revision || context != captured) return@launch
            mutable.value =
                when (result) {
                    is Outcome.Ok ->
                        SetupReviewUi(
                            phase =
                                if (result.value.isEmpty) SetupReviewPhase.Empty
                                else SetupReviewPhase.Ready,
                            content = result.value,
                            online = true,
                            guided = mutable.value.guided,
                        )
                    is Outcome.Failed ->
                        mutable.value.copy(
                            phase =
                                when (result.failure) {
                                    is Failure.SignIn -> SetupReviewPhase.NeedsSignIn
                                    is Failure.Unavailable -> SetupReviewPhase.Unsupported
                                    is Failure.Network,
                                    is Failure.ComputerDidNotAnswer -> SetupReviewPhase.Offline
                                    else -> SetupReviewPhase.Error
                                },
                            refreshing = false,
                        )
                }
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: SetupReviewRoute): SetupReviewViewModel
    }
}
