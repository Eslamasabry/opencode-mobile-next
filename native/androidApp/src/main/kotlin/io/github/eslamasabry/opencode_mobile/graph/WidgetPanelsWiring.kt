package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.widgets.WidgetFeed
import io.github.eslamasabry.oc.android.widgets.WidgetPersistence
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.engine.settings.prefs.Preferences
import io.github.eslamasabry.oc.feature.settings.presentation.PreferenceMapping
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@ContributesTo(AppScope::class)
interface WidgetPanelAccessors {
    val widgetPanelsWiring: WidgetPanelsWiring
}

/**
 * The standalone overlay opens display preferences and widget state only; it never warms profiles
 * or starts a server.
 */
@SingleIn(AppScope::class)
@Inject
class WidgetPanelsWiring(
    private val stores: PreviewStores,
    private val migration: MigrationWiring,
    private val appScope: AppCoroutineScope,
    val dispatchers: AppDispatchers,
    private val clock: Clock,
) {
    data class Display(val appearance: KitAppearance, val locale: String?)

    private val preparation = Mutex()
    private var displayPreferences: Preferences? = null

    suspend fun prepare(): Outcome<Display> =
        withContext(dispatchers.io) {
            preparation.withLock {
                migration.engine.gate.awaitOpen()
                attempt({ Failure.StorageRefused() }) {
                    stores.prefs.warm()
                    if (WidgetFeed.persistence == null) {
                        WidgetFeed.persistence =
                            WidgetPersistence(
                                stores.prefs,
                                stores.lanes,
                                DefaultProfileScope(stores.prefs, stores.secrets, stores.lanes.app),
                                appScope,
                                clock,
                            )
                    }
                    val preferences =
                        (displayPreferences
                                ?: Preferences(stores.prefs, stores.lanes.app, appScope).also {
                                    displayPreferences = it
                                })
                            .value
                            .value
                    Display(
                        PreferenceMapping.appearance(preferences),
                        PreferenceMapping.localeTag(preferences),
                    )
                }
            }
        }
}
