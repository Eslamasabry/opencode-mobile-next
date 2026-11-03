package io.github.eslamasabry.oc.engine.connectors.catalog

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueSnapshot
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueStatus

/** The ways to add an MCP server (Dart `McpAddPath`, map `mcp-add-sheet`). */
public enum class McpAddPath { Catalog, Manual }

/**
 * Whether this server can take a new MCP server from the app at all: a saved configuration write or an add for the
 * current location. Gated on capabilities, never on the server's flavour (Dart `mcpAddSupported`).
 */
public fun mcpAddSupported(capabilities: ServerCapabilities): Boolean =
    capabilities.serverCatalog && (capabilities.mcpConfigWrites || capabilities.mcpRuntimeAdds)

/**
 * MCP › Add (P2.4): browse the catalogue, or enter a server by hand. No setup-assistant row (not built). When the
 * catalogue is unavailable its row stays, disabled, with [catalogUnavailable] as its only supporting line.
 */
public data class McpAddSheetUi(val catalogEnabled: Boolean) {
    val choices: List<McpAddPath> get() = listOf(McpAddPath.Catalog, McpAddPath.Manual)

    /** Copy: "No catalogue for this server: it doesn't accept new MCP servers from the app." (`mcpAddBrowseNone`). */
    val catalogUnavailable: Boolean get() = !catalogEnabled

    /** The path a tap chooses; null (the sheet stays) for the disabled catalogue row. */
    public fun choose(path: McpAddPath): McpAddPath? = if (path == McpAddPath.Catalog && !catalogEnabled) null else path

    public companion object {
        public fun of(capabilities: ServerCapabilities): McpAddSheetUi = McpAddSheetUi(mcpAddSupported(capabilities))
    }
}

/** One fact under a catalogue row, joined with " · " by the UI. */
public sealed interface CatalogueFact {
    /** "Hosted by {host}" */
    public data class HostedBy(val host: String) : CatalogueFact

    /** "Needs Node on the server" / "Needs Node on this phone" */
    public data class NeedsNode(val onPhone: Boolean) : CatalogueFact

    /** "Needs Python with uv on the server" */
    public data object NeedsPython : CatalogueFact

    /** "Needs an API key" */
    public data object NeedsKey : CatalogueFact

    /** "Needs settings" */
    public data object NeedsSettings : CatalogueFact
}

/** Why a row's switch cannot be used (Dart `disabledReason`). */
public enum class CatalogueRowBlock {
    Adding,
    Removing,

    /** "On. This server keeps it in its configuration, and the app can't remove it." */
    CannotRemove,

    /** "Runs in Docker. To add it anyway, …" */
    NeedsDocker,
    NoEndpoint,
}

public data class CatalogueRowUi(
    val catalogId: String,
    val serverName: String,
    /** Plain text, redacted; never rendered as markdown or a link. */
    val title: String,
    val description: String?,
    val on: Boolean,
    val facts: List<CatalogueFact>,
    val blocked: CatalogueRowBlock?,
) {
    val enabled: Boolean get() = blocked == null
}

/** The catalogue page (map `mcp-catalog`). */
public sealed interface McpCatalogueUi {
    /** No add path on this server, or no profile. */
    public data object Unavailable : McpCatalogueUi

    public data object Loading : McpCatalogueUi

    /** Asks before the first request: Load the list / Enter manually. */
    public data object Consent : McpCatalogueUi

    /** The server's own list could not be read: Retry. */
    public data object InventoryFailed : McpCatalogueUi

    /** "Couldn't load the public MCP registry" (no [query]) or the search failed; Retry and Enter manually. */
    public data class Failed(val query: String?) : McpCatalogueUi

    /** Nothing listed, or nothing matches [query]; Enter manually. */
    public data class Empty(val query: String?) : McpCatalogueUi

    /** Rows (on, then addable, then not addable), a stale notice when a refresh failed, and the price note. */
    public data class Rows(val rows: List<CatalogueRowUi>, val stale: Boolean) : McpCatalogueUi {
        /** "The registry lists no prices. A hosted server's owner may charge for it or ask for an account." */
        val priceNote: Boolean get() = true
    }
}

/** What turning a row's switch does. */
public sealed interface CatalogueToggle {
    /** The manual form, filled from the listing (same Save, same gateway call). */
    public data class OpenForm(val draft: McpServerDraft) : CatalogueToggle

    /** On this phone a Node server first offers Add tools › Node or Continue ("{title} runs with Node"). */
    public data class ChooseNode(val title: String, val draft: McpServerDraft) : CatalogueToggle

    /** The MCP page's removal question, then its removal call. */
    public data class ConfirmRemoval(val serverName: String) : CatalogueToggle

    public data object Nothing : CatalogueToggle
}

/** Everything the catalogue page is built from. */
public data class CatalogueScreenInput(
    val capabilities: ServerCapabilities,
    val hasProfile: Boolean,
    /** Null while the saved list is being read. */
    val registry: CatalogueSnapshot?,
    /** The server's MCP names; null while loading. */
    val installed: Set<String>?,
    val inventoryFailed: Boolean = false,
    val busy: Set<String> = emptySet(),
    /** The profile is this phone's own host (Node is installed here, not on a server). */
    val phoneHost: Boolean = false,
)

/** The catalogue's presentation logic (Dart `McpCatalogScreen._body/_content/_row/_turnOn/_turnOff`). */
public object CataloguePresenter {
    /** The menu's "Stop using the registry" is offered only once the person opted in. */
    public fun canForget(input: CatalogueScreenInput): Boolean = input.registry?.optedIn == true

    public fun present(input: CatalogueScreenInput): McpCatalogueUi {
        if (!mcpAddSupported(input.capabilities) || !input.hasProfile) return McpCatalogueUi.Unavailable
        val registry = input.registry ?: return McpCatalogueUi.Loading
        if (!registry.optedIn) return McpCatalogueUi.Consent
        val loading = registry.status == CatalogueStatus.Loading
        val failed = registry.status == CatalogueStatus.Error
        if (input.inventoryFailed) return McpCatalogueUi.InventoryFailed
        if ((loading && registry.entries.isEmpty()) || input.installed == null) return McpCatalogueUi.Loading
        if (failed && registry.entries.isEmpty()) return McpCatalogueUi.Failed(registry.query)
        if (registry.entries.isEmpty()) return McpCatalogueUi.Empty(registry.query)
        val rows = orderCatalog(registry.entries, input.installed).map { row(it, input) }
        return McpCatalogueUi.Rows(rows, stale = failed)
    }

    public fun row(item: McpCatalogItem, input: CatalogueScreenInput): CatalogueRowUi {
        val on = input.installed?.contains(item.serverName) == true
        val busy = item.serverName in input.busy
        val canRemove = input.capabilities.mcpRuntimeRemovals
        val blocked =
            when {
                busy -> if (on) CatalogueRowBlock.Removing else CatalogueRowBlock.Adding
                on && !canRemove -> CatalogueRowBlock.CannotRemove
                !on && !item.addable ->
                    if (item.runtime == McpCatalogRuntime.Docker) {
                        CatalogueRowBlock.NeedsDocker
                    } else {
                        CatalogueRowBlock.NoEndpoint
                    }
                else -> null
            }
        val facts = buildList {
            when (item.runtime) {
                McpCatalogRuntime.Hosted -> add(CatalogueFact.HostedBy(item.hostedBy ?: item.serverName))
                McpCatalogRuntime.Node -> add(CatalogueFact.NeedsNode(input.phoneHost))
                McpCatalogRuntime.Python -> add(CatalogueFact.NeedsPython)
                McpCatalogRuntime.Docker, McpCatalogRuntime.None -> Unit
            }
            if (item.needsKey) add(CatalogueFact.NeedsKey)
            if (item.needsSettings) add(CatalogueFact.NeedsSettings)
        }
        return CatalogueRowUi(
            catalogId = item.entry.name,
            serverName = item.serverName,
            title = item.title,
            description = item.description.ifEmpty { null },
            on = on,
            facts = facts,
            blocked = blocked,
        )
    }

    /** What a tap on [item]'s switch does, given the page's current [input]. */
    public fun toggle(item: McpCatalogItem, input: CatalogueScreenInput): CatalogueToggle {
        val row = row(item, input)
        if (!row.enabled) return CatalogueToggle.Nothing
        if (row.on) return CatalogueToggle.ConfirmRemoval(item.serverName)
        val draft = item.draft ?: return CatalogueToggle.Nothing
        if (item.runtime == McpCatalogRuntime.Node && input.phoneHost) {
            return CatalogueToggle.ChooseNode(item.title, draft)
        }
        return CatalogueToggle.OpenForm(draft)
    }
}
