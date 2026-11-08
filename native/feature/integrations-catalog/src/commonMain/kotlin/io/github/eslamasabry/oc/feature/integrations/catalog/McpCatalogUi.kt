package io.github.eslamasabry.oc.feature.integrations.catalog

import io.github.eslamasabry.oc.engine.integrations.catalog.CatalogRuntime
import io.github.eslamasabry.oc.engine.integrations.catalog.CatalogUrl
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogEntry
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogNotice
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogState

/**
 * What a catalogue row says it needs (Flutter mcp_catalog_screen.dart `_row` facts line): where it
 * runs, then a key, then other settings. The screen turns each into words.
 */
public sealed interface McpCatalogFact {
    /** A hosted server: the host of its address, or its server name when the address has none. */
    public data class HostedBy(val host: String) : McpCatalogFact

    public data object NeedsNode : McpCatalogFact

    public data object NeedsPython : McpCatalogFact

    public data object NeedsKey : McpCatalogFact

    public data object NeedsSettings : McpCatalogFact
}

/** Why a row that is not on cannot be added from the catalogue (Flutter `disabledReason`). */
public enum class McpCatalogBlocked {
    /** Runs only in Docker. */
    Docker,

    /** Lists nothing the app can start. */
    NoEndpoint,
}

public data class McpCatalogRowUi(
    val id: String,
    val title: String,
    val description: String,
    val installed: Boolean,
    val facts: List<McpCatalogFact>,
    val blocked: McpCatalogBlocked?,
)

public data class McpCatalogUi(
    val catalog: McpCatalogState = McpCatalogState(),
    val rows: List<McpCatalogRowUi> = emptyList(),
    /** "Stop using the registry" sits in the bar's menu while the list is in use. */
    val canForget: Boolean = false,
    /** The last Stop using the registry could not be saved. */
    val forgetFailed: Boolean = false,
    /** The registry lists no prices: said under any list. */
    val priceNote: Boolean = false,
)

/**
 * One row's facts. The native draft keeps only required header and variable names (the engine
 * drops the registry's secret flag), so any required one reads as a key, as the review page asks
 * for each as a secret.
 */
public fun mcpCatalogRow(entry: McpCatalogEntry, installed: Set<String>): McpCatalogRowUi {
    val on = entry.serverName in installed
    val draft = entry.draft
    val facts = buildList {
        when (entry.runtime) {
            CatalogRuntime.Hosted ->
                add(
                    McpCatalogFact.HostedBy(
                        draft?.url?.let(CatalogUrl::parse)?.host?.takeIf { it.isNotEmpty() }
                            ?: entry.serverName
                    )
                )
            CatalogRuntime.Node -> add(McpCatalogFact.NeedsNode)
            CatalogRuntime.Python -> add(McpCatalogFact.NeedsPython)
            CatalogRuntime.Docker,
            CatalogRuntime.Unavailable -> Unit
        }
        if (draft != null && (draft.headers.isNotEmpty() || draft.environment.isNotEmpty()))
            add(McpCatalogFact.NeedsKey)
        if (entry.requiresArguments) add(McpCatalogFact.NeedsSettings)
    }
    val blocked =
        when {
            on || draft != null -> null
            entry.runtime == CatalogRuntime.Docker -> McpCatalogBlocked.Docker
            else -> McpCatalogBlocked.NoEndpoint
        }
    return McpCatalogRowUi(
        id = entry.id,
        title = entry.title.ifEmpty { entry.serverName },
        description = entry.description,
        installed = on,
        facts = facts,
        blocked = blocked,
    )
}

/**
 * The page from the engine's state. [forgetting] is set by the ViewModel while (and after) the
 * person asked to stop using the registry: a save failure then reads as a failed forget.
 */
public fun mcpCatalogUi(state: McpCatalogState, forgetting: Boolean): McpCatalogUi {
    val rows = state.entries.map { mcpCatalogRow(it, state.installed) }
    return McpCatalogUi(
        catalog = state,
        rows = rows,
        canForget = state.optedIn && !state.adding,
        forgetFailed = forgetting && state.notice == McpCatalogNotice.SaveFailed,
        priceNote = state.optedIn && state.selected == null && rows.isNotEmpty(),
    )
}
