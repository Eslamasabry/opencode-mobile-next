package io.github.eslamasabry.oc.engine.team.fleet

import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamHostMode
import io.github.eslamasabry.oc.engine.team.TeamProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** A Gas City host in memory: GET answers by path, writes recorded, one stream fed by the test. */
internal class FakeGasCity(private val routes: MutableMap<String, String> = defaultRoutes()) : FleetTransports {
    val opened = mutableListOf<String>()
    val gets = mutableListOf<String>()
    val sends = mutableListOf<Triple<String, String, String?>>()
    val frames = Channel<FleetFrame>(Channel.UNLIMITED)
    var sendReply: FleetReply = FleetReply.Answered(202, """{"request_id":"r1"}""")
    var down = false

    fun route(path: String, json: String) {
        routes[path] = json
    }

    override fun open(baseUrl: String): FleetTransport {
        opened += baseUrl
        return object : FleetTransport {
            override suspend fun get(path: String, query: List<Pair<String, String>>, timeoutMs: Long): FleetReply {
                if (down) return FleetReply.Unreachable()
                val key = if (query.isEmpty()) path else path + "?" + query.joinToString("&") { "${it.first}=${it.second}" }
                gets += key
                val body = routes[key] ?: routes[path].takeIf { query.isEmpty() } ?: return FleetReply.Answered(404, """{"status":404,"title":"Not Found"}""")
                return FleetReply.Answered(200, body)
            }

            override suspend fun send(method: String, path: String, body: Map<String, Any?>?, requestId: String, idempotencyKey: String?): FleetReply {
                sends += Triple(method, path, idempotencyKey)
                return sendReply
            }

            override suspend fun stream(path: String, lastEventId: String?, stallMs: Long, onFrame: suspend (FleetFrame) -> Unit): FleetStreamEnd {
                onFrame(FleetFrame("heartbeat", null, """{"timestamp":"2026-11-11T10:00:00Z"}"""))
                for (frame in frames) onFrame(frame)
                awaitCancellation()
            }

            override fun close() = Unit
        }
    }

    companion object {
        const val CITY = "/v0/city/main"

        fun defaultRoutes(): MutableMap<String, String> =
            mutableMapOf(
                "/health" to """{"status":"ok","version":"1.4.1","city":"main"}""",
                "/v0/cities" to """{"items":[{"name":"main","running":true}]}""",
                "$CITY/health" to """{"status":"ok","version":"1.4.1","city":"main"}""",
                "$CITY/status" to """{"name":"main","rig_details":[{"name":"app","path":"/src/app"},{"name":"site","suspended":true}],"agents":{"running":1}}""",
                "$CITY/runs" to """{"runs":[{"run_id":"r-1","title":"mol-deacon-patrol","status":"active"}]}""",
                "$CITY/convoys" to """{"items":[{"id":"c-1","title":"sling-b-1","status":"open","dependencies":[{"depends_on_id":"b-1","type":"tracks"}]}]}""",
                "$CITY/beads" to """{"items":[{"id":"b-1","title":"Fix the login page","status":"in_progress","assignee":"app/polecat-1"}]}""",
                "$CITY/pending" to """{"items":[{"request_id":"q-1","kind":"choice","session_id":"s-1","prompt":"Keep drafts in SQLite?","options":["Yes","No"]}]}""",
                "$CITY/agents" to """{"items":[{"name":"app/polecat-1","state":"stopped","running":false,"session":{"name":"polecat-1"}}]}""",
                "$CITY/sessions" to """{"items":[{"id":"s-1","session_name":"polecat-1","state":"active","running":true}]}""",
                "$CITY/usage" to """{"available":true,"source":"local_estimate","today":{"cost_usd_estimate":1.25}}""",
                "$CITY/orders" to """{"orders":[{"name":"nightly","trigger":"cron","schedule":"0 3 * * *","enabled":false}]}""",
            )
    }
}

private object FixedClock : Clock {
    override fun now(): Instant = Instant.parse("2026-11-11T10:00:00Z")
}

private val CONFIG = TeamConfig(TeamProvider.GasCity, url = "http://100.101.102.103:8372", city = "main")

class FleetEngineTest {
    private fun engine(host: FakeGasCity) = FleetEngine(host, FixedClock, newRequestId = { "req-1" })

    @Test
    fun a_bare_supervisor_on_the_tailnet_is_read_only_and_shows_the_team() =
        runTest {
            val host = FakeGasCity()
            val fleet = engine(host)
            backgroundScope.launch { fleet.live("p1", CONFIG) }
            advanceUntilIdle()
            val state = fleet.state("p1").value
            assertEquals(FleetPhase.Ready, state.phase)
            assertEquals(FleetCapabilities.READ, state.capabilities)
            assertFalse(state.capabilities.anyControl)
            assertEquals("main", state.host?.city)
            assertEquals(FleetStream.Live, state.stream)
            // The patrol is the host's chore: kept, never shown.
            assertEquals(listOf("Fix the login page"), state.snapshot.visibleRuns.map { it.title })
            assertEquals(RunState.Working, state.snapshot.visibleRuns.single().state)
            assertEquals(listOf("app", "site"), state.snapshot.projects.map { it.id })
            assertTrue(state.snapshot.projects.last().suspended)
            val gate = state.snapshot.gates.single()
            assertEquals(GateKind.Choice, gate.kind)
            assertEquals(listOf("Yes", "No"), gate.choices)
            // The agent list says stopped, but its session runs and has a question open: it waits for the person.
            assertEquals(AgentState.Waiting, state.snapshot.agents.single().state)
            assertEquals("s-1", state.snapshot.agents.single().sessionId)
            assertEquals(1.25, state.snapshot.usage?.costUsdToday)
            assertFalse(state.stale(FixedClock.now().toEpochMilliseconds()))
        }

    @Test
    fun a_write_without_a_front_is_refused_without_a_request() =
        runTest {
            val host = FakeGasCity()
            val fleet = engine(host)
            backgroundScope.launch { fleet.live("p1", CONFIG) }
            advanceUntilIdle()
            val receipt = fleet.control("p1", FleetControl.Agent("app/polecat-1", FleetAgentAction.Pause))
            assertEquals(ReceiptStatus.Rejected, receipt.status)
            assertEquals(FleetReceipt.FRONT_REQUIRED, receipt.message)
            assertTrue(host.sends.isEmpty())
        }

    @Test
    fun behind_a_front_that_allows_this_phone_writes_carry_an_idempotency_key() =
        runTest {
            val host = FakeGasCity()
            host.route(
                FleetProbe.WELL_KNOWN,
                """{"provider":"gascity","front":true,"capabilities":{"control":true},"identity":{"allowed":true}}""",
            )
            val fleet = engine(host)
            backgroundScope.launch { fleet.live("p1", CONFIG) }
            advanceUntilIdle()
            assertEquals(FleetCapabilities.FRONT, fleet.state("p1").value.capabilities)
            val receipt = fleet.control("p1", FleetControl.Answer("q-1", FleetAnswer.Choice("Yes")))
            assertEquals(ReceiptStatus.Accepted, receipt.status)
            assertEquals(Triple("POST", "/v0/city/main/session/s-1/respond", "req-1"), host.sends.single())
            assertEquals(receipt, fleet.state("p1").value.receipts["req-1"])
        }

    @Test
    fun plain_http_to_a_lan_address_is_refused_before_any_request() =
        runTest {
            val host = FakeGasCity()
            val fleet = engine(host)
            backgroundScope.launch { fleet.live("p1", CONFIG.copy(url = "http://192.168.1.20:8372")) }
            advanceUntilIdle()
            val state = fleet.state("p1").value
            assertEquals(FleetPhase.Failed, state.phase)
            assertEquals(FleetFailure.PlainHttpRefused, state.failure)
            assertTrue(host.opened.isEmpty())
        }

    @Test
    fun an_unreachable_host_fails_and_looks_again_when_asked() =
        runTest {
            val host = FakeGasCity().apply { down = true }
            val fleet = engine(host)
            backgroundScope.launch { fleet.live("p1", CONFIG) }
            advanceUntilIdle()
            assertEquals(FleetFailure.Unreachable, fleet.state("p1").value.failure)
            host.down = false
            fleet.refresh("p1")
            advanceUntilIdle()
            assertEquals(FleetPhase.Ready, fleet.state("p1").value.phase)
            assertNull(fleet.state("p1").value.failure)
        }

    @Test
    fun a_bead_event_reads_the_runs_again_after_the_debounce() =
        runTest {
            val host = FakeGasCity()
            val fleet = engine(host)
            backgroundScope.launch { fleet.live("p1", CONFIG) }
            advanceUntilIdle()
            val before = host.gets.count { it == "/v0/city/main/runs" }
            host.route("/v0/city/main/beads", """{"items":[{"id":"b-1","title":"Fix the login page","status":"closed"}]}""")
            host.frames.trySend(FleetFrame("event", "7", """{"type":"bead.closed","seq":7,"subject":"b-1"}"""))
            advanceTimeBy(100)
            assertEquals(before, host.gets.count { it == "/v0/city/main/runs" })
            advanceUntilIdle()
            assertEquals(before + 1, host.gets.count { it == "/v0/city/main/runs" })
            assertEquals(RunState.Completed, fleet.state("p1").value.snapshot.visibleRuns.single().state)
        }

    @Test
    fun scheduled_jobs_load_when_asked() =
        runTest {
            val host = FakeGasCity()
            val fleet = engine(host)
            backgroundScope.launch { fleet.live("p1", CONFIG) }
            advanceUntilIdle()
            assertNull(fleet.state("p1").value.jobs)
            fleet.loadJobs("p1")
            val job = fleet.state("p1").value.jobs!!.single()
            assertEquals(FleetJobTrigger.Cron, job.trigger)
            assertFalse(job.enabled)
        }

    @Test
    fun the_team_on_this_phone_on_loopback_takes_controls_without_a_front() =
        runTest {
            val host = FakeGasCity()
            val fleet = engine(host)
            backgroundScope.launch { fleet.live("p1", CONFIG.copy(url = "http://127.0.0.1:8372", hostMode = TeamHostMode.Phone)) }
            advanceUntilIdle()
            assertEquals(FleetCapabilities.LOOPBACK, fleet.state("p1").value.capabilities)
            fleet.control("p1", FleetControl.Agent("app/polecat-1", FleetAgentAction.Stop))
            // No front: no idempotency key; the pool agent's session is stopped.
            assertEquals(Triple("POST", "/v0/city/main/session/s-1/stop", null), host.sends.single())
        }
}
