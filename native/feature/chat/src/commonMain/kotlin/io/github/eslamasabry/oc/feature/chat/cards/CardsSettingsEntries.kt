package io.github.eslamasabry.oc.feature.chat.cards

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.CardsSettingsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.McpCatalogRoute
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/**
 * The page's entry: one row per connected server, rebuilt whenever the cards setup or the
 * connections change.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class CardsSettingsEntries(
    private val cards: ChatCards,
    private val registry: ConnectionRegistry,
    private val nav: AppNavigator,
    private val connections: ServerConnections,
) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<CardsSettingsRoute> {
            val changes by cards.setup.changes.collectAsStateWithLifecycle()
            val live by registry.live.collectAsStateWithLifecycle()
            val saved by connections.servers.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            var pending by remember { mutableStateOf(emptySet<String>()) }
            var failed by remember { mutableStateOf(emptySet<String>()) }
            LaunchedEffect(live.keys) { live.keys.forEach { cards.setup.recheck(it.raw) } }
            val rows =
                remember(changes, live, saved, pending, failed) {
                    live.values
                        .mapNotNull { graph -> cards.setup.settingsOwner(graph.profile.id.raw) }
                        .distinct()
                        .mapNotNull { owner ->
                            val savedOwner =
                                saved.firstOrNull { it.id.raw == owner } ?: return@mapNotNull null
                            CardsRowUi(
                                owner,
                                savedOwner.name,
                                cards.setup.desired(owner),
                                cards.status(owner),
                                owner in pending,
                                owner in failed,
                            )
                        }
                        .toImmutableList()
                }
            val actions = remember {
                object : CardsSettingsActions {
                    override fun setEnabled(profileId: String, on: Boolean) {
                        pending = pending + profileId
                        failed = failed - profileId
                        scope.launch {
                            val result = cards.setEnabled(profileId, on)
                            pending = pending - profileId
                            if (result is Outcome.Failed && cards.setup.desired(profileId) != on)
                                failed = failed + profileId
                        }
                    }

                    override fun back(): Unit = nav.back()
                }
            }
            CardsSettingsScreen(CardsSettingsUi(rows), actions) { profile ->
                val id = ProfileId(profile)
                val location = registry.live.value[id]?.session?.server?.location?.value
                nav.open(McpCatalogRoute(id, location?.directory, location?.workspace))
            }
        }
    }
}
