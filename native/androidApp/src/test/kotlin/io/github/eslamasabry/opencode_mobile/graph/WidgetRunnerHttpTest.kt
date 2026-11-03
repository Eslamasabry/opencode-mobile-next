package io.github.eslamasabry.opencode_mobile.graph

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.eslamasabry.oc.android.widgets.runner.WidgetActionRunner
import io.github.eslamasabry.oc.android.widgets.runner.WidgetActionWorld
import io.github.eslamasabry.oc.android.widgets.runner.WidgetOpen
import io.github.eslamasabry.oc.android.widgets.runner.WidgetRunPreview
import io.github.eslamasabry.oc.android.widgets.runner.WidgetRunProfile
import io.github.eslamasabry.oc.android.widgets.runner.WidgetStatusRunner
import io.github.eslamasabry.oc.android.widgets.runner.WidgetStatusWorld
import io.github.eslamasabry.oc.android.widgets.runner.WidgetTap
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.okhttp.OkHttpEngineFactory
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.oc2.live.Oc2GatewayFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.Collections
import kotlin.random.asKotlinRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/**
 * The widget runners over the production OpenCode 2 factory, client and gateway against a loopback test server
 * (ports of test/widget_action_runner_http_test.dart and test/widget_status_runner_http_test.dart). Synthetic data
 * only; no live server is reached.
 */
class WidgetRunnerHttpTest {
    private val dispatchers =
        object : AppDispatchers {
            override val main = Dispatchers.Default
            override val default = Dispatchers.Default
            override val io = Dispatchers.IO

            override fun serial(name: String) = Dispatchers.Default.limitedParallelism(1)
        }
    private val factory =
        Oc2GatewayFactory(
            HttpClientFactory(OkHttpEngineFactory()),
            dispatchers,
            Clock.System,
            Redactor(),
            SecureRandom().asKotlinRandom(),
        )

    private class Synthetic(val handle: (HttpExchange, String) -> Pair<Int, String?>) : AutoCloseable {
        val seen: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val queries: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val server: HttpServer =
            HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
                createContext("/") { exchange ->
                    val path = exchange.requestURI.path
                    seen.add("${exchange.requestMethod} $path")
                    exchange.requestURI.rawQuery?.let { queries.add(URLDecoder.decode(it, "UTF-8")) }
                    val body = exchange.requestBody.readBytes().decodeToString()
                    val (status, reply) = handle(exchange, body)
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    if (reply == null) exchange.sendResponseHeaders(status, -1)
                    else {
                        val bytes = reply.toByteArray()
                        exchange.sendResponseHeaders(status, bytes.size.toLong())
                        exchange.responseBody.use { it.write(bytes) }
                    }
                    exchange.close()
                }
                start()
            }

        fun profile() =
            ServerProfile(
                ProfileId("profile"),
                "Synthetic server",
                BackendKind.OpenCode2,
                ServerOrigin("http", server.address.address.hostAddress, server.address.port),
                ProfileTrust(null, null),
                managedOnPhone = false,
                username = "opencode",
            )

        override fun close() = server.stop(0)
    }

    private fun opener(synthetic: Synthetic): suspend (Map<String, Any?>) -> WidgetOpen = { target ->
        widgetColdOpen(
            factory,
            synthetic.profile(),
            ConnectionCredentials.None,
            ServerLocation(target["directory"] as? String, target["workspace"] as? String),
            dispatchers,
        )
    }

    @Test
    fun `B-ANDR-103 a cold allow once or reject on OpenCode 2 stable and beta reconciles the real status route after the reply`() =
        runTest {
            withContext(Dispatchers.IO) {
                for (stable in listOf(true, false)) for (kind in listOf("allowOnce", "reject")) coldTap(stable, kind)
            }
        }

    private suspend fun coldTap(stable: Boolean, kind: String) {
        val session = "ses_widget_test"
        val request = "per_widget_test"
        val decision = if (kind == "allowOnce") "once" else "reject"
        val replies = Collections.synchronizedList(mutableListOf<String>())
        var pending = true
        var statusReads = 0
        val replyPath = "/api/session/$session/permission/$request/reply"
        Synthetic { exchange, body ->
            val path = exchange.requestURI.path
            when {
                path == "/api/health" && !stable -> 200 to """{"healthy":true,"version":"0.0.0-beta-18600"}"""
                path == "/api/info" && stable -> 200 to """{"version":"2.2.5"}"""
                path == "/api/permission/request" ->
                    200 to
                        (if (pending) """{"data":[{"id":"$request","sessionID":"$session","action":"shell","resources":["sleep 50"]}]}"""
                        else """{"data":[]}""")
                path == "/api/session/$session/message" ->
                    200 to """{"data":[{"id":"user-1","type":"user","text":"Synthetic prompt"}]}"""
                path == "/api/session/active" && exchange.requestMethod == "GET" -> {
                    statusReads++
                    // The model continues briefly after the decision, then ends.
                    200 to (if (statusReads == 1) """{"data":{"$session":{"type":"running"}}}""" else """{"data":{}}""")
                }
                path == replyPath && exchange.requestMethod == "POST" -> {
                    replies.add(body)
                    val expected = """{"${if (stable) "decision" else "reply"}":"$decision"}"""
                    if (body.replace(" ", "") == expected) {
                        pending = false
                        204 to null
                    } else 400 to "{}"
                }
                else -> 404 to "{}"
            }
        }
            .use { synthetic ->
                val target =
                    mapOf<String, Any?>(
                        "profileID" to "profile",
                        "scopeToken" to "scope",
                        "fingerprint" to "fp",
                        "directory" to "/synthetic/project",
                        "workspace" to "wrk_test",
                    )
                val open = opener(synthetic)
                val world =
                    object : WidgetActionWorld {
                        override suspend fun loadProfile(profileId: String) = WidgetRunProfile("profile", "fp")

                        override suspend fun loadTarget(tap: WidgetTap, fingerprint: String) = target

                        override suspend fun isCurrent(tap: WidgetTap, fingerprint: String) = true

                        override suspend fun open(profile: WidgetRunProfile, target: Map<String, Any?>) = open(target)
                    }
                val other =
                    mapOf("id" to "ses_other", "requestID" to "per_other", "onDemandActions" to listOf("allowOnce", "reject"))
                val snapshot =
                    mapOf(
                        "version" to 2,
                        "profileID" to "profile",
                        "scopeToken" to "scope",
                        "connected" to false,
                        "pendingCount" to 2,
                        "runningCount" to 1,
                        "onDemand" to mapOf("fingerprint" to "fp"),
                        "sessions" to
                            listOf(
                                mapOf(
                                    "id" to session,
                                    "busy" to true,
                                    "startedAt" to 1L,
                                    "step" to "Running shell",
                                    "requestID" to request,
                                    "onDemandActions" to listOf("allowOnce", "reject"),
                                ),
                                other,
                            ),
                    )
                val runner =
                    WidgetActionRunner(world, { System.currentTimeMillis() }, statusPollInterval = kotlin.time.Duration.parse("1ms"))
                val tap = WidgetTap("tap", kind, "profile", "scope", session, request)
                val result = runner.execute(tap, snapshot, System.currentTimeMillis() + 10_000)
                val label = "${if (stable) "stable" else "beta"} $kind"
                assertEquals(1, replies.size, label)
                assertTrue(replies.single().contains(if (stable) "\"decision\"" else "\"reply\""), label)
                assertEquals("accepted", result.status, label)
                assertFalse(pending, label)
                val calls = synthetic.seen.toList()
                // Health first (the factory's own check, then the runner's), never the event stream.
                assertEquals("GET /api/health", calls.first(), label)
                assertTrue(calls.none { it.contains("/event") }, "$label: $calls")
                val work = calls.filter { !it.endsWith("/health") && !it.endsWith("/info") }
                assertEquals(
                    listOf(
                        "GET /api/permission/request",
                        "GET /api/session/$session/message",
                        "POST $replyPath",
                        "GET /api/session/active",
                        "GET /api/session/active",
                    ),
                    work,
                    label,
                )
                assertTrue(
                    synthetic.queries.any {
                        it.contains("location[directory]=/synthetic/project") && it.contains("location[workspace]=wrk_test")
                    },
                    "$label: ${synthetic.queries}",
                )
                val updated = result.snapshot!!
                assertEquals(1, updated["pendingCount"], label)
                assertEquals(0, updated["runningCount"], label)
                assertEquals(false, updated["connected"], label)
                assertEquals(2, statusReads, label)
                val rows = updated["sessions"] as List<*>
                val first = rows.first() as Map<*, *>
                assertFalse(first.containsKey("requestID"), label)
                assertEquals(false, first["busy"], label)
                assertEquals(0L, first["startedAt"], label)
                assertEquals("", first["step"], label)
                assertEquals(other, rows.last(), label)
                // Duplicate delivery of the same tap cannot send another reply.
                runner.execute(tap, snapshot, System.currentTimeMillis() + 10_000)
                assertEquals(1, replies.size, label)
            }
    }

    @Test
    fun `B-ANDR-118 a scheduled OpenCode 2 stable read reports busy or idle using only GET and one status read`() =
        runTest {
            withContext(Dispatchers.IO) { for (busy in listOf(true, false)) scheduledRead(busy) }
        }

    private suspend fun scheduledRead(busy: Boolean) {
        var statusReads = 0
        Synthetic { exchange, _ ->
            when (exchange.requestURI.path) {
                "/api/health" -> 404 to "{}"
                "/api/info" -> 200 to """{"version":"2.0.29"}"""
                "/api/session/session/message" ->
                    200 to
                        // Stable 2.0.29 appends an idle timeline record after the assistant finishes.
                        ("""{"data":[""" +
                            (if (!busy) """{"id":"idle-1","type":"idle","time":{"created":4}},""" else "") +
                            """{"id":"notice-1","type":"system","text":"Synthetic notice","time":{"created":3}},""" +
                            """{"id":"user-1","type":"user","text":"Synthetic","time":{"created":1}}]}""")
                "/api/session/active" -> {
                    statusReads++
                    200 to (if (busy) """{"data":{"session":{"type":"running"}}}""" else """{"data":{}}""")
                }
                "/api/permission/request" -> 200 to """{"data":[]}"""
                else -> 404 to "{}"
            }
        }
            .use { synthetic ->
                val target =
                    mapOf<String, Any?>(
                        "profileID" to "profile",
                        "scopeToken" to "scope",
                        "fingerprint" to "fp",
                        "directory" to "/synthetic/project",
                    )
                val open = opener(synthetic)
                val world =
                    object : WidgetStatusWorld {
                        override suspend fun loadProfile(profileId: String) = WidgetRunProfile("profile", "fp")

                        override suspend fun loadTarget(tap: WidgetTap, fingerprint: String) = target

                        override suspend fun isCurrent(ticket: Map<String, Any?>) = true

                        override suspend fun open(profile: WidgetRunProfile, target: Map<String, Any?>) = open(target)
                    }
                val ticket = target + mapOf("sessionID" to "session", "runMarker" to "user-1", "token" to "ticket")
                val runner = WidgetStatusRunner(world, { System.currentTimeMillis() }, WidgetRunPreview(Redactor()::redact)::of)
                assertEquals(
                    mapOf("status" to if (busy) "busy" else "idle"),
                    runner.check(ticket, System.currentTimeMillis() + 3000),
                )
                assertEquals(1, statusReads)
                val calls = synthetic.seen.toList()
                assertTrue(calls.all { it.startsWith("GET ") }, "$calls")
                assertEquals(2, calls.count { it == "GET /api/session/session/message" }, "$calls")
                assertTrue(calls.none { it.contains("/event") }, "$calls")
            }
    }
}
