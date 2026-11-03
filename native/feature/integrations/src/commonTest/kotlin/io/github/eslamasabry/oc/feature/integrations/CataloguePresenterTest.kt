package io.github.eslamasabry.oc.feature.integrations

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.core.model.McpServerKind
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogueUi
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueCache
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueStore
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryClient
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryEntry
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueAbilities
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueAsk
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueBlock
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueProblemKind
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueScreenPresenter
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueSource
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorServer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val redactor = Redactor()

private fun entry(text: String): RegistryEntry = RegistryEntry.fromJson(Json.parseToJsonElement(text), redactor)!!

/** A hosted server with no key, one with a key, a Node package with a key, a Python package, and a Docker image. */
internal fun listings(): List<RegistryEntry> =
    listOf(
        """{"name":"com.example/design","title":"Design","version":"1.0.0","description":"Draws things.",
            "remotes":[{"type":"streamable-http","url":"https://design.example/mcp"}]}""",
        """{"name":"com.example/weather","title":"Weather","version":"2.0.1","description":"Forecasts.",
            "remotes":[{"type":"streamable-http","url":"https://weather.example/mcp",
              "headers":[{"name":"Authorization","isRequired":true,"isSecret":true}]}]}""",
        """{"name":"io.github.acme/files-mcp","title":"Files","version":"1.2.0","description":"Reads files.",
            "packages":[{"registryType":"npm","identifier":"@acme/files-mcp","version":"1.2.0","transport":{"type":"stdio"},
              "environmentVariables":[{"name":"FILES_TOKEN","isRequired":true,"isSecret":true}]}]}""",
        """{"name":"io.github.acme/notes","version":"0.3.0","description":"Notes.",
            "packages":[{"registryType":"pypi","identifier":"acme-notes","version":"0.3.0","transport":{"type":"stdio"}}]}""",
        """{"name":"io.github.acme/box","version":"1.0.0","description":"A container.",
            "packages":[{"registryType":"oci","identifier":"docker.io/acme/box","version":"1.0.0","transport":{"type":"stdio"}}]}""",
    ).map(::entry)

internal class MemoryCache : CatalogueCache {
    val values = mutableMapOf<String, String>()

    override fun read(profileId: String): String? = values[profileId]

    override suspend fun write(profileId: String, value: String): Outcome<Unit> {
        values[profileId] = value
        return Unit.ok()
    }

    override suspend fun remove(profileId: String): Outcome<Unit> {
        values.remove(profileId)
        return Unit.ok()
    }
}

internal class FakeRegistry(var entries: List<RegistryEntry> = listings()) : RegistryClient {
    val asked = mutableListOf<String?>()
    var failure: Failure? = null

    override suspend fun fetch(search: String?): Outcome<List<RegistryEntry>> {
        asked += search
        failure?.let { return it.failed() }
        return (if (search == null) entries else entries.filter { it.name.contains(search) }).ok()
    }
}

class CataloguePresenterTest {
    private val home = ProfileId("home")

    private class Source(
        val cache: MemoryCache = MemoryCache(),
        val registry: FakeRegistry = FakeRegistry(),
        var abilities: CatalogueAbilities = CatalogueAbilities(canList = true, canAdd = true, canRemove = true),
    ) : CatalogueSource {
        override val servers = MutableStateFlow(listOf(ConnectorServer(ProfileId("home"), "Home")).toImmutableList())
        override val active: StateFlow<ProfileId?> = MutableStateFlow(null)
        val names = mutableListOf<String>()
        val calls = mutableListOf<String>()
        var addFailure: Failure? = null
        var listFailure: Failure? = null
        var stores = 0

        override fun abilities(profile: ProfileId): CatalogueAbilities = abilities

        override suspend fun installed(profile: ProfileId): Outcome<ImmutableList<McpServerInfo>> {
            calls += "list"
            listFailure?.let { return it.failed() }
            return names.map { McpServerInfo(it, "connected", null) }.toImmutableList().ok()
        }

        override suspend fun add(profile: ProfileId, draft: McpServerDraft): Outcome<Unit> {
            calls += "add ${draft.name} ${draft.kind} ${draft.url ?: draft.command.joinToString(" ")}"
            addFailure?.let { return it.failed() }
            names += draft.name
            return Unit.ok()
        }

        override suspend fun remove(profile: ProfileId, name: String): Outcome<Unit> {
            calls += "remove $name"
            names -= name
            return Unit.ok()
        }

        override fun catalogue(profile: ProfileId): CatalogueStore {
            stores++
            return CatalogueStore(cache, profile.raw, registry, { 1_000L }, redactor)
        }
    }

    private fun TestScope.presenter(source: Source) =
        CatalogueScreenPresenter(null, source, backgroundScope).also { runCurrent() }

    private fun TestScope.optedIn(source: Source): CatalogueScreenPresenter {
        val p = presenter(source)
        p.load()
        runCurrent()
        return p
    }

    @Test
    fun `B-AGENT-133 the first visit asks before any registry request, and Load saves consent and lists`() = runTest {
        val source = Source()
        val p = presenter(source)
        assertEquals(McpCatalogueUi.Consent, p.state.value.page)
        assertTrue(source.registry.asked.isEmpty())
        assertFalse(p.state.value.canForget)
        p.load()
        runCurrent()
        assertEquals(listOf<String?>(null), source.registry.asked)
        assertIs<McpCatalogueUi.Rows>(p.state.value.page)
        assertEquals(5, p.state.value.rows.size)
        assertTrue(p.state.value.canForget)
        assertTrue(source.cache.values.getValue("home").contains("\"optedIn\":true"))
    }

    @Test
    fun `B-AGENT-133 a saved consent loads the saved list and asks the registry again on the next visit`() = runTest {
        // Listings without a secret declaration: the engine's saved form of `"isSecret":true` does not read back yet.
        val source = Source(registry = FakeRegistry(listings().filter { it.name in setOf("com.example/design", "io.github.acme/notes", "io.github.acme/box") }))
        optedIn(source)
        source.registry.asked.clear()
        source.registry.failure = Failure.Network(NetworkWhy.Offline)
        val again = presenter(source)
        val page = assertIs<McpCatalogueUi.Rows>(again.state.value.page)
        assertTrue(page.stale) // the refresh failed: the saved listings stay, with a notice
        assertEquals(3, again.state.value.rows.size)
        assertEquals(listOf<String?>(null), source.registry.asked)
    }

    @Test
    fun `B-AGENT-212 rows put what is on first and say why a switch cannot be used`() = runTest {
        val source = Source()
        source.names += "notes"
        val p = optedIn(source)
        val rows = p.state.value.rows
        assertEquals("notes", rows.first().row.serverName)
        assertTrue(rows.first().row.on)
        val byName = rows.associateBy { it.row.serverName }
        assertNull(byName.getValue("design").block)
        assertEquals(CatalogueBlock.NeedsKey, byName.getValue("weather").block)
        assertEquals(CatalogueBlock.NeedsKey, byName.getValue("files-mcp").block)
        assertEquals(CatalogueBlock.NeedsDocker, byName.getValue("box").block)
        assertEquals("box", rows.last().row.serverName)
    }

    @Test
    fun `B-AGENT-212 turning a row on asks first, then adds it and reads the list again`() = runTest {
        val source = Source()
        val p = optedIn(source)
        source.calls.clear()
        p.toggle("com.example/design", true)
        assertEquals(CatalogueAsk.Add("design", "Design"), p.state.value.ask)
        assertTrue(source.calls.isEmpty())
        p.confirm()
        runCurrent()
        assertEquals(listOf("add design Remote https://design.example/mcp", "list"), source.calls)
        assertNull(p.state.value.ask)
        val design = p.state.value.rows.first { it.row.serverName == "design" }
        assertTrue(design.row.on)
        assertEquals("design", p.state.value.rows.first().row.serverName)
    }

    @Test
    fun `B-AGENT-212 Cancel changes nothing, and a disabled row does not ask`() = runTest {
        val source = Source()
        val p = optedIn(source)
        source.calls.clear()
        p.toggle("com.example/design", true)
        p.cancel()
        runCurrent()
        assertNull(p.state.value.ask)
        p.toggle("com.example/weather", true)
        assertNull(p.state.value.ask)
        assertTrue(source.calls.isEmpty())
    }

    @Test
    fun `B-AGENT-212 a refused add says so in plain words and the list is read again`() = runTest {
        val source = Source()
        source.addFailure = Failure.Conflict()
        val p = optedIn(source)
        source.calls.clear()
        p.toggle("com.example/design", true)
        p.confirm()
        runCurrent()
        assertEquals(CatalogueProblemKind.AddFailed, p.state.value.problem?.kind)
        assertEquals("design", p.state.value.problem?.name)
        assertEquals("list", source.calls.last())
        assertFalse(p.state.value.rows.first { it.row.serverName == "design" }.row.on)
    }

    @Test
    fun `B-AGENT-212 turning an installed row off asks, removes it and reads the list again`() = runTest {
        val source = Source()
        source.names += "notes"
        val p = optedIn(source)
        source.calls.clear()
        p.toggle("io.github.acme/notes", false)
        assertEquals(CatalogueAsk.Remove("notes"), p.state.value.ask)
        p.confirm()
        runCurrent()
        assertEquals(listOf("remove notes", "list"), source.calls)
        assertFalse(p.state.value.rows.first { it.row.serverName == "notes" }.row.on)
    }

    @Test
    fun `B-AGENT-212 a server that cannot remove keeps the row on and says why`() = runTest {
        val source = Source(abilities = CatalogueAbilities(canList = true, canAdd = true, canRemove = false))
        source.names += "notes"
        val p = optedIn(source)
        assertEquals(CatalogueBlock.CannotRemove, p.state.value.rows.first().block)
    }

    @Test
    fun `B-AGENT-212 an unreadable MCP list stops the switches`() = runTest {
        val source = Source()
        source.listFailure = Failure.Server(500)
        val p = optedIn(source)
        assertEquals(McpCatalogueUi.InventoryFailed, p.state.value.page)
    }

    @Test
    fun `B-AGENT-212 a server that takes no MCP servers explains, with no store and no request`() = runTest {
        val source = Source(abilities = CatalogueAbilities.None)
        val p = presenter(source)
        assertEquals(McpCatalogueUi.Unavailable, p.state.value.page)
        assertEquals(0, source.stores)
        assertTrue(source.registry.asked.isEmpty())
    }

    @Test
    fun `B-AGENT-212 search asks the registry with the words, and clearing goes back to the saved list`() = runTest {
        val source = Source()
        val p = optedIn(source)
        p.search("acme")
        runCurrent()
        assertEquals("acme", source.registry.asked.last())
        assertEquals(3, p.state.value.rows.size)
        p.search("nothing-like-it")
        runCurrent()
        assertEquals(McpCatalogueUi.Empty("nothing-like-it"), p.state.value.page)
        p.search("")
        runCurrent()
        assertEquals(5, p.state.value.rows.size)
        assertEquals(3, source.registry.asked.size)
    }

    @Test
    fun `B-AGENT-133 Stop using the registry forgets consent and the saved list`() = runTest {
        val source = Source()
        val p = optedIn(source)
        p.forget()
        runCurrent()
        assertEquals(McpCatalogueUi.Consent, p.state.value.page)
        assertFalse(p.state.value.canForget)
        assertTrue(source.cache.values.isEmpty())
    }

    @Test
    fun `B-AGENT-212 the engine sees a runtime add and removal, never a saved configuration write`() {
        val caps = CatalogueScreenPresenter.capabilities(CatalogueAbilities(canList = true, canAdd = true, canRemove = true))
        assertTrue(caps.serverCatalog && caps.mcpRuntimeAdds && caps.mcpRuntimeRemovals)
        assertFalse(caps.mcpConfigWrites)
        assertFalse(CatalogueScreenPresenter.capabilities(CatalogueAbilities.None).serverCatalog)
    }

    @Test
    fun `B-AGENT-212 a local listing is added with its exact command`() = runTest {
        val source = Source()
        val p = optedIn(source)
        source.calls.clear()
        p.toggle("io.github.acme/notes", true)
        p.confirm()
        runCurrent()
        assertEquals("add notes ${McpServerKind.Local} uvx acme-notes==0.3.0", source.calls.first())
    }
}
