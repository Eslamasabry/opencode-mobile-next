package io.github.eslamasabry.oc.engine.aisetup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Parses a JSON literal (tests write configs as text, like the Dart maps). */
fun json(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

fun remote(url: String): JsonObject = json("""{"type":"remote","url":"$url","enabled":false}""")

fun str(value: String): JsonPrimitive = JsonPrimitive(value)

/** Known secrets plus the credential shapes the tests use (a small KitRedact). */
class FakeRedaction : SetupRedaction {
    private val secrets = mutableListOf<String>()
    private val userInfo = Regex("(://)[^/@\\s]+@")
    private val bearer = Regex("(?i)(bearer)\\s+[A-Za-z0-9._~+/=-]+")
    private val providerKey = Regex("(sk-)[A-Za-z0-9_-]{8,}")

    fun register(secret: String) {
        if (secret.length >= 6 && secret !in secrets) secrets += secret
    }

    override fun text(value: String): String {
        var out = value
        for (secret in secrets.sortedByDescending { it.length }) out = out.replace(secret, mask)
        out = userInfo.replace(out) { "${it.groupValues[1]}$mask@" }
        out = bearer.replace(out) { "${it.groupValues[1]} $mask" }
        out = providerKey.replace(out) { "${it.groupValues[1]}$mask" }
        return out
    }

    override fun registerCredentialValues(config: JsonElement) {
        when (config) {
            is JsonObject -> for ((k, v) in config) {
                val name = k.lowercase().replace(Regex("[-_]"), "")
                if (v is JsonPrimitive && v.isString && name in CREDENTIAL_FIELDS) register(v.content)
                registerCredentialValues(v)
            }
            is JsonArray -> config.forEach(::registerCredentialValues)
            else -> Unit
        }
    }

    private companion object {
        val CREDENTIAL_FIELDS = setOf("apikey", "token", "password", "authorization", "secret")
    }
}

class FakeAuditLog : SetupAuditLog {
    val stored = mutableListOf<SetupAuditRecord>()
    var available = true
    var refuse: (SetupAuditAction) -> Boolean = { false }
    var now = 1_000L

    override val isAvailable: Boolean get() = available
    override val records: List<SetupAuditRecord> get() = if (available) stored.toList() else emptyList()

    override suspend fun append(
        id: String,
        action: SetupAuditAction,
        operation: SetupAuditOperation?,
    ): Outcome<Unit> {
        if (!available || refuse(action)) return Outcome.Failed(Failure.StorageRefused())
        stored += SetupAuditRecord(id, action, now++, operation)
        return Unit.ok()
    }
}

/** Applies edits the way a host would, creating absent ancestors (independent of the engine's helpers). */
fun applyEdits(config: JsonObject, edits: List<SetupEdit>): JsonObject {
    var out = config
    for (edit in edits) out = applyAt(out, edit.path, edit)
    return out
}

private fun applyAt(map: JsonObject, path: List<String>, edit: SetupEdit): JsonObject {
    val copy = LinkedHashMap<String, JsonElement>(map)
    if (path.size == 1) {
        if (edit.remove) copy.remove(path[0]) else copy[path[0]] = edit.value ?: JsonNull
    } else {
        val child = map[path[0]] as? JsonObject ?: JsonObject(emptyMap())
        copy[path[0]] = applyAt(child, path.drop(1), edit)
    }
    return JsonObject(copy)
}

/** Dart `_AtomicHost`: a CAS host with restore handles, counters and fault switches. */
open class AtomicHost(
    var config: JsonObject = json("""{"model":"example/old","mcp":{},"unchanged":{"nullable":null}}"""),
    private val supportValue: SetupSupport = SetupSupport(readConfig = true, writeConfig = true),
) : SetupTransactionalConfigGateway {
    var version = 0
    var reads = 0
    var writes = 0
    var restores = 0
    var target = "opaque-target"
    var loseResponse = false
    var badReceipt = false
    var badReadback = false
    var throwOnCommit = false
    var beforeCommit: (() -> Unit)? = null
    var duringRead: (suspend (Int) -> Unit)? = null
    var afterCommit: (suspend () -> Unit)? = null
    var servers: List<SetupMcpStatus> = emptyList()
    var readGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    val originals = mutableMapOf<String, SetupConfigRevision>()
    var sent: List<SetupEdit> = emptyList()

    val current: SetupConfigRevision get() = SetupConfigRevision(target, "$version", config)

    override val support: SetupSupport get() = supportValue

    override suspend fun readConfig(): Outcome<JsonObject> {
        readGate?.await()
        return config.ok()
    }

    override suspend fun listMcpServers(): Outcome<List<SetupMcpStatus>> = servers.ok()

    override suspend fun readSnapshot(): Outcome<SetupConfigRevision> {
        reads++
        readGate?.await()
        duringRead?.invoke(reads)
        if (badReadback && writes > 0) version++
        return current.ok()
    }

    override suspend fun commit(
        expected: SetupConfigRevision,
        edits: List<SetupEdit>,
        operationId: String,
    ): Outcome<SetupConfigCommit> {
        beforeCommit?.invoke()
        if (expected.targetId != target || expected.revision != "$version") return Outcome.Failed(Failure.Conflict())
        val before = current
        sent = edits
        config = applyEdits(config, edits)
        version++
        writes++
        originals[operationId] = before
        afterCommit?.invoke()
        if (throwOnCommit) error("untrusted server body")
        if (loseResponse) return Outcome.Failed(Failure.Network(NetworkWhy.Reset))
        val after = if (badReceipt) SetupConfigRevision("wrong", "$version", config) else current
        return SetupConfigCommit(before, after, operationId).ok()
    }

    override suspend fun restore(commit: SetupConfigCommit, operationId: String): Outcome<SetupConfigCommit> {
        if (commit.after.targetId != target || commit.after.revision != "$version") {
            return Outcome.Failed(Failure.Conflict())
        }
        val before = current
        config = originals.getValue(commit.undoHandle).config
        version++
        restores++
        return SetupConfigCommit(before, current, operationId).ok()
    }
}

/** Dart `_LegacyHost`: advertises writes but has no atomic transaction facet. */
class LegacyHost : SetupConfigGateway {
    override val support: SetupSupport = SetupSupport(readConfig = true, writeConfig = true)

    override suspend fun readConfig(): Outcome<JsonObject> = json("""{"model":"example/old"}""").ok()

    override suspend fun listMcpServers(): Outcome<List<SetupMcpStatus>> = emptyList<SetupMcpStatus>().ok()
}

/** A read-only adapter the review tests control (Dart slice test `_Gateway`). */
class ReviewGateway : SetupConfigGateway {
    var config: JsonObject = JsonObject(emptyMap())
    var servers: List<SetupMcpStatus> = emptyList()
    var failure: Failure? = null
    var hold: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var reads = 0

    override val support: SetupSupport = SetupSupport(readConfig = true, mcpInventory = true)

    override suspend fun readConfig(): Outcome<JsonObject> {
        reads++
        hold?.await()
        failure?.let { return Outcome.Failed(it) }
        return config.ok()
    }

    override suspend fun listMcpServers(): Outcome<List<SetupMcpStatus>> = servers.ok()
}

fun <T> Outcome<T>.value(): T = (this as Outcome.Ok).value

fun Outcome<*>.code(): SetupFailureCode? = (this as? Outcome.Failed)?.failure?.setupCode()
