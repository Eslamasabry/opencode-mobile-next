package io.github.eslamasabry.oc.engine.aisetup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

/**
 * Profile-and-location-bound review coordinator (Dart `SetupController`). Recreate it on a location change; [dispose]
 * it before deleting a profile. Nothing queues writes while offline. Every command runs alone: a second command while
 * one runs answers [SetupFailureCode.Busy]. Commands return [Outcome]; [Failure.setupCode] gives the category and
 * [state]'s [SetupSnapshot.reason] the sentence.
 *
 * [isCurrent] answers false once the selected profile, location or transport changed: from then on nothing is
 * published and no further mutation is sent, but a dispatched transaction still reconciles its original host.
 */
public class SetupController(
    private val gateway: SetupConfigGateway,
    private val auditLog: SetupAuditLog,
    private val redaction: SetupRedaction,
    public val profileId: String,
    public val locationId: String,
    private val isCurrent: () -> Boolean = { true },
    private val newOperationId: () -> String = ::randomOperationId,
) {
    init {
        require(profileId.isNotEmpty() && locationId.isNotEmpty()) { "Setup requires a profile and location." }
    }

    private val _state = MutableStateFlow(SetupSnapshot())
    private val _changes = MutableSharedFlow<SetupSnapshot>(extraBufferCapacity = 64)

    /** The latest published snapshot. */
    public val state: StateFlow<SetupSnapshot> = _state.asStateFlow()

    /** Every published snapshot in order (phases included), for observers that must not miss a step. */
    public val changes: SharedFlow<SetupSnapshot> = _changes.asSharedFlow()

    public val snapshot: SetupSnapshot get() = _state.value

    /** Metadata-only history, oldest first. */
    public val audit: List<SetupAuditRecord> get() = auditLog.records

    private val transactional: SetupTransactionalConfigGateway? = gateway as? SetupTransactionalConfigGateway

    /** The advertised support, minus writes when the host has no atomic transaction facet. */
    public val support: SetupSupport
        get() {
            val advertised = gateway.support
            if (!advertised.writeConfig || transactional != null) return advertised
            return advertised.copy(writeConfig = false, reason = SetupReason.NoAtomicRestore)
        }

    private var base: JsonObject? = null
    private var baseRevision: SetupConfigRevision? = null
    private var undoCommit: SetupConfigCommit? = null
    private var edits: List<SetupEdit> = emptyList()
    private var online = true
    private var disposed = false
    private val lane = Mutex()

    private val writable: Boolean get() = support.writeConfig && transactional != null
    private val writeReason: SetupReason
        get() = if (!support.writeConfig) support.reason else SetupReason.NoAtomicRestore

    public fun setOnline(value: Boolean) {
        online = value
        if (!value) {
            emit(
                SetupSnapshot(
                    phase = SetupPhase.Offline,
                    config = snapshot.config,
                    servers = snapshot.servers,
                    reason = SetupReason.ReconnectToRefresh,
                ),
            )
        }
    }

    public suspend fun refresh(): Outcome<Unit> =
        run {
            requireOnline()
            if (!support.readConfig) stop(SetupFailureCode.Unsupported, support.reason)
            emit(SetupSnapshot(phase = SetupPhase.Loading))
            val revision = transactional?.let { call { it.readSnapshot() } }
            requireOnline()
            if (revision != null) {
                redaction.registerCredentialValues(revision.config)
                validateRevision(revision)
            }
            val config = revision?.config ?: call { gateway.readConfig() }
            requireOnline()
            val servers = if (support.mcpInventory) call { gateway.listMcpServers() } else emptyList()
            requireOnline()
            redaction.registerCredentialValues(config)
            base = freezeSetup(config) as JsonObject
            baseRevision = revision
            edits = emptyList()
            emit(
                SetupSnapshot(
                    phase = if (config.isEmpty() && servers.isEmpty()) SetupPhase.Empty else SetupPhase.Ready,
                    config = setupRedact(config, redaction) as JsonObject,
                    servers = servers.map { SetupTool(redaction.text(it.name), SetupToolStatus.fromWire(it.status)) },
                    canUndo = undoCommit != null,
                ),
            )
        }

    /**
     * Returns a reviewable diff. Validation is local and cannot prove that a server accepts a schema or that a
     * package can run on its host. Works offline (the proposal is then not appliable).
     */
    public suspend fun propose(proposed: List<SetupEdit>): Outcome<SetupProposal> =
        run {
            requireCurrent()
            val current = base ?: stop(SetupFailureCode.Invalid, SetupReason.ReadFirst)
            if (current.containsKey("sources")) stop(SetupFailureCode.Unsupported, SetupReason.LayeredSources)
            validate(proposed)?.let { stop(SetupFailureCode.Invalid, it) }
            edits = proposed.map { SetupEdit(it.path.toList(), it.value?.let(::freezeSetup), it.remove) }
            val unavailable = mutationReason(current, edits)
            val proposal = SetupProposal(
                id = newOperationId(),
                changes = edits.map { e ->
                    SetupDiff(
                        path = e.path.map(redaction::text),
                        before = setupRedact(at(current, e.path), redaction, e.path.last()),
                        after = setupRedact(e.value, redaction, e.path.last()),
                        remove = e.remove,
                        beforePresent = has(current, e.path),
                        afterPresent = !e.remove,
                    )
                },
                canApply = writable && unavailable == null && online && isCurrent(),
                reason = when {
                    !writable -> writeReason
                    unavailable != null -> unavailable
                    !online -> SetupReason.ReconnectBeforeApplying
                    else -> null
                },
            )
            audit(proposal.id, SetupAuditAction.Proposed)
            requireCurrent()
            emit(
                SetupSnapshot(
                    phase = if (online) SetupPhase.Ready else SetupPhase.Offline,
                    config = snapshot.config,
                    servers = snapshot.servers,
                    proposal = proposal,
                    canUndo = undoCommit != null,
                ),
            )
            proposal
        }

    /** Local validation; null when every edit passes, else the first problem. */
    public fun validate(proposed: List<SetupEdit>): SetupReason? {
        if (proposed.isEmpty() || proposed.size > MAX_EDITS) return SetupReason.ChangeCount
        for (edit in proposed) {
            if (edit.path.isEmpty() || edit.path.first() !in ROOTS || edit.path.any { !SEGMENT.matches(it) }) {
                return SetupReason.UnsupportedField
            }
            if (!edit.remove) shapeProblem(edit)?.let { return it }
            // Raw credentials must use the existing sign-in or secret entry flows.
            val raw = put(JsonObject(emptyMap()), edit.path, edit.value).toString()
            if (raw.length > MAX_RAW || redaction.containsSecret(raw) || raw.contains(redaction.mask)) {
                return SetupReason.UseSecretFlow
            }
            if (SENSITIVE.containsMatchIn(edit.path.joinToString(".")) || sensitiveMap(edit.value)) {
                return SetupReason.UseSecretFlow
            }
            if (!edit.remove && edit.value.isAbsentOrNull()) return SetupReason.ValueRequired
        }
        for (i in proposed.indices) {
            for (j in i + 1 until proposed.size) {
                val a = proposed[i].path
                val b = proposed[j].path
                if (prefix(a, b) || prefix(b, a)) return SetupReason.OverlappingFields
            }
        }
        return null
    }

    private fun shapeProblem(edit: SetupEdit): SetupReason? {
        val root = edit.path.first()
        val value = edit.value
        if (root in SCALAR_ROOTS && (edit.path.size != 1 || value.textOrNull()?.trim().isNullOrEmpty())) {
            return SetupReason.InvalidIdentifier
        }
        if (root == "permission" && (edit.path.size != 2 || value.textOrNull() !in EFFECTS)) {
            return SetupReason.InvalidPermission
        }
        if (root == "mcp" && edit.path.size == 2) {
            val map = value as? JsonObject
            val type = map?.get("type").textOrNull()
            if (map == null || type !in MCP_TYPES || map["enabled"] != JsonPrimitive(false)) {
                return SetupReason.McpMustStartDisabled
            }
            if (type == "local") {
                val command = map["command"] as? JsonArray
                if (command == null || command.isEmpty() || command.any { it.textOrNull()?.trim().isNullOrEmpty() }) {
                    return SetupReason.InvalidLocalCommand
                }
            }
            if (type == "remote" && map["url"].textOrNull()?.let(::isAllowedMcpUrl) != true) {
                return SetupReason.InvalidMcpUrl
            }
        }
        return null
    }

    private fun mutationReason(current: JsonObject, proposed: List<SetupEdit>): SetupReason? {
        for (edit in proposed) {
            var ancestor: JsonElement? = current
            for (segment in edit.path.dropLast(1)) {
                if (ancestor !is JsonObject || !ancestor.containsKey(segment)) break
                ancestor = ancestor[segment]
                if (ancestor !is JsonObject) return SetupReason.ReplacesContainer
            }
            val root = edit.path.first()
            if (root in SCALAR_ROOTS && !edit.remove && edit.path.size == 1) continue
            if (root == "permission" && !edit.remove && edit.path.size == 2) continue
            if (root == "mcp" && edit.path.size == 2) {
                val previous = at(current, edit.path)
                val exists = (current["mcp"] as? JsonObject)?.containsKey(edit.path.last()) == true
                if (exists &&
                    (previous !is JsonObject ||
                        previous["type"].textOrNull() != "remote" ||
                        previous["enabled"] != JsonPrimitive(false))
                ) {
                    return SetupReason.OnlyDisabledRemoteMcp
                }
                if (edit.remove) {
                    if (exists) continue
                    return SetupReason.McpAbsent
                }
                val value = edit.value as? JsonObject
                val disabledRemote = value != null &&
                    value["type"].textOrNull() == "remote" &&
                    value["enabled"] == JsonPrimitive(false)
                if (disabledRemote) {
                    continue
                }
                return SetupReason.LocalMcpNeedsReview
            }
            return SetupReason.NoReversibleAction
        }
        return null
    }

    /** Only an atomic source transaction can make a reviewed, confirmed proposal writable. */
    public suspend fun apply(proposalId: String, confirmed: Boolean): Outcome<Unit> =
        run {
            requireOnline()
            if (!writable) stop(SetupFailureCode.Unsupported, writeReason)
            val host = transactional!!
            val proposal = snapshot.proposal
            if (!confirmed || proposal == null || proposal.id != proposalId || !proposal.canApply) {
                stop(SetupFailureCode.Invalid, SetupReason.ReviewAndConfirm)
            }
            operationPhase(SetupPhase.Applying)
            val current = call { host.readSnapshot() }
            redaction.registerCredentialValues(current.config)
            requireOnline()
            if (!sameRevision(current, baseRevision)) stop(SetupFailureCode.Conflict, SetupReason.ConfigChanged)
            var expected = current.config
            for (edit in edits) {
                expected = if (edit.remove) remove(expected, edit.path) else put(expected, edit.path, edit.value)
            }
            val sending = edits
            val result = transact(proposal.id, SetupAuditOperation.Apply, current, expected) {
                host.commit(current, sending, proposal.id)
            }
            undoCommit = result
            requireOnline()
            publishReady()
        }

    public suspend fun undo(confirmed: Boolean): Outcome<Unit> =
        run {
            requireOnline()
            if (!writable) stop(SetupFailureCode.Unsupported, writeReason)
            val host = transactional!!
            val previous = undoCommit
            if (!confirmed || previous == null) stop(SetupFailureCode.Invalid, SetupReason.NothingToUndo)
            operationPhase(SetupPhase.Undoing)
            val current = call { host.readSnapshot() }
            redaction.registerCredentialValues(current.config)
            requireOnline()
            if (!sameRevision(current, previous.after)) stop(SetupFailureCode.Conflict, SetupReason.ChangedAfterApply)
            val id = newOperationId()
            transact(id, SetupAuditOperation.Undo, current, previous.before.config) {
                host.restore(previous, id)
            }
            undoCommit = null
            requireOnline()
            publishReady()
        }

    private suspend fun transact(
        id: String,
        operation: SetupAuditOperation,
        current: SetupConfigRevision,
        expected: JsonObject,
        send: suspend () -> Outcome<SetupConfigCommit>,
    ): SetupConfigCommit {
        audit(id, SetupAuditAction.Pending, operation)
        requireOnline()
        val host = transactional!!
        var acknowledged = false
        try {
            val sent = guarded(send)
            if (sent is Outcome.Failed) {
                if (sent.failure is Failure.Conflict) {
                    throw SetupStop(SetupFailureCode.Conflict, SetupReason.ConfigChanged, sent.failure, rejected = true)
                }
                stop(SetupFailureCode.Uncertain, SetupReason.OutcomeUnknown)
            }
            val result = (sent as Outcome.Ok).value
            acknowledged = true
            redaction.registerCredentialValues(result.before.config)
            redaction.registerCredentialValues(result.after.config)
            // After dispatch reconcile the original captured host even if its screen closed. Never send another
            // mutation or publish ready to a stale owner.
            audit(id, SetupAuditAction.Accepted, operation)
            validateRevision(result.before)
            validateRevision(result.after)
            if (!sameRevision(result.before, current) ||
                result.after.targetId != current.targetId ||
                result.after.revision == current.revision ||
                result.undoHandle.isEmpty() ||
                result.after.config != expected
            ) {
                stop(SetupFailureCode.Uncertain, SetupReason.NotVerified)
            }
            if (canPublish) operationPhase(SetupPhase.Verifying)
            val actual = call { host.readSnapshot() }
            redaction.registerCredentialValues(actual.config)
            if (!sameRevision(actual, result.after)) stop(SetupFailureCode.Uncertain, SetupReason.NotVerified)
            audit(id, SetupAuditAction.Verified, operation)
            base = actual.config
            baseRevision = actual
            return result
        } catch (error: SetupStop) {
            base = null
            baseRevision = null
            undoCommit = null
            val rejected = !acknowledged && error.rejected
            // Best effort: a refused write leaves the durable pending record.
            auditLog.append(id, if (rejected) SetupAuditAction.Rejected else SetupAuditAction.Uncertain, operation)
            if (rejected) throw error
            stop(SetupFailureCode.Uncertain, SetupReason.OutcomeUnknown)
        } finally {
            edits = emptyList()
        }
    }

    private val canPublish: Boolean get() = !disposed && online && isCurrent() && auditLog.isAvailable

    private fun operationPhase(phase: SetupPhase) =
        emit(SetupSnapshot(phase = phase, config = snapshot.config, servers = snapshot.servers))

    private fun publishReady() =
        emit(
            SetupSnapshot(
                phase = SetupPhase.Ready,
                config = setupRedact(base ?: JsonObject(emptyMap()), redaction) as JsonObject,
                servers = snapshot.servers,
                canUndo = undoCommit != null,
            ),
        )

    private fun validateRevision(revision: SetupConfigRevision) {
        if (revision.targetId.isEmpty() || revision.revision.isEmpty()) {
            stop(SetupFailureCode.Invalid, SetupReason.UnsupportedSnapshot)
        }
    }

    private suspend fun audit(id: String, action: SetupAuditAction, operation: SetupAuditOperation? = null) {
        val outcome = auditLog.append(id, action, operation)
        if (outcome is Outcome.Failed) {
            val code = outcome.failure.setupCode()
            if (code == SetupFailureCode.Offline) stop(code, SetupReason.ScopeClosed, outcome.failure)
            stop(SetupFailureCode.Storage, SetupReason.HistoryNotSaved, outcome.failure)
        }
    }

    /** Calls the gateway; an expected failure or a throw stops the command with its category. */
    private suspend fun <T> call(block: suspend () -> Outcome<T>): T {
        return when (val inner = guarded(block)) {
            is Outcome.Ok -> inner.value
            is Outcome.Failed -> {
                val failure = inner.failure
                when (failure) {
                    is Failure.Conflict -> stop(SetupFailureCode.Conflict, SetupReason.ConfigChanged, failure)
                    is Failure.SignIn -> stop(SetupFailureCode.NeedsSignIn, SetupReason.SignInNeeded, failure)
                    is Failure.Unavailable -> stop(SetupFailureCode.Unsupported, SetupReason.NoSetupApis, failure)
                    else -> stop(SetupFailureCode.Transport, SetupReason.Transport, failure)
                }
            }
        }
    }

    /** A throw from an adapter is a bug path, still a value here. */
    private suspend fun <T> guarded(block: suspend () -> Outcome<T>): Outcome<T> =
        when (val outer = attempt({ Failure.Unexpected() }) { block() }) {
            is Outcome.Ok -> outer.value
            is Outcome.Failed -> outer
        }

    private suspend fun <T> run(action: suspend () -> T): Outcome<T> {
        if (disposed || !lane.tryLock()) {
            return Outcome.Failed(SetupFailureCode.Busy.failure())
        }
        try {
            return Outcome.Ok(action())
        } catch (stop: SetupStop) {
            val phase = when (stop.code) {
                SetupFailureCode.Offline -> SetupPhase.Offline
                SetupFailureCode.Unsupported -> SetupPhase.Unsupported
                SetupFailureCode.NeedsSignIn -> SetupPhase.NeedsSignIn
                SetupFailureCode.Uncertain -> SetupPhase.Uncertain
                else -> SetupPhase.Error
            }
            emit(
                SetupSnapshot(phase = phase, config = snapshot.config, servers = snapshot.servers, reason = stop.reason),
            )
            return Outcome.Failed(stop.failure)
        } finally {
            lane.unlock()
        }
    }

    private fun requireCurrent() {
        if (disposed || !isCurrent()) {
            stop(SetupFailureCode.Offline, SetupReason.ScopeClosed, Failure.Refused(RefusalReason.StaleScope))
        }
        if (!auditLog.isAvailable) stop(SetupFailureCode.Storage, SetupReason.HistoryUnavailable)
    }

    private fun requireOnline() {
        requireCurrent()
        if (!online) stop(SetupFailureCode.Offline, SetupReason.ReconnectToContinue)
    }

    private fun emit(value: SetupSnapshot) {
        if (!disposed && isCurrent()) {
            _state.value = value
            _changes.tryEmit(value)
        }
    }

    /** Stops admission, waits for a running command, then forgets every raw value. */
    public suspend fun dispose() {
        disposed = true
        lane.withLock { }
        base = null
        baseRevision = null
        edits = emptyList()
        undoCommit = null
        _state.value = SetupSnapshot()
    }

    private class SetupStop(
        val code: SetupFailureCode,
        val reason: SetupReason,
        val failure: Failure,
        val rejected: Boolean = false,
    ) : Exception()

    private fun stop(code: SetupFailureCode, reason: SetupReason, failure: Failure = code.failure()): Nothing =
        throw SetupStop(code, reason, failure)

    private companion object {
        const val MAX_EDITS = 32
        const val MAX_RAW = 32768
        val ROOTS = setOf(
            "model", "small_model", "default_agent", "provider", "providers", "agent", "agents", "permission",
            "permissions", "command", "commands", "mcp",
        )
        val SCALAR_ROOTS = setOf("model", "small_model", "default_agent")
        val EFFECTS = setOf("allow", "ask", "deny")
        val MCP_TYPES = setOf("local", "remote")
        val SEGMENT = Regex("^[a-zA-Z0-9_./:@*-]{1,160}$")
        val SENSITIVE = Regex(
            "key|token|secret|password|credential|environment|headers|options",
            RegexOption.IGNORE_CASE,
        )

        fun sameRevision(a: SetupConfigRevision, b: SetupConfigRevision?): Boolean =
            b != null && a.targetId == b.targetId && a.revision == b.revision && a.config == b.config

        fun prefix(a: List<String>, b: List<String>): Boolean = a.size <= b.size && a.indices.all { a[it] == b[it] }

        fun sensitiveMap(value: JsonElement?): Boolean =
            when (value) {
                is JsonObject -> value.entries.any { (k, v) -> SENSITIVE.containsMatchIn(k) || sensitiveMap(v) }
                is JsonArray -> value.any(::sensitiveMap)
                else -> false
            }
    }
}

/** 24 random bytes as 48 lowercase hex characters (the audit id shape). */
public fun randomOperationId(): String =
    Random.nextBytes(24).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
