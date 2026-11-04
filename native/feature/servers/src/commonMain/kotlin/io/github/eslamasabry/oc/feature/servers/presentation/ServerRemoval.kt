package io.github.eslamasabry.oc.feature.servers.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.ui.routes.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

public data class ServerRemovalPlan(
    val token: String,
    val name: String,
    val queued: Int,
    val uncertain: Int,
    val drafts: Int,
    val active: Boolean,
)

public interface ServerRemovalPort {
    public suspend fun inspect(profile: ProfileId): Outcome<ServerRemovalPlan>

    public suspend fun remove(token: String, keepQueued: Boolean): Outcome<Unit>

    public fun forget(token: String)
}

public data class ServerRemovalUi(
    val loading: Boolean = true,
    val plan: ServerRemovalPlan? = null,
    val busy: Boolean = false,
    val deleteQueued: Boolean = false,
    val confirming: Boolean = false,
    val failure: Failure? = null,
)

public interface ServerRemovalActions {
    public fun back()

    public fun refresh()

    public fun deleteQueued(value: Boolean)

    public fun askRemove()

    public fun cancel()

    public fun confirm()
}

@AssistedInject
public class ServerRemovalViewModel(
    @Assisted private val route: ServersRoute.Remove,
    private val port: ServerRemovalPort,
    private val nav: AppNavigator,
) : ViewModel(), ServerRemovalActions {
    private val mutable = MutableStateFlow(ServerRemovalUi())
    public val state: StateFlow<ServerRemovalUi> = mutable.asStateFlow()
    private var generation = 0

    init {
        refresh()
    }

    override fun back() {
        if (!mutable.value.busy) nav.back()
    }

    override fun refresh() {
        if (mutable.value.busy) return
        mutable.value.plan?.let { port.forget(it.token) }
        val ticket = ++generation
        mutable.value = ServerRemovalUi()
        viewModelScope.launch {
            val result = port.inspect(route.profile)
            if (ticket != generation) {
                if (result is Outcome.Ok) port.forget(result.value.token)
                return@launch
            }
            when (result) {
                is Outcome.Ok ->
                    mutable.value = ServerRemovalUi(loading = false, plan = result.value)
                is Outcome.Failed ->
                    mutable.value = ServerRemovalUi(loading = false, failure = result.failure)
            }
        }
    }

    override fun deleteQueued(value: Boolean) {
        if (!mutable.value.busy) mutable.update { it.copy(deleteQueued = value) }
    }

    override fun askRemove() {
        if (mutable.value.plan != null && !mutable.value.busy)
            mutable.update { it.copy(confirming = true) }
    }

    override fun cancel() {
        mutable.update { it.copy(confirming = false) }
    }

    override fun confirm() {
        val own = mutable.value
        val plan = own.plan ?: return
        if (own.busy || !own.confirming) return
        mutable.update { it.copy(confirming = false, busy = true, failure = null) }
        viewModelScope.launch {
            try {
                when (val result = port.remove(plan.token, !own.deleteQueued)) {
                    is Outcome.Ok -> nav.back()
                    is Outcome.Failed -> mutable.update { it.copy(failure = result.failure) }
                }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    override fun onCleared() {
        generation++
        mutable.value.plan?.let { port.forget(it.token) }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ServersRoute.Remove): ServerRemovalViewModel
    }
}
