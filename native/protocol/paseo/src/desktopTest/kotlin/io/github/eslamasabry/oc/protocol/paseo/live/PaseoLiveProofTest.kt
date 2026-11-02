package io.github.eslamasabry.oc.protocol.paseo.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.protocol.paseo.PaseoTestedVersions
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGateway
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoProbeResult
import io.github.eslamasabry.oc.protocol.paseo.gateway.probePaseoConnection
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * The Paseo live proof (plan 04 §8 "Live proof", WP-04.10): the real transport, gateway and mappers against a running
 * daemon — FX-PASEO (real `@getpaseo/cli` 0.11.2 with the fixture's fake Claude Code, or its stub) — over the JDK
 * WebSocket. Skipped unless `OC_PASEO_URL` is set (the build passes it as `oc.paseo.url`); `OC_PASEO_DIR` is the
 * project folder and `OC_PASEO_PASSWORD` the daemon password, if any. The password is never printed.
 *
 * Steps 1–4 (hello, version pin, providers, scoped agent list) are the plan's proof; 5–9 add the chat round trip
 * (draft → create on first prompt → streamed turn → history → archive), 10 the refused password, 11 a dropped link.
 * The step log is written to build/paseo-live-proof.txt.
 */
class PaseoLiveProofTest {
    private val url: String? = System.getProperty("oc.paseo.url")
    private val dir: String = System.getProperty("oc.paseo.dir").orEmpty()
    private val password: String = System.getProperty("oc.paseo.password").orEmpty()
    private val log = mutableListOf<String>()

    private fun step(name: String, ok: Boolean, note: String = "") {
        log += "${if (ok) "PASS" else "FAIL"}  $name${if (note.isNotEmpty()) "  ($note)" else ""}"
    }

    @Test
    fun `WP-04-10 Paseo live proof against a scratch daemon`() {
        val target = url ?: return // not configured: nothing to prove here
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "paseo-live-lane").apply { isDaemon = true } }
        val lane = executor.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + lane)
        try {
            runTest(timeout = 4.minutes) { withContext(lane) { prove(target, scope, lane) } }
        } finally {
            scope.cancel()
            executor.shutdownNow()
            File("build/paseo-live-proof.txt").writeText(log.joinToString("\n", postfix = "\n"))
            println(log.joinToString("\n"))
        }
        assertTrue(log.none { it.startsWith("FAIL") }, log.joinToString("\n"))
    }

    private fun gateway(scope: CoroutineScope, lane: kotlinx.coroutines.CoroutineDispatcher, pw: String = password): PaseoGateway {
        val transport = PaseoTransport(endpoint = url!!, password = pw, socketFactory = JdkPaseoSocketFactory(), scope = scope, lane = lane)
        return PaseoGateway(transport, scope, lane, clock = Clock.System, directory = dir)
    }

    private suspend fun prove(target: String, scope: CoroutineScope, lane: kotlinx.coroutines.CoroutineDispatcher) {
        // 1–3: the pairing probe (hello, scoped read, ready runtimes).
        val probe = probePaseoConnection(target, password, dir) { _, _, _ -> gateway(scope, lane) }
        step("1 probe verifies the daemon", probe is PaseoProbeResult.Verified, probe.toString())
        val version = (probe as? PaseoProbeResult.Verified)?.version
        step("2 daemon version is a tested pin", PaseoTestedVersions.paseoSupports(version), "version=$version")
        val runtimes = (probe as? PaseoProbeResult.Verified)?.runtimes.orEmpty()
        step("3 ready runtimes listed", runtimes.isNotEmpty(), runtimes.joinToString())

        val g = gateway(scope, lane)
        val events = mutableListOf<GatewayEvent>()
        scope.launch { g.events.all.collect { events += it.event } }
        g.startListening()
        val connected = withTimeoutOrNull(15_000) { g.events.status.first { it == StreamStatus.Connected } }
        step("4 listening connects and reads this folder's agents", connected != null && g.allSessions() is Outcome.Ok)

        // 5–9: a chat round trip on the default runtime.
        val defaults = g.chatDefaults()
        val model = (defaults as? Outcome.Ok)?.value?.model
        step("5 chat defaults name a model", model != null, model?.toString().orEmpty())
        val draft = (g.createSession(NewSession()) as? Outcome.Ok)?.value
        val sent = draft?.let { g.prompt(it.id, OutgoingPrompt("Say hello from the live proof.", model?.let { m -> ModelSelection(ModelRef(m.providerId, m.modelId)) })) }
        step("6 first prompt creates the agent", sent is Outcome.Ok, sent.toString())
        val ended = withTimeoutOrNull(90_000) {
            while (events.none { it is GatewayEvent.RunEnded && (it.sessionId == draft?.id) }) delay(100)
            true
        } == true
        val streamed = events.count { it is GatewayEvent.MessageUpserted || it is GatewayEvent.PartUpserted || it is GatewayEvent.PartDelta }
        step("7 the turn streams and ends", ended && streamed > 0, "events=$streamed ${events.filterIsInstance<GatewayEvent.RunEnded>().map { it.outcome }}")
        val history = draft?.let { g.messagePage(it.id, null, 100) }
        val messages = (history as? Outcome.Ok)?.value?.items.orEmpty()
        step(
            "8 history reads the user prompt and a reply",
            messages.any { it.info.role == Role.User } && messages.any { it.info.role == Role.Assistant },
            "messages=${messages.size}",
        )
        val archived = draft?.let { g.deleteSession(it.id) }
        val listed = (g.allSessions() as? Outcome.Ok)?.value.orEmpty()
        step("9 delete archives the agent", archived is Outcome.Ok && listed.none { it.id == draft?.id }, archived.toString())

        // 10: a wrong password is a sign-in failure (only when the daemon has one).
        if (password.isNotEmpty()) {
            val wrong = gateway(scope, lane, pw = "wrong-$password".take(64)).health()
            step("10 a wrong password reads as sign-in needed", (wrong as? Outcome.Failed)?.failure is Failure.Net.SignInNeeded, wrong.toString().replace(password, "•••"))
        } else {
            step("10 a wrong password reads as sign-in needed", true, "skipped: daemon has no password")
        }

        // 11: a dropped link reconnects and re-reads the list (FX-PASEO ctl drop-socket, when given).
        val ctl = System.getenv("OC_PASEO_CTL")
        if (ctl != null) {
            val before = g.events.connects.value
            dropSockets(ctl)
            val again = withTimeoutOrNull(30_000) { g.events.connects.first { it > before } }
            step("11 a dropped socket reconnects", again != null, "connects=${g.events.connects.value}")
        } else {
            step("11 a dropped socket reconnects", true, "skipped: no OC_PASEO_CTL")
        }
        g.close()
    }

    private fun dropSockets(ctl: String) {
        val connection = java.net.URI("$ctl/drop-socket").toURL().openConnection(java.net.Proxy.NO_PROXY) as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write("{}".toByteArray()) }
        connection.inputStream.use { it.readBytes() }
    }
}
