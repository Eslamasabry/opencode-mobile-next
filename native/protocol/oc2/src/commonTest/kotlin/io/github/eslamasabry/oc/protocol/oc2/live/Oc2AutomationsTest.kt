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
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.oc2.adapter.Oc2CoreProjection
import io.github.eslamasabry.oc.protocol.oc2.adapter.Oc2EventAdapter
import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Envelope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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

    private val sampleListJson =
        """{
          "data": [
            {
              "id": "aut_1",
              "name": "CI check",
              "status": "active",
              "tz": "Europe/Berlin",
              "trigger": {"type": "every", "every": "5 minutes"},
              "action": {"type": "prompt", "prompt": "Check CI", "to": {"session": "ses_42"}},
              "nextDueAt": "2026-11-26T08:00:00Z",
              "runCount": 4,
              "lastRunAt": 1795676400000,
              "lastOutcome": "fired",
              "lastOutput": "token sk-live-secret-token leaked"
            },
            {
              "id": "aut_2",
              "name": "Cleanup",
              "status": "paused",
              "trigger": {"type": "cron", "cron": "0 3 * * *", "tz": "America/New_York"},
              "action": {"type": "shell", "command": "git gc"},
              "lastRunAt": 1795590000000,
              "lastOutcome": "failed",
              "last_output": "paused: directory missing with sk-live-secret-token"
            },
            {
              "id": "aut_3",
              "name": "Release watch",
              "status": "active",
              "trigger": {
                "type": "event",
                "server": "github",
                "event": "release.published",
                "args": {"repo": "oc_app", "token": "sk-live-secret-token"}
              },
              "action": {"type": "prompt", "prompt": "Write notes", "to": {"subagent": "writer"}}
            },
            {
              "id": "aut_4",
              "name": "Soon reminder",
              "status": "active",
              "trigger": {"type": "at", "at": "in 10 minutes", "tz": "Europe/Berlin"},
              "action": {"type": "prompt", "prompt": "Check deploy"}
            },
            {
              "id": "aut_5",
              "name": "Wall-clock reminder",
              "status": "active",
              "trigger": {"type": "at", "at": "2026-11-27T09:00", "tz": "Europe/Berlin"},
              "action": {"type": "prompt", "prompt": "Start release"}
            }
          ]
        }"""

    @Test
    fun `B-AUTO-005 list decodes real v2_4_1 payloads with tz, event, to targets, at and redacts output`() =
        runTest {
            val redactor = Redactor()
            redactor.registerSecret("sk-live-secret-token")
            val server =
                RowServer(redactor = redactor) { seen ->
                    when {
                        seen.method == "GET" && seen.path == "/api/experimental/automation" ->
                            json(sampleListJson)
                        else -> error("Unexpected ${seen.method} ${seen.path}")
                    }
                }

            val listed = assertIs<Outcome.Ok<*>>(server.gateway.listAutomations(SessionId("ses_1")))
            val items = listed.value as List<ServerAutomation>
            assertEquals(5, items.size)
            assertEquals("ses_1", server.seen.first().query["sessionID"])

            val first = items[0]
            assertEquals("aut_1", first.id)
            assertEquals("CI check", first.name)
            assertEquals("Europe/Berlin", first.timeZone)
            assertEquals(AutomationStatus.Active, first.status)
            assertEquals(AutomationTrigger.Every("5 minutes"), first.trigger)
            assertEquals(
                AutomationAction.Prompt("Check CI", subagent = null, targetSessionId = "ses_42"),
                first.action,
            )
            assertEquals(AutomationOutcome.Fired, first.lastOutcome)
            assertEquals(4, first.runCount)
            assertNotNull(first.nextDueAtMs)
            assertFalse(first.lastOutput!!.text.contains("sk-live-secret-token"))

            val second = items[1]
            assertEquals(AutomationStatus.Paused, second.status)
            assertEquals("America/New_York", second.timeZone)
            assertEquals(AutomationTrigger.Cron("0 3 * * *", timeZone = "America/New_York"), second.trigger)
            assertEquals(AutomationAction.Shell("git gc"), second.action)
            assertEquals(AutomationOutcome.Failed, second.lastOutcome)
            assertNotNull(second.lastOutput)
            assertFalse(second.lastOutput!!.text.contains("sk-live-secret-token"))

            val third = items[2]
            val ev = assertIs<AutomationTrigger.Event>(third.trigger)
            assertEquals("github", ev.server)
            assertEquals("release.published", ev.event)
            assertNotNull(ev.args)
            assertFalse(ev.args!!.text.contains("sk-live-secret-token"))
            assertEquals(
                AutomationAction.Prompt("Write notes", subagent = "writer", targetSessionId = null),
                third.action,
            )

            val fourth = items[3]
            val relAt = assertIs<AutomationTrigger.At>(fourth.trigger)
            assertNull(relAt.atMs)
            assertEquals("in 10 minutes", relAt.raw)
            assertEquals("Europe/Berlin", relAt.timeZone)

            val fifth = items[4]
            val wallAt = assertIs<AutomationTrigger.At>(fifth.trigger)
            assertNull(wallAt.atMs)
            assertEquals("2026-11-27T09:00", wallAt.raw)
            assertEquals("Europe/Berlin", wallAt.timeZone)
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
            assertIs<Outcome.Ok<ServerAutomation>>(server.gateway.pauseAutomation("aut_1"))
        assertEquals(AutomationStatus.Paused, paused.value.status)

        val resumed =
            assertIs<Outcome.Ok<ServerAutomation>>(server.gateway.resumeAutomation("aut_1"))
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
    fun `B-AUTO-018 HTTP 403 ForbiddenError when automations are disabled maps to Unavailable`() =
        runTest {
            val server =
                RowServer(dialect = null) { seen ->
                    when (seen.path) {
                        "/api/health" -> respond("", HttpStatusCode.NotFound)
                        "/api/info" -> json("""{"version":"2.4.1"}""")
                        "/api/experimental/automation" ->
                            respond(
                                """{"_tag":"ForbiddenError","name":"AutomationDisabledError",""" +
                                    """"message":"Automations are disabled"}""",
                                HttpStatusCode.Forbidden,
                                headersOf(
                                    HttpHeaders.ContentType,
                                    ContentType.Application.Json.toString(),
                                ),
                            )
                        else -> error("Unexpected ${seen.path}")
                    }
                }
            assertIs<Outcome.Ok<*>>(server.gateway.health())
            assertTrue(server.gateway.capabilities.value.serverAutomations)

            val result = assertIs<Outcome.Failed>(server.gateway.listAutomations())
            assertEquals(Failure.Unavailable(FeatureId("serverAutomations")), result.failure)
            assertFalse(server.gateway.capabilities.value.serverAutomations)
            server.gateway.close()
        }

    @Test
    fun `B-AUTO-019 health refresh resets automationsDisabled latch after transient 403 or server upgrade`() =
        runTest {
            var automationsEnabled = false
            var version = "2.4.1"
            val server =
                RowServer(dialect = null) { seen ->
                    when (seen.path) {
                        "/api/health" -> respond("", HttpStatusCode.NotFound)
                        "/api/info" -> json("""{"version":"$version"}""")
                        "/api/experimental/automation" ->
                            if (!automationsEnabled) {
                                respond(
                                    """{"_tag":"ForbiddenError","message":"Automations are disabled"}""",
                                    HttpStatusCode.Forbidden,
                                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                                )
                            } else {
                                json("""{"data":[]}""")
                            }
                        else -> error("Unexpected ${seen.path}")
                    }
                }
            assertIs<Outcome.Ok<*>>(server.gateway.health())
            assertTrue(server.gateway.capabilities.value.serverAutomations)

            assertIs<Outcome.Failed>(server.gateway.listAutomations())
            assertFalse(server.gateway.capabilities.value.serverAutomations)

            automationsEnabled = true
            version = "2.4.2"
            assertIs<Outcome.Ok<*>>(server.gateway.health())
            assertTrue(server.gateway.capabilities.value.serverAutomations)

            val recovered = assertIs<Outcome.Ok<*>>(server.gateway.listAutomations())
            assertEquals(emptyList<ServerAutomation>(), recovered.value)
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
