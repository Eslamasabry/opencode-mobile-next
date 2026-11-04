package io.github.eslamasabry.oc.engine.integrations.catalog

import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

public enum class McpCatalogNotice {
    LoadFailed,
    SaveFailed,
    Unsupported,
    SourceChanged,
    Conflict,
    Added,
    Uncertain,
    NeedsSettings,
}

public data class McpCatalogState(
    val loading: Boolean = true,
    val optedIn: Boolean = false,
    val entries: List<McpCatalogEntry> = emptyList(),
    val installed: Set<String> = emptySet(),
    val selected: McpCatalogEntry? = null,
    val notice: McpCatalogNotice? = null,
    val adding: Boolean = false,
    val uncertain: Boolean = false,
    val canAdd: Boolean = false,
)

/**
 * One catalogue screen owner. Writes never retry after dispatch; checking is an inventory read
 * only.
 */
public class McpCatalogController(
    private val host: McpCatalogHost,
    private val profile: String,
    private val directory: String?,
    private val workspace: String?,
    private val redactor: Redactor,
    private val clock: Clock,
) {
    private val mutable = MutableStateFlow(McpCatalogState())
    public val state: StateFlow<McpCatalogState> = mutable.asStateFlow()
    private val sourceIdentity = host.sourceIdentity(profile)
    private var generation = 0
    private var closed = false
    private val writes = Mutex()
    private var saved = emptyList<McpCatalogEntry>()
    private var savedAt: Instant? = null
    private var pendingName: String? = null
    private var pendingAcknowledged = false

    private fun current(): Boolean {
        val result =
            !closed &&
                host.sourceIdentity(profile) === sourceIdentity &&
                host.current(profile, directory, workspace)
        if (!result && !closed)
            mutable.update {
                it.copy(
                    loading = false,
                    adding = false,
                    canAdd = false,
                    notice = McpCatalogNotice.SourceChanged,
                )
            }
        return result
    }

    private fun supported(): Boolean =
        host.capabilities(profile).let { it.serverCatalog && it.mcpRuntimeAdds }

    private fun active(run: Int): Boolean = run == generation && current()

    public suspend fun load() {
        val run = ++generation
        when (val read = host.loadCache(profile)) {
            is Outcome.Failed ->
                if (active(run))
                    mutable.value =
                        McpCatalogState(loading = false, notice = McpCatalogNotice.SaveFailed)
            is Outcome.Ok -> {
                if (!active(run)) return
                val cache = read.value
                saved = cache?.let { parseMcpCatalog(it, redactor) }.orEmpty()
                savedAt =
                    cache?.string("cachedAt")?.let {
                        try {
                            Instant.parse(it)
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    }
                val optedIn = (cache?.get("optedIn") as? JsonPrimitive)?.booleanOrNull == true
                mutable.value =
                    McpCatalogState(
                        loading = false,
                        optedIn = optedIn,
                        entries = orderMcpCatalog(saved, state.value.installed),
                        canAdd = supported(),
                    )
                if (optedIn) refresh()
            }
        }
    }

    private suspend fun persist(run: Int, enabled: Boolean): Boolean = writes.withLock {
        if (!active(run)) return@withLock false
        val payload = buildJsonObject {
            put("version", 1)
            put("optedIn", enabled)
            savedAt?.let { put("cachedAt", it.toString()) }
            put("entries", encodeMcpCatalog(saved))
        }
        val result = host.saveCache(profile, payload)
        if (!active(run)) return@withLock false
        if (result is Outcome.Failed) {
            mutable.update {
                it.copy(optedIn = false, loading = false, notice = McpCatalogNotice.SaveFailed)
            }
            false
        } else true
    }

    public suspend fun setOptIn(enabled: Boolean) {
        if (!current() || state.value.adding) return
        val run = ++generation
        mutable.update {
            it.copy(
                optedIn = enabled,
                loading = false,
                entries = saved,
                selected = null,
                notice = null,
            )
        }
        if (!persist(run, enabled)) return
        if (enabled) refresh()
    }

    public suspend fun refresh(query: String? = null) {
        if (!current() || !state.value.optedIn || state.value.adding) return
        val search = query?.trim()?.takeIf { it.isNotEmpty() }
        if (
            search != null &&
                searchMcpCatalog(
                    buildJsonObject { put("query", search) },
                    emptyList(),
                    null,
                    redactor,
                ) == null
        )
            return
        val run = ++generation
        mutable.update { it.copy(loading = true, notice = null, canAdd = supported()) }
        try {
            val result = withTimeoutOrNull(20_000) { host.fetchRegistry(search) }
            if (!active(run) || !state.value.optedIn) return
            if (result !is Outcome.Ok || result.value["servers"] !is JsonArray) {
                mutable.update {
                    it.copy(
                        entries = orderMcpCatalog(saved, it.installed),
                        notice = McpCatalogNotice.LoadFailed,
                    )
                }
                return
            }
            val entries = parseMcpCatalog(result.value, redactor)
            if (search == null) {
                saved = entries
                savedAt = clock.now()
                if (!persist(run, true)) return
            }
            val inventory = host.inventory(profile, directory, workspace)
            if (!active(run)) return
            val installed =
                if (inventory is Outcome.Ok) inventory.value.map { it.name }.toSet()
                else state.value.installed
            mutable.update {
                it.copy(
                    entries = orderMcpCatalog(entries, installed),
                    installed = installed,
                    canAdd = supported(),
                )
            }
        } finally {
            if (active(run)) mutable.update { it.copy(loading = false) }
        }
    }

    public fun select(id: String?) {
        if (state.value.adding || !current()) return
        mutable.update { ui ->
            ui.copy(
                selected = id?.let { name -> ui.entries.firstOrNull { it.id == name } },
                notice = null,
            )
        }
    }

    public suspend fun add(
        name: String,
        headers: Map<String, String>,
        environment: Map<String, String>,
    ) {
        if (state.value.adding || state.value.uncertain) return
        if (!current()) {
            mutable.update { it.copy(notice = McpCatalogNotice.SourceChanged) }
            return
        }
        if (!supported()) {
            mutable.update { it.copy(notice = McpCatalogNotice.Unsupported) }
            return
        }
        val selected = state.value.selected ?: return
        val original = selected.draft ?: return
        val safeName = name.trim()
        if (
            selected.requiresArguments ||
                safeName.isEmpty() ||
                safeName.length > 64 ||
                !Regex("^[a-zA-Z0-9_-]+$").matches(safeName) ||
                redactor.containsSecret(safeName) ||
                original.headers.keys.any { headers[it].isNullOrBlank() } ||
                original.environment.keys.any { environment[it].isNullOrBlank() }
        ) {
            mutable.update { it.copy(notice = McpCatalogNotice.NeedsSettings) }
            return
        }
        val draft =
            original.copy(
                name = safeName,
                headers =
                    original.headers.keys.associateWith { headers[it].orEmpty() }.toImmutableMap(),
                environment =
                    original.environment.keys
                        .associateWith { environment[it].orEmpty() }
                        .toImmutableMap(),
            )
        draft.headers.values.forEach(redactor::registerSecret)
        draft.environment.values.forEach(redactor::registerSecret)
        if (mcpDraftConfig(draft) !is McpDraftCheck.Valid) return
        val run = ++generation
        mutable.update { it.copy(adding = true, loading = false, notice = null) }
        try {
            val before = host.inventory(profile, directory, workspace)
            if (!active(run)) return
            if (before !is Outcome.Ok) {
                mutable.update { it.copy(notice = McpCatalogNotice.LoadFailed) }
                return
            }
            if (before.value.any { it.name == safeName }) {
                mutable.update { it.copy(notice = McpCatalogNotice.Conflict) }
                return
            }
            pendingName = safeName
            pendingAcknowledged = false
            mutable.update { it.copy(uncertain = true) }
            val added = host.add(profile, directory, workspace, draft)
            if (!active(run)) return
            when (added) {
                is Outcome.Ok -> pendingAcknowledged = true
                is Outcome.Failed -> {
                    if (added.failure is Failure.Conflict) {
                        pendingName = null
                        mutable.update {
                            it.copy(uncertain = false, notice = McpCatalogNotice.Conflict)
                        }
                    } else
                        mutable.update {
                            it.copy(uncertain = true, notice = McpCatalogNotice.Uncertain)
                        }
                    return
                }
            }
            checkNow(run)
        } finally {
            if (active(run)) mutable.update { it.copy(adding = false) }
        }
    }

    public suspend fun check() {
        if (!current() || state.value.adding || pendingName == null) return
        val run = ++generation
        mutable.update { it.copy(adding = true) }
        try {
            checkNow(run)
        } finally {
            if (active(run)) mutable.update { it.copy(adding = false) }
        }
    }

    private suspend fun checkNow(run: Int) {
        val name = pendingName ?: return
        val read = host.inventory(profile, directory, workspace)
        if (!active(run)) return
        val names = (read as? Outcome.Ok)?.value?.map { it.name }?.toSet()
        if (pendingAcknowledged && names != null && name in names) {
            mutable.update {
                it.copy(
                    installed = names,
                    uncertain = false,
                    notice = McpCatalogNotice.Added,
                    selected = null,
                )
            }
            pendingName = null
            pendingAcknowledged = false
        } else
            mutable.update {
                it.copy(
                    installed = names ?: it.installed,
                    uncertain = true,
                    notice = McpCatalogNotice.Uncertain,
                )
            }
    }

    public fun close() {
        closed = true
        generation++
    }
}

/** The feature sees only this factory; redaction and time ownership stay inside the engine. */
@Inject
public class McpCatalogControllers(
    private val host: McpCatalogHost,
    private val redactor: Redactor,
    private val clock: Clock,
) {
    public fun open(profile: String, directory: String?, workspace: String?): McpCatalogController =
        McpCatalogController(host, profile, directory, workspace, redactor, clock)
}
