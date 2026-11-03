package io.github.eslamasabry.oc.engine.connectors

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.connectors.catalog.CatalogueFact
import io.github.eslamasabry.oc.engine.connectors.catalog.CataloguePresenter
import io.github.eslamasabry.oc.engine.connectors.catalog.CatalogueRowBlock
import io.github.eslamasabry.oc.engine.connectors.catalog.CatalogueScreenInput
import io.github.eslamasabry.oc.engine.connectors.catalog.CatalogueToggle
import io.github.eslamasabry.oc.engine.connectors.catalog.McpAddPath
import io.github.eslamasabry.oc.engine.connectors.catalog.McpAddSheetUi
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogItem
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogRuntime
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogueUi
import io.github.eslamasabry.oc.engine.connectors.catalog.catalogServerName
import io.github.eslamasabry.oc.engine.connectors.catalog.orderCatalog
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueSnapshot
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueStatus
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SECRET_IN_TEXT = "token=fake-registry-secret"

/** The four listing kinds of slice_p2_4_5_test.dart `_listings()`. */
internal fun catalogueListings(): List<RegistryEntry> =
    listOf(
        """
        {"name":"com.example/weather","title":"Weather","version":"2.0.1",
         "description":"Forecasts. [Click here](https://evil.example/steal) $SECRET_IN_TEXT",
         "remotes":[{"type":"streamable-http","url":"https://weather.example/mcp",
           "headers":[{"name":"Authorization","isRequired":true,"isSecret":true,
             "value":"Bearer fake-header-default"}]}]}
        """,
        """
        {"name":"io.github.acme/files-mcp","title":"Files","version":"1.2.0","description":"Reads and writes files.",
         "packages":[{"registryType":"npm","identifier":"@acme/files-mcp","version":"1.2.0",
           "transport":{"type":"stdio"},
           "runtimeArguments":[{"value":"--evil; rm -rf /","isRequired":false}],
           "environmentVariables":[{"name":"FILES_TOKEN","isRequired":true,"isSecret":true,
             "default":"fake-env-default"},
             {"name":"LOG_LEVEL","isRequired":false}]}]}
        """,
        """
        {"name":"io.github.acme/notes","version":"0.3.0","description":"Notes.",
         "packages":[{"registryType":"pypi","identifier":"acme-notes","version":"0.3.0","transport":{"type":"stdio"}}]}
        """,
        """
        {"name":"io.github.acme/box","version":"1.0.0","description":"A container.",
         "packages":[{"registryType":"oci","identifier":"docker.io/acme/box","version":"1.0.0",
           "transport":{"type":"stdio"}}]}
        """,
    ).map(::entryOf)

class McpCatalogTest {
    private val items = catalogueListings().associate { it.name to McpCatalogItem.from(it) }

    @Test
    fun `B-AGENT-212 maps each kind of listing to what the form opens with`() {
        val weather = items.getValue("com.example/weather")
        assertEquals("weather", weather.serverName)
        assertEquals("Weather", weather.title)
        assertEquals(McpCatalogRuntime.Hosted, weather.runtime)
        assertEquals("weather.example", weather.hostedBy)
        assertTrue(weather.needsKey)
        assertEquals("https://weather.example/mcp", weather.draft!!.url)
        // Names only: a registry "value" never becomes a header value.
        assertEquals(mapOf("Authorization" to ""), weather.draft!!.headers)

        val files = items.getValue("io.github.acme/files-mcp")
        assertEquals(McpCatalogRuntime.Node, files.runtime)
        assertEquals(listOf("npx", "-y", "@acme/files-mcp@1.2.0"), files.draft!!.command)
        // Required variables only, never a registry default.
        assertEquals(mapOf("FILES_TOKEN" to ""), files.draft!!.environment)

        val notes = items.getValue("io.github.acme/notes")
        assertEquals(McpCatalogRuntime.Python, notes.runtime)
        assertEquals(listOf("uvx", "acme-notes==0.3.0"), notes.draft!!.command)

        val box = items.getValue("io.github.acme/box")
        assertEquals(McpCatalogRuntime.Docker, box.runtime)
        assertFalse(box.addable)
    }

    @Test
    fun `B-AGENT-212 names come from the listing, generic ones from its namespace`() {
        assertEquals("weather-mcp", catalogServerName("io.github.acme/weather-mcp"))
        assertEquals("inference-sh", catalogServerName("ac.inference.sh/mcp"))
        assertEquals("my-tool", catalogServerName("com.Example/My Tool!"))
    }

    @Test
    fun `B-AGENT-212 orders on, then addable, then not addable`() {
        val ordered = orderCatalog(catalogueListings().reversed(), installed = setOf("files-mcp"))
        assertEquals(listOf("files-mcp", "notes", "weather", "box"), ordered.map { it.serverName })
    }

    @Test
    fun `B-AGENT-212 a listing title is redacted like its description`() {
        val entry = entryOf("""{"name":"x/y","version":"1","title":"ghp_abcdefghijklmnopqrstuvwxyz0123456789"}""")
        assertFalse(entry.title.contains("ghp_abcdefghijklmnopqrstuvwxyz"))
        val weather = items.getValue("com.example/weather")
        assertFalse("fake-registry-secret" in weather.description)
        assertTrue("[Click here](https://evil.example/steal)" in weather.description)
    }

    @Test
    fun `B-AGENT-212 the add sheet offers the catalogue and the manual form, nothing dead`() {
        val sheet = McpAddSheetUi.of(openCode2Capabilities)
        assertEquals(listOf(McpAddPath.Catalog, McpAddPath.Manual), sheet.choices)
        assertFalse(sheet.catalogUnavailable)
        assertEquals(McpAddPath.Catalog, sheet.choose(McpAddPath.Catalog))
    }

    @Test
    fun `B-AGENT-212 browse explains when this server has no catalogue`() {
        val sheet = McpAddSheetUi.of(ServerCapabilities(mcpConfigWrites = false, mcpRuntimeAdds = false))
        assertTrue(sheet.catalogUnavailable)
        assertNull(sheet.choose(McpAddPath.Catalog))
        assertEquals(McpAddPath.Manual, sheet.choose(McpAddPath.Manual))
    }

    private fun input(
        registry: CatalogueSnapshot? = CatalogueSnapshot(CatalogueStatus.Ready, true, catalogueListings(), 1L),
        installed: Set<String>? = emptySet(),
        caps: ServerCapabilities = openCode2Capabilities,
        phone: Boolean = false,
    ) = CatalogueScreenInput(caps, hasProfile = true, registry = registry, installed = installed, phoneHost = phone)

    @Test
    fun `B-AGENT-212 the catalogue asks before the first request, then lists as switches`() {
        assertEquals(
            McpCatalogueUi.Consent,
            CataloguePresenter.present(input(registry = CatalogueSnapshot(CatalogueStatus.Disabled, false))),
        )
        val rows = assertIs<McpCatalogueUi.Rows>(CataloguePresenter.present(input())).rows
        assertEquals(4, rows.size)
        val byName = rows.associateBy { it.serverName }
        assertEquals(
            listOf(CatalogueFact.HostedBy("weather.example"), CatalogueFact.NeedsKey),
            byName.getValue("weather").facts,
        )
        assertEquals(listOf(CatalogueFact.NeedsNode(false), CatalogueFact.NeedsKey), byName.getValue("files-mcp").facts)
        assertEquals(listOf(CatalogueFact.NeedsPython), byName.getValue("notes").facts)
        assertEquals(CatalogueRowBlock.NeedsDocker, byName.getValue("box").blocked)
        assertTrue((CataloguePresenter.present(input()) as McpCatalogueUi.Rows).priceNote)
        assertEquals(
            McpCatalogueUi.Unavailable,
            CataloguePresenter.present(
                input(caps = ServerCapabilities(mcpConfigWrites = false, mcpRuntimeAdds = false)),
            ),
        )
    }

    @Test
    fun `B-AGENT-212 catalogue toggles open the form, ask about node on a phone, and remove where supported`() {
        val weather = items.getValue("com.example/weather")
        val open = assertIs<CatalogueToggle.OpenForm>(CataloguePresenter.toggle(weather, input()))
        assertEquals("weather", open.draft.name)
        val node = CataloguePresenter.toggle(items.getValue("io.github.acme/files-mcp"), input(phone = true))
        assertEquals("Files", assertIs<CatalogueToggle.ChooseNode>(node).title)
        val on = input(installed = setOf("weather"))
        assertEquals(CatalogueToggle.ConfirmRemoval("weather"), CataloguePresenter.toggle(weather, on))
        assertEquals("Weather", assertIs<McpCatalogueUi.Rows>(CataloguePresenter.present(on)).rows.first().title)
        val keeps = input(installed = setOf("weather"), caps = qualifiedCapabilities.copy(mcpRuntimeRemovals = false))
        assertEquals(CatalogueRowBlock.CannotRemove, CataloguePresenter.row(weather, keeps).blocked)
        assertEquals(CatalogueToggle.Nothing, CataloguePresenter.toggle(weather, keeps))
    }

    @Test
    fun `B-AGENT-212 catalogue failure, empty search and stale states`() {
        val failed = CatalogueSnapshot(CatalogueStatus.Error, true)
        assertEquals(McpCatalogueUi.Failed(null), CataloguePresenter.present(input(registry = failed)))
        val noMatch = CatalogueSnapshot(CatalogueStatus.Empty, true, query = "zzz")
        assertEquals(McpCatalogueUi.Empty("zzz"), CataloguePresenter.present(input(registry = noMatch)))
        val stale = CatalogueSnapshot(CatalogueStatus.Error, true, catalogueListings())
        assertTrue(assertIs<McpCatalogueUi.Rows>(CataloguePresenter.present(input(registry = stale))).stale)
        assertEquals(McpCatalogueUi.Loading, CataloguePresenter.present(input(installed = null)))
    }
}
