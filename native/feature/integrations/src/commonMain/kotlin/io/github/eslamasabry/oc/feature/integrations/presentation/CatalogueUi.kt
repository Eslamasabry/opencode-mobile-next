package io.github.eslamasabry.oc.feature.integrations.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connectors.catalog.CatalogueFact
import io.github.eslamasabry.oc.engine.connectors.catalog.CatalogueRowBlock
import io.github.eslamasabry.oc.engine.connectors.catalog.CatalogueRowUi
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogueUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Why a catalogue switch cannot be used here. The engine's blocks, plus the ones this page adds because it has no
 * form to fill in yet: a listing that needs a key or other settings cannot be added from the switch alone.
 */
public enum class CatalogueBlock {
    Adding,
    Removing,
    CannotRemove,
    NeedsDocker,
    NoEndpoint,
    NeedsKey,
    NeedsSettings,
    ;

    public companion object {
        /** The engine's reason first; otherwise a listing that is off and needs a key or settings stays off. */
        public fun of(row: CatalogueRowUi): CatalogueBlock? =
            when (row.blocked) {
                CatalogueRowBlock.Adding -> Adding
                CatalogueRowBlock.Removing -> Removing
                CatalogueRowBlock.CannotRemove -> CannotRemove
                CatalogueRowBlock.NeedsDocker -> NeedsDocker
                CatalogueRowBlock.NoEndpoint -> NoEndpoint
                null ->
                    when {
                        row.on -> null
                        CatalogueFact.NeedsKey in row.facts -> NeedsKey
                        CatalogueFact.NeedsSettings in row.facts -> NeedsSettings
                        else -> null
                    }
            }
    }
}

/** One listing as the page shows it: the engine's row and this page's block. */
public data class CatalogueRow(val row: CatalogueRowUi, val block: CatalogueBlock?) {
    val enabled: Boolean get() = block == null
}

/** The short question a switch asks before it changes the server. */
public sealed interface CatalogueAsk {
    public val name: String

    /** "Add {title} until OpenCode restarts?" */
    public data class Add(override val name: String, val title: String) : CatalogueAsk

    /** "Remove {name} until restart?" */
    public data class Remove(override val name: String) : CatalogueAsk
}

public enum class CatalogueProblemKind { AddFailed, RemoveFailed, ForgetFailed, ConsentNotSaved }

/** What last went wrong, for the notice (plain line + Details; never the server's words). */
public data class CatalogueProblem(val kind: CatalogueProblemKind, val name: String? = null, val failure: Failure? = null)

public data class CatalogueUi(
    val servers: ImmutableList<ConnectorServer> = persistentListOf(),
    val server: ProfileId? = null,
    /** The engine's page state (Unavailable, Loading, Consent, InventoryFailed, Failed, Empty, Rows). */
    val page: McpCatalogueUi = McpCatalogueUi.Loading,
    /** The listings when [page] is Rows: on first, then addable, then not addable. */
    val rows: ImmutableList<CatalogueRow> = persistentListOf(),
    val query: String = "",
    val canForget: Boolean = false,
    val refreshing: Boolean = false,
    val ask: CatalogueAsk? = null,
    val problem: CatalogueProblem? = null,
) {
    val serverName: String?
        get() = servers.firstOrNull { it.id == server }?.name

    val noServer: Boolean get() = server == null
}

public interface CatalogueActions {
    public fun back()

    public fun openServers()

    /** Consent: "Load the list". */
    public fun load()

    /** "Stop using the registry": forgets consent and the saved list. */
    public fun forget()

    public fun search(query: String)

    public fun retry()

    /** A row's switch; asks before anything changes. */
    public fun toggle(catalogId: String, on: Boolean)

    public fun confirm()

    public fun cancel()

    public fun dismissProblem()
}
