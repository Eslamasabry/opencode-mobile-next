package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.CatalogProvider
import io.github.eslamasabry.oc.core.model.CatalogSnapshot
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.core.model.IntegrationMethodInfo
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pull request 142, ported: the add-a-provider draft, the JSON it declares, and the outcomes of a save on this
 * phone's own OpenCode (saved and loaded, saved and not loaded, a failed restart, a restart that did not load it,
 * every refusal) and on a server whose settings the app does not own.
 */
class CustomProviderTest {
    private val good =
        CustomProviderDraft("My box", " My-Box ", "https://llm.example.com:8443/v1", "llama-3\n\nqwen\nllama-3\n")

    // ---- the draft

    @Test
    fun `PR142-01 a good draft declares name, package, base address and models, and nothing secret`() {
        val declaration = good.declaration(emptySet())!!
        assertEquals("my-box", declaration.providerId)
        val block = declaration.block
        assertEquals(setOf("name", "package", "settings", "models"), block.keys)
        assertEquals("My box", block.getValue("name").jsonPrimitive.content)
        assertEquals(CUSTOM_PROVIDER_PACKAGE, block.getValue("package").jsonPrimitive.content)
        assertEquals(
            "https://llm.example.com:8443/v1",
            block.getValue("settings").jsonObject.getValue("baseURL").jsonPrimitive.content,
        )
        // Blank lines and a repeated id are dropped; the order typed is kept.
        assertEquals(listOf("llama-3", "qwen"), block.getValue("models").jsonObject.keys.toList())
        val input = Json.parseToJsonElement(declaration.input()).jsonObject
        assertEquals(setOf("id", "block", "replaceDeclared"), input.keys)
        assertFalse(Regex("(?i)key|token|password|secret").containsMatchIn(declaration.input()))
    }

    @Test
    fun `PR142-02 each field refuses what the engine or the settings file cannot hold`() {
        fun problems(name: String = "n", id: String = "x", address: String = "http://h/v1", models: String = "m") =
            CustomProviderDraft(name, id, address, models).problems(emptySet())
        assertEquals(listOf(CustomProviderProblem.NameMissing), problems(name = "  "))
        assertEquals(listOf(CustomProviderProblem.NameInvalid), problems(name = "a\u0007b"))
        assertEquals(listOf(CustomProviderProblem.IdMissing), problems(id = " "))
        assertEquals(listOf(CustomProviderProblem.IdInvalid), problems(id = "my box"))
        assertEquals(listOf(CustomProviderProblem.IdInvalid), problems(id = "a/b"))
        assertEquals(listOf(CustomProviderProblem.IdInvalid), problems(id = "-lead"))
        assertEquals(listOf(CustomProviderProblem.IdInvalid), problems(id = "x".repeat(65)))
        assertEquals(listOf(CustomProviderProblem.AddressInvalid), problems(address = "ftp://h/v1"))
        assertEquals(listOf(CustomProviderProblem.AddressInvalid), problems(address = "h/v1"))
        assertEquals(listOf(CustomProviderProblem.AddressInvalid), problems(address = "http:///v1"))
        assertEquals(listOf(CustomProviderProblem.AddressInvalid), problems(address = "http://h:99999/v1"))
        assertEquals(listOf(CustomProviderProblem.AddressInvalid), problems(address = "http://h /v1"))
        assertEquals(listOf(CustomProviderProblem.AddressInvalid), problems(address = "http://h/v1#x"))
        assertEquals(listOf(CustomProviderProblem.AddressHasCredentials), problems(address = "https://me:pw@h/v1"))
        assertEquals(listOf(CustomProviderProblem.AddressHasCredentials), problems(address = "https://token@h/v1"))
        assertEquals(listOf(CustomProviderProblem.ModelsMissing), problems(models = " \n "))
        assertEquals(listOf(CustomProviderProblem.ModelInvalid), problems(models = "llama 3"))
        assertEquals(listOf(CustomProviderProblem.ModelInvalid), problems(models = "model#fast"))
        assertEquals(
            listOf(CustomProviderProblem.TooManyModels),
            problems(models = (1..65).joinToString("\n") { "m$it" }),
        )
        // Every field at once: one problem each, nothing declared.
        val all = CustomProviderDraft("", "", "", "")
        assertEquals(4, all.problems(emptySet()).map { it.field }.distinct().size)
        assertNull(all.declaration(emptySet()))
    }

    @Test
    fun `PR142-03 the addresses people type are accepted, the scheme written lower case`() {
        val typed =
            listOf(
                "http://127.0.0.1:8080/v1",
                "http://[::1]:11434/v1",
                "https://gpu-box.example",
                "HTTPS://Api.Example.com/v1?x=1",
            )
        for (address in typed) {
            assertNull(customProviderAddress(address).problem, address)
        }
        assertEquals("https://Api.Example.com/v1", customProviderAddress(" HTTPS://Api.Example.com/v1 ").value)
    }

    @Test
    fun `PR142-04 an id the server already lists is refused in any case, never patching a catalog provider`() {
        assertEquals(
            listOf(CustomProviderProblem.IdTaken),
            CustomProviderDraft("Mine", "OpenAI", "https://evil.example/v1", "gpt").problems(setOf("openai")),
        )
    }

    // ---- the session on this phone and elsewhere

    private class Host(
        var writes: List<Outcome<Int>> = listOf(Outcome.Ok(0)),
        var restartResult: Outcome<Unit> = Outcome.Ok(Unit),
    ) : CustomProviderHost {
        val written = mutableListOf<CustomProviderDeclaration>()
        var restarts = 0

        override suspend fun write(declaration: CustomProviderDeclaration): Outcome<Int> {
            written += declaration
            return writes[(written.size - 1).coerceAtMost(writes.size - 1)]
        }

        override suspend fun restart(): Outcome<Unit> {
            restarts++
            return restartResult
        }
    }

    /** The integrations the server lists, answer by answer (the last one repeats). */
    private class Server(var answers: List<Outcome<ImmutableList<IntegrationInfo>>>) {
        var reads = 0

        fun next(): Outcome<ImmutableList<IntegrationInfo>> = answers[reads++.coerceAtMost(answers.size - 1)]
    }

    private fun row(id: String) =
        IntegrationInfo(
            id,
            id,
            persistentListOf(
                IntegrationMethodInfo(
                    "key",
                    null,
                    "Manually enter API Key",
                    persistentListOf(),
                    persistentListOf(),
                    persistentMapOf(),
                )
            ),
            persistentListOf(),
            0,
        )

    private fun listing(vararg ids: String): Outcome<ImmutableList<IntegrationInfo>> =
        Outcome.Ok(ids.map(::row).toImmutableList())

    private fun TestScope.session(
        host: CustomProviderHost?,
        listings: Server,
        owned: Boolean = true,
        catalog: CatalogSnapshot? = null,
        catalogAnswer: Outcome<CatalogSnapshot>? = null,
    ): LibrarySession {
        val noCatalog = CatalogSnapshot(persistentListOf(), persistentListOf(), persistentListOf())
        val source =
            object : LibrarySource {
                override val profile = ProfileId("p")
                override val origin = "http://127.0.0.1:4096"
                override val location = ServerLocation.Default
                override val capabilities = ServerCapabilities(serverCatalog = true, customProviders = owned)
                override val server: ServerGateway = FakeServerGateway()
                override val operations: ServerOperationsGateway =
                    object : FakeOperationsGateway() {
                        override suspend fun integrations() = listings.next()

                        override suspend fun catalog() = catalogAnswer ?: Outcome.Ok(catalog ?: noCatalog)
                    }
                override val customProviders = host

                override fun current() = true
            }
        val recovery =
            object : LibraryRecoveryStore {
                override suspend fun read(): Outcome<String?> = Outcome.Ok(null)

                override suspend fun write(json: String): Outcome<Unit> = Outcome.Ok(Unit)
            }
        return LibrarySession({ source }, recovery, backgroundScope, Clock.System)
    }

    @Test
    fun `PR142-05 saved and loaded when the engine lists the provider after the write, with no restart`() = runTest {
        val host = Host()
        val server = Server(listOf(listing("openai"), listing("openai"), listing("openai", "my-box")))
        assertEquals(CustomProviderOutcome.Loaded, session(host, server).addCustomProvider(good))
        assertEquals(listOf("my-box"), host.written.map { it.providerId })
        assertEquals(0, host.restarts)
    }

    @Test
    fun `PR142-06 saved but not loaded is its own outcome, and a restart that brings it in is loaded`() = runTest {
        val host = Host()
        val server = Server(listOf(listing("openai")))
        val library = session(host, server)
        assertEquals(CustomProviderOutcome.SavedNotLoaded, library.addCustomProvider(good))
        assertEquals(0, host.restarts)
        server.answers = listOf(Outcome.Failed(Failure.Network(NetworkWhy.Refused)), listing("openai", "my-box"))
        server.reads = 0
        assertEquals(CustomProviderOutcome.Loaded, library.restartForCustomProvider("my-box"))
        assertEquals(1, host.restarts)
    }

    @Test
    fun `PR142-07 a failed restart keeps the save and says the restart failed, with a stable label only`() = runTest {
        val host = Host(restartResult = Outcome.Failed(Failure.Runtime(RuntimeProblem.ServerStartUnavailable)))
        val library = session(host, Server(listOf(listing())))
        val outcome = library.restartForCustomProvider("my-box")
        assertEquals(CustomProviderOutcome.RestartFailed("Runtime · ServerStartUnavailable"), outcome)
    }

    @Test
    fun `PR142-08 a restart that comes back without the provider says it is saved but not loaded`() = runTest {
        val host = Host()
        assertEquals(
            CustomProviderOutcome.NotLoadedAfterRestart,
            session(host, Server(listOf(listing("openai")))).restartForCustomProvider("my-box"),
        )
    }

    @Test
    fun `PR142-09 every refusal of the settings write changes nothing and names its reason`() = runTest {
        val cases =
            mapOf(
                20 to CustomProviderRefusal.NotSetUp,
                21 to CustomProviderRefusal.Unreadable,
                22 to CustomProviderRefusal.Unsafe,
                23 to CustomProviderRefusal.Busy,
                24 to CustomProviderRefusal.AlreadyDeclared,
                25 to CustomProviderRefusal.HandManaged,
                26 to CustomProviderRefusal.Failed,
                127 to CustomProviderRefusal.Failed,
                -1 to CustomProviderRefusal.Failed,
            )
        for ((code, reason) in cases) {
            val outcome = session(Host(listOf(Outcome.Ok(code))), Server(listOf(listing()))).addCustomProvider(good)
            assertEquals(CustomProviderOutcome.Refused(reason, "Exit $code"), outcome)
        }
        val failedRun =
            session(Host(listOf(Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic)))), Server(listOf(listing())))
                .addCustomProvider(good)
        assertEquals(CustomProviderOutcome.Refused(CustomProviderRefusal.Failed, "Runtime · Generic"), failedRun)
    }

    @Test
    fun `PR142-10 a server whose settings the app does not own refuses before anything is written`() = runTest {
        val host = Host()
        val remote = session(host, Server(listOf(listing())), owned = false)
        assertEquals(CustomProviderOutcome.Refused(CustomProviderRefusal.Unavailable), remote.addCustomProvider(good))
        assertEquals(
            CustomProviderOutcome.Refused(CustomProviderRefusal.Unavailable),
            remote.restartForCustomProvider("my-box"),
        )
        val noHost = session(null, Server(listOf(listing())), owned = true)
        assertEquals(CustomProviderOutcome.Refused(CustomProviderRefusal.Unavailable), noHost.addCustomProvider(good))
        assertTrue(host.written.isEmpty())
        assertEquals(0, host.restarts)
        // The capability is false unless the app applies it: no protocol default turns it on.
        assertFalse(ServerCapabilities().customProviders)
        assertTrue(ServerCapabilities().withCustomProviders(true).customProviders)
    }

    @Test
    fun `PR142-11 a listed id is refused before the write, an unanswered check writes nothing`() = runTest {
        val host = Host()
        val taken = session(host, Server(listOf(listing("my-box")))).addCustomProvider(good)
        assertEquals(CustomProviderOutcome.Invalid(listOf(CustomProviderProblem.IdTaken)), taken)
        val silent =
            session(host, Server(listOf(Outcome.Failed(Failure.Network(NetworkWhy.Refused))))).addCustomProvider(good)
        assertEquals(CustomProviderOutcome.Refused(CustomProviderRefusal.CouldNotCheck, "Network · Refused"), silent)
        assertTrue(host.written.isEmpty())
    }

    // ---- issue 152: a declaration the settings file still holds under an id no list shows

    @Test
    fun `B-LIB-311 adding an id that is declared but not shown replaces the old declaration`() = runTest {
        val host = Host()
        // Nothing shows my-box: the server lists no row, none is kept and the catalog (loaded, and empty) holds no
        // such id. Only the settings file can still hold it, left by a delete that could not remove it, so the
        // writer is told to replace instead of refusing.
        val library = session(host, Server(listOf(listing())))
        library.refresh()
        assertEquals(CustomProviderOutcome.SavedNotLoaded, library.addCustomProvider(good))
        val written = host.written.single()
        assertEquals("my-box", written.providerId)
        assertTrue("\"replaceDeclared\":true" in written.input(), "the writer is told to replace")
    }

    @Test
    fun `B-LIB-312 adding an id that is shown in the list is still refused`() = runTest {
        val host = Host()
        val taken = session(host, Server(listOf(listing("my-box")))).addCustomProvider(good)
        assertEquals(CustomProviderOutcome.Invalid(listOf(CustomProviderProblem.IdTaken)), taken)
        assertTrue(host.written.isEmpty())
    }

    @Test
    fun `B-LIB-314 a catalog id is still refused even when it is not shown`() = runTest {
        val host = Host()
        val disabled =
            CatalogSnapshot(
                persistentListOf(CatalogProvider("my-box", "My box", enabled = false, integrationId = null)),
                persistentListOf(),
                persistentListOf(),
            )
        val library = session(host, Server(listOf(listing())), catalog = disabled)
        library.refresh()
        val refused = library.addCustomProvider(good)
        assertEquals(CustomProviderOutcome.Invalid(listOf(CustomProviderProblem.IdTaken)), refused)
        assertTrue(host.written.isEmpty())
    }

    @Test
    fun `B-LIB-318 a catalog that did not load sends no replace flag`() = runTest {
        val failed = Host()
        val unread =
            session(
                failed,
                Server(listOf(listing())),
                catalogAnswer = Outcome.Failed(Failure.Network(NetworkWhy.Refused)),
            )
        unread.refresh()
        assertEquals(CustomProviderOutcome.SavedNotLoaded, unread.addCustomProvider(good))
        assertFalse(failed.written.single().replaceDeclared)

        val fresh = Host()
        val neverLoaded = session(fresh, Server(listOf(listing())))
        assertEquals(CustomProviderOutcome.SavedNotLoaded, neverLoaded.addCustomProvider(good))
        assertFalse(fresh.written.single().replaceDeclared)
    }
}
