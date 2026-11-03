package io.github.eslamasabry.oc.engine.attention.monitor

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.testing.TestClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class MonitorAttentionReaderTest {
    private val clock = TestClock(at("2026-09-28T00:00:00Z"))

    private class FakeEngine(
        val served: String,
        val snapshot: MonitorTeamWorkspace = MonitorTeamWorkspace(emptyList()),
        val failWorkspace: Boolean = false,
    ) : MonitorPhoneEngine {
        val paths = mutableListOf<String>()
        var closed = false
        override val projects: Boolean = true

        override suspend fun health(): Outcome<String> {
            paths += "/v1/health"
            return served.ok()
        }

        override suspend fun workspace(): Outcome<MonitorTeamWorkspace> {
            paths += "/v1/workspace"
            return if (failWorkspace) Failure.Unexpected().failed() else snapshot.ok()
        }

        override fun close() {
            closed = true
        }
    }

    private fun phoneProfile(token: String = "private-phone-token") =
        MonitorProfile(id = "server", baseUrl = "https://server.example", hasPhoneEngine = true, teamEngineToken = token)

    private val plain = MonitorProfile(id = "server", baseUrl = "https://server.example")

    private fun TestScope.reader(engine: (suspend (MonitorProfile) -> MonitorPhoneEngine)? = null, timeout: kotlin.time.Duration = 8_000.milliseconds) =
        MonitorAttentionReader(clock, backgroundScope, phoneEngine = engine, timeSource = testTimeSource, timeout = timeout)

    @Test
    fun `B-AGENT-138 the phone monitor authenticates the selected profile and reads project attention`() =
        runTest {
            val engine =
                FakeEngine(
                    served = "server",
                    snapshot =
                        MonitorTeamWorkspace(
                            listOf(
                                MonitorTeamProject(
                                    id = "project",
                                    tasks =
                                        listOf(
                                            MonitorTeamTask("failed", "Failed task", "failed"),
                                            MonitorTeamTask("running", "Running task", "running"),
                                            MonitorTeamTask("interrupted", "Paused task", "interrupted"),
                                        ),
                                    requests =
                                        listOf(
                                            MonitorTeamRequest("question", taskId = "task", title = "Choose a behavior"),
                                            MonitorTeamRequest("answered", answered = true, title = "Already answered"),
                                        ),
                                ),
                            ),
                        ),
                )
            var token = ""
            val r =
                reader({
                    token = it.teamEngineToken
                    engine
                })
            val details = r.read(phoneProfile(), TestGateway(), emptyList(), emptyMap()) { true }
            assertEquals("private-phone-token", token, "the selected profile's own credential")
            assertTrue(details.teamComplete)
            val tasks = details.items.map { it.taskId }
            assertTrue(tasks.containsAll(listOf("task", "failed")))
            assertFalse("running" in tasks)
            assertEquals(2, details.items.size)
            assertEquals(listOf("/v1/health", "/v1/workspace"), engine.paths)
            assertTrue(engine.closed)
            r.dispose()
        }

    @Test
    fun `B-AGENT-138 wrong identity and missing credentials read as unknown`() =
        runTest {
            for (missing in listOf(false, true)) {
                val engine = FakeEngine(served = "another-profile")
                var built = 0
                val r =
                    reader({
                        built++
                        engine
                    })
                val details = r.read(phoneProfile(token = if (missing) "" else "private-phone-token"), TestGateway(), emptyList(), emptyMap()) { true }
                assertFalse(details.teamComplete)
                assertTrue(details.items.isEmpty())
                assertEquals(if (missing) 0 else 1, engine.paths.size)
                if (missing) assertEquals(0, built) else assertTrue(engine.closed)
                r.dispose()
            }
        }

    @Test
    fun `B-AGENT-138 the project inventory cap keeps coverage partial`() =
        runTest {
            val engine =
                FakeEngine(
                    served = "server",
                    snapshot = MonitorTeamWorkspace(listOf(MonitorTeamProject("project", tasks = List(300) { MonitorTeamTask("$it", "task", "failed") }))),
                )
            val r = reader({ engine })
            val details = r.read(phoneProfile(), TestGateway(), emptyList(), emptyMap()) { true }
            assertFalse(details.teamComplete)
            assertFalse(details.complete)
            assertEquals(MonitorAttentionReader.TEAM_RECORD_LIMIT - 1, details.items.size)
            assertTrue(engine.closed)
            r.dispose()
        }

    @Test
    fun `B-AGENT-139 the shared deadline stops later reads and closes a late engine client`() =
        runTest {
            val late = CompletableDeferred<MonitorPhoneEngine>()
            val engine = FakeEngine(served = "server")
            val r = reader({ late.await() }, timeout = 250.milliseconds)
            val details = r.read(phoneProfile(), TestGateway(), emptyList(), emptyMap()) { true }
            assertFalse(details.complete)
            assertFalse(details.teamComplete)
            late.complete(engine)
            runCurrent()
            assertTrue(engine.closed)
            assertTrue(engine.paths.isEmpty())
            r.dispose()
        }

    @Test
    fun `B-AGENT-139 a confirmed current failure is reported and busy clears it without history`() =
        runTest {
            val r = reader()
            val gateway = TestGateway()
            val sessions = listOf(MonitorSession("s", "Task"))
            val failed = r.read(plain, gateway, sessions, emptyMap()) { true }
            assertTrue(failed.complete)
            assertEquals(MonitorAttentionKind.FailedRun, failed.items.single().kind)
            assertEquals(MonitorWorkPhase.Failed, failed.items.single().phase)
            assertEquals(setOf("s"), failed.checkedSessionIds)
            val busy = r.read(plain, gateway, sessions, mapOf("s" to "busy")) { true }
            assertTrue(busy.items.isEmpty())
            assertEquals(setOf("s"), busy.checkedSessionIds)
            assertEquals(listOf("s"), gateway.messageCalls, "busy reads no history")
            gateway.page = MonitorMessagePage(listOf(MonitorMessage("user-3", "user")))
            val nextTurn = r.read(plain, gateway, sessions, emptyMap()) { true }
            assertTrue(nextTurn.items.isEmpty())
            assertEquals(setOf("s"), nextTurn.checkedSessionIds)
            r.dispose()
        }

    @Test
    fun `B-AGENT-139 bounded rotation and truncated history keep coverage uncertain`() =
        runTest {
            val r = reader()
            val gateway = TestGateway()
            val sessions = (0 until 6).map { MonitorSession("$it") }
            val first = r.read(plain, gateway, sessions, emptyMap()) { true }
            assertFalse(first.complete)
            assertEquals(listOf("0", "1", "2", "3"), gateway.messageCalls)
            r.read(plain, gateway, sessions, emptyMap()) { true }
            assertEquals(listOf("4", "5", "0", "1"), gateway.messageCalls.drop(4))
            assertTrue(gateway.messageLimits.all { it == 50 })
            gateway.page = MonitorMessagePage(listOf(MonitorMessage("assistant-2", "assistant", finish = "length")), hasMore = true)
            val unknown = r.read(plain, gateway, listOf(MonitorSession("s")), emptyMap()) { true }
            assertTrue(unknown.items.isEmpty())
            assertTrue(unknown.checkedSessionIds.isEmpty())
            assertFalse(unknown.complete)
            r.forget("server")
            gateway.messageCalls.clear()
            r.read(plain, gateway, sessions, emptyMap()) { true }
            assertEquals("0", gateway.messageCalls.first())
            r.dispose()
        }

    @Test
    fun `B-AGENT-139 a revoked ownership rejects late history and performs no later calls`() =
        runTest {
            var built = 0
            val r =
                reader({
                    built++
                    error("must not build")
                })
            val gateway = TestGateway()
            val pendingPage = CompletableDeferred<MonitorMessagePage>()
            gateway.pendingPage = pendingPage
            var current = true
            val result = async { r.read(phoneProfile(), gateway, listOf(MonitorSession("a"), MonitorSession("b")), emptyMap()) { current } }
            runCurrent()
            current = false
            pendingPage.complete(gateway.page)
            val details = result.await()
            assertTrue(details.items.isEmpty())
            assertFalse(details.complete)
            assertTrue(details.checkedSessionIds.isEmpty())
            assertEquals(listOf("a"), gateway.messageCalls)
            assertEquals(0, built)
            r.dispose()
        }

    @Test
    fun `B-AGENT-139 a partial read stays partial and closes the owned engine client`() =
        runTest {
            val engine = FakeEngine(served = "server", failWorkspace = true)
            val r = reader({ engine })
            val details = r.read(phoneProfile(), TestGateway(), emptyList(), emptyMap()) { true }
            assertFalse(details.complete)
            assertFalse(details.teamComplete)
            assertTrue(details.items.isEmpty())
            assertTrue(engine.closed)
            r.dispose()
        }
}
