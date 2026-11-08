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
                gatewayFor = { _, token, signer -> HttpFactsBookGateway(http, token, Random(1), retryDelayMs = 1, owner = signer) },
                outboxFor = { id ->
                    object : FactsBookOutboxStore {
                        override suspend fun read(): String? = store.readOutbox(id)

                        override suspend fun write(value: String?): Boolean = store.writeOutbox(id, value)
                    }
                },
                clock = clock,
                random = Random(2),
                signing = FactsBookSigning({ n -> ByteArray(n) { (it + 7).toByte() } }),
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

    private val ownerLink = FLUTTER_LINK.dropLast(1) + ""","owner_device":"dev_1"}"""

    @Test
    fun `B-FB-019 an approval is signed by this phone's owner key and bound to the digest and revisions shown`() = runTest {
        val r = rig(prefs = mapOf("flutter.oc.factsbookNode.p1" to RawPref.Str(ownerLink)))
        r.engine.open(profile)
        assertTrue(r.engine.state.value.canApprove)
        // The seed was made on first use and kept under Flutter's name.
        assertEquals(64, r.secrets.values.getValue("oc.factsbookOwner.p1").length)
        r.http.healthyNode()
        r.http.on("POST /v1/review/rvw_1/decision", 200, """{"status":"ok","id":"rvw_1"}""")
        val item =
            ReviewItem.read(
                Json.parseToJsonElement("""{"rvw":"rvw_1","class":"fact_add","state":"proposed","digest":"dg","preconditions":{"per_01":3},"proposal":{"text":"x"}}""")
                    .jsonObject
            )
        assertIs<FbResult.Ok<MutationResult>>(r.engine.decide(item, ReviewDecision.Approve))
        val sent = r.http.sent.first { it.path == "/v1/review/rvw_1/decision" }
        val body = Json.parseToJsonElement(sent.body!!).jsonObject
        assertEquals("approve", body["decision"]!!.jsonPrimitive.content)
        assertEquals("dg", body["digest"]!!.jsonPrimitive.content)
        assertEquals("3", body["preconditions"]!!.jsonObject["per_01"]!!.jsonPrimitive.content)
        val proof = body["proof"]!!.jsonObject
        assertEquals("dev_1", proof["signer"]!!.jsonPrimitive.content)
        // The node's check: the canonical envelope of the body without the proof, under this phone's public key.
        val envelope =
            kotlinx.serialization.json.JsonObject(
                mapOf(
                    "vault" to kotlinx.serialization.json.JsonPrimitive("v_main"),
                    "client" to kotlinx.serialization.json.JsonPrimitive("cli_01"),
                    "method" to kotlinx.serialization.json.JsonPrimitive("POST"),
                    "path" to kotlinx.serialization.json.JsonPrimitive("/v1/review/rvw_1/decision"),
                    "body" to kotlinx.serialization.json.JsonObject(body - "proof"),
                    "nonce" to proof["nonce"]!!,
                    "authenticated_at" to proof["authenticated_at"]!!,
                )
            )
        val key = bytesOfHex(r.engine.ownerPublicKeyHex()!!)!!
        assertTrue(Ed25519.verify(key, Jcs.bytes(envelope), bytesOfHex(proof["sig"]!!.jsonPrimitive.content)!!))
        // The list was read again after the decision.
        assertTrue(r.http.sent.last().path != "/v1/review/rvw_1/decision")
    }

    @Test
    fun `B-FB-032 without an owner device nothing owner-only is sent, and a stale decision reads the list again`() = runTest {
        val r = rig()
        r.engine.open(profile)
        assertFalse(r.engine.state.value.canApprove)
        val item = ReviewItem("rvw_1", "fact_add", ReviewState.Proposed, "dg", "x")
        assertEquals(FactsBookError.OWNER_REQUIRED, assertIs<FbResult.Err>(r.engine.decide(item, ReviewDecision.Reject)).error.code)
        assertTrue(r.http.sent.isEmpty())
        assertEquals(FactsBookError.OWNER_REQUIRED, assertIs<FbResult.Err>(r.engine.node!!.setAreaShare(sampleArea, AreaShare(readByDefault = true))).error.code)
        assertTrue(r.http.sent.isEmpty())

        val owner = rig(prefs = mapOf("flutter.oc.factsbookNode.p1" to RawPref.Str(ownerLink)))
        owner.engine.open(profile)
        owner.http.healthyNode()
        owner.http.on("POST /v1/review/rvw_1/decision", 409, """{"error":{"code":"stale_approval","request_id":"req_9"}}""")
        val stale = assertIs<FbResult.Err>(owner.engine.decide(item, ReviewDecision.Approve)).error
        assertTrue(stale.stale)
        assertEquals("/v1/capabilities", owner.http.sent.last().path) // the refresh ran to its end
    }

    private val sampleArea = Area("money", sensitiveStorage = false, reviewFirst = true, share = AreaShare(), brief = true, export = false, notify = "generic")

    @Test
    fun `B-FB-033 sharing an area sends the whole area against the current policy revision, signed`() = runTest {
        val r = rig(prefs = mapOf("flutter.oc.factsbookNode.p1" to RawPref.Str(ownerLink)))
        r.engine.open(profile)
        r.http.on("GET /v1/policy", 200, """{"policy_version":12}""")
        r.http.on("PATCH /v1/areas/money", 200, """{"status":"ok","id":"money","rev":13}""")
        assertIs<FbResult.Ok<MutationResult>>(r.engine.node!!.setAreaShare(sampleArea, AreaShare(readByDefault = true)))
        val body = Json.parseToJsonElement(r.http.sent.last().body!!).jsonObject
        assertEquals("12", body["base_rev"]!!.jsonPrimitive.content)
        val area = body["area"]!!.jsonObject
        assertEquals("read", area["share"]!!.jsonObject["default"]!!.jsonPrimitive.content)
        assertEquals("first", area["review"]!!.jsonPrimitive.content)
        assertTrue(body.containsKey("proof"))
    }

    @Test
    fun `B-FB-034 a settings change sends back everything else untouched against the revision seen`() = runTest {
        val r = rig(prefs = mapOf("flutter.oc.factsbookNode.p1" to RawPref.Str(ownerLink)))
        r.engine.open(profile)
        r.http.on(
            "GET /v1/config",
            200,
            """{"rev":7,"vault_zone":"Africa/Cairo","lock_timeout_minutes":15,"budget":{"daily":3},"brief":{"cap_per_day":5,"time":"08:00","quiet_hours":"22:00-07:00"}}""",
        )
        r.http.on("PATCH /v1/config", 200, """{"status":"ok","id":"config","rev":8}""")
        val node = r.engine.node!!
        val settings = node.settings().valueOrNull()!!
        assertEquals(5, settings.brief.capPerDay)
        node.setBriefCap(settings, 2)
        val body = Json.parseToJsonElement(r.http.sent.last().body!!).jsonObject
        assertEquals("7", body["base_rev"]!!.jsonPrimitive.content)
        val sent = body["settings"]!!.jsonObject
        assertEquals("2", sent["brief"]!!.jsonObject["cap_per_day"]!!.jsonPrimitive.content)
        assertEquals("22:00-07:00", sent["brief"]!!.jsonObject["quiet_hours"]!!.jsonPrimitive.content)
        assertEquals("3", sent["budget"]!!.jsonObject["daily"]!!.jsonPrimitive.content)
        node.setHomeZone(settings, "Europe/London")
        val zone = Json.parseToJsonElement(r.http.sent.last().body!!).jsonObject["settings"]!!.jsonObject
        assertEquals("Europe/London", zone["vault_zone"]!!.jsonPrimitive.content)
    }
}
