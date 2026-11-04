@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo.usage

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.FakeSockets
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.toJson
import io.github.eslamasabry.oc.protocol.paseo.transport
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Ported from test/paseo_usage_test.dart. */
class PaseoUsageTest {
    private val bidi = '‮'
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val nowMs = now.toEpochMilliseconds()

    private fun claude(): Map<String, Any?> = mapOf(
        "providerId" to "claude",
        "displayName" to "Claude",
        "status" to "available",
        "planLabel" to "Max 20x (secret-plan)",
        "sourceLabel" to "oauth: user@example.com",
        "fetchedAt" to now.toString(),
        "error" to null,
        "windows" to listOf(
            mapOf("id" to "seven_day_opus", "label" to "Weekly Opus", "usedPct" to 10, "resetsAt" to "2026-10-15T00:00:00Z"),
            mapOf("id" to "weekly", "label" to "Weekly", "remainingPct" to 70.4, "resetsAt" to "2026-10-14T00:00:00Z"),
            mapOf("id" to "five_hour", "label" to "5 hours", "usedPct" to 38, "resetsAt" to "2026-10-12T15:00:00Z"),
            mapOf("id" to "no_numbers", "label" to "Nothing"),
        ),
        "balances" to listOf(mapOf("id" to "b", "label" to "x", "unit" to "usd")),
        "details" to listOf(mapOf("id" to "d", "label" to "x", "value" to "secret-detail")),
        "futureField" to mapOf("a" to 1),
    )

    private fun providers(): MutableList<Any?> = mutableListOf(
        claude(),
        mapOf(
            "providerId" to "zai",
            "displayName" to "Z.ai$bidi evil\n name that is much too long to print",
            "status" to "available",
            "windows" to listOf(mapOf("id" to "tokens", "label" to "Tokens", "usedPct" to 120)),
        ),
        mapOf("providerId" to "copilot", "displayName" to "Copilot", "status" to "error", "error" to "Bearer sk-secret-token failed", "windows" to emptyList<Any>()),
        mapOf("providerId" to "Bad Id!", "displayName" to "x", "status" to "available"),
        "junk",
    )

    private fun payload(list: List<Any?> = providers(), extra: Map<String, Any?> = emptyMap()): JsonObject =
        toJson(mapOf("requestId" to "m1_1", "fetchedAt" to now.toString(), "providers" to list) + extra) as JsonObject

    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    @Test
    fun `B-AGENT-189 keeps numbers, prefers 5-hour then weekly, drops free text`() {
        val readings = parseProviderUsageList(payload(), nowMs).ok()
        assertEquals(listOf("claude", "zai"), readings.map { it.providerId })
        val claude = readings.first()
        assertEquals("Claude", claude.displayName)
        assertEquals(listOf(18000, 604800, null), claude.windows.map { it.durationSeconds })
        assertEquals(listOf(62, 70, 90), claude.windows.map { it.remainingPercent })
        assertEquals(Instant.parse("2026-10-12T15:00:00Z").toEpochMilliseconds(), claude.windows.first().resetsAtMs)
        assertEquals("Weekly Opus", claude.windows.last().label)
        val zai = readings.last()
        assertEquals(0, zai.windows.single().remainingPercent)
        assertTrue(zai.displayName.length <= 24)
        assertFalse(bidi in zai.displayName)
        assertFalse('\n' in zai.displayName)
        val dump = readings.flatMap { r -> listOf(r.providerId, r.displayName) + r.windows.map { it.label } }.joinToString("|")
        for (secret in listOf("secret", "Max 20x", "user@", "Bearer")) assertFalse(secret in dump, secret)
    }

    @Test
    fun `B-AGENT-189 ignores unknown statuses and fields`() {
        val list = providers()
        list += mapOf(
            "providerId" to "grok",
            "displayName" to "Grok",
            "status" to "brand-new-status",
            "windows" to listOf(mapOf("id" to "x", "label" to "x", "usedPct" to 1)),
        )
        assertEquals(2, parseProviderUsageList(payload(list, mapOf("extra" to 1)), nowMs).ok().size)
    }

    @Test
    fun `B-AGENT-189 malformed payloads fail without text`() {
        for (bad in listOf(null, JsonPrimitive("text"), jsonOf("providers" to "x"), jsonOf("error" to "provider_usage_list_failed"))) {
            val failed = assertIs<Outcome.Failed>(parseProviderUsageList(bad, nowMs), "$bad")
            assertEquals(Failure.UnreadableReply(), failed.failure)
        }
        assertEquals(emptyList(), parseProviderUsageList(jsonOf("providers" to JsonArray(emptyList())), nowMs).ok())
    }

    @Test
    fun `B-AGENT-189 a future fetch time is clamped to now`() {
        val list = providers()
        list[0] = claude() + mapOf("fetchedAt" to "2030-01-01T00:00:00Z")
        assertEquals(nowMs, parseProviderUsageList(payload(list), nowMs).ok().first().fetchedAtMs)
    }

    // ---- B-AGENT-190 the reader

    @Test
    fun `B-AGENT-190 asks for provider usage list and closes`() = runTest {
        val daemon = FakeDaemon()
        daemon.handlers["provider.usage.list.request"] = { "provider.usage.list.response" to payload() }
        val reader = PaseoProviderUsageReader(transport(FakeSockets(daemon)), TestClock(now))
        val readings = reader.read().ok()
        reader.close()
        assertEquals(listOf("provider.usage.list.request"), daemon.sent.map { it["type"].let { t -> (t as JsonPrimitive).content } })
        assertEquals(2, readings.size)
        assertTrue(daemon.closed)
    }

    @Test
    fun `B-AGENT-190 an error reply, a timeout and a dropped link are unavailable`() = runTest {
        // An error reply.
        val refusing = FakeDaemon()
        refusing.handlers["provider.usage.list.request"] = { "rpc_error" to jsonOf("error" to "denied Bearer x") }
        val first = PaseoProviderUsageReader(transport(FakeSockets(refusing)), TestClock(now)).read()
        assertEquals(Failure.ComputerDidNotAnswer(), assertIs<Outcome.Failed>(first).failure)
        // No answer at all: the reader's timeout.
        val silent = FakeDaemon()
        val slow = PaseoProviderUsageReader(transport(FakeSockets(silent)), TestClock(now), timeoutMs = 5)
        val pending = async { slow.read() }
        advanceTimeBy(10)
        runCurrent()
        assertEquals(Failure.ComputerDidNotAnswer(), assertIs<Outcome.Failed>(pending.await()).failure)
        // The link drops while it waits.
        val dropping = FakeDaemon()
        val dropped = PaseoProviderUsageReader(transport(FakeSockets(dropping)), TestClock(now))
        val waiting = async { dropped.read() }
        runCurrent()
        dropping.drop()
        runCurrent()
        assertEquals(Failure.ComputerDidNotAnswer(), assertIs<Outcome.Failed>(waiting.await()).failure)
    }

    @Test
    fun `B-AGENT-190 a reply that is not a provider list is invalid`() = runTest {
        val daemon = FakeDaemon()
        daemon.handlers["provider.usage.list.request"] = { "provider.usage.list.response" to jsonOf() }
        val outcome = PaseoProviderUsageReader(transport(FakeSockets(daemon)), TestClock(now)).read()
        assertEquals(Failure.UnreadableReply(), assertIs<Outcome.Failed>(outcome).failure)
    }

    // ---- B-AGENT-191 the monitor

    private class Store : UsageOptInStore {
        val values = LinkedHashMap<String, Boolean>()

        override suspend fun get(key: String) = values[key]

        override suspend fun set(key: String, value: Boolean): Boolean {
            values[key] = value
            return true
        }

        override suspend fun remove(key: String): Boolean {
            values.remove(key)
            return true
        }
    }

    private class Widget : UsageWidgetSink {
        val docs = LinkedHashMap<String, UsageWidgetDoc>()
        var redraws = 0

        override suspend fun record(profileId: String, doc: UsageWidgetDoc) {
            docs[profileId] = doc
            redraws++
        }

        override suspend fun clear(profileId: String) {
            docs.remove(profileId)
        }
    }

    private class Reader(val run: suspend () -> Outcome<List<ProviderUsageReading>>) : ProviderUsageReader {
        var closed = 0

        override suspend fun read() = run()

        override suspend fun close() {
            closed++
        }
    }

    private inner class World {
        val store = Store()
        val widget = Widget()
        val readers = mutableListOf<Reader>()
        var run: suspend () -> Outcome<List<ProviderUsageReading>> = { parseProviderUsageList(payload(), nowMs) }
        val monitor = PaseoUsageMonitor(
            profiles = { listOf(UsageProfile("p1", "Desk", paseo = true), UsageProfile("oc", "Plain", paseo = false)) },
            store = store,
            widget = widget,
            createReader = { Reader { run() }.also(readers::add) },
            clock = TestClock(now),
        )
    }

    @Test
    fun `B-AGENT-191 is off by default and reads nothing`() = runTest {
        val w = World()
        w.monitor.load()
        assertFalse(w.monitor.enabled("p1"))
        assertEquals(PaseoUsageStatus.Off, w.monitor.statusFor("p1"))
        w.monitor.refresh()
        assertTrue(w.readers.isEmpty())
        assertTrue(w.store.values.isEmpty())
        assertTrue(w.widget.docs.isEmpty())
    }

    @Test
    fun `B-AGENT-191 only Paseo servers can be switched on`() = runTest {
        val w = World()
        assertEquals(listOf("p1"), w.monitor.paseoProfiles.map { it.id })
        assertFalse(w.monitor.setEnabled("oc", true))
        assertNull(w.store.values[PaseoUsageMonitor.key("oc")])
    }

    @Test
    fun `B-AGENT-191 switching on reads, writes the widget rows and keeps no free text`() = runTest {
        val w = World()
        assertTrue(w.monitor.setEnabled("p1", true))
        assertEquals(true, w.store.values["oc.paseoUsage.p1"])
        assertEquals(PaseoUsageStatus.Current, w.monitor.statusFor("p1"))
        val doc = w.widget.docs.getValue("p1")
        assertEquals("Desk", doc.name)
        assertTrue(doc.rows.keys.containsAll(listOf("claude", "zai")))
        val claude = doc.rows.getValue("claude")
        assertEquals("Claude", claude.name)
        assertEquals(UsageWidgetWindow(18000, 62, Instant.parse("2026-10-12T15:00:00Z").toEpochMilliseconds()), claude.windows.first())
        val raw = doc.toString()
        for (secret in listOf("secret", "Max 20x", "example.com", "Bearer")) assertFalse(secret in raw, secret)
        assertTrue(w.readers.all { it.closed == 1 })
        assertTrue(w.widget.redraws > 0)
    }

    @Test
    fun `B-AGENT-191 says when it last read and how many providers had numbers`() = runTest {
        val w = World()
        assertNull(w.monitor.lastReadAtMs("p1"))
        w.monitor.setEnabled("p1", true)
        assertEquals(nowMs, w.monitor.lastReadAtMs("p1"))
        assertTrue(w.monitor.shownProviders("p1") > 0)
        // Signed out: the daemon answers, but with no plan numbers.
        w.run = { Outcome.Ok(emptyList()) }
        w.monitor.refresh()
        assertEquals(PaseoUsageStatus.Current, w.monitor.statusFor("p1"))
        assertEquals(0, w.monitor.shownProviders("p1"))
        w.monitor.setEnabled("p1", false)
        assertNull(w.monitor.lastReadAtMs("p1"))
        assertNull(w.widget.docs["p1"])
        assertNull(w.store.values["oc.paseoUsage.p1"])
    }

    @Test
    fun `B-AGENT-191 a failed read keeps the last numbers and says unavailable`() = runTest {
        val w = World()
        w.monitor.setEnabled("p1", true)
        val before = w.widget.docs["p1"]
        w.run = { Outcome.Failed(Failure.ComputerDidNotAnswer()) }
        w.monitor.refresh()
        assertEquals(PaseoUsageStatus.Unavailable, w.monitor.statusFor("p1"))
        assertEquals(before, w.widget.docs["p1"])
        assertEquals(nowMs, w.monitor.lastReadAtMs("p1"))
    }

    @Test
    fun `B-AGENT-191 providers the daemon stops reporting disappear`() = runTest {
        val w = World()
        w.monitor.setEnabled("p1", true)
        w.run = { parseProviderUsageList(payload(listOf(claude())), nowMs) }
        w.monitor.refresh()
        assertEquals(setOf("claude"), w.widget.docs.getValue("p1").rows.keys)
    }

    @Test
    fun `B-AGENT-191 paused reads nothing and deletion drops the numbers`() = runTest {
        val w = World()
        w.monitor.setEnabled("p1", true)
        w.monitor.setRuntime(foreground = false, backgroundAllowed = false)
        val reads = w.readers.size
        w.monitor.refresh()
        assertEquals(reads, w.readers.size)
        assertEquals(PaseoUsageStatus.Paused, w.monitor.statusFor("p1"))
        w.monitor.removeProfile("p1")
        assertNull(w.widget.docs["p1"])
        assertFalse(w.monitor.enabled("p1"))
    }
}
