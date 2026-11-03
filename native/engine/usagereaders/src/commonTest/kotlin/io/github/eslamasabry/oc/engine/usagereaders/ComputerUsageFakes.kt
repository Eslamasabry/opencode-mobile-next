package io.github.eslamasabry.oc.engine.usagereaders

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.engine.usagereaders.computer.ComputerStart
import io.github.eslamasabry.oc.engine.usagereaders.computer.ComputerTerminalPort
import io.github.eslamasabry.oc.engine.usagereaders.computer.ComputerUsageProblem
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import kotlin.io.encoding.Base64

// Synthetic answers in the shape the reader (tool/quota/read_once.mjs) prints. Mirrors test/support/computer_usage_fakes.

internal const val COMPUTER_NOW = 1_791_892_800_000L // 2026-10-13T12:00Z
internal val REF = "a".repeat(64)
internal val REF2 = "b".repeat(64)

internal fun window(id: String, used: Double, seconds: Long = 18_000, hours: Long = 2): Map<String, Any?> = mapOf(
    "id" to id,
    "status" to "reported",
    "usedPercent" to used,
    "durationSeconds" to seconds,
    "resetsAtMs" to COMPUTER_NOW + hours * 3_600_000,
)

internal fun snapshot(
    provider: String,
    status: String = "ok",
    ref: String? = null,
    accountStatus: String = "matched",
    windows: List<Map<String, Any?>>? = null,
    plan: String? = null,
): LinkedHashMap<String, Any?> {
    val ok = status == "ok"
    return linkedMapOf(
        "schemaVersion" to 1L,
        "provider" to provider,
        "source" to when (provider) {
            "codex" -> "codex.wham"
            "glm" -> "glm.codingPlan"
            else -> "minimax.tokenPlan"
        },
        "status" to status,
        "freshness" to if (ok) "fresh" else "none",
        "fetchedAtMs" to COMPUTER_NOW,
        "expiresAtMs" to COMPUTER_NOW + if (ok) 60_000 else 0,
        "account" to buildMap {
            if (ref != null) put("ref", ref)
            put("status", if (ok) accountStatus else "unverified")
            if (plan != null) put("plan", plan)
        },
        "ordinaryUsageAllowed" to null,
        "windows" to if (ok) (windows ?: listOf(window("primary", 30.0))) else emptyList(),
    )
}

internal fun line(json: Map<String, Any?>): String =
    "OCMN_USAGE_V1 ${Base64.encode(DartJson.encode(json).encodeToByteArray())}"

internal fun answer(
    providers: Map<String, Map<String, Any?>> = emptyMap(),
    auth: Map<String, String?> = emptyMap(),
    error: String? = null,
): Map<String, Any?> = buildMap {
    put("v", 1L)
    if (error != null) put("error", error)
    put("providers", providers)
    put("auth", mapOf("codex" to null, "glm" to null, "minimax" to null) + auth)
}

internal val CODEX_OK = answer(
    providers = mapOf("codex" to snapshot("codex", ref = REF, plan = "plus")),
    auth = mapOf("codex" to "oauth"),
)

/** What one start call does. */
internal data class FakeStep(
    val output: List<String> = emptyList(),
    val startError: ComputerUsageProblem? = null,
    val connectError: Boolean = false,
    /** Never ends and never prints (a reader that does not answer). */
    val hang: Boolean = false,
    /** Closing its socket never finishes. */
    val closeHangs: Boolean = false,
)

internal class FakeChannel(private val step: FakeStep) : TerminalChannel {
    var closed = 0
    override val cursor: Long? = null
    override val output: Flow<String> = flow {
        if (step.hang) awaitCancellation()
        for (chunk in step.output) {
            yield()
            emit(chunk)
        }
    }

    override suspend fun write(value: String) {}

    override suspend fun close() {
        closed++
        if (step.closeHangs) awaitCancellation()
    }
}

internal class FakeComputerPort(private val steps: List<FakeStep>) : ComputerTerminalPort {
    val started = mutableListOf<Triple<String, List<String>, String>>()
    val removed = mutableListOf<String>()
    var removeThrows = false
    var closed = 0
    private var next = 0
    private val plans = HashMap<String, FakeStep>()

    override suspend fun start(command: String, arguments: List<String>, title: String): ComputerStart {
        started += Triple(command, arguments, title)
        val step = steps[minOf(next, steps.size - 1)]
        next++
        step.startError?.let { return ComputerStart.NotStarted(it) }
        val id = "pty_$next"
        plans[id] = step
        return ComputerStart.Started(
            TerminalProcess(id, title, command, arguments.toImmutableList(), "/", running = true, pid = 1, exitCode = null),
        )
    }

    override suspend fun connect(id: String): Outcome<TerminalChannel> {
        val step = plans.getValue(id)
        if (step.connectError) error("gone")
        return FakeChannel(step).ok()
    }

    override suspend fun remove(id: String): Outcome<Unit> {
        removed += id
        if (removeThrows) error("server went away")
        return Unit.ok()
    }

    override fun close() {
        closed++
    }
}

internal fun failedWith(failure: Failure): Outcome<Nothing> = Outcome.Failed(failure)
