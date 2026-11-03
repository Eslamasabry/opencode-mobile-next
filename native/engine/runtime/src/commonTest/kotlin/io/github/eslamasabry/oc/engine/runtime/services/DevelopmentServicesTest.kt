package io.github.eslamasabry.oc.engine.runtime.services

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/development_services_test.dart and test/development_service_gateway_test.dart. */
class DevelopmentServicesTest {
    private val sample =
        DevelopmentService(
            id = "vite",
            name = "Shopfront preview",
            command = "npm run dev",
            directory = "/work/shopfront",
            url = "http://shopfront.lan:5173",
        )

    private val settings = MapSettings()
    private val shells = FakeShells()
    private var current = true
    private var writable = true
    private var supported = true
    private var resolver: (suspend () -> ManagedShellPort?)? = null
    private var tokens = 0
    private val store = DevelopmentServiceStore(settings, ProfileId("p"), canWrite = { writable })

    private fun newModel(): DevelopmentServices =
        DevelopmentServices(
            store = store,
            directory = sample.directory,
            workspace = null,
            resolveShells = { resolver?.invoke() ?: shells },
            isCurrent = { current },
            supported = { supported },
            newOwnerToken = { "owner${++tokens}" },
        )

    private fun services(block: suspend TestScope.(DevelopmentServices) -> Unit) = runTest {
        val model = newModel()
        model.reload()
        block(model)
    }

    private suspend fun saved(): List<DevelopmentService> = store.load().getOrNull()!!

    private fun DevelopmentServices.only(): DevelopmentService = state.value.services.single()

    // ---- B-LINUX-060

    @Test
    fun `B-LINUX-060 v2 creates a location-scoped owned shell and keeps only ownership metadata`() {
        val request = ManagedShellWire.startRequest("npm run dev", "/work/shopfront", "ws_preview", "owner")!!
        assertEquals("POST", request.method)
        assertEquals("/api/shell", request.path)
        assertEquals("/work/shopfront", request.query["location[directory]"])
        assertEquals("ws_preview", request.query["location[workspace]"])
        assertEquals(
            buildJsonObject {
                put("command", "npm run dev")
                put("cwd", "/work/shopfront")
                put("timeout", 0)
                putJsonObject("metadata") { put("ocDevelopmentServiceOwner", "owner") }
            },
            request.body,
        )
        val shell =
            ManagedShellWire.decode(
                buildJsonObject {
                    put("id", "sh_test")
                    put("command", "npm run dev")
                    put("cwd", "/work/shopfront")
                    put("status", "running")
                    putJsonObject("time") { put("started", 1234) }
                    putJsonObject("metadata") {
                        put("ocDevelopmentServiceOwner", "owner")
                        put("unrelated", "not mapped")
                    }
                },
            )!!
        assertEquals("owner", shell.ownerToken)
        assertEquals("/work/shopfront", shell.directory)
        assertEquals(1234L, shell.startedAtMillis)
        assertEquals(ManagedShellStatus.Running, shell.status)
        assertEquals(null, ManagedShellWire.startRequest(" ", "/work", null, "owner"), "a blank command is not sent")
        assertEquals(null, ManagedShellWire.decode(JsonObject(mapOf("id" to JsonPrimitive("x")))), "no start time")
    }

    @Test
    fun `B-LINUX-060 start logs stop restart control only receipts from this feature`() = services { model ->
        model.register(sample)
        model.start("vite")
        assertEquals(DevelopmentServiceStatus.Running, model.status(model.only()))
        shells.shells["sh_foreign"] =
            ManagedShell("sh_foreign", "npm run dev", ManagedShellStatus.Running, startedAtMillis = 99)
        model.readLogs("vite")
        assertTrue(model.state.value.logs["vite"]!!.contains("VITE ready"))
        model.restart("vite")
        assertEquals(listOf("sh_1"), shells.stops)
        assertEquals(2, shells.starts)
        assertTrue("sh_foreign" in shells.shells)
        model.stop("vite")
        assertEquals(DevelopmentServiceStatus.Stopped, model.status(model.only()))
        assertTrue(model.state.value.logs.isEmpty())
        val receipt = saved().single().run!!
        assertEquals("sh_2", receipt.shellId)
        assertTrue(receipt.stopped)
    }

    // ---- B-LINUX-061

    @Test
    fun `B-LINUX-061 registration survives restart and never starts a command`() = services { model ->
        model.register(sample)
        assertEquals(0, shells.starts)
        assertEquals("npm run dev", saved().single().command)
        assertEquals(DevelopmentServiceStatus.NotStarted, model.status(model.only()))
        val again = newModel()
        again.reload()
        assertEquals(listOf(sample), again.state.value.services, "a fresh model lists it again")
        assertEquals(DevelopmentServiceStatus.NotStarted, again.status(again.only()))
        assertEquals(0, shells.starts, "nothing runs on its own after a restart")
        assertEquals("oc.developmentServices.p", store.key, "per-profile key the sweep finds")
    }

    @Test
    fun `B-LINUX-061 unsupported transport saves configuration without shell calls`() = services { model ->
        supported = false
        model.register(sample)
        val start = model.start("vite")
        assertTrue(start is Outcome.Failed)
        assertEquals(0, shells.starts)
        assertFalse(model.canStart(model.only()))
        model.remove("vite")
        assertTrue(saved().isEmpty())
    }

    @Test
    fun `B-LINUX-061 corrupt saved configuration is not silently overwritten`() = services { model ->
        settings.values[store.key] = "{broken"
        model.reload()
        model.register(sample)
        assertFalse(model.state.value.readable)
        assertEquals("{broken", settings.values[store.key])
        assertTrue(store.save(sample) is Outcome.Failed, "the store itself refuses to replace it")
        assertEquals("{broken", settings.values[store.key])
    }

    // ---- B-LINUX-062

    @Test
    fun `B-LINUX-062 lost create response stays unknown and recovers only through its ownership nonce`() =
        services { model ->
            model.register(sample)
            shells.loseCreateResponse = true
            model.start("vite")
            assertEquals(DevelopmentServiceStatus.Unknown, model.status(model.only()))
            assertFalse(model.canStart(model.only()))
            assertNull(saved().single().run!!.shellId)
            model.refresh()
            assertEquals("sh_1", model.only().run!!.shellId)
            assertEquals(DevelopmentServiceStatus.Running, model.status(model.only()))
            assertEquals(1, shells.starts)
        }

    @Test
    fun `B-LINUX-062 a same-ID shell with foreign ownership cannot authorize stop or logs`() = services { model ->
        model.register(sample)
        model.start("vite")
        val original = shells.shells.getValue("sh_1")
        shells.shells["sh_1"] = original.copy(ownerToken = "foreign")
        model.stop("vite")
        assertTrue(shells.stops.isEmpty())
        assertEquals(DevelopmentServiceStatus.Unknown, model.status(model.only()))
        model.readLogs("vite")
        assertTrue(shells.cursors.isEmpty())
    }

    @Test
    fun `B-LINUX-062 unknown runs need explicit local forgetting and removal never kills`() = services { model ->
        model.register(sample)
        shells.loseCreateResponse = true
        model.start("vite")
        model.start("vite")
        assertEquals(1, shells.starts)
        model.forgetRun("vite")
        assertTrue(model.canStart(model.only()))
        model.remove("vite")
        assertTrue(shells.stops.isEmpty())
    }

    // ---- B-LINUX-063

    @Test
    fun `B-LINUX-063 a failed refresh does not leave running claimed`() = services { model ->
        model.register(sample)
        model.start("vite")
        shells.failReads = true
        model.refresh()
        assertEquals(DevelopmentServiceStatus.Unknown, model.status(model.only()))
        assertNotNull(model.state.value.error)
    }

    @Test
    fun `B-LINUX-063 a scope change during resolution prevents the POST and the pending receipt`() = services { model ->
        model.register(sample)
        resolver = {
            current = false
            shells
        }
        model.start("vite")
        assertEquals(0, shells.starts)
        assertNull(saved().single().run)
    }

    @Test
    fun `B-LINUX-063 a scope change during stop revalidation prevents the DELETE`() = services { model ->
        model.register(sample)
        model.start("vite")
        val gate = CompletableDeferred<Unit>()
        shells.infoGate = gate
        val stopping = async { model.stop("vite") }
        runCurrent()
        current = false
        model.invalidateRuntime()
        gate.complete(Unit)
        stopping.await()
        assertTrue(shells.stops.isEmpty())
    }

    @Test
    fun `B-LINUX-063 a late create response is saved for recovery after leaving the screen`() = services { model ->
        model.register(sample)
        val gate = CompletableDeferred<Unit>()
        shells.createGate = gate
        val starting = async { model.start("vite") }
        runCurrent()
        current = false
        model.invalidateRuntime()
        gate.complete(Unit)
        starting.await()
        assertEquals("sh_1", saved().single().run!!.shellId)
    }

    @Test
    fun `B-LINUX-063 profile deletion blocks a late receipt without recreating the saved data`() = services { model ->
        model.register(sample)
        val gate = CompletableDeferred<Unit>()
        shells.createGate = gate
        val starting = async { model.start("vite") }
        runCurrent()
        writable = false
        settings.values.remove(store.key)
        gate.complete(Unit)
        starting.await()
        assertFalse(store.key in settings.values)
    }

    // ---- B-LINUX-064

    @Test
    fun `B-LINUX-064 log reading requests only a bounded byte tail and strips control sequences`() =
        services { model ->
            model.register(sample)
            model.start("vite")
            shells.output = "x".repeat(90_000) + "\u001b[31mTAIL\u001b[0m"
            model.readLogs("vite")
            assertEquals(0L, shells.cursors.first())
            assertTrue(shells.cursors.last() > 24_000)
            val log = model.state.value.logs.getValue("vite")
            assertTrue(log.length <= 65_536)
            assertTrue(log.endsWith("TAIL"), log.takeLast(20))
        }
}
