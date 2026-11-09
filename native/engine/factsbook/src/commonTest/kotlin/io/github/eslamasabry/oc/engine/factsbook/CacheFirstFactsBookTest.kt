package io.github.eslamasabry.oc.engine.factsbook

import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.testing.TestClock
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement

/** Cache-first FactsBook (owner rule, 2026-11-09): every page opens on the node's last answers, then reads live. */
class CacheFirstFactsBookTest {
    private val clock = TestClock(Instant.parse("2026-11-08T10:00:00Z"))

    private class MemoryKept : FactsBookKept {
        val answers = LinkedHashMap<String, JsonElement>()

        override suspend fun read(profile: String, key: String): JsonElement? = answers["$profile|$key"]

        override suspend fun keep(profile: String, key: String, body: JsonElement) {
            answers["$profile|$key"] = body
        }
    }

    private fun TestScope.engine(http: FakeHttp, kept: MemoryKept, link: String = FLUTTER_LINK): FactsBookEngine {
        val (kv, _) = flutterPrefs(mapOf("flutter.oc.factsbookNode.p1" to RawPref.Str(link)))
        val store = FactsBookStore(kv, MemorySecrets(mapOf("oc.factsbook.p1" to "t")), laneApp)
        lateinit var engine: FactsBookEngine
        engine =
            FactsBookEngine(
                store,
                gatewayFor = { l, token, signer ->
                    HttpFactsBookGateway(KeptFactsBookHttp(http, kept, { engine.state.value.profileId }, l.baseUrl), token, Random(1), retryDelayMs = 1, owner = signer)
                },
                outboxFor = { id ->
                    object : FactsBookOutboxStore {
                        override suspend fun read(): String? = store.readOutbox(id)

                        override suspend fun write(value: String?): Boolean = store.writeOutbox(id, value)
                    }
                },
                clock = clock,
                signing = FactsBookSigning({ n -> ByteArray(n) { (it + 3).toByte() } }),
                kept = kept,
            )
        return engine
    }

    private fun FakeHttp.healthy() {
        on("GET /v1/status", 200, STATUS_JSON)
        on("GET /v1/review", 200, """{"items":[{"rvw":"rvw_1","class":"fact_add","state":"proposed","digest":"d","proposal":{"text":"x"}}]}""")
        on("GET /v1/inbox/pending", 200, """{"items":[]}""")
        on("GET /v1/things", 200, THINGS_JSON)
        on("GET /v1/rules", 200, """{"items":[{"id":"rul_1","rev":4,"updated_at":"2026-11-07T10:00:00Z","definition":{"v":1,"name":"Clinic","enabled":true,
          "source":{"client_id":"cli_01","kind":"share","instance":"manual-share"},"template":{"id":"appointment","version":2,"locale":"en"},
          "area":"health","contains_all":["clinic"],"outcomes":[{"kind":"reminder"}],"limit_per_day":5}}]}""")
        on("GET /v1/events", 200, """{"items":[]}""")
        on("GET /v1/trigger-firings", 200, """{"items":[]}""")
        on("GET /v1/reminder-proposals", 200, """{"items":[]}""")
        on("GET /v1/offers", 200, """{"items":[]}""")
    }

    private suspend fun TestScope.visitOnce(kept: MemoryKept) {
        val first = engine(FakeHttp().apply { healthy() }, kept)
        first.open("p1")
        first.refresh()
    }

    @Test
    fun `CF-60 the first frame of the overview, Needs you and Status has the live data with no request sent`() = runTest {
        val kept = MemoryKept()
        visitOnce(kept)
        val away = FakeHttp().apply { unreachable = true }
        val reopened = engine(away, kept)
        reopened.open("p1")
        val s = reopened.state.value
        assertEquals(FactsBookLoad.Ready, s.load)
        assertNotNull(s.status)
        assertEquals(1, s.needsYouCount)
        assertEquals(listOf("per_01"), s.recent.map { it.id })
        assertTrue(away.sent.isEmpty(), "the kept read sends nothing")
    }

    @Test
    fun `CF-61 the first frame of the Triggers pages has the kept rules, and a failed live read keeps them`() = runTest {
        val kept = MemoryKept()
        visitOnce(kept)
        val away = FakeHttp().apply { unreachable = true }
        val reopened = engine(away, kept)
        reopened.open("p1")
        assertEquals(TriggersLoad.Ready, reopened.triggers.state.value.load)
        assertEquals(listOf("rul_1"), reopened.triggers.state.value.rules.map { it.id })
        reopened.refresh()
        assertTrue(reopened.state.value.error!!.unreachable)
        assertEquals(FactsBookLoad.Ready, reopened.state.value.load)
        assertEquals(listOf("rul_1"), reopened.triggers.state.value.rules.map { it.id })
    }

    @Test
    fun `CF-62 a page's own read (things, a thing) answers from what was kept`() = runTest {
        val kept = MemoryKept()
        val first = engine(FakeHttp().apply { healthy(); on("GET /v1/things/per_01", 200, """{"id":"per_01","title":"Sara","kind":"person","updated_at":"2026-10-10T08:00:00Z","facts":[]}""") }, kept)
        first.open("p1")
        first.node!!.thing("per_01")
        val reopened = engine(FakeHttp().apply { unreachable = true }, kept)
        reopened.open("p1")
        assertEquals("Sara", (reopened.keptNode!!.thing("per_01") as FbResult.Ok).value.title)
        assertEquals(listOf("per_01", "doc_01"), (reopened.keptNode!!.things() as FbResult.Ok).value.map { it.id })
    }

    @Test
    fun `CF-63 another node's answers never show, and with nothing kept the first load is as before`() = runTest {
        val kept = MemoryKept()
        visitOnce(kept)
        val other = FLUTTER_LINK.replace("127.0.0.1:8787", "127.0.0.1:9999")
        val http = FakeHttp().apply { unreachable = true }
        val elsewhere = engine(http, kept, other)
        elsewhere.open("p1")
        assertEquals(FactsBookLoad.Idle, elsewhere.state.value.load)
        assertNull(elsewhere.state.value.status)
        assertEquals(TriggersLoad.Idle, elsewhere.triggers.state.value.load)
        val fresh = engine(http, MemoryKept())
        fresh.open("p1")
        assertEquals(FactsBookLoad.Idle, fresh.state.value.load)
    }

    @Test
    fun `CF-64 only display reads are kept, never the control history, a search or an export`() = runTest {
        val kept = MemoryKept()
        val http = FakeHttp().apply {
            healthy()
            on("GET /v1/control", 200, """{"items":[]}""")
            on("GET /v1/search", 200, """{"items":[]}""")
            on("GET /v1/inbox/c1/export", 200, """{"item":"c1"}""")
        }
        val e = engine(http, kept)
        e.open("p1")
        e.refresh()
        e.node!!.control()
        e.node!!.search("sara")
        e.node!!.exportCapture("c1")
        assertTrue(kept.answers.keys.none { "control" in it || "search" in it || "export" in it }, kept.answers.keys.toString())
        assertTrue(kept.answers.keys.any { "/v1/status" in it })
    }
}
