package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.AutomationAction
import io.github.eslamasabry.oc.core.model.AutomationOutcome
import io.github.eslamasabry.oc.core.model.AutomationStatus
import io.github.eslamasabry.oc.core.model.AutomationTrigger
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.oc2.adapter.Oc2CoreProjection
import io.github.eslamasabry.oc.protocol.oc2.adapter.Oc2EventAdapter
import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Envelope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class Oc2AutomationsTest {
    @Test
    fun `B-AUTO-003 supportsAutomations gates cleanly on stable OpenCode 2_4_0 and newer`() {
        assertFalse(Oc2Dialect.supportsAutomations(null))
        assertFalse(Oc2Dialect.supportsAutomations("0.0.0-beta-12"))
        assertFalse(Oc2Dialect.supportsAutomations("1.9.0"))
        assertFalse(Oc2Dialect.supportsAutomations("2.3.2"))
        assertTrue(Oc2Dialect.supportsAutomations("2.4.0"))
        assertTrue(Oc2Dialect.supportsAutomations("2.4.1"))
        assertTrue(Oc2Dialect.supportsAutomations("2.5.0-rc.1"))
        assertTrue(Oc2Dialect.supportsAutomations("3.0.0"))
    }

    @Test
    fun `B-AUTO-004 health turns serverAutomations on for 2_4_1 and keeps it off for 2_3_2`() =
        runTest {
            val older =
                RowServer(dialect = null) { seen ->
                    when (seen.path) {
                        "/api/health" -> respond("", HttpStatusCode.NotFound)
                        "/api/info" -> json("""{"version":"2.3.2"}""")
                        else -> error("Unexpected ${seen.path}")
                    }
                }
            assertIs<Outcome.Ok<*>>(older.gateway.health())
            assertFalse(older.gateway.capabilities.value.serverAutomations)
            older.gateway.close()

            val newer =
                RowServer(dialect = null) { seen ->
                    when (seen.path) {
                        "/api/health" -> respond("", HttpStatusCode.NotFound)
                        "/api/info" -> json("""{"version":"2.4.1"}""")
                        else -> error("Unexpected ${seen.path}")
                    }
                }
            assertIs<Outcome.Ok<*>>(newer.gateway.health())
            assertTrue(newer.gateway.capabilities.value.serverAutomations)
            newer.gateway.close()
        }

    @Test
    fun `B-AUTO-005 list decodes automations and redacts secrets in output and pause reason`() =
        runTest {
            val redactor = Redactor()
            redactor.registerSecret("sk-live-secret-token")
            val server =
                RowServer(redactor = redactor) { seen ->
                    when {
                        seen.method == "GET" && seen.path == "/api/experimental/automation" ->
                            json(
                                """{
                                  "data": [
                                    {
                                      "id": "aut_1",
                                      "name": "CI check",
                                      "status": "active",
                                      "trigger": {"type": "every", "every": "1h"},
                                      "action": {"type": "prompt", "prompt": "Check CI"},
                                      "nextDueAt": "2026-11-26T09:00:00Z",
                                      "runCount": 4,
                                      "lastRunAt": 1795676400000,
                                      "lastOutcome": "fired",
                                      "lastOutput": "token sk-live-secret-token leaked"
                                    },
                                    {
                                      "id": "aut_2",
                                      "name": "Cleanup",
                                      "status": "paused",
                                      "trigger": {"type": "cron", "cron": "0 3 * * *"},
                                      "action": {"type": "shell", "command": "git gc"},
                                      "lastRunAt": 1795590000000,
                                      "lastOutcome": "failed",
                                      "pauseReason": "too many failures with sk-live-secret-token"
                                    }
                                  ]
                                }"""
                            )
                        else -> error("Unexpected ${seen.method} ${seen.path}")
                    }
                }

            val listed = assertIs<Outcome.Ok<*>>(server.gateway.listAutomations(SessionId("ses_1")))
            val items =
                listed.value as List<io.github.eslamasabry.oc.core.model.ServerAutomation>
            assertEquals(2, items.size)
            assertEquals("ses_1", server.seen.first().query["sessionID"])

            val first = items[0]
            assertEquals("aut_1", first.id)
            assertEquals("CI check", first.name)
            assertEquals(AutomationStatus.Active, first.status)
            assertEquals(AutomationTrigger.Every("1h"), first.trigger)
            assertEquals(AutomationAction.Prompt("Check CI"), first.action)
            assertEquals(AutomationOutcome.Fired, first.lastOutcome)
            assertEquals(4, first.runCount)
            assertNotNull(first.nextDueAtMs)
            assertFalse(first.lastOutput!!.text.contains("sk-live-secret-token"))

            val second = items[1]
            assertEquals(AutomationStatus.Paused, second.status)
            assertEquals(AutomationTrigger.Cron("0 3 * * *"), second.trigger)
            assertEquals(AutomationAction.Shell("git gc"), second.action)
            assertEquals(AutomationOutcome.Failed, second.lastOutcome)
            assertFalse(second.pauseReason!!.text.contains("sk-live-secret-token"))
            server.gateway.close()
        }

    @Test
    fun `B-AUTO-005 pause, resume and delete hit experimental automation endpoints`() = runTest {
        val server =
            RowServer { seen ->
                when {
                    seen.method == "POST" &&
                        seen.path == "/api/experimental/automation/aut_1/pause" ->
                        json(
                            """{"data":{"id":"aut_1","name":"CI check","status":"paused",""" +
                                """"trigger":{"type":"every","every":"1h"},""" +
                                """"action":{"type":"prompt","prompt":"Check CI"}}}"""
                        )
                    seen.method == "POST" &&
                        seen.path == "/api/experimental/automation/aut_1/resume" ->
                        json(
                            """{"data":{"id":"aut_1","name":"CI check","status":"active",""" +
                                """"trigger":{"type":"every","every":"1h"},""" +
                                """"action":{"type":"prompt","prompt":"Check CI"}}}"""
                        )
                    seen.method == "DELETE" && seen.path == "/api/experimental/automation/aut_2" ->
                        respond("", HttpStatusCode.NoContent)
                    else -> error("Unexpected ${seen.method} ${seen.path}")
                }
            }

        val paused =
            assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.ServerAutomation>>(
                server.gateway.pauseAutomation("aut_1")
            )
        assertEquals(AutomationStatus.Paused, paused.value.status)

        val resumed =
            assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.ServerAutomation>>(
                server.gateway.resumeAutomation("aut_1")
            )
        assertEquals(AutomationStatus.Active, resumed.value.status)

        assertIs<Outcome.Ok<Unit>>(server.gateway.deleteAutomation("aut_2"))
        server.gateway.close()
    }

    @Test
    fun `B-AUTO-006 404 on automation list disables capability and short-circuits later calls`() =
        runTest {
            val server =
                RowServer(dialect = null) { seen ->
                    when (seen.path) {
                        "/api/health" -> respond("", HttpStatusCode.NotFound)
                        "/api/info" -> json("""{"version":"2.4.1"}""")
                        "/api/experimental/automation" -> respond("", HttpStatusCode.NotFound)
                        else -> error("Unexpected ${seen.path}")
                    }
                }
            assertIs<Outcome.Ok<*>>(server.gateway.health())
            assertTrue(server.gateway.capabilities.value.serverAutomations)

            val first = assertIs<Outcome.Failed>(server.gateway.listAutomations())
            assertEquals(Failure.Unavailable(FeatureId("serverAutomations")), first.failure)
            assertFalse(server.gateway.capabilities.value.serverAutomations)

            val countBefore = server.seen.size
            val second = assertIs<Outcome.Failed>(server.gateway.listAutomations())
            assertEquals(Failure.Unavailable(FeatureId("serverAutomations")), second.failure)
            assertEquals(countBefore, server.seen.size, "disabled capability should not hit the network again")
            server.gateway.close()
        }

    @Test
    fun `B-AUTO-007 history and live inbox messages preserve metadata automation while ordinary prompts stay null`() {
        val history = Oc2History(Redactor())
        val autoJson =
            Json.parseToJsonElement(
                """{
                  "id": "msg_auto",
                  "type": "user",
                  "text": "Check CI",
                  "time": {"created": 1000},
                  "metadata": {
                    "automation": {"id": "aut_1", "name": "CI check", "run": 4, "late": false}
                  }
                }"""
            )
        val humanJson =
            Json.parseToJsonElement(
                """{"id":"msg_human","type":"user","text":"Hi","time":{"created":2000}}"""
            )
        val autoMsg = assertNotNull(history.message("ses_1", autoJson))
        assertEquals("CI check", autoMsg.info.automation?.name)
        assertEquals(4, autoMsg.info.automation?.run)
        assertEquals("aut_1", autoMsg.info.automation?.id)

        val humanMsg = assertNotNull(history.message("ses_1", humanJson))
        assertNull(humanMsg.info.automation)

        val adapter = Oc2EventAdapter { 3000L }
        val projection = Oc2CoreProjection(Redactor()::details)
        val envelope =
            Oc2Envelope.parse(
                """{
                  "type": "session.inbox.enqueued",
                  "data": {
                    "sessionID": "ses_1",
                    "inboxID": "inb_1",
                    "item": {
                      "id": "inb_1",
                      "type": "user",
                      "payload": {"text": "Check CI"},
                      "metadata": {"automation": {"id": "aut_1", "name": "CI check", "run": 5}}
                    }
                  }
                }"""
            )
        val upserted =
            adapter
                .adapt(envelope)
                .map(projection::toCore)
                .filterIsInstance<GatewayEvent.MessageUpserted>()
                .single()
        assertEquals("CI check", upserted.message.automation?.name)
        assertEquals(5, upserted.message.automation?.run)
    }
}
