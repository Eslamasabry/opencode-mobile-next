package io.github.eslamasabry.oc.feature.integrations.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.engine.connectors.catalog.CataloguePresenter
import io.github.eslamasabry.oc.engine.connectors.catalog.CatalogueScreenInput
import io.github.eslamasabry.oc.engine.connectors.catalog.CatalogueToggle
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogItem
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogueUi
import io.github.eslamasabry.oc.engine.connectors.catalog.orderCatalog
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueSnapshot
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueStore
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The catalogue page's state machine (Dart `McpCatalogScreen` state), apart from the ViewModel so tests drive it with a
 * test scope. The engine decides what the page shows ([CataloguePresenter.present]); this class owns the per-profile
 * [CatalogueStore], the server's MCP list, the confirm questions and the calls. A switch never claims success: after
 * any add or remove, accepted or not, the server's list is read again and the rows show what it reports.
 */
public class CatalogueScreenPresenter(
    initial: ProfileId?,
    private val source: CatalogueSource,
    private val scope: CoroutineScope,
) {
    private val local = MutableStateFlow(CatalogueUi(server = initial))
    public val state: StateFlow<CatalogueUi> = local.asStateFlow()

    private var store: CatalogueStore? = null
    private var watching: Job? = null
    private var snapshot: CatalogueSnapshot? = null
    private var installed: Set<String>? = null
    private var inventoryFailed = false
    private var inventoryGeneration = 0
    private val busy = mutableSetOf<String>()
    private var items: Map<String, McpCatalogItem> = emptyMap()
    private var pendingAdd: McpServerDraft? = null
    private var opened = false

    init {
        scope.launch {
            source.servers.collect { servers ->
                val before = local.value.server
                val chosen =
                    before?.takeIf { id -> servers.any { it.id == id } }
                        ?: source.active.value?.takeIf { id -> servers.any { it.id == id } }
                        ?: servers.firstOrNull()?.id
                local.update { it.copy(servers = servers, server = chosen) }
                if (chosen != before || !opened) open(chosen)
            }
        }
    }

    // ---- actions ---------------------------------------------------------------------------------------------------

    /** Consent given: save it, then ask the registry for its list. */
    public fun load() {
        val s = store ?: return
        scope.launch {
            if (s !== store) return@launch
            val saved = s.setOptIn(true)
            if (s !== store) return@launch
            if (saved is Outcome.Failed) {
                local.update { it.copy(problem = CatalogueProblem(CatalogueProblemKind.ConsentNotSaved, failure = saved.failure)) }
                return@launch
            }
            s.refresh()
        }
    }

    /** Forgets consent and the saved list; the page goes back to asking. */
    public fun forget() {
        val s = store ?: return
        local.update { it.copy(query = "", ask = null) }
        scope.launch {
            if (s !== store) return@launch
            val cleared = s.clear()
            if (cleared is Outcome.Failed && s === store) {
                local.update { it.copy(problem = CatalogueProblem(CatalogueProblemKind.ForgetFailed, failure = cleared.failure)) }
            }
        }
    }

    /** The registry's own search; blank goes back to the saved list without a request. */
    public fun search(query: String) {
        val s = store ?: return
        if (query == local.value.query) return
        local.update { it.copy(query = query) }
        if (snapshot?.optedIn != true) return
        if (query.isBlank()) {
            s.showSaved()
            return
        }
        scope.launch { if (s === store) s.refresh(query = query) }
    }

    /** Reads the server's list again and, after consent, asks the registry again. */
    public fun retry() {
        loadInventory()
        val s = store ?: return
        if (snapshot?.optedIn != true) return
        val query = local.value.query.ifBlank { null }
        scope.launch { if (s === store) s.refresh(query = query) }
    }

    /** A switch: turning on asks to add the listing, turning off asks to remove it. Nothing changes before the answer. */
    public fun toggle(catalogId: String, on: Boolean) {
        val item = items[catalogId] ?: return
        val row = local.value.rows.firstOrNull { it.row.catalogId == catalogId } ?: return
        if (!row.enabled || row.row.on == on) return
        val input = input()
        when (val next = CataloguePresenter.toggle(item, input)) {
            is CatalogueToggle.OpenForm -> {
                pendingAdd = next.draft
                local.update { it.copy(ask = CatalogueAsk.Add(item.serverName, item.title), problem = null) }
            }
            is CatalogueToggle.ChooseNode -> { // not offered: this page never runs on the phone's own host
                pendingAdd = next.draft
                local.update { it.copy(ask = CatalogueAsk.Add(item.serverName, item.title), problem = null) }
            }
            is CatalogueToggle.ConfirmRemoval ->
                local.update { it.copy(ask = CatalogueAsk.Remove(next.serverName), problem = null) }
            CatalogueToggle.Nothing -> Unit
        }
    }

    public fun cancel() {
        pendingAdd = null
        local.update { it.copy(ask = null) }
    }

    public fun confirm() {
        val ask = local.value.ask ?: return
        val server = local.value.server ?: return
        val draft = pendingAdd
        pendingAdd = null
        local.update { it.copy(ask = null) }
        val call: suspend () -> Outcome<Unit> =
            when (ask) {
                is CatalogueAsk.Add -> draft?.let { { source.add(server, it) } } ?: return
                is CatalogueAsk.Remove -> { { source.remove(server, ask.name) } }
            }
        if (!busy.add(ask.name)) return // a second confirm joins the change already in flight
        recompute()
        scope.launch {
            val result = call()
            if (local.value.server != server) return@launch // the page moved on
            busy.remove(ask.name)
            if (result is Outcome.Failed) {
                val kind = if (ask is CatalogueAsk.Add) CatalogueProblemKind.AddFailed else CatalogueProblemKind.RemoveFailed
                local.update { it.copy(problem = CatalogueProblem(kind, ask.name, result.failure)) }
            }
            // The change may have landed even when its answer was lost: read the list again either way.
            loadInventory()
        }
    }

    public fun dismissProblem() {
        local.update { it.copy(problem = null) }
    }

    // ---- profile, store, inventory ---------------------------------------------------------------------------------

    private fun open(profile: ProfileId?) {
        opened = true
        val old = store
        store = null
        watching?.cancel()
        watching = null
        if (old != null) scope.launch { old.dispose() }
        snapshot = null
        installed = null
        inventoryFailed = false
        inventoryGeneration++
        busy.clear()
        pendingAdd = null
        local.update { it.copy(query = "", ask = null, problem = null) }
        if (profile == null) {
            recompute()
            return
        }
        if (!source.abilities(profile).canList) {
            recompute()
            return
        }
        val s = source.catalogue(profile)
        store = s
        watching =
            scope.launch {
                s.snapshot.collect { value ->
                    if (s !== store) return@collect
                    snapshot = value
                    recompute()
                }
            }
        s.load()
        snapshot = s.snapshot.value
        recompute()
        loadInventory()
        // Opening the catalogue after saying yes is asking for the list.
        if (s.snapshot.value.optedIn) scope.launch { if (s === store) s.refresh() }
    }

    private fun loadInventory() {
        val server = local.value.server ?: return
        if (!source.abilities(server).canList) return
        val generation = ++inventoryGeneration
        installed = null
        inventoryFailed = false
        local.update { it.copy(refreshing = true) }
        recompute()
        scope.launch {
            val result = source.installed(server)
            if (generation != inventoryGeneration || local.value.server != server) return@launch
            when (result) {
                is Outcome.Ok -> installed = result.value.map { it.name }.toSet()
                is Outcome.Failed -> inventoryFailed = true
            }
            local.update { it.copy(refreshing = false) }
            recompute()
        }
    }

    private fun input(): CatalogueScreenInput {
        val server = local.value.server
        val abilities = server?.let(source::abilities) ?: CatalogueAbilities.None
        return CatalogueScreenInput(
            capabilities = capabilities(abilities),
            hasProfile = server != null,
            registry = snapshot,
            installed = installed,
            inventoryFailed = inventoryFailed,
            busy = busy.toSet(),
            phoneHost = false,
        )
    }

    private fun recompute() {
        val input = input()
        val page = CataloguePresenter.present(input)
        val rows =
            if (page is McpCatalogueUi.Rows) {
                val ordered = orderCatalog(input.registry?.entries.orEmpty(), input.installed.orEmpty())
                    .distinctBy { it.entry.name }
                items = ordered.associateBy { it.entry.name }
                ordered.map { item ->
                    val row = CataloguePresenter.row(item, input)
                    CatalogueRow(row, CatalogueBlock.of(row))
                }.toImmutableList()
            } else {
                items = emptyMap()
                persistentListOf()
            }
        local.update { it.copy(page = page, rows = rows, canForget = CataloguePresenter.canForget(input)) }
    }

    public companion object {
        /**
         * The capabilities the engine reads, from what the source says this server can do. The native OC2 gateway does
         * not advertise MCP writes, so the flags come from the port: a runtime add (never a saved configuration write)
         * and a runtime removal.
         */
        public fun capabilities(abilities: CatalogueAbilities): ServerCapabilities =
            ServerCapabilities.Unknown.copy(
                serverCatalog = abilities.canList,
                mcpConfigWrites = false,
                mcpRuntimeAdds = abilities.canAdd,
                mcpRuntimeRemovals = abilities.canRemove,
            )
    }
}
