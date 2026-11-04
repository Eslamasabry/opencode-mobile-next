package io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

public data class AgentFeatureOptionUi(val id: String, val label: String)

public data class AgentFeatureUi(
    val id: String,
    val label: String,
    val description: String?,
    val on: Boolean? = null,
    val selected: String? = null,
    val options: List<AgentFeatureOptionUi> = emptyList(),
)

public data class AgentFeaturesSnapshot(val owner: String?, val features: List<AgentFeatureUi>)

public data class AgentFeaturesUi(
    val available: Boolean = false,
    val visible: Boolean = false,
    val loading: Boolean = false,
    val snapshot: AgentFeaturesSnapshot? = null,
    val failure: Failure? = null,
)

public interface AgentFeaturesPort {
    public fun available(ref: ConversationRef): Boolean

    public suspend fun read(ref: ConversationRef): Outcome<AgentFeaturesSnapshot>

    public suspend fun set(
        ref: ConversationRef,
        id: String,
        value: Any,
    ): Outcome<AgentFeaturesSnapshot>
}

@ContributesTo(AppScope::class)
public interface AgentFeaturesBindings {
    @Multibinds(allowEmpty = true) public val agentFeaturePorts: Set<AgentFeaturesPort>
}

public interface AgentFeaturesActions {
    public fun open()

    public fun close()

    public fun refresh()

    public fun set(id: String, value: Any)
}

/** One request at a time; values are read back from the agent instead of optimistically shown. */
@AssistedInject
public class AgentFeaturesViewModel(
    @Assisted route: ChatRoute,
    ports: Set<AgentFeaturesPort>,
    registry: ConnectionRegistry,
) : ViewModel(), AgentFeaturesActions {
    private val ref = route.ref
    private val adapters = ports
    private val mutable = MutableStateFlow(AgentFeaturesUi())
    public val state: StateFlow<AgentFeaturesUi> = mutable.asStateFlow()
    private var revision = 0L

    init {
        viewModelScope.launch {
            registry.live.collect {
                val available = adapters.any { it.available(ref) }
                if (!available) revision++
                mutable.value =
                    mutable.value.copy(
                        available = available,
                        visible = mutable.value.visible && available,
                        loading = mutable.value.loading && available,
                    )
            }
        }
    }

    override fun open() {
        if (!mutable.value.available) return
        mutable.value = mutable.value.copy(visible = true)
        refresh()
    }

    override fun close() {
        mutable.value = mutable.value.copy(visible = false)
    }

    override fun refresh() = request(null, null)

    override fun set(id: String, value: Any) = request(id, value)

    private fun request(id: String?, value: Any?) {
        if (mutable.value.loading || !mutable.value.available) return
        val port = adapters.firstOrNull { it.available(ref) } ?: return
        val ticket = ++revision
        mutable.value = mutable.value.copy(loading = true, failure = null)
        viewModelScope.launch {
            val result = if (id == null) port.read(ref) else port.set(ref, id, value!!)
            if (ticket != revision) return@launch
            mutable.value =
                when (result) {
                    is Outcome.Ok -> mutable.value.copy(loading = false, snapshot = result.value)
                    is Outcome.Failed ->
                        mutable.value.copy(loading = false, failure = result.failure)
                }
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatRoute): AgentFeaturesViewModel
    }
}
