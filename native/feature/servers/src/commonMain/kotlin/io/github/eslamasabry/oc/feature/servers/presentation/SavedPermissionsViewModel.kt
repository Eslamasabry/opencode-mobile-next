package io.github.eslamasabry.oc.feature.servers.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.SavedPermission
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlinx.collections.immutable.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** A scope token is opaque and changes on graph replacement or project/workspace selection. */
public interface SavedPermissionsPort {
    public fun scope(profile: ProfileId): Any?

    public fun scopes(profile: ProfileId): Flow<Any?>

    public suspend fun read(profile: ProfileId, scope: Any): Outcome<ImmutableList<SavedPermission>>

    public suspend fun revoke(profile: ProfileId, scope: Any, id: String): Outcome<Unit>
}

public data class SavedPermissionsUi(
    val loading: Boolean = true,
    val permissions: ImmutableList<SavedPermission> = persistentListOf(),
    val failure: Failure? = null,
    val removing: String? = null,
    val confirm: SavedPermission? = null,
    val revoked: String? = null,
)

public interface SavedPermissionsActions {
    public fun back()

    public fun refresh()

    public fun askRevoke(id: String)

    public fun cancelRevoke()

    public fun confirmRevoke()

    public fun dismissRevoked()
}

@AssistedInject
public class SavedPermissionsViewModel(
    @Assisted private val route: ServersRoute.Permissions,
    private val port: SavedPermissionsPort,
    private val nav: AppNavigator,
) : ViewModel(), SavedPermissionsActions {
    private val mutable = MutableStateFlow(SavedPermissionsUi())
    public val state: StateFlow<SavedPermissionsUi> = mutable.asStateFlow()
    private var scope: Any? = null
    private var generation = 0

    init {
        viewModelScope.launch {
            port.scopes(route.profile).distinctUntilChanged().collect {
                scope = it
                generation++
                mutable.value = SavedPermissionsUi()
                load()
            }
        }
    }

    override fun back() = nav.back()

    override fun refresh() {
        if (mutable.value.removing == null) load()
    }

    private fun load() {
        val at = scope
        val ticket = ++generation
        mutable.update { it.copy(loading = true, failure = null, revoked = null) }
        viewModelScope.launch {
            val read =
                if (at == null) Failure.Refused(RefusalReason.StaleScope).failed()
                else port.read(route.profile, at)
            if (ticket != generation || at != port.scope(route.profile)) return@launch
            when (read) {
                is Outcome.Ok ->
                    mutable.update { it.copy(permissions = read.value, loading = false) }
                is Outcome.Failed ->
                    mutable.update { it.copy(loading = false, failure = read.failure) }
            }
        }
    }

    override fun askRevoke(id: String) {
        if (mutable.value.removing != null) return
        mutable.update { it.copy(confirm = it.permissions.firstOrNull { p -> p.id == id }) }
    }

    override fun cancelRevoke() {
        mutable.update { it.copy(confirm = null) }
    }

    override fun confirmRevoke() {
        val permission = mutable.value.confirm ?: return
        val at = scope ?: return
        if (at != port.scope(route.profile) || mutable.value.removing != null) return
        val ticket = generation
        mutable.update {
            it.copy(removing = permission.id, confirm = null, failure = null, revoked = null)
        }
        viewModelScope.launch {
            val result = port.revoke(route.profile, at, permission.id)
            if (ticket != generation || at != port.scope(route.profile)) return@launch
            when (result) {
                is Outcome.Ok ->
                    mutable.update {
                        it.copy(
                            removing = null,
                            permissions =
                                it.permissions
                                    .filterNot { p -> p.id == permission.id }
                                    .toImmutableList(),
                            revoked = permission.action,
                        )
                    }
                is Outcome.Failed ->
                    mutable.update { it.copy(removing = null, failure = result.failure) }
            }
        }
    }

    override fun dismissRevoked() {
        mutable.update { it.copy(revoked = null) }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ServersRoute.Permissions): SavedPermissionsViewModel
    }
}
