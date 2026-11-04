package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.widgets.WidgetFeed
import io.github.eslamasabry.oc.android.widgets.WidgetPanelStore
import io.github.eslamasabry.oc.android.widgets.WidgetPanelStyle
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetPanelPalette
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetPanelSnapshot
import io.github.eslamasabry.oc.ui.shell.widgets.presentation.WidgetPanelSource
import kotlinx.coroutines.withContext

@ContributesTo(AppScope::class)
interface WidgetPanelAppGraph {
    val widgetPanelBindings: WidgetPanelBindings
}

/** Standalone activities reuse the graph's gated stores; no MainActivity or server connection is opened. */
@Inject
@SingleIn(AppScope::class)
class WidgetPanelBindings(
    private val app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
) : WidgetPanelSource {
    private val store: WidgetPanelStore?
        get() = WidgetFeed.persistence?.let { WidgetPanelStore(app, it) }

    fun owns(widgetId: Int): Boolean = store?.owns(widgetId) == true
    fun choosesProject(widgetId: Int): Boolean = store?.choosesProject(widgetId) == true

    override suspend fun load(widgetId: Int): Outcome<WidgetPanelSnapshot> = withContext(dispatchers.io) {
        when (val warmed = stores.warm()) {
            is Outcome.Failed -> warmed
            is Outcome.Ok -> store?.load(widgetId)?.map { it.snapshot() } ?: Outcome.Failed(Failure.StorageRefused())
        }
    }

    override fun palette(background: String, accent: String): WidgetPanelPalette {
        val value = requireNotNull(store).palette(background, accent)
        return WidgetPanelPalette(value.background, value.text, value.muted, value.accent)
    }

    override suspend fun saveStyle(snapshot: WidgetPanelSnapshot, background: String, accent: String): Outcome<Unit> =
        withContext(dispatchers.io) { store?.saveStyle(snapshot.style(), background, accent) ?: Outcome.Failed(Failure.StorageRefused()) }

    override suspend fun saveProject(snapshot: WidgetPanelSnapshot, project: String): Outcome<Unit> =
        withContext(dispatchers.io) { store?.saveProject(snapshot.style(), project) ?: Outcome.Failed(Failure.StorageRefused()) }

    private fun WidgetPanelStyle.snapshot() = WidgetPanelSnapshot(widgetId, owner, scope, background, accent, project, projects, revision)
    private fun WidgetPanelSnapshot.style() = WidgetPanelStyle(widgetId, owner, scope, background, accent, project, projects, revision)
}
