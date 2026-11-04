package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.domain.SessionSkillGateway as ServerSessionSkills
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.*
import kotlin.time.Clock
import kotlinx.collections.immutable.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** One authenticated source. Identity is checked again immediately before each mutation. */
public interface LibrarySource {
    public val profile: ProfileId
    public val origin: String
    public val location: ServerLocation
    public val capabilities: ServerCapabilities
    public val server: ServerGateway
    public val operations: ServerOperationsGateway

    public fun current(): Boolean
}

/** Recovery metadata only: never a key, answer, authorization URL, code or provider message. */
public interface LibraryRecoveryStore {
    public suspend fun read(): Outcome<String?>

    public suspend fun write(json: String): Outcome<Unit>
}

public data class LibraryAttempt(
    val integration: String,
    val attempt: String,
    val command: Boolean,
    val mode: IntegrationAuthMode,
    val expires: Long,
    val origin: String,
    val directory: String?,
    val workspace: String?,
    val state: IntegrationAuthState = IntegrationAuthState.Pending,
    /**
     * A new command waits eight seconds before offering Check; recovered commands can be checked
     * immediately.
     */
    val checkAfterMs: Long = 0,
)

public enum class LibraryNotice {
    Changed,
    Failed,
    Done,
    Uncertain,
    Staged,
    Unsupported,
    Saved,
    Storage,
    Waiting,
    Ended,
}

public data class LibraryInventory(
    val loading: Boolean = true,
    val capabilities: ServerCapabilities? = null,
    val commands: ImmutableList<CommandInfo> = persistentListOf(),
    val skills: ImmutableList<SkillInfo> = persistentListOf(),
    val references: ImmutableList<ReferenceInfo> = persistentListOf(),
    val providers: ImmutableList<IntegrationInfo> = persistentListOf(),
    val servers: ImmutableList<McpServerInfo> = persistentListOf(),
    val resources: ImmutableList<McpResourceInfo> = persistentListOf(),
    val conversations: ImmutableList<Session> = persistentListOf(),
    val attempts: ImmutableList<LibraryAttempt> = persistentListOf(),
    val uncertain: ImmutableSet<String> = persistentSetOf(),
    val failedSections: ImmutableSet<String> = persistentSetOf(),
    val busy: Boolean = false,
    val notice: LibraryNotice? = null,
    val storageUncertain: Boolean = false,
    val hasAttemptsAtOtherSource: Boolean = false,
    val catalog: CatalogSnapshot? = null,
    val activeCredential: ImmutableMap<String, String?> = persistentMapOf(),
)

/**
 * The Tools inventory and actions, pinned to one profile/location. Reads retain their last good
 * list; every mutation makes one request, then reconciles. Automatic OAuth waits are bounded and
 * never restart a mutation.
 */
public class LibrarySession(
    private val source: () -> LibrarySource?,
    private val recovery: LibraryRecoveryStore,
    private val scope: CoroutineScope,
    private val clock: Clock,
) {
    private val lane = Mutex()
    private val mutable = MutableStateFlow(LibraryInventory())
    public val state: StateFlow<LibraryInventory> = mutable.asStateFlow()
    public val profileId: ProfileId?
        get() = pinned?.profile ?: current()?.profile

    private var initialized = false
    private var corrupt = false

    private data class SourceKey(
        val profile: ProfileId,
        val origin: String,
        val location: ServerLocation,
    )

    private data class AttemptKey(
        val origin: String,
        val directory: String?,
        val workspace: String?,
        val integration: String,
        val attempt: String,
        val command: Boolean,
    )

    private fun key(entry: LibraryAttempt) =
        AttemptKey(
            entry.origin,
            entry.directory,
            entry.workspace,
            entry.integration,
            entry.attempt,
            entry.command,
        )

    private var pinned: SourceKey? = null
    private var closed = false
    private var clients = 0

    /** Multiple routes share the same profile/location owner and its uncertain-start admission. */
    public fun retain() {
        clients++
        closed = false
    }

    private val launches = mutableMapOf<AttemptKey, IntegrationAuthLaunch>()
    private val waits = mutableMapOf<AttemptKey, Job>()
    private var events: Job? = null
    private var eventsServer: ServerGateway? = null

    public fun clearNotice(): Unit = mutable.update { it.copy(notice = null) }

    public fun isCurrent(): Boolean = current() != null

    private fun current(): LibrarySource? {
        if (closed) return null
        val value = source()?.takeIf { it.current() } ?: return null
        val identity = SourceKey(value.profile, value.origin, value.location)
        if (pinned == null) pinned = identity
        return value.takeIf { pinned == identity }
    }

    private fun refused(notice: LibraryNotice): Outcome<Unit> {
        mutable.update { it.copy(notice = notice) }
        return Outcome.Failed(Failure.Unavailable(FeatureId("library")))
    }

    public suspend fun refresh(): Unit = lane.withLock { refreshInside() }

    private suspend fun refreshInside() {
        val src = current()
        if (src == null) {
            mutable.update { it.copy(loading = false, notice = LibraryNotice.Changed) }
            return
        }
        if (!initialized) {
            initialized = true
            when (val raw = recovery.read()) {
                is Outcome.Failed -> {
                    corrupt = true
                    mutable.update { it.copy(storageUncertain = true) }
                }
                is Outcome.Ok ->
                    if (raw.value != null) {
                        val parsed = parseAttempts(raw.value!!, src.profile.raw)
                        if (parsed == null) {
                            corrupt = true
                            mutable.update { it.copy(storageUncertain = true) }
                        } else
                            mutable.update {
                                it.copy(
                                    attempts =
                                        parsed.filter { e -> sameSource(e, src) }.toImmutableList(),
                                    hasAttemptsAtOtherSource =
                                        parsed.any { e -> !sameSource(e, src) },
                                )
                            }
                    }
            }
        }
        mutable.update { it.copy(loading = true, capabilities = src.capabilities) }
        val failures = mutableSetOf<String>()
        suspend fun <T> read(section: String, block: suspend () -> Outcome<T>, apply: (T) -> Unit) {
            when (val value = block()) {
                is Outcome.Failed -> failures += section
                is Outcome.Ok -> if (src.current()) apply(value.value)
            }
        }
        val ops = src.operations
        if (src.capabilities.slashCommands)
            read("commands", { ops.commands() }) { rows ->
                mutable.update { it.copy(commands = rows) }
            }
        if (src.capabilities.serverCatalog) {
            read("catalog", { ops.catalog() }) { catalog ->
                mutable.update { it.copy(catalog = catalog) }
            }
            read("skills", { ops.skills() }) { rows -> mutable.update { it.copy(skills = rows) } }
            read("references", { ops.references() }) { rows ->
                mutable.update { it.copy(references = rows) }
            }
            read("providers", { ops.integrations() }) { rows ->
                mutable.update { state ->
                    state.copy(
                        providers = withConfiguredProviders(rows, state.catalog),
                        activeCredential =
                            state.activeCredential
                                .filter { (provider, id) ->
                                    rows.any {
                                        it.id == provider &&
                                            (id == null ||
                                                it.connections.any { connection ->
                                                    connection.type == "credential" &&
                                                        connection.id == id
                                                })
                                    }
                                }
                                .toImmutableMap(),
                    )
                }
            }
            read("mcp", { ops.mcpServers() }) { rows -> mutable.update { it.copy(servers = rows) } }
            read("resources", { ops.mcpResources() }) { rows ->
                mutable.update { it.copy(resources = rows) }
            }
        }
        read("conversations", { src.server.sessionPage(limit = 100) }) { page ->
            mutable.update { it.copy(conversations = page.items) }
        }
        mutable.update {
            it.copy(
                loading = false,
                failedSections = failures.toImmutableSet(),
                notice = if (!src.current()) LibraryNotice.Changed else it.notice,
            )
        }
        if (src.current() && eventsServer !== src.server) {
            events?.cancel()
            eventsServer = src.server
            mutable.update { it.copy(activeCredential = persistentMapOf()) }
            events = scope.launch {
                src.server.events.all.collect { envelope ->
                    if (!src.current() || current() == null) return@collect
                    val place = envelope.location
                    if (
                        place != null &&
                            (place.directory != src.location.directory ||
                                place.workspace != src.location.workspace)
                    )
                        return@collect
                    when (val event = envelope.event) {
                        GatewayEvent.Connected -> {
                            mutable.update { it.copy(activeCredential = persistentMapOf()) }
                            scope.launch { refresh() }
                        }
                        is GatewayEvent.CredentialSwitched -> {
                            if (src.server.events.status.value != StreamStatus.Connected)
                                return@collect
                            val provider =
                                mutable.value.providers.firstOrNull { it.id == event.integrationId }
                            val id = event.credentialId
                            if (
                                provider != null &&
                                    (id == null ||
                                        provider.connections.any {
                                            it.type == "credential" && it.id == id
                                        })
                            ) {
                                mutable.update {
                                    it.copy(
                                        activeCredential =
                                            (it.activeCredential + (event.integrationId to id))
                                                .toImmutableMap()
                                    )
                                }
                            }
                        }
                        is GatewayEvent.RefreshHint ->
                            if (event.kind.raw in setOf("catalog", "integration", "config"))
                                scope.launch { refresh() }
                        else -> Unit
                    }
                }
            }
        }
        for (entry in mutable.value.attempts) if (entry.state == IntegrationAuthState.Pending) {
            if (entry.command) attachCommandCheck(entry)
            else if (entry.mode == IntegrationAuthMode.Auto) attach(entry)
        }
    }

    private fun withConfiguredProviders(
        rows: ImmutableList<IntegrationInfo>,
        catalog: CatalogSnapshot?,
    ): ImmutableList<IntegrationInfo> {
        val known =
            rows.flatMap { row -> listOf(row.id) + row.connections.mapNotNull { it.id } }.toSet()
        val configured =
            catalog?.providers.orEmpty().filter {
                it.enabled && it.id.isNotEmpty() && it.id !in known && it.integrationId !in known
            }
        return (rows +
                configured.map {
                    IntegrationInfo(
                        it.id,
                        it.name.ifBlank { it.id },
                        persistentListOf(),
                        persistentListOf(IntegrationConnectionInfo("config", null, "", null)),
                        1,
                    )
                })
            .toImmutableList()
    }

    /**
     * The caller preserves a lost response as uncertain and never offers another send from the same
     * confirmation.
     */
    public suspend fun runCommand(
        session: SessionId,
        command: CommandInfo,
        arguments: String,
    ): Outcome<Unit> = lane.withLock {
        val src = current() ?: return@withLock refused(LibraryNotice.Changed)
        if (!src.capabilities.slashCommands) return@withLock refused(LibraryNotice.Unsupported)
        val result = src.server.slashCommand(session, command.name, arguments)
        mutable.update {
            it.copy(
                notice = if (result is Outcome.Ok) LibraryNotice.Done else LibraryNotice.Uncertain
            )
        }
        result
    }

    public suspend fun activateSkill(
        session: SessionId,
        skill: SkillInfo,
        resume: Boolean,
    ): Outcome<Unit> = lane.withLock {
        val src = current() ?: return@withLock refused(LibraryNotice.Changed)
        val gateway =
            src.server.facet<ServerSessionSkills>()
                ?: return@withLock refused(LibraryNotice.Unsupported)
        if (!gateway.sessionSkillsSupported) return@withLock refused(LibraryNotice.Unsupported)
        val detail = src.server.session(session)
        if (detail !is Outcome.Ok) return@withLock refused(LibraryNotice.Failed)
        if (!src.current()) return@withLock refused(LibraryNotice.Changed)
        if (detail.value.reverted || detail.value.stagedRevert != null)
            return@withLock refused(LibraryNotice.Staged)
        val result = gateway.activateSessionSkill(session, skill.id ?: skill.name, resume)
        mutable.update {
            it.copy(
                notice =
                    when {
                        result is Outcome.Ok -> LibraryNotice.Done
                        !gateway.sessionSkillsSupported -> LibraryNotice.Unsupported
                        else -> LibraryNotice.Uncertain
                    }
            )
        }
        result
    }

    public suspend fun connectKey(provider: IntegrationInfo, key: String): Outcome<Unit> = mutate {
        if (key.trim().isEmpty()) return@mutate Outcome.Failed(Failure.UnreadableReply())
        it.operations.connectIntegrationKey(provider.id, key.trim())
    }

    public suspend fun disconnect(provider: IntegrationInfo): Outcome<Unit> = mutate {
        it.operations.disconnectIntegration(provider)
    }

    public suspend fun credential(id: String, action: String, label: String = ""): Outcome<Unit> =
        mutate { src ->
            val gateway = src.server.facet<IntegrationCredentialGateway>()
            if (!src.capabilities.integrationCredentials || gateway == null)
                return@mutate Outcome.Failed(
                    Failure.Unavailable(FeatureId("integrationCredentials"))
                )
            when (action) {
                "rename" -> gateway.renameCredential(id, label)
                "activate" -> gateway.activateCredential(id)
                "remove" -> gateway.removeCredential(id)
                else -> Outcome.Failed(Failure.UnreadableReply())
            }
        }

    public suspend fun mcp(server: McpServerInfo, remove: Boolean = false): Outcome<Unit> =
        mutate { src ->
            if (remove) {
                val gateway = src.server.facet<McpRemovalGateway>()
                if (src.capabilities.mcpRuntimeRemovals && gateway != null)
                    gateway.removeMcpServer(server.name)
                else Outcome.Failed(Failure.Unavailable(FeatureId("mcpRuntimeRemovals")))
            } else if (server.status == "connected") src.operations.disconnectMcp(server.name)
            else src.operations.connectMcp(server.name)
        }

    public suspend fun addMcp(draft: McpServerDraft): Outcome<Unit> = mutate { src ->
        if (!src.capabilities.mcpRuntimeAdds && !src.capabilities.mcpConfigWrites)
            return@mutate Outcome.Failed(Failure.Unavailable(FeatureId("mcpRuntimeAdds")))
        src.operations.addMcpServer(
            draft,
            if (src.capabilities.mcpConfigWrites) McpConfigScope.Project
            else McpConfigScope.RuntimeLocation,
        )
    }

    private suspend fun mutate(block: suspend (LibrarySource) -> Outcome<Unit>): Outcome<Unit> =
        lane.withLock {
            val src = current() ?: return@withLock refused(LibraryNotice.Changed)
            mutable.update { it.copy(busy = true, notice = null) }
            try {
                val result = block(src)
                mutable.update {
                    it.copy(
                        notice =
                            if (result is Outcome.Ok) LibraryNotice.Done else LibraryNotice.Failed
                    )
                }
                if (src.current()) refreshInside()
                return@withLock result
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }

    /**
     * Starts once. A lost launch reply blocks a second start until the person explicitly forgets
     * it.
     */
    public suspend fun start(
        provider: IntegrationInfo,
        method: IntegrationMethodInfo,
        inputs: ImmutableMap<String, String>,
    ): IntegrationAuthLaunch? = lane.withLock {
        val src = current() ?: return@withLock null.also { refused(LibraryNotice.Changed) }
        val methodId = method.id ?: return@withLock null
        if (
            !validId(provider.id) ||
                !validId(methodId) ||
                !src.capabilities.integrationCredentials ||
                method.type !in setOf("oauth", "command") ||
                mutable.value.providers.none {
                    it.id == provider.id &&
                        it.methods.any { candidate ->
                            candidate.id == method.id && candidate.type == method.type
                        }
                }
        )
            return@withLock null
        if (
            corrupt ||
                mutable.value.storageUncertain ||
                mutable.value.attempts.size >= 16 ||
                provider.id in mutable.value.uncertain ||
                mutable.value.attempts.any { it.integration == provider.id && sameSource(it, src) }
        )
            return@withLock null
        val stored =
            when (val loaded = recovery.read()) {
                is Outcome.Failed ->
                    return@withLock null.also {
                        mutable.update {
                            it.copy(storageUncertain = true, notice = LibraryNotice.Storage)
                        }
                    }
                is Outcome.Ok ->
                    if (loaded.value == null) emptyList()
                    else parseAttempts(loaded.value!!, src.profile.raw)
            }
        if (stored == null) {
            corrupt = true
            mutable.update { it.copy(storageUncertain = true, notice = LibraryNotice.Storage) }
            return@withLock null
        }
        if (!src.current()) return@withLock null.also { refused(LibraryNotice.Changed) }
        if (stored.size >= 16)
            return@withLock null.also { mutable.update { it.copy(notice = LibraryNotice.Storage) } }
        val command = method.type == "command"
        val commands = src.server.facet<IntegrationCommandGateway>()
        if (command && (!src.capabilities.integrationCommandAuth || commands == null))
            return@withLock null
        mutable.update { it.copy(busy = true, notice = null) }
        try {
            val result =
                if (command) commands!!.startIntegrationCommand(provider.id, methodId)
                else src.operations.startIntegrationOAuth(provider.id, methodId, inputs)
            if (result !is Outcome.Ok) {
                mutable.update {
                    it.copy(
                        uncertain = (it.uncertain + provider.id).toImmutableSet(),
                        notice = LibraryNotice.Uncertain,
                    )
                }
                return@withLock null
            }
            val launch = result.value
            val entry =
                LibraryAttempt(
                    provider.id,
                    launch.attemptId,
                    command,
                    launch.mode,
                    minOf(
                        launch.expiresAtMs ?: (clock.now().toEpochMilliseconds() + RETENTION),
                        clock.now().toEpochMilliseconds() + RETENTION,
                    ),
                    src.origin,
                    src.location.directory,
                    src.location.workspace,
                    checkAfterMs = if (command) clock.now().toEpochMilliseconds() + 8_000 else 0,
                )
            mutable.update { it.copy(attempts = (it.attempts + entry).toImmutableList()) }
            launches[key(entry)] = launch
            if (!persist(src))
                mutable.update { it.copy(storageUncertain = true, notice = LibraryNotice.Storage) }
            if (command) attachCommandCheck(entry)
            return@withLock if (src.current()) launch else null
        } finally {
            mutable.update { it.copy(busy = false) }
        }
    }

    private fun attachCommandCheck(entry: LibraryAttempt) {
        if (!entry.command || entry.checkAfterMs == 0L || waits[key(entry)]?.isActive == true)
            return
        waits[key(entry)] = scope.launch {
            delay((entry.checkAfterMs - clock.now().toEpochMilliseconds()).coerceAtLeast(0))
            mutable.update { state ->
                state.copy(
                    attempts =
                        state.attempts
                            .map { if (key(it) == key(entry)) it.copy(checkAfterMs = 0) else it }
                            .toImmutableList()
                )
            }
        }
    }

    public fun launch(entry: LibraryAttempt): IntegrationAuthLaunch? = launches[key(entry)]

    public fun attach(entry: LibraryAttempt) {
        if (
            entry.command ||
                entry.mode != IntegrationAuthMode.Auto ||
                waits[key(entry)]?.isActive == true
        )
            return
        waits[key(entry)] = scope.launch {
            var failures = 0
            // The wait belongs to this page and is bounded by the server expiry and the 24-hour
            // retention cap.
            while (
                current()?.let { sameSource(entry, it) } == true &&
                    clock.now().toEpochMilliseconds() < entry.expires
            ) {
                val result = check(entry)
                if (
                    result == IntegrationAuthState.Complete ||
                        result == IntegrationAuthState.Failed ||
                        result == IntegrationAuthState.Expired
                )
                    return@launch
                if (result == null && ++failures > 3) break
                if (result != null) failures = 0
                delay(3_000)
            }
            if (current()?.let { sameSource(entry, it) } != true) return@launch
            mutable.update { state ->
                state.copy(
                    attempts =
                        state.attempts
                            .map {
                                if (key(it) == key(entry))
                                    it.copy(state = IntegrationAuthState.Expired)
                                else it
                            }
                            .toImmutableList()
                )
            }
        }
    }

    public suspend fun check(
        entry: LibraryAttempt,
        code: String? = null,
        cancel: Boolean = false,
    ): IntegrationAuthState? = lane.withLock {
        val src =
            current()?.takeIf { sameSource(entry, it) }
                ?: return@withLock null.also { refused(LibraryNotice.Changed) }
        if (mutable.value.attempts.none { key(it) == key(entry) })
            return@withLock null.also { refused(LibraryNotice.Changed) }
        val ops = src.operations
        val restore = src.server.facet<IntegrationAuthRecoveryGateway>() ?: return@withLock null
        restore.restoreIntegrationAuthAttempt(
            entry.integration,
            entry.attempt,
            entry.command,
            entry.directory,
            entry.workspace,
        )
        val command = src.server.facet<IntegrationCommandGateway>()
        if (cancel) {
            val result =
                if (entry.command)
                    command?.cancelIntegrationCommand(entry.integration, entry.attempt)
                else ops.cancelIntegrationOAuth(entry.attempt)
            if (result is Outcome.Ok) removeEntry(entry, src)
            else mutable.update { it.copy(notice = LibraryNotice.Failed) }
            return@withLock if (result is Outcome.Ok) IntegrationAuthState.Expired else null
        }
        val known = mutable.value.attempts.first { key(it) == key(entry) }
        if (known.command && clock.now().toEpochMilliseconds() < known.checkAfterMs)
            return@withLock IntegrationAuthState.Pending
        if (clock.now().toEpochMilliseconds() >= entry.expires) {
            mutable.update { state ->
                state.copy(
                    attempts =
                        state.attempts
                            .map {
                                if (key(it) == key(entry))
                                    it.copy(state = IntegrationAuthState.Expired)
                                else it
                            }
                            .toImmutableList(),
                    notice = LibraryNotice.Ended,
                )
            }
            return@withLock IntegrationAuthState.Expired
        }
        if (code != null && !entry.command) {
            if (
                ops.completeIntegrationOAuth(entry.attempt, providerOAuthCompletionCode(code))
                    !is Outcome.Ok
            )
                return@withLock null
        }
        val result =
            if (entry.command) command?.integrationCommandStatus(entry.integration, entry.attempt)
            else ops.integrationOAuthStatus(entry.attempt)
        if (result !is Outcome.Ok) {
            mutable.update { it.copy(notice = LibraryNotice.Failed) }
            return@withLock null
        }
        val status = result.value.state
        mutable.update { state ->
            state.copy(
                attempts =
                    state.attempts
                        .map { if (key(it) == key(entry)) it.copy(state = status) else it }
                        .toImmutableList(),
                notice =
                    when (status) {
                        IntegrationAuthState.Complete -> LibraryNotice.Done
                        IntegrationAuthState.Pending -> LibraryNotice.Waiting
                        else -> LibraryNotice.Ended
                    },
            )
        }
        if (status == IntegrationAuthState.Complete) {
            removeEntry(entry, src)
            refreshInside()
        }
        return@withLock status
    }

    public suspend fun forget(entry: LibraryAttempt): Unit = lane.withLock {
        val src = current() ?: return@withLock
        removeEntry(entry, src)
    }

    public fun forgetUncertain(id: String): Unit = mutable.update {
        it.copy(uncertain = (it.uncertain - id).toImmutableSet())
    }

    public suspend fun retryPersistence(): Unit = lane.withLock { current()?.let { persist(it) } }

    private suspend fun removeEntry(entry: LibraryAttempt, src: LibrarySource) {
        val before = mutable.value.attempts
        mutable.update {
            it.copy(
                attempts = it.attempts.filterNot { e -> key(e) == key(entry) }.toImmutableList()
            )
        }
        if (!persist(src)) mutable.update { it.copy(attempts = before) }
        else {
            val waiting = waits.remove(key(entry))
            if (waiting != currentCoroutineContext()[Job]) waiting?.cancel()
            launches.remove(key(entry))
        }
    }

    private suspend fun persist(src: LibrarySource): Boolean {
        if (corrupt || !src.current() || current() == null) {
            mutable.update { it.copy(storageUncertain = true, notice = LibraryNotice.Storage) }
            return false
        }
        val rows =
            JsonArray(
                    mutable.value.attempts.map { entry ->
                        buildJsonObject {
                            put("attemptID", entry.attempt)
                            put("integrationID", entry.integration)
                            put("kind", if (entry.command) "command" else "oauth")
                            put(
                                "mode",
                                if (entry.mode == IntegrationAuthMode.Code) "code" else "auto",
                            )
                            put("profileID", src.profile.raw)
                            put("origin", entry.origin)
                            put("directory", entry.directory?.let(::JsonPrimitive) ?: JsonNull)
                            put("workspace", entry.workspace?.let(::JsonPrimitive) ?: JsonNull)
                            put("expiresAt", entry.expires)
                        }
                    }
                )
                .toString()
        if (
            rows.encodeToByteArray().size > 524_288 || parseAttempts(rows, src.profile.raw) == null
        ) {
            mutable.update { it.copy(storageUncertain = true, notice = LibraryNotice.Storage) }
            return false
        }
        val saved = recovery.write(rows) is Outcome.Ok
        mutable.update {
            it.copy(
                storageUncertain = !saved,
                notice = if (!saved) LibraryNotice.Storage else it.notice,
            )
        }
        return saved
    }

    private fun parseAttempts(raw: String, profile: String): List<LibraryAttempt>? =
        LibraryRecoveryCodec.parse(raw, profile, clock.now().toEpochMilliseconds())

    private fun sameSource(entry: LibraryAttempt, src: LibrarySource): Boolean =
        entry.origin == src.origin &&
            entry.directory == src.location.directory &&
            entry.workspace == src.location.workspace

    public fun close() {
        clients = (clients - 1).coerceAtLeast(0)
        if (clients > 0) return
        closed = true
        waits.values.forEach { it.cancel() }
        waits.clear()
        events?.cancel()
        events = null
        eventsServer = null
    }

    private fun validId(id: String): Boolean =
        id.length <= 256 && id != "." && id != ".." && Regex("^[A-Za-z0-9_.:-]+$").matches(id)

    private companion object {
        const val RETENTION: Long = 24 * 60 * 60 * 1000L
        val LOCATION_CONTROLS = Regex("[\\x00-\\x1f\\x7f-\\x9f]")
    }
}
