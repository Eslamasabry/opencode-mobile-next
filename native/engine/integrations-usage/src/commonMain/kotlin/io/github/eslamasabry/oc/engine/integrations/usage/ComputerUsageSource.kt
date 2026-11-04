package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

public const val COMPUTER_USAGE_TITLE: String = "OpenCode Mobile usage check"
public const val COMPUTER_USAGE_MARKER: String = "OCMN_USAGE_V1"

/**
 * Fixed reasons only. Terminal output, scripts and provider response bodies never become
 * application state.
 */
public enum class ComputerUsageProblem {
    NodeMissing,
    NodeTooOld,
    ServerTooOld,
    Unreachable,
    NoAnswer,
}

public data class ComputerUsageServer(
    val id: String,
    val name: String,
    val readable: Boolean = true,
    val inApp: Boolean = false,
)

/**
 * A private, temporary PTY. The owner checks the live connection generation immediately before each
 * operation.
 */
public interface ComputerTerminalPort {
    public suspend fun start(
        command: String,
        arguments: List<String>,
        title: String,
    ): Outcome<String>

    public suspend fun connect(id: String): Outcome<ComputerUsageChannel>

    public suspend fun remove(id: String): Outcome<Unit>

    public fun close()
}

public interface ComputerUsageChannel {
    public val output: Flow<String>

    public suspend fun close()
}

/** Only validated numbers and allowlisted metadata survive the decoder. */
public data class ComputerPlanSnapshot(
    val status: String,
    val fetchedAt: Instant,
    val accountRef: String?,
    val plan: String?,
    val windows: List<UsageWindow>,
    val canShowWindows: Boolean,
)

public data class ComputerUsageResult(
    val snapshots: Map<String, ComputerPlanSnapshot> = emptyMap(),
    val auth: Map<String, String> = emptyMap(),
    val problem: ComputerUsageProblem? = null,
) {
    public fun payAsYouGo(provider: String): Boolean =
        auth[provider] == "api" &&
            (provider == "codex" ||
                (provider == "minimax" && snapshots[provider]?.status == "unsupported"))

    public val found: List<String>
        get() = COMPUTER_PROVIDERS.filter { auth.containsKey(it) && !payAsYouGo(it) }
}

private val COMPUTER_PROVIDERS = listOf("codex", "minimax", "glm")
private val ANSI =
    Regex(
        "\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|\u001B\\[[0-9;?<=>]*[ -/]*[@-~]|\u001B[@-Z\\\\-_]"
    )
private val MARKER = Regex("^$COMPUTER_USAGE_MARKER ([A-Za-z0-9+/]+={0,2})$")

public fun stripComputerTerminalCodes(text: String): String = text.replace(ANSI, "")

/**
 * B-AGENT-071: echo never counts; a split marker is parsed after its complete encoded document
 * arrives.
 */
public fun parseComputerUsageOutput(output: String): ComputerUsageResult? {
    var result: ComputerUsageResult? = null
    for (line in stripComputerTerminalCodes(output).split(Regex("[\\r\\n]+"))) {
        val encoded = MARKER.matchEntire(line.trim())?.groupValues?.get(1) ?: continue
        if (encoded.length > 96 * 1024) continue
        val decoded =
            try {
                Base64.decode(encoded).decodeToString(throwOnInvalidSequence = true)
            } catch (_: Exception) {
                continue
            }
        val json = DartJson.decodeOrNull(decoded) as? Map<*, *> ?: continue
        if (json["v"] != 1L && json["v"] != 1) continue
        if (json["error"] != null) {
            result =
                ComputerUsageResult(
                    problem =
                        if (json["error"] == "nodeTooOld") ComputerUsageProblem.NodeTooOld
                        else ComputerUsageProblem.NoAnswer
                )
            continue
        }
        val providers = json["providers"] as? Map<*, *>
        val rawAuth = json["auth"] as? Map<*, *>
        val snapshots = LinkedHashMap<String, ComputerPlanSnapshot>()
        val auth = LinkedHashMap<String, String>()
        for (provider in COMPUTER_PROVIDERS) {
            parseSnapshot(provider, providers?.get(provider))?.let { snapshots[provider] = it }
            (rawAuth?.get(provider) as? String)
                ?.takeIf { it == "oauth" || it == "api" }
                ?.let { auth[provider] = it }
        }
        result = ComputerUsageResult(snapshots, auth)
    }
    return result
}

private fun integer(value: Any?): Long? =
    when (value) {
        is Long -> value
        is Int -> value.toLong()
        else -> null
    }

private fun snapshotTime(value: Any?): Instant? =
    integer(value)
        ?.takeIf { it in 0..8_640_000_000_000_000L }
        ?.let { Instant.fromEpochMilliseconds(it) }

private fun parseSnapshot(provider: String, value: Any?): ComputerPlanSnapshot? {
    val raw = value as? Map<*, *> ?: return null
    val source =
        when (provider) {
            "codex" -> "codex.wham"
            "minimax" -> "minimax.tokenPlan"
            else -> "glm.codingPlan"
        }
    if (
        integer(raw["schemaVersion"]) != 1L ||
            raw["provider"] != provider ||
            raw["source"] != source
    )
        return null
    val status = raw["status"] as? String ?: return null
    if (
        status !in
            setOf(
                "ok",
                "unconfigured",
                "unsupported",
                "authRequired",
                "rateLimited",
                "unavailable",
                "invalidResponse",
            )
    )
        return null
    if (raw["freshness"] !in setOf("fresh", "stale", "none")) return null
    val fetchedAt = snapshotTime(raw["fetchedAtMs"]) ?: return null
    val expires = snapshotTime(raw["expiresAtMs"]) ?: return null
    if (expires < fetchedAt) return null
    val account = raw["account"] as? Map<*, *> ?: return null
    val accountStatus = account["status"] as? String ?: return null
    if (accountStatus !in setOf("matched", "unverified", "mismatch", "sourceBound")) return null
    if (provider == "codex" && accountStatus == "sourceBound") return null
    val reference = account["ref"]
    if (reference != null && (reference !is String || !Regex("^[a-f0-9]{64}$").matches(reference)))
        return null
    val attributed =
        accountStatus == "matched" || (provider != "codex" && accountStatus == "sourceBound")
    if (attributed && reference == null) return null
    val plan =
        (account["plan"] as? String)?.takeIf {
            it in setOf("free", "go", "plus", "pro", "team", "business", "enterprise", "edu")
        }
    val ordinary = raw["ordinaryUsageAllowed"]
    if (ordinary != null && (ordinary !is Boolean || provider != "codex")) return null
    val rawWindows = raw["windows"] as? List<*> ?: return null
    if (rawWindows.size > 64) return null
    if ((status != "ok" || !attributed) && (rawWindows.isNotEmpty() || ordinary != null))
        return null
    val ids = HashSet<String>()
    val windows = ArrayList<UsageWindow>()
    for (item in rawWindows) {
        val window = item as? Map<*, *> ?: return null
        val id = window["id"] as? String ?: return null
        if (!Regex("^[a-zA-Z0-9._/-]{1,64}$").matches(id) || !ids.add(id)) return null
        val reported =
            when (window["status"]) {
                "reported" -> true
                "missing" -> false
                else -> return null
            }
        val used = window["usedPercent"]
        if (
            used != null &&
                (used !is Number || !used.toDouble().isFinite() || used.toDouble() !in 0.0..100.0)
        )
            return null
        if (reported != (used != null)) return null
        val seconds = window["durationSeconds"]
        if (seconds != null && (integer(seconds) == null || integer(seconds)!! !in 1..315_360_000L))
            return null
        val reset = window["resetsAtMs"]
        val resetsAt = reset?.let { snapshotTime(it) ?: return null }
        if (used is Number) windows += UsageWindow(id, used.toDouble(), integer(seconds), resetsAt)
    }
    return ComputerPlanSnapshot(
        status,
        fetchedAt,
        reference as? String,
        plan,
        windows,
        status == "ok" && attributed,
    )
}

public fun classifyComputerUsageOutput(output: String): ComputerUsageProblem {
    val text = stripComputerTerminalCodes(output).lowercase()
    return when {
        listOf("not found", "not recognized", "enoent", "no such file").any { it in text } ->
            ComputerUsageProblem.NodeMissing
        listOf("syntaxerror", "err_unknown_builtin", "unexpected token", "requires node").any {
            it in text
        } -> ComputerUsageProblem.NodeTooOld
        else -> ComputerUsageProblem.NoAnswer
    }
}

public fun classifyComputerStartFailure(failure: Failure): ComputerUsageProblem =
    when (failure) {
        is Failure.Unavailable,
        is Failure.NotFound -> ComputerUsageProblem.ServerTooOld
        is Failure.Rejected ->
            if (failure.status == 404 || failure.status == 405 || failure.status == 501)
                ComputerUsageProblem.ServerTooOld
            else ComputerUsageProblem.NodeMissing
        is Failure.Network,
        is Failure.TimedOut,
        is Failure.Certificate,
        is Failure.SignIn,
        is Failure.Refused -> ComputerUsageProblem.Unreachable
        is Failure.Server ->
            if (failure.status == 501) ComputerUsageProblem.ServerTooOld
            else ComputerUsageProblem.NodeMissing
        else -> ComputerUsageProblem.NodeMissing
    }

/**
 * One total deadline covers node and the login-shell fallback; teardown has its own short bounds.
 */
public suspend fun runComputerUsageCheck(
    port: ComputerTerminalPort,
    source: String,
    clock: () -> Instant,
    timeout: Duration = 25.seconds,
): ComputerUsageResult {
    val deadline = clock() + timeout
    val attempts =
        listOf(
            "node" to listOf("--input-type=module", "-e", source),
            "bash" to
                listOf(
                    "-lc",
                    "command -v node >/dev/null 2>&1 || { echo \"node: command not found\"; sleep 1; exit 127; }; node --input-type=module -e \"\$1\"; s=\$?; sleep 1; exit \$s",
                    "_",
                    source,
                ),
        )
    var last = ComputerUsageProblem.NoAnswer
    for ((command, arguments) in attempts) {
        val left = deadline - clock()
        if (left <= Duration.ZERO) break
        val next = computerAttempt(port, command, arguments, left)
        if (next.problem == null) return next
        if (next.problem != ComputerUsageProblem.NoAnswer) last = next.problem
        if (last == ComputerUsageProblem.ServerTooOld || last == ComputerUsageProblem.Unreachable)
            break
    }
    return ComputerUsageResult(problem = last)
}

private suspend fun computerAttempt(
    port: ComputerTerminalPort,
    command: String,
    arguments: List<String>,
    timeout: Duration,
): ComputerUsageResult {
    var id: String? = null
    var channel: ComputerUsageChannel? = null
    val seen = StringBuilder()
    var reason = ComputerUsageProblem.Unreachable
    try {
        return withTimeoutOrNull(timeout) {
            val started = port.start(command, arguments, COMPUTER_USAGE_TITLE)
            if (started is Outcome.Failed)
                return@withTimeoutOrNull ComputerUsageResult(
                    problem = classifyComputerStartFailure(started.failure)
                )
            id = (started as Outcome.Ok).value
            reason = ComputerUsageProblem.NodeMissing
            val connected = port.connect(id!!)
            if (connected !is Outcome.Ok)
                return@withTimeoutOrNull ComputerUsageResult(problem = reason)
            channel = connected.value
            reason = ComputerUsageProblem.NoAnswer
            val parsed =
                channel!!
                    .output
                    .mapNotNull { chunk ->
                        if (seen.length + chunk.length > 256 * 1024)
                            return@mapNotNull ComputerUsageResult(
                                problem = ComputerUsageProblem.NoAnswer
                            )
                        seen.append(chunk)
                        parseComputerUsageOutput(seen.toString())
                    }
                    .firstOrNull()
            parsed ?: ComputerUsageResult(problem = classifyComputerUsageOutput(seen.toString()))
        }
            ?: ComputerUsageResult(
                problem =
                    if (seen.isEmpty()) reason else classifyComputerUsageOutput(seen.toString())
            )
    } finally {
        seen.clear()
        // B-AGENT-072: cancellation still stops this exact temporary PTY; each teardown step is
        // bounded independently.
        withContext(NonCancellable) {
            channel?.let { c ->
                withTimeoutOrNull(2.seconds) { attempt({ Failure.Device() }) { c.close() } }
            }
            id?.let { terminal ->
                withTimeoutOrNull(5.seconds) {
                    attempt({ Failure.Device() }) { port.remove(terminal) }
                }
            }
        }
    }
}

/**
 * Per-server opt-in, serial reads, and only numbers in memory. The app owns the source and profile
 * deletion calls [forget].
 */
public class ComputerUsageSource(
    private val store: KeyValueStore,
    private val lane: WriterLane,
    private val servers: () -> List<ComputerUsageServer>,
    private val createPort: (ComputerUsageServer) -> ComputerTerminalPort,
    private val loadSource: suspend () -> String,
    private val clock: () -> Instant,
    private val timeout: Duration = 25.seconds,
    private val laneFor: (String) -> WriterLane = { lane },
    private val profileExists: (String) -> Boolean = { true },
) : UsageSource {
    private val lock = Mutex()

    private data class Memory(
        val attempted: Map<String, Instant> = emptyMap(),
        val problems: Map<String, ComputerUsageProblem?> = emptyMap(),
        val found: Map<String, List<String>> = emptyMap(),
        val pending: Map<String, ComputerUsageResult> = emptyMap(),
        val epochs: Map<String, Int> = emptyMap(),
    )

    private val memory = MutableStateFlow(Memory())

    public companion object {
        public fun enabledKey(id: String): String = "oc.usageComputerReader.$id"
    }

    public fun enabled(id: String): Boolean = store.bool(enabledKey(id)) == true

    public fun problemFor(id: String): ComputerUsageProblem? = memory.value.problems[id]

    public fun foundOn(id: String): List<String> = memory.value.found[id].orEmpty()

    public suspend fun setEnabled(id: String, value: Boolean): Outcome<Unit> = lock.withLock {
        if (!profileExists(id) || servers().none { it.id == id && it.readable && !it.inApp }) {
            return@withLock Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        }
        val saved =
            store.commit(laneFor(id)) {
                if (value) putBool(enabledKey(id), true) else remove(enabledKey(id))
            }
        if (saved is Outcome.Ok && !value) forget(id)
        saved
    }

    public fun forget(id: String) {
        memory.update {
            it.copy(
                attempted = it.attempted - id,
                problems = it.problems - id,
                found = it.found - id,
                pending = it.pending - id,
                epochs = it.epochs + (id to ((it.epochs[id] ?: 0) + 1)),
            )
        }
    }

    public suspend fun readNow(id: String): ComputerUsageResult = lock.withLock {
        val server =
            servers().firstOrNull { it.id == id && it.readable && !it.inApp && profileExists(id) }
                ?: return@withLock ComputerUsageResult(problem = ComputerUsageProblem.Unreachable)
        val result = run(server)
        if (profileExists(id)) memory.update { it.copy(pending = it.pending + (id to result)) }
        result
    }

    private suspend fun run(server: ComputerUsageServer): ComputerUsageResult {
        val mine = memory.value.epochs[server.id] ?: 0
        val started = clock()
        memory.update { it.copy(attempted = it.attempted + (server.id to started)) }
        var port: ComputerTerminalPort? = null
        val outcome =
            attempt({ Failure.Network() }) {
                val script = loadSource()
                port = createPort(server)
                try {
                    runComputerUsageCheck(port!!, script, clock, timeout)
                } finally {
                    port?.close()
                }
            }
        val result =
            (outcome as? Outcome.Ok)?.value
                ?: ComputerUsageResult(problem = ComputerUsageProblem.Unreachable)
        if (
            (memory.value.epochs[server.id] ?: 0) != mine ||
                servers().none {
                    it.id == server.id && it.readable && !it.inApp && profileExists(server.id)
                }
        ) {
            return ComputerUsageResult(problem = ComputerUsageProblem.Unreachable)
        }
        memory.update { current ->
            if ((current.epochs[server.id] ?: 0) != mine) current
            else
                current.copy(
                    problems = current.problems + (server.id to result.problem),
                    found = current.found + (server.id to result.found),
                )
        }
        if (result.problem == ComputerUsageProblem.ServerTooOld)
            store.commit(laneFor(server.id)) { remove(enabledKey(server.id)) }
        return result
    }

    override suspend fun read(context: UsageReadContext): Outcome<UsageSourceResult> =
        lock.withLock {
            val readings = ArrayList<UsageReading>()
            val signIns = ArrayList<UsageSignIn>()
            val failures = ArrayList<UsageFailure>()
            val unreached = LinkedHashSet<String>()
            for (server in servers().take(64)) {
                if (!server.readable || server.inApp || !enabled(server.id)) continue
                var result = memory.value.pending[server.id]
                memory.update { it.copy(pending = it.pending - server.id) }
                if (result == null) {
                    val before = memory.value.attempted[server.id]
                    val cadence = if (context.background) 15.minutes else 1.minutes
                    if (!context.force && before != null && context.now - before < cadence) continue
                    result = run(server)
                }
                val id = server.id
                if (result.problem == ComputerUsageProblem.Unreachable) {
                    unreached += id
                    continue
                }
                if (result.problem != null) {
                    for (provider in COMPUTER_PROVIDERS) {
                        if (
                            context.lastReading(id, provider) != null ||
                                provider in context.signedIn(id)
                        )
                            failures += UsageFailure(id, provider, UsageProblem.Broken)
                    }
                    continue
                }
                for (provider in COMPUTER_PROVIDERS) {
                    val name =
                        when (provider) {
                            "codex" -> "Codex"
                            "minimax" -> "MiniMax"
                            else -> "GLM"
                        }
                    fun bare(status: UsageReadingStatus): UsageReading =
                        UsageReading(
                            provider,
                            name,
                            id,
                            status,
                            context.now,
                            method = SignInMethod.Plan,
                        )
                    val auth = result.auth[provider]
                    val snapshot = result.snapshots[provider]
                    if (auth == null) {
                        if (context.lastReading(id, provider) != null)
                            readings += bare(UsageReadingStatus.NotSignedIn)
                        continue
                    }
                    val payAsYouGo = result.payAsYouGo(provider)
                    signIns +=
                        UsageSignIn(
                            provider,
                            id,
                            method = if (payAsYouGo) SignInMethod.ApiKey else SignInMethod.Plan,
                            displayName = name,
                        )
                    if (payAsYouGo || snapshot == null) continue
                    when {
                        snapshot.status == "ok" && snapshot.canShowWindows ->
                            readings +=
                                UsageReading(
                                    provider,
                                    name,
                                    id,
                                    if (snapshot.windows.isEmpty()) UsageReadingStatus.NoLimits
                                    else UsageReadingStatus.Ok,
                                    snapshot.fetchedAt,
                                    snapshot.windows,
                                    snapshot.accountRef,
                                    snapshot.plan,
                                    SignInMethod.Plan,
                                )
                        snapshot.status == "authRequired" ->
                            readings += bare(UsageReadingStatus.AuthRequired)
                        snapshot.status == "rateLimited" ->
                            failures += UsageFailure(id, provider, UsageProblem.RateLimited)
                        else -> failures += UsageFailure(id, provider, UsageProblem.Broken)
                    }
                }
            }
            Outcome.Ok(UsageSourceResult(readings, signIns, failures, unreached))
        }
}
