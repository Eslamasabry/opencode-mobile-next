package io.github.eslamasabry.oc.engine.setupassist

import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/setup_registry_test.dart. */
class SetupRegistryTest {
    private class Transport : RegistryHttp {
        val requests = mutableListOf<RegistryRequest>()
        var status = 200
        var body =
            """{"servers":[{"server":{"name":"org.example/weather","version":"1.0.0","description":"Weather tools",
               "remotes":[{"type":"streamable-http","url":"https://tools.example/mcp"}]}}]}"""
        var gate: CompletableDeferred<Unit>? = null
        var entered: CompletableDeferred<Unit>? = null
        var fail = false

        override suspend fun get(request: RegistryRequest, cancellation: RegistryCancellation): RegistryHttpReply {
            requests += request
            entered?.complete(Unit)
            gate?.await()
            if (fail) return RegistryHttpReply.Failed // "private upstream diagnostics" never crosses the boundary
            val bytes = body.encodeToByteArray()
            if (bytes.size > request.maxBytes) return RegistryHttpReply.TooLarge
            // A redirect is a status here: the transport never follows it (followRedirects = false).
            return RegistryHttpReply.Body(status, bytes)
        }
    }

    private val redactor = Redactor()
    private val clock = FixedClock()
    private val prefs = FakePrefs()
    private val transport = Transport()
    private val client = SetupRegistryClient(transport, redactor)
    private var store = SetupRegistryStore(prefs, "one", client, redactor, clock)

    private fun storedOptIn(): Boolean =
        (json(prefs.getString(store.storageKey)!!).jsonObject["optedIn"] as JsonPrimitive).content == "true"

    private fun TestScope.gateDisk() {
        prefs.gateKey = store.storageKey
        prefs.gate = CompletableDeferred()
        prefs.entered = CompletableDeferred()
    }

    @Test
    fun `B-STATE-133 opt-out is persisted after a refresh write already in flight`() = runTest {
        store.load()
        store.setOptIn(true)
        gateDisk()
        val refresh = async { store.refresh() }
        prefs.entered.await()
        val disable = async { store.setOptIn(false) }
        runCurrent()
        prefs.gate!!.complete(Unit)
        refresh.await()
        disable.await()
        assertFalse(storedOptIn())
        assertFalse(store.snapshot.optedIn)
    }

    @Test
    fun `B-STATE-133 clear waits for a pending refresh write then removes it`() = runTest {
        store.setOptIn(true)
        gateDisk()
        val refresh = async { store.refresh() }
        prefs.entered.await()
        val clear = async { store.clear() }
        runCurrent()
        prefs.gate!!.complete(Unit)
        refresh.await()
        clear.await()
        assertFalse(prefs.containsKey("oc.setupRegistry.one"))
        assertTrue(store.snapshot.entries.isEmpty())
    }

    @Test
    fun `B-STATE-133 awaiting close drains writes before profile deletion`() = runTest {
        store.setOptIn(true)
        gateDisk()
        val refresh = async { store.refresh() }
        prefs.entered.await()
        var closed = false
        val closing = launch { store.close(); closed = true }
        runCurrent()
        assertFalse(closed)
        prefs.gate!!.complete(Unit)
        refresh.await()
        closing.join()
        assertTrue(closed)
        // The profile sweep: every oc.<what>.<id> key of the doomed id.
        ProfileKeyRule.scopedKeys("one", prefs.keys(), setOf("one")).forEach { prefs.remove(it) }
        assertFalse(prefs.containsKey("oc.setupRegistry.one"))
    }

    @Test
    fun `B-SEC-168 by default load, refresh and offline mode never touch the internet`() = runTest {
        store.load()
        store.refresh()
        assertEquals(SetupRegistryStatus.Disabled, store.snapshot.status)
        store.setOptIn(true)
        assertTrue(transport.requests.isEmpty())
        store.refresh(online = false)
        assertTrue(transport.requests.isEmpty())
        assertEquals(SetupRegistryStatus.Offline, store.snapshot.status)
    }

    @Test
    fun `B-SEC-168 an explicit refresh uses the fixed unauthenticated origin and caches safely`() = runTest {
        val states = mutableListOf<SetupRegistryStatus>()
        val watching = launch(UnconfinedTestDispatcher(testScheduler)) { store.changes.collect { states += it.status } }
        store.setOptIn(true)
        store.refresh()
        watching.cancel()
        val loading = states.indexOf(SetupRegistryStatus.Loading)
        assertTrue(loading >= 0 && states.subList(loading, states.size).contains(SetupRegistryStatus.Ready))
        val request = transport.requests.single()
        assertEquals("https://registry.modelcontextprotocol.io/v0.1/servers", request.url)
        assertEquals(mapOf("limit" to "100", "version" to "latest"), request.query)
        assertFalse(request.followRedirects)
        assertEquals(0, request.maxRedirects)
        assertFalse(request.headers.keys.any { it.equals("Authorization", ignoreCase = true) })
        assertEquals("org.example/weather", store.snapshot.entries.single().name)
        assertEquals(
            obj("""{"type":"remote","url":"https://tools.example/mcp","enabled":false}"""),
            store.snapshot.entries.single().connectableRemote!!.toMcpConfig().valueOrNull(),
        )
        assertTrue(prefs.containsKey("oc.setupRegistry.one"))
        assertNotNull(store.snapshot.cachedAt)
    }

    @Test
    fun `B-SEC-168 B-STATE-133 restart preserves consent and cache, opt-out and offline do not fetch`() = runTest {
        store.setOptIn(true)
        store.refresh()
        val reopened = SetupRegistryStore(prefs, "one", client, redactor, clock)
        reopened.load()
        assertEquals(1, reopened.snapshot.entries.size)
        reopened.refresh(online = false)
        assertEquals(SetupRegistryStatus.Offline, reopened.snapshot.status)
        reopened.setOptIn(false)
        reopened.refresh()
        assertEquals(1, reopened.snapshot.entries.size)
        assertEquals(1, transport.requests.size)
        reopened.close()
    }

    @Test
    fun `B-STATE-133 the profile cache is isolated and the deletion sweep removes it`() = runTest {
        store.setOptIn(true)
        store.refresh()
        val other = SetupRegistryStore(prefs, "two", client, redactor, clock)
        other.load()
        assertFalse(other.snapshot.optedIn)
        assertTrue(other.snapshot.entries.isEmpty())
        other.setOptIn(true)
        val swept = ProfileKeyRule.scopedKeys("one", prefs.keys(), setOf("one", "two"))
        assertTrue(store.storageKey in swept)
        swept.forEach { prefs.remove(it) }
        assertFalse(prefs.containsKey(store.storageKey))
        assertTrue(prefs.containsKey(other.storageKey))
        other.close()
    }

    @Test
    fun `B-STATE-133 clear forgets consent and cache without changing other preferences`() = runTest {
        prefs.setString("oc.unrelated.two", "kept")
        store.setOptIn(true)
        store.refresh()
        store.clear()
        assertEquals(SetupRegistryStatus.Disabled, store.snapshot.status)
        assertTrue(store.snapshot.entries.isEmpty())
        assertFalse(prefs.containsKey(store.storageKey))
        assertEquals("kept", prefs.getString("oc.unrelated.two"))
    }

    @Test
    fun `B-PROTO-106 an offline transition cancels an in-flight refresh and keeps cached state`() = runTest {
        store.setOptIn(true)
        store.refresh()
        transport.gate = CompletableDeferred()
        transport.entered = CompletableDeferred()
        val refresh = async { store.refresh() }
        transport.entered!!.await()
        store.refresh(online = false)
        transport.gate!!.complete(Unit)
        refresh.await()
        assertEquals(SetupRegistryStatus.Offline, store.snapshot.status)
        assertEquals(1, store.snapshot.entries.size)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `B-STATE-133 opt-out during a refresh cannot resurrect entries or consent`() = runTest {
        store.setOptIn(true)
        transport.gate = CompletableDeferred()
        transport.entered = CompletableDeferred()
        val refresh = async { store.refresh() }
        transport.entered!!.await()
        store.setOptIn(false)
        transport.gate!!.complete(Unit)
        refresh.await()
        assertFalse(store.snapshot.optedIn)
        assertTrue(store.snapshot.entries.isEmpty())
        assertFalse(storedOptIn())
    }

    @Test
    fun `B-STATE-133 deletion during a refresh cannot recreate a profile preference`() = runTest {
        store.setOptIn(true)
        transport.gate = CompletableDeferred()
        transport.entered = CompletableDeferred()
        val refresh = async { store.refresh() }
        transport.entered!!.await()
        prefs.remove(store.storageKey)
        transport.gate!!.complete(Unit)
        refresh.await()
        assertFalse(prefs.containsKey(store.storageKey))
    }

    @Test
    fun `B-PROTO-106 redirects, oversized data and invalid JSON fail with safe copy`() = runTest {
        store.setOptIn(true)
        for ((status, body) in listOf(302 to "{}", 200 to "x".repeat(SetupRegistryClient.MAX_RESPONSE_BYTES + 1), 200 to "not JSON")) {
            transport.status = status
            transport.body = body
            store.refresh()
            assertEquals(SetupRegistryStatus.Error, store.snapshot.status)
            assertEquals("The public registry could not be loaded. Showing saved listings.", store.snapshot.reason)
        }
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun `B-PROTO-106 a failed refresh keeps cached entries and hides raw exceptions`() = runTest {
        store.setOptIn(true)
        store.refresh()
        transport.fail = true
        store.refresh()
        assertEquals(1, store.snapshot.entries.size)
        assertFalse(store.snapshot.reason!!.contains("private upstream diagnostics"))
        assertEquals(SetupRegistryStatus.Error, store.snapshot.status)
    }

    @Test
    fun `B-SEC-169 metadata redaction drops credentials, unsafe URLs and executable arguments`() = runTest {
        transport.body =
            """{"servers":[{"server":{"name":"org.example/weather","version":"1.0.0","description":"token=fake-registry-secret",
               "remotes":[{"type":"sse","url":"http://tools.example/mcp"},
                 {"type":"sse","url":"https://user:fake-password@tools.example/mcp"},
                 {"type":"sse","url":"https://tools.example/mcp?token=fake-query"},
                 {"type":"sse","url":"https://tools.example/{tenant}"},
                 {"type":"sse","url":"https://tools.example/mcp","headers":[{"name":"Authorization","value":"Bearer fake-header"}]}],
               "packages":[{"registryType":"npm","identifier":"@example/weather","version":"1.0.0",
                 "runtimeArguments":["fake-command"],"environmentVariables":[{"name":"API_KEY","value":"fake-env"}]}]}}]}"""
        store.setOptIn(true)
        store.refresh()
        val entry = store.snapshot.entries.single()
        assertFalse(entry.description.contains("fake-registry-secret"))
        assertEquals(1, entry.remotes.size)
        assertTrue(entry.remotes.single().requiresConfiguration)
        assertNull(entry.connectableRemote)
        assertTrue(entry.unsupportedReason!!.contains("connection details"))
        assertTrue(entry.remotes.single().toMcpConfig() is SetupOutcome.Failed)
        val stored = prefs.getString(store.storageKey)!!
        for (dropped in listOf("fake-registry-secret", "fake-password", "fake-query", "fake-header", "fake-command", "fake-env")) {
            assertFalse(stored.contains(dropped), dropped)
        }
        assertEquals("@example/weather", entry.packages.single().identifier)
    }

    @Test
    fun `B-SEC-169 package-only listings explain the unsupported installation path`() {
        val entry = RegistryEntry.fromJson(
            redactor,
            json(
                """{"name":"org.example/package","version":"1.0.0",
                   "packages":[{"registryType":"npm","identifier":"@example/package","version":"1.0.0"}]}""",
            ),
        )!!
        assertNull(entry.connectableRemote)
        assertTrue(entry.unsupportedReason!!.contains("reviewed command"))
    }

    @Test
    fun `B-PROTO-107 deleted listings are excluded and the server response count is bounded`() = runTest {
        val server = """{"name":"org.example/weather","version":"1.0.0"}"""
        transport.body = buildString {
            append("""{"servers":[{"server":$server,"_meta":{"io.modelcontextprotocol.registry/official":{"status":"deleted"}}}""")
            repeat(101) { append(""",{"server":$server}""") }
            append("]}")
        }
        val entries = client.fetch().valueOrNull()!!
        assertEquals(99, entries.size)
    }

    @Test
    fun `B-PROTO-107 a search is sent as the registry search parameter and never saved`() = runTest {
        store.setOptIn(true)
        store.refresh()
        val saved = prefs.getString(store.storageKey)
        store.refresh(query = " weather ")
        val request = transport.requests.last()
        assertEquals("https://registry.modelcontextprotocol.io/v0.1/servers", request.url)
        assertEquals(mapOf("limit" to "100", "version" to "latest", "search" to "weather"), request.query)
        assertFalse(request.headers.containsKey("Authorization"))
        assertEquals("weather", store.snapshot.query)
        assertEquals(saved, prefs.getString(store.storageKey))
        store.showSaved()
        assertNull(store.snapshot.query)
        assertEquals("org.example/weather", store.snapshot.entries.single().name)
    }

    @Test
    fun `B-SEC-168 a search term that looks like a credential is not sent`() = runTest {
        client.fetch(search = "ghp_abcdefghijklmnopqrstuvwxyz0123456789")
        assertFalse(transport.requests.single().query.containsKey("search"))
    }

    @Test
    fun `B-SEC-169 declared header and variable names are kept, never their values`() {
        val entry = RegistryEntry.fromJson(
            redactor,
            json(
                """{"name":"org.example/both","version":"1.0.0","title":"Both",
                   "remotes":[{"type":"streamable-http","url":"https://tools.example/mcp","headers":[
                     {"name":"Authorization","isRequired":true,"isSecret":true,"value":"Bearer fake-header","default":"fake-default"},
                     {"name":"bad name; rm","isRequired":true}]}],
                   "packages":[{"registryType":"npm","identifier":"@example/both","version":"1.0.0","runtimeHint":"npx",
                     "transport":{"type":"stdio"},"packageArguments":[{"value":"fake-argument","isRequired":true}],
                     "environmentVariables":[{"name":"API_KEY","isSecret":true,"default":"fake-env"}]}]}""",
            ),
        )!!
        assertEquals("Both", entry.title)
        val header = entry.remotes.single().headers.single()
        assertEquals(Triple("Authorization", true, true), Triple(header.name, header.required, header.secret))
        val pkg = entry.packages.single()
        assertEquals("stdio", pkg.transport)
        assertEquals("npx", pkg.runtimeHint)
        assertTrue(pkg.requiresArguments)
        assertEquals("API_KEY", pkg.environment.single().name)
        val encoded = entry.toJson(redactor).toString()
        for (dropped in listOf("fake-header", "fake-default", "fake-argument", "fake-env", "rm")) {
            assertFalse(encoded.contains(dropped), dropped)
        }
        // The saved form reads back the same.
        assertEquals(encoded, RegistryEntry.fromJson(redactor, entry.toJson(redactor))!!.toJson(redactor).toString())
    }
}
