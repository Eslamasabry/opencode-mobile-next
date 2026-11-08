package io.github.eslamasabry.oc.engine.factsbook

import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.testing.TestClock
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Flutter test/factsbook/factsbook_state_test.dart, factsbook_session_test.dart and the outbox rules. */
class FactsBookEngineTest {
    private val profile = "p1"
    private val clock = TestClock(Instant.parse("2026-11-08T10:00:00Z"))

    private class Rig(val engine: FactsBookEngine, val http: FakeHttp, val secrets: MemorySecrets, val store: FactsBookStore)

    private fun TestScope.rig(
        prefs: Map<String, RawPref> = mapOf("flutter.oc.factsbookNode.p1" to RawPref.Str(FLUTTER_LINK)),
        secrets: Map<String, String> = mapOf("oc.factsbook.p1" to "fixture-token"),
    ): Rig {
        val (kv, _) = flutterPrefs(prefs)
        val vault = MemorySecrets(secrets)
        val store = FactsBookStore(kv, vault, laneApp)
        val http = FakeHttp()
        val engine =
            FactsBookEngine(
                store,
                gatewayFor = { _, token -> HttpFactsBookGateway(http, token, Random(1), retryDelayMs = 1) },
                outboxFor = { id ->
                    object : FactsBookOutboxStore {
                        override suspend fun read(): String? = store.readOutbox(id)

                        override suspend fun write(value: String?): Boolean = store.writeOutbox(id, value)
                    }
                },
                clock = clock,
                random = Random(2),
            )
        return Rig(engine, http, vault, store)
    }

    private fun FakeHttp.healthyNode() {
        on("GET /v1/status", 200, STATUS_JSON)
        on("GET /v1/review", 200, """{"items":[{"rvw":"rvw_1","class":"fact_add","state":"proposed","digest":"d","proposal":{"text":"x"}},
            {"rvw":"rvw_2","class":"fact_add","state":"approved","digest":"d","proposal":{}}]}""")
        on("GET /v1/inbox/pending", 200, """{"items":[]}""")
        on("GET /v1/things", 200, THINGS_JSON)
    }

    @Test
    fun `B-FB-001 a Flutter pairing is read in place from oc factsbookNode and the migrated token`() = runTest {
        val r = rig()
        r.engine.open(profile)
        val state = r.engine.state.value
        assertTrue(state.paired)
        assertEquals("http://127.0.0.1:8787", state.link?.baseUrl)
        assertEquals("cli_01", state.link?.clientId)
        r.http.healthyNode()
        r.engine.refresh()
        assertEquals("Bearer fixture-token", r.http.sent.first().headers["Authorization"])
    }

    @Test
    fun `B-FB-002 no record or no token reads as unpaired and nothing is sent`() = runTest {
        val noToken = rig(secrets = emptyMap())
        noToken.engine.open(profile)
        assertFalse(noToken.engine.state.value.paired)
        assertTrue(noToken.engine.state.value.loaded)
        val damaged = rig(prefs = mapOf("flutter.oc.factsbookNode.p1" to RawPref.Str("{not json")))
        damaged.engine.open(profile)
        assertFalse(damaged.engine.state.value.paired)
        damaged.engine.refresh()
        assertTrue(damaged.http.sent.isEmpty())
    }

    @Test
    fun `B-FB-003 the overview counts only proposals still waiting and keeps the three newest things`() = runTest {
        val r = rig()
        r.engine.open(profile)
        r.http.healthyNode()
        r.engine.refresh()
        val state = r.engine.state.value
        assertEquals(FactsBookLoad.Ready, state.load)
        assertEquals(1, state.needsYouCount)
        assertEquals(listOf("per_01"), state.recent.map { it.id })
    }

    @Test
    fun `B-FB-004 a failed read keeps the last copy and names the failure`() = runTest {
        val r = rig()
        r.engine.open(profile)
        r.http.healthyNode()
        r.engine.refresh()
        r.http.unreachable = true
        r.engine.refresh()
        val state = r.engine.state.value
        assertEquals(FactsBookLoad.Ready, state.load) // the last copy stands
        assertTrue(state.error!!.unreachable)
        assertEquals(1, state.needsYouCount)
    }

    @Test
    fun `B-FB-005 an unreachable node keeps the capture on the phone in Flutter's outbox shape`() = runTest {
        val r = rig()
        r.engine.open(profile)
        r.http.unreachable = true
        val outcome = r.engine.capture(CaptureRequest(CaptureKind.Note, "Buy milk"))
        assertIs<CaptureOutcome.KeptOnPhone>(outcome)
        val saved = Json.parseToJsonElement(r.secrets.values.getValue("oc.factsbookOutbox.p1")) as JsonArray
        val entry = saved.single().jsonObject
        assertEquals("note", entry["request"]!!.jsonObject["kind"]!!.jsonPrimitive.content)
        assertEquals("Buy milk", entry["request"]!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertTrue(entry["key"]!!.jsonPrimitive.content.drop(4).all { it in 'a'..'z' }) // letters only
        assertEquals(1, r.engine.state.value.needsYou.single { it.kind == NeedsYouKind.NotSaved }.count)
    }

    @Test
    fun `B-FB-006 a Flutter outbox entry is read and sent once the node answers`() = runTest {
        val flutterOutbox =
            """[{"id":"out_1_0","key":"cap-abcdefghijklmnopqrstuvwx","request":{"kind":"note","text":"Old note","trust":"user_authored"},"created_at":"2026-10-01T09:00:00.000Z","retries":0}]"""
        val r = rig(secrets = mapOf("oc.factsbook.p1" to "fixture-token", "oc.factsbookOutbox.p1" to flutterOutbox))
        r.engine.open(profile)
        assertEquals("Old note", r.engine.state.value.pending.single().text)
        r.http.healthyNode()
        r.http.on("POST /v1/outbox", 200, """{"receipts":[{"key":"cap-abcdefghijklmnopqrstuvwx","status":"accepted"}]}""")
        r.engine.refresh()
        assertNull(r.secrets.values["oc.factsbookOutbox.p1"])
        assertTrue(r.engine.state.value.pending.isEmpty())
    }

    @Test
    fun `B-FB-007 a refusal from the node is not hidden in the outbox`() = runTest {
        val r = rig()
        r.engine.open(profile)
        r.http.healthyNode()
        r.http.on("POST /v1/inbox", 422, """{"error":{"code":"bad_request","request_id":"req_9"}}""")
        val outcome = r.engine.capture(CaptureRequest(CaptureKind.Note, "x"))
        val refused = assertIs<CaptureOutcome.Refused>(outcome)
        assertEquals("bad_request", refused.error.code)
        assertEquals("req_9", refused.error.requestId)
        assertNull(r.secrets.values["oc.factsbookOutbox.p1"])
    }

    @Test
    fun `B-FB-008 disconnect forgets token owner key and pairing but keeps the outbox`() = runTest {
        val r = rig(secrets = mapOf("oc.factsbook.p1" to "t", "oc.factsbookOwner.p1" to "seed", "oc.factsbookOutbox.p1" to "[]"))
        r.engine.open(profile)
        r.engine.disconnect()
        assertFalse(r.engine.state.value.paired)
        assertEquals(setOf("oc.factsbookOutbox.p1"), r.secrets.values.keys)
        assertNull(r.store.link(profile))
    }

    @Test
    fun `B-FB-009 pairing accepts only a private address and saves Flutter's record`() = runTest {
        val r = rig(prefs = emptyMap(), secrets = emptyMap())
        r.engine.open(profile)
        val pairHttp = FakeHttp()
        pairHttp.on(
            "POST /v1/pair",
            200,
            """{"token":"fresh-token","client_id":"cli_2","scopes":["read","write"],"vault":"v","root_fingerprint":"fp","node":"n"}""",
        )
        val refused = r.engine.pair("example.com:8787", "123456", null) { HttpFactsBookGateway(pairHttp, null) }
        assertEquals("address_not_allowed", refused.errorOrNull()?.code)
        assertTrue(pairHttp.sent.isEmpty())
        r.http.healthyNode()
        val ok = r.engine.pair("192.168.1.20", " 123456 ", null) { HttpFactsBookGateway(pairHttp, null) }
        assertIs<FbResult.Ok<Unit>>(ok)
        assertNull(pairHttp.sent.single().headers["Authorization"])
        assertEquals("fresh-token", r.secrets.values["oc.factsbook.p1"])
        assertEquals("http://192.168.1.20:8787", FactsBookLink.decode(r.store.link(profile)!!.encode())!!.baseUrl)
        assertTrue(r.engine.state.value.paired)
    }

    @Test
    fun `B-FB-010 a busy node is asked again briefly and an error body never becomes text`() = runTest {
        val r = rig()
        r.engine.open(profile)
        r.http.on("GET /v1/status", 429, """{"error":{"code":"slow_down","message":"secret words","retry_after_s":3}}""")
        val gateway = r.engine.node!!
        val result = gateway.status()
        assertEquals(4, r.http.sent.size) // one try and three more
        val error = result.errorOrNull()!!
        assertEquals("slow_down", error.code)
        assertEquals(3, error.retryAfterSeconds)
        assertFalse("secret words" in error.toString())
    }

    @Test
    fun `B-FB-011 areas are read page by page like every other list`() = runTest {
        val r = rig()
        r.engine.open(profile)
        val area = """{"slug":"health","storage":"sensitive","review":"first","share":{"default":"none"},"brief":false,"export":false}"""
        r.http.on("GET /v1/areas", 200, """{"items":[$area],"next_cursor":null}""")
        val areas = r.engine.node!!.areas().valueOrNull()!!
        assertEquals("health", areas.single().slug)
        assertTrue(areas.single().sensitiveStorage)
        assertEquals("200", r.http.sent.single().query["limit"])
    }

    @Test
    fun `B-FB-012 a reminder is sent in the node's structured form and read back`() = runTest {
        val r = rig()
        r.engine.open(profile)
        val timing = ReminderTiming(ReminderRepeat.Weekly, kotlinx.datetime.LocalDateTime(2000, 1, 1, 8, 5), weekdays = setOf(3, 1))
        r.http.on(
            "POST /v1/reminders/parse",
            200,
            """{"what":"Gym","zone":"vault","locale":"en","ambiguities":[],"preview_digest":"dg","schedule":{"id":"sch_0","when":{"every":"week","on":["mon","wed"],"at":"08:05"},"zone":"vault"}}""",
        )
        val node = r.engine.node as FactsBookReminders
        val preview = node.previewReminder(" Gym ", timing, "vault", "en").valueOrNull()!!
        val sent = Json.parseToJsonElement(r.http.sent.single().body!!).jsonObject
        assertEquals("Gym", sent["what"]!!.jsonPrimitive.content)
        val text = Json.parseToJsonElement(sent["text"]!!.jsonPrimitive.content).jsonObject
        assertEquals(timing.toWhen(), text["when"])
        assertTrue(text["id"]!!.jsonPrimitive.content.startsWith("sch_"))
        assertTrue(preview.confirmable)
        assertEquals(timing, ReminderTiming.fromWhen(preview.schedule!!.`when`))
    }

    @Test
    fun `B-FB-013 pausing sends the revision the person saw`() = runTest {
        val r = rig()
        r.engine.open(profile)
        r.http.on(
            "GET /v1/reminders",
            200,
            """{"items":[{"id":"rem_1","what":"Gym","rev":4,"schedule":{"id":"sch_0","when":{"every":"day","at":"07:00"},"zone":"vault"}}]}""",
        )
        r.http.on("PATCH /v1/reminders/rem_1", 200, """{"status":"ok","id":"rem_1","rev":5}""")
        val node = r.engine.node as FactsBookReminders
        val reminder = node.reminders().valueOrNull()!!.single()
        node.setReminderPaused(reminder, true)
        val body = Json.parseToJsonElement(r.http.sent.last().body!!).jsonObject
        assertEquals("4", body["base_rev"]!!.jsonPrimitive.content)
        assertEquals("true", body["paused"]!!.jsonPrimitive.content)
        assertTrue(r.http.sent.last().headers["Idempotency-Key"]!!.startsWith("app-"))
    }
}
