package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.protocol.paseo.DIR
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.FakeSockets
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoSocketFactory
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransport
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/** Ports of the Dart test helpers `agentJson`, `writePermission`, `stream` and `timeline` (test/paseo_gateway_test.dart). */
fun agentJson(
    id: String,
    cwd: String = DIR,
    status: String = "idle",
    archivedAt: String? = null,
    pendingPermissions: List<JsonObject> = emptyList(),
    loaded: Boolean = true,
    updatedAt: String = "2026-09-19T03:35:31.806Z",
    provider: String = "claude",
    title: String? = "Agent $id",
): JsonObject {
    val fields = linkedMapOf<String, Any?>(
        "id" to id,
        "provider" to provider,
        "persistence" to mapOf("provider" to provider, "sessionId" to "native-$id"),
        "cwd" to cwd,
        "model" to "fixture-model-1",
        "title" to title,
        "status" to status,
        "createdAt" to "2026-09-19T03:35:27.840Z",
        "updatedAt" to updatedAt,
        "currentModeId" to "default",
        "availableModes" to listOf(
            mapOf("id" to "default", "label" to "Always Ask"),
            mapOf("id" to "acceptEdits", "label" to "Accept File Edits"),
        ),
        "pendingPermissions" to pendingPermissions,
        "lastUsage" to mapOf("totalCostUsd" to 0.04),
        "archivedAt" to archivedAt,
    )
    // A loaded agent's snapshot names its `activeTurn`; a saved record has none.
    if (loaded) fields["activeTurn"] = if (status == "running") mapOf("turnId" to "turn-$id", "startedAt" to updatedAt) else null
    return jsonOf(*fields.toList().toTypedArray())
}

fun writePermission(id: String): JsonObject =
    jsonOf(
        "id" to id,
        "provider" to "claude",
        "name" to "Write",
        "kind" to "tool",
        "input" to mapOf("file_path" to "$DIR/hello.txt", "content" to "hi"),
        "detail" to mapOf("type" to "write", "filePath" to "$DIR/hello.txt", "content" to "hi"),
        "suggestions" to listOf(mapOf("type" to "setMode", "mode" to "acceptEdits", "destination" to "session")),
        "metadata" to mapOf("toolUseId" to "toolu_1"),
    )

fun stream(agentId: String, event: JsonObject, seq: Long? = null): JsonObject =
    jsonOf(
        "agentId" to agentId,
        "event" to event,
        "timestamp" to "2026-09-19T03:40:54.082Z",
        "seq" to seq,
        "epoch" to (if (seq != null) "epoch-1" else null),
    ).let { o -> JsonObject(o.filterValues { it != kotlinx.serialization.json.JsonNull }) }

fun timeline(item: JsonObject): JsonObject = jsonOf("type" to "timeline", "provider" to "claude", "item" to item, "turnId" to "turn-1")

fun turn(type: String, provider: String = "claude"): JsonObject = jsonOf("type" to type, "provider" to provider)

/** The default daemon answers of the Dart setUp: a1 here, `other` elsewhere, `gone` archived; three runtimes. */
fun FakeDaemon.seedDefaults(): FakeDaemon {
    handlers["fetch_agents_request"] = {
        "fetch_agents_response" to jsonOf(
            "entries" to listOf(
                mapOf("agent" to agentJson("a1")),
                mapOf("agent" to agentJson("other", cwd = "/work/elsewhere")),
                mapOf("agent" to agentJson("gone", archivedAt = "2026-09-19T04:00:00.000Z")),
            ),
            "pageInfo" to mapOf("nextCursor" to null, "prevCursor" to null, "hasMore" to false),
        )
    }
    handlers["get_providers_snapshot_request"] = {
        "get_providers_snapshot_response" to jsonOf(
            "entries" to listOf(
                mapOf(
                    "provider" to "claude",
                    "status" to "ready",
                    "models" to listOf(
                        mapOf("provider" to "claude", "id" to "opus", "label" to "Opus"),
                        mapOf("provider" to "claude", "id" to "haiku", "label" to "Haiku", "isDefault" to true),
                    ),
                    "modes" to listOf(mapOf("id" to "plan", "label" to "Plan"), mapOf("id" to "default", "label" to "Always Ask")),
                ),
                mapOf("provider" to "pi", "status" to "ready", "models" to emptyList<Any>()),
                mapOf("provider" to "codex", "status" to "error", "error" to "not installed"),
            ),
            "generatedAt" to "2026-09-19T03:35:27.840Z",
        )
    }
    return this
}

class GatewayHarness(
    val test: TestScope,
    val daemon: FakeDaemon = FakeDaemon().seedDefaults(),
    sockets: PaseoSocketFactory = FakeSockets(daemon),
    password: String = "",
    directory: String? = DIR,
    defaultProviderModes: Map<String, String> = emptyMap(),
    trackLocalWork: Boolean = false,
    val clock: TestClock = TestClock(Instant.parse("2026-09-19T03:36:00Z")),
) {
    val lane = StandardTestDispatcher(test.testScheduler)
    val transport = PaseoTransport(
        endpoint = "ws://127.0.0.1:6767",
        password = password,
        socketFactory = sockets,
        scope = test.backgroundScope,
        lane = lane,
    )
    val gateway = PaseoGateway(
        transport = transport,
        scope = test.backgroundScope,
        lane = lane,
        clock = clock,
        directory = directory,
        defaultProviderModes = defaultProviderModes,
        trackLocalWork = trackLocalWork,
        providerWarmupIntervalMs = 0,
    )
    val events = mutableListOf<GatewayEvent>()

    init {
        test.backgroundScope.launch(UnconfinedTestDispatcher(test.testScheduler)) {
            gateway.events.all.collect { events += it.event }
        }
    }

    fun pump() = test.runCurrent()

    fun types(): List<String> = events.map { it::class.simpleName ?: "?" }
}
