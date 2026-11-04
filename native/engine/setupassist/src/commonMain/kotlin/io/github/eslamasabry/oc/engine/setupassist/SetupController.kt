package io.github.eslamasabry.oc.engine.setupassist

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

/** Source of the random proposal ids (48 hex characters); production passes a secure source. */
public fun interface SetupIds {
    public fun next(): String

    public companion object {
        public fun from(random: Random): SetupIds = SetupIds {
            buildString { repeat(24) { append(random.nextInt(256).toString(16).padStart(2, '0')) } }
        }
    }
}

/**
 * Profile-and-location-bound review coordinator (port of lib/state/setup_controller.dart). Recreate on location change;
 * [dispose] before deleting a profile. Nothing queues writes while offline. Every command answers a value; the
 * snapshot publishes the plain reason.
 */
public class SetupController(
    public val gateway: SetupConfigGateway,
    private val audits: SetupAuditRegistry,
    public val profileId: String,
    public val locationId: String,
    private val redactor: Redactor,
    private val ids: SetupIds,
    private val isCurrent: () -> Boolean = { true },
) {
    init {
        require(profileId.isNotEmpty() && locationId.isNotEmpty()) { "Setup requires a profile and location." }
    }

    private val auditStore: SetupAuditStore? = audits.forProfile(profileId).valueOrNull()
    public val auditKey: String get() = SetupProfileKeys.audit(profileId)

    private val events = MutableSharedFlow<SetupSnapshot>(extraBufferCapacity = 64)
    public val changes: SharedFlow<SetupSnapshot> = events.asSharedFlow()
    public var snapshot: SetupSnapshot = SetupSnapshot()
        private set

    private var base: JsonObject? = null
    private var baseRevision: SetupConfigRevision? = null
    private var undoCommit: SetupConfigCommit? = null
    private var edits: List<SetupEdit> = emptyList()
    private var online = true
    private var busy = false
    private var disposed = false
    private var finished: CompletableDeferred<Unit>? = null

    private val transactional: SetupTransactionalConfigGateway? get() = gateway as? SetupTransactionalConfigGateway

    public val support: SetupSupport
        get() {
            val advertised = gateway.support
            if (!advertised.writeConfig || transactional != null) return advertised
            return SetupSupport(advertised.readConfig, false, advertised.mcpInventory, advertised.assistant, NO_RESTORE)
        }

    private val writable: Boolean get() = support.writeConfig && transactional != null
    private val writeReason: String get() = if (!support.writeConfig) support.reason else NO_RESTORE

    /** Bounded metadata only; never snapshots or restore handles. */
    public val audit: List<SetupAuditRecord> get() = auditStore?.records ?: emptyList()

    public fun setOnline(value: Boolean) {
        online = value
        if (!value) {
            emit(SetupSnapshot(SetupPhase.Offline, snapshot.config, snapshot.servers, reason = "Reconnect to your server to refresh or apply changes."))
        }
    }

    public suspend fun refresh(): SetupOutcome<Unit> = act {
        requireOnline()
        if (!support.readConfig) abort(SetupFailureCode.Unsupported, support.reason)
        emit(SetupSnapshot(SetupPhase.Loading))
        val revision = transactional?.let { unwrap(it.readSnapshot()) }
        requireOnline()
        if (revision != null) {
            redactor.registerCredentialValues(revision.config)
            validateRevision(revision)
        }
        val config = revision?.config ?: unwrap(gateway.readConfig())
        requireOnline()
        val servers = if (support.mcpInventory) unwrap(gateway.listMcpServers()) else emptyList()
        requireOnline()
        redactor.registerCredentialValues(config)
        base = config
        baseRevision = revision
        edits = emptyList()
        emit(
            SetupSnapshot(
                phase = if (config.isEmpty() && servers.isEmpty()) SetupPhase.Empty else SetupPhase.Ready,
                config = setupRedactObject(redactor, config),
                servers = servers.map { SetupMcpStatus(redactor.redact(it.name), status(it.status)) },
                canUndo = undoCommit != null,
            ),
        )
    }

    /** A reviewable diff. Local validation cannot prove the server accepts a schema or a package can run on its host. */
    public suspend fun propose(proposed: List<SetupEdit>): SetupOutcome<SetupProposal> = act {
        requireCurrent()
        val base = base ?: abort(SetupFailureCode.Invalid, "Read the current configuration first.")
        if (base.containsKey("sources")) {
            abort(SetupFailureCode.Unsupported, "This server returns layered sources. Effective-config diffs are unavailable.")
        }
        validate(proposed).firstOrNull()?.let { abort(SetupFailureCode.Invalid, it) }
        edits = proposed.map { SetupEdit(it.path.toList(), it.value, it.remove) }
        val unavailable = mutationReason(base, edits)
        val proposal = SetupProposal(
            id = ids.next(),
            changes = edits.map { e ->
                SetupDiff.of(
                    redactor,
                    e.path,
                    before = setupRedact(redactor, at(base, e.path), e.path.last()),
                    after = setupRedact(redactor, e.value, e.path.last()),
                    remove = e.remove,
                    beforePresent = has(base, e.path),
                    afterPresent = !e.remove,
                )
            },
            canApply = writable && unavailable == null && online && isCurrent(),
            reason = if (!writable) writeReason else unavailable ?: if (!online) "Reconnect to your server before applying." else null,
        )
        auditOrAbort(proposal.id, "proposed")
        requireCurrent()
        emit(SetupSnapshot(if (online) SetupPhase.Ready else SetupPhase.Offline, snapshot.config, snapshot.servers, proposal, canUndo = undoCommit != null))
        proposal
    }

    /** Local validation; an empty list means the edits may be proposed. */
    public fun validate(edits: List<SetupEdit>): List<String> {
        if (edits.isEmpty() || edits.size > 32) return listOf("Choose between one and 32 changes.")
        for (edit in edits) {
            if (edit.path.isEmpty() || edit.path.first() !in ROOTS || edit.path.any { !SEGMENT.matches(it) }) {
                return listOf("Choose a supported configuration field.")
            }
            val value = edit.value?.takeUnless { it is JsonNull }
            if (!edit.remove) {
                val root = edit.path.first()
                if (root in SCALAR_ROOTS &&
                    (edit.path.size != 1 || value.stringOrNull()?.trim().isNullOrEmpty())
                ) {
                    return listOf("Choose a valid model or agent identifier.")
                }
                if (root == "permission" && (edit.path.size != 2 || value.stringOrNull() !in EFFECTS)) {
                    return listOf("Choose an allow, ask, or deny permission rule.")
                }
                if (root == "mcp" && edit.path.size == 2) {
                    val type = (value as? JsonObject)?.get("type").stringOrNull()
                    if (value !is JsonObject || type !in setOf("local", "remote") || value["enabled"] != JsonPrimitive(false)) {
                        return listOf("A new MCP proposal must specify its type and start disabled.")
                    }
                    if (type == "local") {
                        val command = value["command"]
                        if (command !is JsonArray || command.isEmpty() || command.any { it.stringOrNull()?.trim().isNullOrEmpty() }) {
                            return listOf("Choose a valid command for the local MCP server.")
                        }
                    }
                    if (type == "remote" && value["url"].stringOrNull()?.let(SetupUri::isSafeMcpUrl) != true) {
                        return listOf("Choose an HTTPS or local loopback MCP URL without credentials.")
                    }
                }
            }
            // Raw credentials must use the existing sign-in or secret entry flows.
            val raw = put(JsonObject(emptyMap()), edit.path, value ?: JsonNull).toString()
            if (raw.length > 32768 || redactor.containsSecret(raw) || raw.contains(Redactor.MASK) ||
                SENSITIVE.containsMatchIn(edit.path.joinToString(".")) || sensitiveMap(value)
            ) {
                return listOf("Use the existing sign-in or secret entry flow for credentials.")
            }
            if (!edit.remove && value == null) return listOf("A configuration value is required.")
        }
        for (i in edits.indices) {
            for (j in i + 1 until edits.size) {
                val a = edits[i].path
                val b = edits[j].path
                if (prefix(a, b) || prefix(b, a)) return listOf("Review overlapping fields as separate proposals.")
            }
        }
        return emptyList()
    }

    /** Only an atomic source transaction can make a reviewed proposal writable. */
    public suspend fun apply(proposalId: String, confirmed: Boolean): SetupOutcome<Unit> = act {
        requireOnline()
        if (!writable) abort(SetupFailureCode.Unsupported, writeReason)
        val gateway = transactional!!
        val proposal = snapshot.proposal
        if (!confirmed || proposal == null || proposal.id != proposalId || !proposal.canApply) {
            abort(SetupFailureCode.Invalid, "Review and confirm the current proposal first.")
        }
        operationPhase(SetupPhase.Applying)
        val current = unwrap(gateway.readSnapshot())
        redactor.registerCredentialValues(current.config)
        requireOnline()
        if (!sameRevision(current, baseRevision)) {
            abort(SetupFailureCode.Conflict, "Configuration changed on the server. Refresh and review a new proposal.")
        }
        var expected = current.config
        for (edit in edits) expected = if (edit.remove) remove(expected, edit.path) else put(expected, edit.path, edit.value ?: JsonNull)
        val chosen = edits
        undoCommit = transact(proposal.id, "apply", current, expected) { gateway.commit(current, chosen, proposal.id) }
        requireOnline()
        publishReady()
    }

    public suspend fun undo(confirmed: Boolean): SetupOutcome<Unit> = act {
        requireOnline()
        if (!writable) abort(SetupFailureCode.Unsupported, writeReason)
        val gateway = transactional!!
        val previous = undoCommit
        if (!confirmed || previous == null) abort(SetupFailureCode.Invalid, "There is no confirmed change to undo in this setup session.")
        operationPhase(SetupPhase.Undoing)
        val current = unwrap(gateway.readSnapshot())
        redactor.registerCredentialValues(current.config)
        requireOnline()
        if (!sameRevision(current, previous.after)) {
            abort(SetupFailureCode.Conflict, "Configuration changed after Apply. Refresh before making another change.")
        }
        val id = ids.next()
        transact(id, "undo", current, previous.before.config) { gateway.restore(previous, id) }
        undoCommit = null
        requireOnline()
        publishReady()
    }

    private suspend fun transact(
        id: String,
        operation: String,
        current: SetupConfigRevision,
        expected: JsonObject,
        send: suspend () -> SetupOutcome<SetupConfigCommit>,
    ): SetupConfigCommit {
        auditOrAbort(id, "pending", operation)
        requireOnline()
        var acknowledged = false
        try {
            val result = unwrap(send())
            acknowledged = true
            redactor.registerCredentialValues(result.before.config)
            redactor.registerCredentialValues(result.after.config)
            // After dispatch, reconcile the captured host even if its screen closes; never publish to a stale owner.
            auditOrAbort(id, "accepted", operation)
            validateRevision(result.before)
            validateRevision(result.after)
            if (!sameRevision(result.before, current) || result.after.targetId != current.targetId ||
                result.after.revision == current.revision || result.undoHandle.isEmpty() || result.after.config != expected
            ) {
                abort(SetupFailureCode.Uncertain, NOT_VERIFIED)
            }
            if (canPublish) operationPhase(SetupPhase.Verifying)
            val actual = unwrap(transactional!!.readSnapshot())
            redactor.registerCredentialValues(actual.config)
            if (!sameRevision(actual, result.after)) abort(SetupFailureCode.Uncertain, NOT_VERIFIED)
            auditOrAbort(id, "verified", operation)
            base = actual.config
            baseRevision = actual
            return result
        } catch (error: SetupAbort) {
            base = null
            baseRevision = null
            undoCommit = null
            val rejected = !acknowledged && error.failure.code == SetupFailureCode.Conflict
            auditStore?.append(id, if (rejected) "rejected" else "uncertain", operation) // durable pending remains on refusal
            if (rejected) throw error
            abort(SetupFailureCode.Uncertain, "The change outcome is unknown. Refresh before trying again.")
        } finally {
            edits = emptyList()
        }
    }

    private val canPublish: Boolean get() = !disposed && online && isCurrent() && auditStore?.isAvailable == true

    private fun operationPhase(phase: SetupPhase) = emit(SetupSnapshot(phase, snapshot.config, snapshot.servers))

    private fun publishReady() =
        emit(SetupSnapshot(SetupPhase.Ready, setupRedactObject(redactor, base), snapshot.servers, canUndo = undoCommit != null))

    private fun validateRevision(revision: SetupConfigRevision) {
        if (revision.targetId.isEmpty() || revision.revision.isEmpty()) {
            abort(SetupFailureCode.Invalid, "The server returned an unsupported configuration snapshot.")
        }
    }

    private suspend fun auditOrAbort(id: String, action: String, operation: String? = null) {
        val store = auditStore ?: abort(SetupFailureCode.Storage, "Setup history is unavailable.")
        val written = store.append(id, action, operation)
        if (written is SetupOutcome.Failed) throw SetupAbort(written.failure)
    }

    private suspend fun <T> act(action: suspend () -> T): SetupOutcome<T> {
        if (disposed || busy) return SetupOutcome.Failed(SetupFailure(SetupFailureCode.Busy, "Setup is busy or closed."))
        busy = true
        val done = CompletableDeferred<Unit>()
        finished = done
        try {
            return SetupOutcome.Ok(action())
        } catch (error: SetupAbort) {
            val failure = error.failure
            val phase = when (failure.code) {
                SetupFailureCode.Offline -> SetupPhase.Offline
                SetupFailureCode.Unsupported -> SetupPhase.Unsupported
                SetupFailureCode.NeedsSignIn -> SetupPhase.NeedsSignIn
                SetupFailureCode.Uncertain -> SetupPhase.Uncertain
                else -> SetupPhase.Error
            }
            emit(SetupSnapshot(phase, snapshot.config, snapshot.servers, reason = redactor.redact(failure.message)))
            return SetupOutcome.Failed(failure)
        } finally {
            busy = false
            done.complete(Unit)
        }
    }

    private fun requireCurrent() {
        if (disposed || !isCurrent()) abort(SetupFailureCode.Offline, "Setup is closed or the selected server changed.")
        if (auditStore?.isAvailable != true) abort(SetupFailureCode.Storage, "Setup history is unavailable. Refresh before trying again.")
    }

    private fun requireOnline() {
        requireCurrent()
        if (!online) abort(SetupFailureCode.Offline, "Reconnect to your server before continuing.")
    }

    private fun emit(value: SetupSnapshot) {
        if (!disposed && isCurrent()) {
            snapshot = value
            events.tryEmit(value)
        }
    }

    /** Stops publication, waits for an in-flight action, then forgets the profile's snapshot. */
    public suspend fun dispose() {
        disposed = true
        finished?.await()
        base = null
        baseRevision = null
        edits = emptyList()
        undoCommit = null
        snapshot = SetupSnapshot()
    }

    /** An expected failure travelling up to [act]; never leaves this class. */
    private class SetupAbort(val failure: SetupFailure) : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }

    private fun abort(code: SetupFailureCode, message: String): Nothing = throw SetupAbort(SetupFailure(code, message))

    private fun <T> unwrap(outcome: SetupOutcome<T>): T = when (outcome) {
        is SetupOutcome.Ok -> outcome.value
        is SetupOutcome.Failed -> throw SetupAbort(outcome.failure)
    }

    private companion object {
        const val NO_RESTORE = "This server has no verified atomic configuration restore endpoint. Apply and Undo are unavailable."
        const val NOT_VERIFIED = "The configuration change could not be verified."
        val ROOTS = setOf(
            "model", "small_model", "default_agent", "provider", "providers", "agent", "agents",
            "permission", "permissions", "command", "commands", "mcp",
        )
        val SCALAR_ROOTS = setOf("model", "small_model", "default_agent")
        val EFFECTS = setOf("allow", "ask", "deny")
        val SEGMENT = Regex("^[a-zA-Z0-9_./:@*-]{1,160}$")
        val SENSITIVE = Regex("key|token|secret|password|credential|environment|headers|options", RegexOption.IGNORE_CASE)
        val KNOWN_STATUS = setOf("connected", "pending", "disabled", "failed", "needs_auth", "needs_client_registration")

        fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

        fun status(value: String): String = if (value in KNOWN_STATUS) value else "unknown"

        fun prefix(a: List<String>, b: List<String>): Boolean = a.size <= b.size && a.indices.all { a[it] == b[it] }

        fun sameRevision(a: SetupConfigRevision, b: SetupConfigRevision?): Boolean =
            b != null && a.targetId == b.targetId && a.revision == b.revision && a.config == b.config

        fun sensitiveMap(value: JsonElement?): Boolean = when (value) {
            is JsonObject -> value.entries.any { (k, v) -> SENSITIVE.containsMatchIn(k) || sensitiveMap(v) }
            is JsonArray -> value.any(::sensitiveMap)
            else -> false
        }

        fun at(map: JsonObject, path: List<String>): JsonElement? {
            var at: JsonElement? = map
            for (part in path) at = (at as? JsonObject)?.get(part) ?: return null
            return at
        }

        fun has(map: JsonObject, path: List<String>): Boolean {
            var at: JsonElement? = map
            for (key in path) {
                val obj = at as? JsonObject ?: return false
                if (!obj.containsKey(key)) return false
                at = obj[key]
            }
            return true
        }

        fun remove(map: JsonObject, path: List<String>): JsonObject {
            if (path.size == 1) return JsonObject(map - path[0])
            val child = map[path[0]] as? JsonObject ?: return map
            return JsonObject(map + (path[0] to remove(child, path.drop(1))))
        }

        fun put(map: JsonObject, path: List<String>, value: JsonElement): JsonObject {
            if (path.size == 1) return JsonObject(map + (path[0] to value))
            val child = map[path[0]] as? JsonObject ?: JsonObject(emptyMap())
            return JsonObject(map + (path[0] to put(child, path.drop(1), value)))
        }

        /** Dart's _mutationReason: which reviewed reversible action, if any, covers every edit. */
        fun mutationReason(base: JsonObject, edits: List<SetupEdit>): String? {
            for (edit in edits) {
                var ancestor: JsonElement? = base
                for (segment in edit.path.dropLast(1)) {
                    val obj = ancestor as? JsonObject
                    if (obj == null || !obj.containsKey(segment)) break
                    ancestor = obj[segment]
                    if (ancestor !is JsonObject) {
                        return "This change would replace a containing setting. Review that setting separately before continuing."
                    }
                }
                val root = edit.path.first()
                if (root in SCALAR_ROOTS && !edit.remove && edit.path.size == 1) continue
                if (root == "permission" && !edit.remove && edit.path.size == 2) continue
                if (root == "mcp" && edit.path.size == 2) {
                    val previous = at(base, edit.path)
                    val exists = (base["mcp"] as? JsonObject)?.containsKey(edit.path.last()) == true
                    if (exists && (previous !is JsonObject || previous["type"].stringOrNull() != "remote" ||
                            previous["enabled"] != JsonPrimitive(false))
                    ) {
                        return "Only disabled remote MCP definitions can be changed here. Runtime connections and sign-in state are not restored."
                    }
                    if (edit.remove) {
                        if (exists) continue
                        return "The MCP definition is absent. Refresh and review a current definition."
                    }
                    val value = edit.value as? JsonObject
                    if (value != null && value["type"].stringOrNull() == "remote" && value["enabled"] == JsonPrimitive(false)) continue
                    return "Local MCP commands require a separate command review. They cannot be applied here."
                }
                return "This setting has no reviewed reversible setup action. Use the existing server configuration or secure sign-in flow."
            }
            return null
        }
    }
}
