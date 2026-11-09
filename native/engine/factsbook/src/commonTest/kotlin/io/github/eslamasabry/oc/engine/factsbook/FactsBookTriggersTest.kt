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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Flutter test/factsbook/factsbook_triggers_test.dart: rules, tests, events and what they proposed. */
class FactsBookTriggersTest {
    private val clock = TestClock(Instant.parse("2026-11-08T10:00:00Z"))
    private val ownerLink = FLUTTER_LINK.dropLast(1) + ""","owner_device":"dev_1"}"""

    private fun TestScope.engine(http: FakeHttp, owner: Boolean = true): FactsBookEngine {
        val (kv, _) = flutterPrefs(mapOf("flutter.oc.factsbookNode.p1" to RawPref.Str(if (owner) ownerLink else FLUTTER_LINK)))
        val secrets = MemorySecrets(mapOf("oc.factsbook.p1" to "t"))
        val store = FactsBookStore(kv, secrets, laneApp)
        return FactsBookEngine(
            store,
            gatewayFor = { _, token, signer -> HttpFactsBookGateway(http, token, Random(1), retryDelayMs = 1, owner = signer) },
            outboxFor = { id ->
                object : FactsBookOutboxStore {
                    override suspend fun read(): String? = store.readOutbox(id)

                    override suspend fun write(value: String?): Boolean = store.writeOutbox(id, value)
                }
            },
            clock = clock,
            signing = FactsBookSigning({ n -> ByteArray(n) { (it + 3).toByte() } }),
        )
    }

    private val rule =
        """{"id":"rul_1","rev":4,"updated_at":"2026-11-07T10:00:00Z","definition":{"v":1,"name":"Clinic","enabled":true,
          "source":{"client_id":"cli_01","kind":"share","instance":"manual-share"},"template":{"id":"appointment","version":2,"locale":"en"},
          "area":"health","contains_all":["clinic"],"outcomes":[{"kind":"reminder"},{"kind":"fact","thing":"per_01","base_rev":2}],"limit_per_day":5}}"""

    private fun FakeHttp.triggerNode() {
        on("GET /v1/rules", 200, """{"items":[$rule,{"id":"rul_2","rev":1,"updated_at":"2026-11-07T10:00:00Z","deleted":true,"definition":{}}]}""")
        on(
            "GET /v1/events",
            200,
            """{"items":[{"id":"evt_1","source":{"kind":"share"},"trust":"third_party_untrusted","sens":"normal","captured_at":"2026-11-08T09:00:00Z",
              "processing":{"state":"evaluated","reason":"matched"},"firings":["trg_1"],"claims":{"sender":"Clinic"}}]}""",
        )
        on(
            "GET /v1/trigger-firings",
            200,
            """{"items":[{"id":"trg_1","event_id":"evt_1","rule_id":"rul_1","template":{"id":"appointment"},"at":"2026-11-08T09:00:01Z","state":"proposed","outcomes":["rpr_1"]},
              {"id":"trg_0","event_id":"evt_0","rule_id":"rul_1","template":{"id":"appointment"},"at":"2026-10-01T09:00:00Z","state":"capped","outcomes":[]}]}""",
        )
        on(
            "GET /v1/reminder-proposals",
            200,
            """{"items":[{"id":"rpr_1","rev":2,"event_id":"evt_1","firing_id":"trg_1","area":"health","digest":"dg","state":"proposed",
              "preview":{"what":"Clinic","zone":"vault","locale":"en","preview_digest":"pd"}},
              {"id":"rpr_0","rev":1,"digest":"d0","state":"confirmed","preview":{"what":"Old","preview_digest":"p0"}}]}""",
        )
        on("GET /v1/offers", 200, """{"items":[]}""")
    }

    @Test
    fun `B-FB-060 a node without triggers is unavailable, never an error`() = runTest {
        val http = FakeHttp()
        val e = engine(http)
        e.open("p1")
        // The shipped matrix lists triggers; a node that answers not found is unavailable.
        e.triggers.refresh()
        assertEquals(TriggersLoad.Unavailable, e.triggers.state.value.load)
        assertNull(e.triggers.state.value.error)
    }

    @Test
    fun `B-FB-061 the lists skip deleted rules and settled proposals and count a rule's week`() = runTest {
        val http = FakeHttp().apply { triggerNode() }
        val e = engine(http)
        e.open("p1")
        e.triggers.refresh()
        val s = e.triggers.state.value
        assertEquals(TriggersLoad.Ready, s.load)
        assertEquals(listOf("rul_1"), s.rules.map { it.id })
        assertEquals(listOf("rpr_1"), s.proposals.map { it.id })
        assertEquals("Clinic", s.eventById("evt_1")!!.claims.sender)
        assertEquals("trg_1", s.firingOfOutcome("rpr_1")!!.id)
        // The capped firing is older than a week.
        assertEquals(RuleActivity(proposed = 1, ignored = 0, offers = 0), s.activity("rul_1", clock.now()))
        assertEquals(listOf(TriggerSourceKind.Share), s.rulesBySource.keys.toList())
    }

    @Test
    fun `B-FB-062 pausing replaces the rule at the revision seen, signed, keeping a fact outcome exactly`() = runTest {
        val http = FakeHttp().apply { triggerNode() }
        http.on("PUT /v1/rules/rul_1", 200, rule.replace("\"rev\":4", "\"rev\":5").replace("\"enabled\":true", "\"enabled\":false"))
        val e = engine(http)
        e.open("p1")
        e.triggers.refresh()
        val saved = assertIs<FbResult.Ok<Rule>>(e.triggers.setPaused(e.triggers.state.value.rules.single(), paused = true)).value
        assertFalse(saved.definition.enabled)
        val body = Json.parseToJsonElement(http.sent.first { it.method == "PUT" }.body!!).jsonObject
        assertEquals("4", body["base_rev"]!!.jsonPrimitive.content)
        val sent = body["rule"]!!.jsonObject
        assertEquals("false", sent["enabled"]!!.jsonPrimitive.content)
        assertEquals("false", sent["auto_start"]!!.jsonPrimitive.content)
        assertEquals("per_01", sent["outcomes"]!!.jsonArray[1].jsonObject["thing"]!!.jsonPrimitive.content)
        assertTrue(body.containsKey("proof"))
    }

    @Test
    fun `B-FB-063 trying a draft sends the rule and the sample, writes nothing and needs no proof`() = runTest {
        val http = FakeHttp().apply { triggerNode() }
        http.on("POST /v1/rules/test", 200, """{"matched":true,"reason":"matched","summary":"Library visit","when":"2026-11-12T09:00"}""")
        val e = engine(http, owner = false)
        e.open("p1")
        val draft = RuleDefinition.read(Json.parseToJsonElement(rule).jsonObject["definition"]!!.jsonObject)
        val sample = TriggerSample(TriggerSourceKind.Share, "Appointment: Library visit | 2026-11-12T09:00", "health", "en", "manual-share")
        val result = assertIs<FbResult.Ok<RuleTestResult>>(e.triggers.testDraft(draft, sample)).value
        assertTrue(result.matched)
        assertEquals("2026-11-12T09:00", result.whenText)
        val body = Json.parseToJsonElement(http.sent.single().body!!).jsonObject
        assertFalse(body.containsKey("proof"))
        assertEquals("manual-share", body["sample"]!!.jsonObject["source_instance"]!!.jsonPrimitive.content)
        assertTrue(RuleRules.check(draft).isEmpty())
        assertEquals(listOf(RuleProblem.NameEmpty, RuleProblem.NoOutcome), RuleRules.check(draft.copy(name = " ", outcomes = emptyList())))
        assertEquals(listOf("a", "b", "ج"), RuleRules.splitWords("a, b،B\nج,, a"))
    }

    @Test
    fun `B-FB-064 confirming a proposed reminder is signed and bound to its revision and digest`() = runTest {
        val http = FakeHttp().apply { triggerNode() }
        http.on("POST /v1/reminder-proposals/rpr_1/decision", 200, """{"status":"ok","id":"rpr_1"}""")
        val e = engine(http)
        e.open("p1")
        e.triggers.refresh()
        val proposal = e.triggers.state.value.proposals.single()
        assertIs<FbResult.Ok<MutationResult>>(e.triggers.decideProposal(proposal, ProposalDecision.Confirm))
        val body = Json.parseToJsonElement(http.sent.first { it.path.endsWith("/decision") }.body!!).jsonObject
        assertEquals("confirm", body["decision"]!!.jsonPrimitive.content)
        assertEquals("2", body["rev"]!!.jsonPrimitive.content)
        assertEquals("dg", body["digest"]!!.jsonPrimitive.content)
        assertTrue(body.containsKey("proof"))
    }

    @Test
    fun `B-FB-077 with rules on a capture becomes an event, a share signed as ordinary text, and never also goes to the inbox`() = runTest {
        val http = FakeHttp().apply { triggerNode() }
        http.on("POST /v1/events", 200, """{"event":{"id":"evt_9","source":{"kind":"share"},"captured_at":"2026-11-08T10:00:00Z","evidence_ref":"itm_9"}}""")
        val e = engine(http)
        e.open("p1")
        val note = e.capture(CaptureRequest(CaptureKind.Note, "Appointment: Library visit | 2026-11-12T09:00"), rulesLook = true)
        assertEquals("itm_9", assertIs<CaptureOutcome.SavedAtNode>(note).itemId)
        val first = Json.parseToJsonElement(http.sent.first { it.path == "/v1/events" }.body!!).jsonObject
        assertFalse(first.containsKey("proof"))
        assertTrue(http.sent.first { it.path == "/v1/events" }.headers["Idempotency-Key"]!!.startsWith("evt-"))
        e.capture(CaptureRequest(CaptureKind.Share, "موعد: زيارة | 2026-11-12T09:00"), rulesLook = true)
        val share = Json.parseToJsonElement(http.sent.last { it.path == "/v1/events" }.body!!).jsonObject
        assertEquals("true", share["classify_normal"]!!.jsonPrimitive.content)
        assertEquals("ar", share["locale"]!!.jsonPrimitive.content)
        assertTrue(share.containsKey("proof"))
        assertFalse(http.sent.any { it.path == "/v1/inbox" })

        // The node will not take the event: the capture is saved to the inbox as before.
        http.on("POST /v1/events", 422, """{"error":{"code":"bad_request"}}""")
        http.on("POST /v1/inbox", 200, """{"status":"saved","item_id":"itm_10"}""")
        assertIs<CaptureOutcome.SavedAtNode>(e.capture(CaptureRequest(CaptureKind.Note, "Buy milk"), rulesLook = true))
        assertTrue(http.sent.any { it.path == "/v1/inbox" })
    }

    @Test
    fun `the rule editor offers the templates the node lists, never bill due`() {
        val listed =
            FactsBookCapabilities.read(
                Json.parseToJsonElement("""{"milestone":"B5","supported":{"local_trigger_slice":true},"disabled":{},"trigger_templates_v1":["appointment","delivery"]}""")
                    .jsonObject
            )
        assertEquals(setOf("appointment", "delivery"), listed.triggerTemplates)
        val own = FactsBookCapabilities.read(Json.parseToJsonElement("""{"milestone":"B5","supported":{},"disabled":{}}""").jsonObject)
        assertTrue(own.triggerTemplates.isEmpty())
        assertFalse(TriggerTemplateId.BillDue in TriggerTemplateId.offered)
    }
}
