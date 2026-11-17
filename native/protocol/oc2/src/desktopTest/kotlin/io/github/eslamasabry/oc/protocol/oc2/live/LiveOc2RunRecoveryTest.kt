package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.net.*
import io.github.eslamasabry.oc.core.net.okhttp.OkHttpEngineFactory
import io.github.eslamasabry.oc.core.net.stream.*
import io.github.eslamasabry.oc.core.security.HostClass
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.client.request.header
import io.ktor.client.request.url
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.*
import kotlin.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import org.junit.Assume.assumeTrue

/** Explicit build-server opt-in. Own server, own HOME/XDG dirs and generated password; no saved provider login. */
class LiveOc2RunRecoveryTest {
    @Test
    fun `B-435 live OC2 refetches a turn ended while its event stream was cut`() = runBlocking {
        assumeTrue("Isolated live recovery proof not requested", System.getProperty("oc.liveRecovery") == "true")
        val binary = System.getenv("OC2_BINARY")?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), "tools/oc2/node_modules/@opencode/cli-linux-x64/bin/opencode")
        check(Files.isExecutable(binary)) { "Stable OC2 binary unavailable" }
        val scratch = Files.createTempDirectory("oc2-run-recovery")
        Files.setPosixFilePermissions(scratch, PosixFilePermissions.fromString("rwx------"))
        val port = ServerSocket(0).use { it.localPort }
        val builder = ProcessBuilder(binary.toString(), "--log-level", "none", "serve", "--hostname", "127.0.0.1", "--port", port.toString())
            .directory(Files.createDirectory(scratch.resolve("work")).toFile()).redirectErrorStream(true)
        val environment = builder.environment()
        environment.keys.filter { it.contains("API_KEY") || it.contains("AUTH_TOKEN") || it.contains("SERVER_PASSWORD") }
            .toList().forEach(environment::remove)
        for ((key, dir) in mapOf("HOME" to "home", "XDG_DATA_HOME" to "data", "XDG_CONFIG_HOME" to "config", "XDG_CACHE_HOME" to "cache"))
            environment[key] = Files.createDirectory(scratch.resolve(dir)).toString()
        val process = builder.start()
        var gateway: Oc2Gateway? = null
        val scope = ConnectionCoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val reader = process.inputStream.bufferedReader()
            val password = withTimeout(15_000) {
                var found: String? = null
                while (found == null) {
                    check(process.isAlive) { "Scratch OC2 stopped before readiness" }
                    if (reader.ready()) {
                        val line = reader.readLine().orEmpty()
                        found = Regex("(?i)password\\s*(?:[:=]\\s*)?(\\S+)").find(line)?.groupValues?.get(1)
                    } else delay(25)
                }
                requireNotNull(found)
            }
            val dispatchers = object : AppDispatchers {
                override val main = Dispatchers.Default
                override val default = Dispatchers.Default
                override val io = Dispatchers.IO
                override fun serial(name: String) = Dispatchers.Default.limitedParallelism(1)
            }
            val origin = ServerOrigin("http", "127.0.0.1", port)
            val net = HttpClientFactory(OkHttpEngineFactory()).create(TransportSpec(origin, HostClass.Loopback, true))
            val g = Oc2Gateway(net, origin, ConnectionCredentials("opencode", password), Clock.System, Redactor(),
                ServerLocation(scratch.resolve("work").toString()), initialDialect = Oc2Dialect.Stable)
            gateway = g
            val cut = CompletableDeferred<Unit>()
            val allowReconnect = CompletableDeferred<Unit>()
            val started = CompletableDeferred<Unit>()
            val recovered = CompletableDeferred<GatewayEvent.RunEnded>()
            var streams = 0
            val delegate = KtorSseConnector(net.http) {
                url(origin.toString() + "/api/event")
                g.auth?.let { header("Authorization", it) }
            }
            val connector = SseConnector { cursor, session ->
                val first = streams++ == 0
                if (!first) allowReconnect.await()
                delegate.connect(cursor) { body ->
                    session(object : SseBody {
                        override suspend fun read(buffer: ByteArray): Int =
                            if (!first) body.read(buffer) else coroutineScope {
                                val read = async { body.read(buffer) }
                                try { select { read.onAwait { it }; cut.onAwait { -1 } } }
                                finally { read.cancel() }
                            }
                    })
                }
            }
            scope.launch {
                g.events.all.collect { envelope ->
                    when (val event = envelope.event) {
                        is GatewayEvent.RunStarted -> { cut.complete(Unit); started.complete(Unit) }
                        is GatewayEvent.RunEnded -> if (event.reconciledHistory != null) recovered.complete(event)
                        else -> Unit
                    }
                }
            }
            // The production delivery lane calls this same bounded operation once on a successful reconnect.
            scope.launch {
                g.events.connects.drop(1).collect { generation ->
                    if (generation > 1) withTimeout(10_000) { g.reconcileRuns(setOf(sessionId!!)) }
                }
            }
            val profile = ServerProfile(ProfileId("live-recovery"), "Live recovery", BackendKind.OpenCode2, origin,
                ProfileTrust(null, null), false, "opencode")
            g.start(GatewaySpec(profile, ConnectionCredentials("opencode", password), scope), dispatchers, streamConnector = connector)
            withTimeout(20_000) { g.events.status.first { it == StreamStatus.Connected } }
            val created = assertIs<Outcome.Ok<Session>>(g.createSession(NewSession(title = "Stream recovery proof"))).value
            sessionId = created.id
            assertIs<Outcome.Ok<*>>(g.prompt(created.id, OutgoingPrompt("Reply with exactly RECOVERY_OK. Do not use tools.")))
            withTimeout(30_000) { started.await() }
            withTimeout(120_000) {
                var idle = false
                while (!idle) {
                    delay(500)
                    val statuses = assertIs<Outcome.Ok<*>>(g.sessionStatuses()).value as Map<*, *>
                    idle = created.id !in statuses
                }
            }
            allowReconnect.complete(Unit)
            val end = withTimeout(30_000) { recovered.await() }
            assertEquals(created.id, end.sessionId)
            val history = assertNotNull(end.reconciledHistory)
            assertEquals(1, history.count { it.info.role == Role.User }, "no prompt replay")
            assertTrue(history.any { it.info.role == Role.Assistant && it.parts.any { part -> part.text?.contains("RECOVERY_OK") == true } },
                "server completed actual reply while stream was absent")
            println("LIVE_RECOVERY server=stable stream_cut=true server_ended=true reconnected=true refetched=true idle=true user_prompts=1")
            assertIs<Outcome.Ok<Unit>>(g.deleteSession(created.id))
        } finally {
            gateway?.close()
            scope.cancel()
            process.destroy()
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor() }
            scratch.toFile().deleteRecursively()
        }
    }

    @Volatile private var sessionId: io.github.eslamasabry.oc.core.common.ids.SessionId? = null
}
