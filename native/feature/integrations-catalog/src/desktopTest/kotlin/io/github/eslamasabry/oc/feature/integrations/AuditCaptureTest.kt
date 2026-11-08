package io.github.eslamasabry.oc.feature.integrations

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogNotice
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogState
import io.github.eslamasabry.oc.engine.integrations.catalog.orderMcpCatalog
import io.github.eslamasabry.oc.feature.integrations.catalog.McpCatalogScreen
import io.github.eslamasabry.oc.feature.integrations.catalog.RecordingActions
import io.github.eslamasabry.oc.feature.integrations.catalog.entry
import io.github.eslamasabry.oc.feature.integrations.catalog.mcpCatalogUi
import io.github.eslamasabry.oc.feature.integrations.catalog.registryEntries
import kotlin.test.Test

/** Audit capture only: the MCP catalogue page in many states. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private fun shot(name: String, state: McpCatalogState, forgetting: Boolean = false) =
        Audit.guard("integrations-catalog__catalog__${name}__build") {
            val ui = mcpCatalogUi(state, forgetting)
            Audit.shoot("integrations-catalog", "catalog", name) { McpCatalogScreen(ui, RecordingActions()) }
        }

    @Test
    fun catalog() {
        val entries = orderMcpCatalog(registryEntries(), setOf("docs"))
        val longTitle =
            entry("Weather").copy(
                title = "A very long registry listing title that goes on and on until it has to wrap",
                description = "An extremely long description. ".repeat(12),
            )
        shot("consent", McpCatalogState(loading = false, optedIn = false))
        shot("consent-save-failed", McpCatalogState(loading = false, optedIn = false, notice = McpCatalogNotice.SaveFailed), forgetting = true)
        shot("loading", McpCatalogState(loading = true, optedIn = true))
        shot("list", McpCatalogState(loading = false, optedIn = true, entries = entries, installed = setOf("docs"), canAdd = true))
        shot("list-cannot-add", McpCatalogState(loading = false, optedIn = true, entries = entries, canAdd = false))
        shot("list-long-title", McpCatalogState(loading = false, optedIn = true, entries = listOf(longTitle) + entries, canAdd = true))
        shot("empty", McpCatalogState(loading = false, optedIn = true))
        shot("load-failed", McpCatalogState(loading = false, optedIn = true, notice = McpCatalogNotice.LoadFailed))
        shot("load-failed-with-list", McpCatalogState(loading = false, optedIn = true, entries = entries, notice = McpCatalogNotice.LoadFailed))
        shot("unsupported", McpCatalogState(loading = false, optedIn = true, entries = entries, notice = McpCatalogNotice.Unsupported))
        shot("source-changed", McpCatalogState(loading = false, optedIn = true, entries = entries, notice = McpCatalogNotice.SourceChanged))
        shot("added", McpCatalogState(loading = false, optedIn = true, entries = entries, installed = setOf("docs", "weather-mcp"), notice = McpCatalogNotice.Added, canAdd = true))
        shot("detail-npm", McpCatalogState(loading = false, optedIn = true, entries = entries, selected = entry("Weather"), canAdd = true))
        shot("detail-hosted", McpCatalogState(loading = false, optedIn = true, entries = entries, selected = entry("Docs search"), canAdd = true))
        shot("detail-long", McpCatalogState(loading = false, optedIn = true, entries = entries, selected = longTitle, canAdd = true))
        shot("detail-adding", McpCatalogState(loading = false, optedIn = true, entries = entries, selected = entry("Weather"), adding = true, canAdd = true))
        shot("detail-conflict", McpCatalogState(loading = false, optedIn = true, entries = entries, selected = entry("Weather"), notice = McpCatalogNotice.Conflict, canAdd = true))
        shot("detail-uncertain", McpCatalogState(loading = false, optedIn = true, entries = entries, selected = entry("Weather"), notice = McpCatalogNotice.Uncertain, uncertain = true, canAdd = true))
        shot("detail-needs-settings", McpCatalogState(loading = false, optedIn = true, entries = entries, selected = entry("Notes"), notice = McpCatalogNotice.NeedsSettings, canAdd = true))
        shot("detail-save-failed", McpCatalogState(loading = false, optedIn = true, entries = entries, selected = entry("Weather"), notice = McpCatalogNotice.SaveFailed, canAdd = true))
    }
}
