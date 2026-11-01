package io.github.eslamasabry.oc.feature.servers.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the diagnosis needs from the platform (PlatformCapabilities in production). */
public data class ServersEnvironment(val supportsTermux: Boolean)

/** One saved server → the failure it shows, or null (shared by the list, the page and the switcher). */
internal fun SavedServer.failureUi(env: ServersEnvironment, policy: AddressPolicy): ConnectionFailureUi? {
    val f = failure ?: return null
    if (status != ServerStatus.Failed) return null
    return ConnectionDiagnosis.diagnose(
        f,
        profile.origin,
        DiagnosisContext(
            backend = profile.backend,
            supportsTermux = env.supportsTermux,
            managedPhoneServer = profile.managedOnPhone && !inAppServer,
            inAppServer = inAppServer,
            attempts = attempts,
        ),
        policy,
    )
}

/**
 * Servers (07 §5 E01). The phone's rows lead; every row opens its own server page; a failed connect shows inline in
 * the verdict style; forget = confirm + the profile sweep; connect success → `land()`.
 */
public class ServersViewModel(
    private val port: ServersPort,
    private val policy: AddressPolicy,
    private val env: ServersEnvironment,
    private val nav: ServersNavigator,
    scope: CoroutineScope,
) : ViewModel(scope), ServersActions {
    private val local = MutableStateFlow(Local())

    private data class Local(val connecting: ProfileId? = null, val forgetting: ProfileId? = null)

    public val state: StateFlow<ServersUi> =
        combine(port.servers, local) { saved, l ->
            val rows =
                saved
                    .sortedWith(compareByDescending<SavedServer> { it.profile.managedOnPhone }.thenBy { it.profile.name.lowercase() })
                    .map { s ->
                        ServerRowUi(
                            id = s.profile.id,
                            name = s.profile.name,
                            address = s.profile.origin.toString(),
                            status = s.status,
                            active = s.active,
                            onPhone = s.profile.managedOnPhone,
                            failure = s.failureUi(env, policy),
                        )
                    }.toImmutableList()
            ServersUi(rows = rows, connecting = l.connecting, forgetting = rows.firstOrNull { it.id == l.forgetting })
        }.stateIn(scope, SharingStarted.Eagerly, ServersUi())

    override fun addServer(): Unit = nav.openEditor(EditorMode.Add)

    override fun openServer(id: ProfileId): Unit = nav.openServerPage(id)

    override fun connect(id: ProfileId) {
        if (local.value.connecting != null) return
        local.update { it.copy(connecting = id) }
        viewModelScope.launch {
            val result = port.connect(id)
            local.update { it.copy(connecting = null) }
            if (result is Outcome.Ok) nav.land() // a failure stays on its row (the port keeps it)
        }
    }

    override fun fix(id: ProfileId, action: FailureAction) {
        when (action) {
            FailureAction.Retry, FailureAction.StartPhoneServer -> connect(id)
            FailureAction.UpdatePassword, FailureAction.UpdateToken ->
                nav.openEditor(EditorMode.Credentials(id), focusPassword = true)
            FailureAction.ReviewCertificate, FailureAction.EditServer -> nav.openEditor(EditorMode.Edit(id))
            FailureAction.ChangeServer -> nav.openServerPage(id)
        }
    }

    override fun forget(id: ProfileId): Unit = local.update { it.copy(forgetting = id) }

    override fun cancelForget(): Unit = local.update { it.copy(forgetting = null) }

    override fun confirmForget() {
        val id = local.value.forgetting ?: return
        local.update { it.copy(forgetting = null) }
        viewModelScope.launch { port.forget(id) }
    }
}
