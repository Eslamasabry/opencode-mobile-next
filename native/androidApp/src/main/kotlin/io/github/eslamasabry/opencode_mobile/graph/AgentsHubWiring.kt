package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import io.github.eslamasabry.oc.feature.agents.hub.AgentsHubConnection
import io.github.eslamasabry.oc.feature.agents.hub.AgentsHubConnectionUi
import java.net.URI
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Reads the saved phone-server owner without starting Linux; switching uses normal connection
 * admission.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<AgentsHubConnection>())
class AgentsHubWiring(
    private val stores: PreviewStores,
    private val profiles: ProfileStore,
    private val connections: ServerConnections,
    appScope: AppCoroutineScope,
) : AgentsHubConnection {
    override val state = flow {
        if (stores.warm() is Outcome.Failed) {
            emit(AgentsHubConnectionUi())
            return@flow
        }
        emitAll(
            combine(
                profiles.profiles,
                profiles.activeId,
                stores.prefs.changes.onStart { emit(emptySet()) },
            ) { saved, active, _ ->
                val owner = ownerId()?.takeIf { id -> saved.any { it.id == id } }
                AgentsHubConnectionUi(
                    builtInAvailable = owner != null,
                    usingBuiltIn = saved.any { it.id == active && isBuiltIn(it.profile.baseUrl) },
                )
            }
        )
    }
        .stateIn(appScope, SharingStarted.Eagerly, AgentsHubConnectionUi())

    override suspend fun switchToBuiltIn(): Outcome<Unit> {
        when (val warmed = stores.warm()) {
            is Outcome.Failed -> return warmed
            is Outcome.Ok -> Unit
        }
        val owner =
            ownerId()?.takeIf { profiles.profile(it) != null }
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
        return connections.connect(owner)
    }

    private fun ownerId(): ProfileId? {
        val saved = profiles.profiles.value
        val recorded = (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value
        return saved.firstOrNull { it.id.raw == recorded && isBuiltIn(it.profile.baseUrl) }?.id
            ?: saved.firstOrNull { isBuiltIn(it.profile.baseUrl) }?.id
    }

    /** The Flutter built-in server's exact address rule; Termux's port 4096 is a separate host. */
    private fun isBuiltIn(address: String): Boolean =
        try {
            val uri = URI(address)
            uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port == 4097
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: java.net.URISyntaxException) {
            false
        }
}
