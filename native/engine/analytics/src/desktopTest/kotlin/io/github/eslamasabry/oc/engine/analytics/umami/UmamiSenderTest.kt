package io.github.eslamasabry.oc.engine.analytics.umami

import io.github.eslamasabry.oc.engine.analytics.DeviceClass
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.analytics.health.NoOpTelemetryVendor
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryVendor
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryService
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.ProductScreen
import io.github.eslamasabry.oc.engine.analytics.product.productSchema
import java.util.concurrent.Executor
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UmamiSenderTest {
    private class Lane : Executor {
        val work = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { work.add(command) }
        fun run() { while (work.isNotEmpty()) work.removeFirst().run() }
    }
    private class Store : UmamiStore {
        var value: String? = null
        override suspend fun read(): Outcome<String?> = Outcome.Ok(value)
        override suspend fun replace(value: String?): Outcome<Unit> { this.value = value; return Outcome.Ok(Unit) }
    }
    private class Transport : UmamiTransport {
        val batches = mutableListOf<List<UmamiEvent>>()
        var fail = false
        var receipt: Set<Int>? = null
        var onExecute: (() -> Unit)? = null
        var cancelled = false
        override fun prepare(events: List<UmamiEvent>): UmamiCall = object : UmamiCall {
            override fun cancel() { cancelled = true }
            override suspend fun execute(): Outcome<Set<Int>> {
                onExecute?.invoke()
                if (cancelled) return Outcome.Failed(Failure.Network(io.github.eslamasabry.oc.core.common.NetworkWhy.Unknown))
                batches += events
                return if (fail) Outcome.Failed(Failure.Server(503)) else Outcome.Ok(receipt ?: events.indices.toSet())
            }
        }
    }
    private class Rig(configured: Boolean = true, bytes: Int = UmamiSender.MAX_BYTES) {
        val lane = Lane()
        val store = Store()
        val transport = Transport()
        var now = 1_000_000L
        val sender = UmamiSender(
            website = "00000000-0000-4000-8000-000000000001",
            build = UmamiBuild("3.0.1-test.10081", "10081"),
            device = { UmamiDevice("ar", "browser", 16, DeviceClass.Phone) },
            now = { now },
            store = store,
            transport = transport.takeIf { configured },
            maxBytes = bytes,
            executor = lane,
        )
        fun on() { sender.setUsage(true); lane.run() }
        fun event(name: String, props: Map<String, String> = emptyMap()) = sender.event(name, props)
    }

    @Test
    fun `B-180 a session sends app open first then its first page with individual times`() {
        val r = Rig(); r.on()
        r.event("app_open", mapOf("kind" to "cold")); r.now += 1000
        r.event("screen_view", mapOf("screen" to "conversations")); r.now += 1000
        r.event("screen_view", mapOf("screen" to "new_chat")); r.now += 1000
        r.event("starter_used", mapOf("starter" to productSchema.getValue("starter_used").getValue("starter").first()))
        r.lane.run()
        val events = r.transport.batches.single()
        assertEquals(listOf("app_open", null, null, "starter_used"), events.map { it.payload.name })
        assertEquals(listOf("/home", "/home", "/new-conversation", "/new-conversation"), events.map { it.payload.url })
        assertEquals(listOf(1000L, 1001L, 1002L, 1003L), events.map { it.payload.timestamp })
        assertTrue(events.all { it.payload.tag == "10081" && it.payload.data["version"] == "3.0.1-test.10081" && it.payload.data["build"] == "10081" })
    }

    @Test
    fun `B-180 first consent during an open app starts a session without replaying earlier events`() {
        val r = Rig()
        r.event("screen_view", mapOf("screen" to "chat"))
        r.on(); r.event("screen_view", mapOf("screen" to "settings")); r.lane.run()
        assertEquals(listOf("app_open", null), r.transport.batches.single().map { it.payload.name })
        assertEquals(2, r.transport.batches.single().size)
    }

    @Test
    fun `B-180 granting consent in a conversation pairs both receivers with the current page`() = runTest {
        val r = Rig()
        val google = mutableListOf<String>()
        val primary = object : TelemetryVendor by NoOpTelemetryVendor {
            override fun event(name: String, props: Map<String, String>) { google += name }
        }
        val telemetry = TelemetryService(UsageTelemetryVendor(primary, r.sender, UmamiBuild("3.0.1", "10081")),
            available = true, scope = this)
        telemetry.track(ProductEvent.ScreenView, mapOf("screen" to ProductScreen.Settings))
        telemetry.track(ProductEvent.ScreenView, mapOf("screen" to ProductScreen.Chat))
        r.lane.run()
        assertEquals(0, r.sender.queued)
        assertTrue(google.isEmpty() && r.transport.batches.isEmpty())
        telemetry.setConsent(usage = true, crashes = false)
        telemetry.track(ProductEvent.MemoryPressure, mapOf("level" to "low"))
        r.lane.run()
        assertEquals(listOf("app_open", "screen_view", "memory_pressure"), google)
        val events = r.transport.batches.single()
        assertEquals(listOf("app_open", null, "memory_pressure"), events.map { it.payload.name })
        assertEquals(listOf("/home", "/conversation", "/conversation"), events.map { it.payload.url })
    }

    @Test
    fun `B-180 every event maps its exact allowed keys and drops forbidden additions`() {
        val device = UmamiDevice("en-US", "browser", 16, DeviceClass.Phone)
        for ((name, fields) in productSchema) {
            val props = fields.mapValues { it.value.first() }
            val event = assertNotNull(umamiEvent(name, props + mapOf("prompt" to "private", "id" to "secret", "model_key" to "private"),
                umamiPages.getValue("chat"), UmamiSource("website", UmamiBuild("3.0.1", "10081"), device), 2000))
            assertEquals("event", event.type, name)
            assertEquals(fields.keys + setOf("version", "build", "os_major", "device_class"), event.payload.data.keys, name)
            val wire = UmamiCodec.json.parseToJsonElement(UmamiCodec.batch(listOf(event))).jsonArray.single().jsonObject
            assertEquals(setOf("type", "payload"), wire.keys, name)
            val payload = wire.getValue("payload").jsonObject
            val expected = setOf("website", "hostname", "language", "url", "title", "tag", "timestamp", "data", "salt") +
                if (name == "screen_view") emptySet() else setOf("name")
            assertEquals(expected, payload.keys, name)
            // Umami rotates the anonymous visitor hash on this word (v3.5.0 request-schema.ts `salt`, crypto.ts getSalt).
            assertEquals("month", payload.getValue("salt").jsonPrimitive.content, "$name pins the monthly hash period")
            assertEquals(fields.keys + setOf("version", "build", "os_major", "device_class"), payload.getValue("data").jsonObject.keys, name)
            assertEquals(name.takeUnless { it == "screen_view" }, event.payload.name, name)
            val page = if (name == "screen_view" || name == "jank") umamiPages.getValue(props.getValue("screen")) else umamiPages.getValue("chat")
            assertEquals(page.url, event.payload.url, name)
            assertEquals("opencode-mobile.app", event.payload.hostname, name)
            assertFalse(UmamiCodec.batch(listOf(event)).contains("private"), name)
        }
        assertEquals(ProductEvent.entries.map { it.wire }.toSet(), productSchema.keys)
        val listed = UmamiSource("website", UmamiBuild("3.0.1", "10081"), device)
        assertNull(umamiEvent("not_listed", emptyMap(), umamiPages.getValue("chat"), listed, 2000))
    }

    @Test
    fun `B-180 forbidden values in listed fields and arbitrary page addresses cannot enter the payload`() {
        val device = UmamiDevice("en-US", "browser", 16, DeviceClass.Phone)
        val build = UmamiBuild("3.0.1", "10081")
        val source = UmamiSource("website", build, device)
        val mapped = umamiEvent("chat_turn", mapOf("agent" to "private/model", "server_kind" to "private.example", "outcome" to "user text"),
            umamiPages.getValue("chat"), source, 1000)!!
        assertEquals(setOf("version", "build", "os_major", "device_class"), mapped.payload.data.keys)
        assertEquals("/conversation", mapped.payload.url)
        val chat = umamiPages.getValue("chat")
        assertNull(umamiEvent("app_open", emptyMap(), UmamiPage("/conversation/private", "Private"), source, 1000))
        assertNull(umamiEvent("screen_view", mapOf("screen" to "private.example"), chat, source, 1000))
        assertNull(umamiEvent("app_open", emptyMap(), chat, source, -1000))
        assertNull(umamiEvent("app_open", emptyMap(), chat, source, 2_147_483_648_000))
    }

    @Test
    fun `B-180 every screen is a page view with a fixed address and title`() {
        assertEquals(ProductScreen.entries.map { it.wire }.toSet(), umamiPages.keys)
        assertEquals(umamiPages.size, umamiPages.values.map { it.url }.toSet().size)
        for ((screen, page) in umamiPages) {
            val r = Rig(); r.on(); r.event("screen_view", mapOf("screen" to screen)); r.lane.run()
            val view = r.transport.batches.single().last().payload
            assertNull(view.name)
            assertEquals(page.url, view.url); assertEquals(page.title, view.title)
        }
    }

    @Test
    fun `B-180 off makes no request and keeps no event even with a configured sender`() {
        val r = Rig(); r.sender.setUsage(false)
        r.event("app_open", mapOf("kind" to "cold")); r.lane.run()
        assertEquals(0, r.sender.queued); assertNull(r.store.value); assertTrue(r.transport.batches.isEmpty())
    }

    @Test
    fun `B-180 turning off immediately clears pending events and then the disk queue`() {
        val r = Rig(); r.transport.fail = true; r.on()
        r.event("app_open", mapOf("kind" to "cold")); r.lane.run()
        assertNotNull(r.store.value); assertEquals(1, r.sender.queued)
        r.sender.setUsage(false)
        assertEquals(0, r.sender.queued)
        r.lane.run(); assertNull(r.store.value)
        r.now += UmamiSender.MAX_BACKOFF_MS; r.on()
        assertEquals(1, r.transport.batches.size)
    }

    @Test
    fun `B-180 turning off cancels a prepared request before it sends`() {
        val r = Rig(); r.on()
        r.transport.onExecute = { r.sender.setUsage(false) }
        r.event("app_open", mapOf("kind" to "cold")); r.lane.run()
        assertTrue(r.transport.cancelled); assertTrue(r.transport.batches.isEmpty()); assertNull(r.store.value)
    }

    @Test
    fun `B-180 a dead server backs off without a retry timer and remembers backoff across restarts`() {
        val r = Rig(); r.transport.fail = true; r.on()
        r.event("app_open", mapOf("kind" to "cold")); r.lane.run()
        repeat(20) { r.event("memory_pressure", mapOf("level" to "low")); r.lane.run() }
        assertEquals(1, r.transport.batches.size)
        val restored = Rig(); restored.store.value = r.store.value; restored.transport.fail = true; restored.on()
        assertTrue(restored.transport.batches.isEmpty())
        r.now += 29_999L
        r.event("memory_pressure", mapOf("level" to "low")); r.lane.run()
        assertEquals(1, r.transport.batches.size, "no request before the full first backoff")
        r.now += 1
        r.event("memory_pressure", mapOf("level" to "low")); r.lane.run()
        assertEquals(2, r.transport.batches.size); assertTrue(r.lane.work.isEmpty())
    }

    @Test
    fun `B-180 repeated failures double the delay to the six hour cap without scheduling retries`() {
        val r = Rig(); r.transport.fail = true; r.on()
        var wanted = 30_000L
        repeat(16) {
            r.event("app_open", mapOf("kind" to "warm")); r.lane.run()
            val saved = UmamiCodec.json.decodeFromString<StoredOutbox>(r.store.value!!)
            assertEquals(wanted, saved.retryAtMs - r.now)
            assertTrue(r.lane.work.isEmpty(), "backoff schedules no wake-up")
            r.now = saved.retryAtMs
            wanted = (wanted * 2).coerceAtMost(21_600_000L)
        }
        assertEquals(16, r.transport.batches.size)
        assertEquals(21_600_000L, wanted)
    }

    @Test
    fun `B-180 count and byte bounds hold while offline`() {
        val r = Rig(bytes = 1600); r.transport.fail = true; r.on()
        repeat(600) { r.event("app_open", mapOf("kind" to "warm")) }
        assertTrue(r.sender.queued <= 256)
        assertTrue(r.sender.queuedBytes <= 1600)
        assertEquals(1, r.lane.work.size, "producer work is coalesced and bounded too")
        r.lane.run()
        val count = Rig(); count.transport.fail = true; count.on()
        repeat(600) { count.event("memory_pressure", mapOf("level" to "low")) }
        assertTrue(count.sender.queued <= 256)
        assertTrue(count.sender.queuedBytes <= 256 * 1024)
        count.lane.run()
        assertEquals("app_open", count.transport.batches.first().first().payload.name, "overflow cannot orphan an unsent session")
    }

    @Test
    fun `B-180 events older than three days expire before a later connection`() {
        val r = Rig(); r.transport.fail = true; r.on()
        r.event("app_open", mapOf("kind" to "cold")); r.lane.run()
        r.now += 259_200_001L; r.transport.fail = false
        r.event("app_open", mapOf("kind" to "warm")); r.lane.run()
        assertEquals(1, r.transport.batches.last().size, "the expired session must be gone before delivery")
        assertEquals("warm", r.transport.batches.last().single().payload.data["kind"])
    }

    @Test
    fun `B-180 no address configured means no request or queue`() {
        val r = Rig(configured = false); r.on(); r.event("app_open", mapOf("kind" to "cold")); r.lane.run()
        assertEquals(0, r.sender.queued); assertTrue(r.transport.batches.isEmpty()); assertNull(r.store.value)
        assertFalse(UmamiHttp.configured("", ""))
        assertFalse(UmamiHttp.configured("https://example.com", ""))
        assertFalse(UmamiHttp.configured("http://example.com", "00000000-0000-4000-8000-000000000001"))
    }

    @Test
    fun `B-180 a partial batch retries only its rejected events in original order`() {
        val r = Rig(); r.transport.receipt = setOf(0); r.on()
        r.event("app_open", mapOf("kind" to "cold")); r.event("screen_view", mapOf("screen" to "chat")); r.lane.run()
        assertEquals(1, r.sender.queued)
        r.now += UmamiSender.FIRST_BACKOFF_MS; r.transport.receipt = null
        r.event("memory_pressure", mapOf("level" to "low")); r.lane.run()
        assertEquals(listOf(null, "memory_pressure"), r.transport.batches.last().map { it.payload.name })
    }

    @Test
    fun `B-180 an identical event arriving during a request is kept until its own acknowledgement`() {
        val r = Rig(); r.on()
        r.event("memory_pressure", mapOf("level" to "low"))
        r.transport.onExecute = {
            r.transport.onExecute = null
            r.event("memory_pressure", mapOf("level" to "low"))
        }
        r.lane.run()
        assertEquals(1, r.sender.queued, "equal payload and time are still two different event occurrences")
        r.now += UmamiSender.MIN_SEND_GAP_MS
        r.event("memory_pressure", mapOf("level" to "critical")); r.lane.run()
        assertEquals(listOf("low", "critical"), r.transport.batches.last().map { it.payload.data["level"] })
    }

    @Test
    fun `B-180 the existing vendor boundary sends the identical event feed to both receivers`() {
        val r = Rig()
        val google = mutableListOf<String>()
        val primary = object : TelemetryVendor by NoOpTelemetryVendor {
            override fun event(name: String, props: Map<String, String>) { google += name }
        }
        val vendor = UsageTelemetryVendor(primary, r.sender, UmamiBuild("3.0.1", "10081"))
        vendor.setCollection(true, false)
        vendor.event("app_open", mapOf("kind" to "cold"))
        vendor.event("screen_view", mapOf("screen" to "chat"))
        r.lane.run()
        assertEquals(listOf("app_open", "screen_view"), google)
        assertEquals(listOf("app_open", null), r.transport.batches.single().map { it.payload.name })
    }

    @Test
    fun `B-180 late consent establishes the same first opening for both receivers only once`() {
        val r = Rig()
        val google = mutableListOf<String>()
        val primary = object : TelemetryVendor by NoOpTelemetryVendor {
            override fun event(name: String, props: Map<String, String>) { google += name }
        }
        val vendor = UsageTelemetryVendor(primary, r.sender, UmamiBuild("3.0.1", "10081"))
        vendor.setCollection(false, false)
        vendor.event("screen_view", mapOf("screen" to "chat"))
        assertTrue(google.isEmpty())
        vendor.setCollection(true, false)
        vendor.event("screen_view", mapOf("screen" to "chat"))
        vendor.event("app_open", mapOf("kind" to "cold"))
        r.lane.run()
        assertEquals(listOf("app_open", "screen_view"), google)
        assertEquals(listOf("app_open", null), r.transport.batches.single().map { it.payload.name })
    }

    @Test
    fun `B-180 Google receives the same feed and sets the exact build property once while on`() {
        val properties = mutableListOf<Map<String, String>>()
        val events = mutableListOf<String>()
        val primary = object : TelemetryVendor by NoOpTelemetryVendor {
            override fun event(name: String, props: Map<String, String>) { events += name }
            override fun setUserProperties(values: Map<String, String>) { properties += values }
        }
        val vendor = UsageTelemetryVendor(primary, null, UmamiBuild("3.0.1-test.10081", "10081"))
        vendor.setCollection(false, false); assertTrue(properties.isEmpty())
        vendor.setCollection(true, false); vendor.setCollection(true, true)
        assertEquals(listOf(mapOf("app_build" to "10081")), properties)
        vendor.event("app_open", mapOf("kind" to "cold")); assertEquals(listOf("app_open"), events)
        vendor.setCollection(false, false); vendor.setCollection(true, false)
        assertEquals(2, properties.size)
    }
    @Test
    fun `B-435 previous exit reaches Google and Umami from one consented product call`() = runTest {
        val r = Rig()
        val google = mutableListOf<Pair<String, Map<String, String>>>()
        val primary = object : TelemetryVendor by NoOpTelemetryVendor {
            override fun event(name: String, props: Map<String, String>) { google += name to props }
        }
        val service = TelemetryService(UsageTelemetryVendor(primary, r.sender, UmamiBuild("3.0.1", "10081")), true, scope = this)
        service.start(io.github.eslamasabry.oc.engine.analytics.health.TelemetryConsent(true, false),
            object : io.github.eslamasabry.oc.engine.analytics.health.TelemetryConsentStore {
                override suspend fun write(consent: io.github.eslamasabry.oc.engine.analytics.health.TelemetryConsent) = true
            }, io.github.eslamasabry.oc.engine.analytics.health.MemoryTelemetryReportStore())
        val platform = object : io.github.eslamasabry.oc.engine.analytics.health.HealthPlatform {
            override suspend fun deviceContext(): io.github.eslamasabry.oc.engine.analytics.health.HealthDeviceContext? = null
            override suspend fun exits(limit: Int) = listOf(io.github.eslamasabry.oc.engine.analytics.health.HealthExit(4, 5000, true))
            override fun coldStartMs(): Long? = null
            override val appVersion = "3.0.1"
            override val declaredBuildKind: String? = null
        }
        val marks = object : io.github.eslamasabry.oc.engine.analytics.health.HealthExitMarks {
            var at = 0L
            override fun lastExitAtMs() = 0L
            override suspend fun setLastExitAtMs(atMs: Long) = Unit
            override fun lastAnalyticsExitAtMs() = at
            override suspend fun setLastAnalyticsExitAtMs(atMs: Long): Boolean { at = atMs; return true }
        }
        io.github.eslamasabry.oc.engine.analytics.health.HealthTelemetry(service, platform, marks, backgroundScope).start()
        runCurrent()
        r.lane.run()
        assertEquals(listOf("app_exit" to mapOf("exit_reason" to "crash", "foreground" to "yes")), google.filter { it.first == "app_exit" })
        val own = r.transport.batches.flatten().filter { it.payload.name == "app_exit" }
        assertEquals(1, own.size)
        assertEquals(mapOf("exit_reason" to "crash", "foreground" to "yes", "version" to "3.0.1-test.10081", "build" to "10081", "os_major" to "16", "device_class" to "phone"), own.single().payload.data)
    }

}
