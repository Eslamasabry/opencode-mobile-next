package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.core.model.IntegrationMethodInfo
import io.github.eslamasabry.oc.core.model.McpServerInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.json.Json

/**
 * B-292: the last-known Tools lists written to disk never hold the two server texts that can carry an MCP or sign-in
 * secret, an MCP server's error prose and a sign-in method's hidden preset answers. All values are fake.
 */
class KeptLibrarySecretsTest {
    private val leaked = "fake-mcp-error-value-Zq81"
    private val preset = "fake-hidden-preset-Wm42"

    private val inventory =
        LibraryInventory(
            servers =
                persistentListOf(
                    McpServerInfo("github", "failed", "connect failed: GITHUB_TOKEN=$leaked rejected")
                ),
            providers =
                persistentListOf(
                    IntegrationInfo(
                        "gitlab",
                        "GitLab",
                        persistentListOf(
                            IntegrationMethodInfo(
                                "oauth",
                                "gitlab",
                                "Sign in",
                                persistentListOf(),
                                persistentListOf(),
                                persistentMapOf("clientSecret" to preset),
                            )
                        ),
                        persistentListOf(),
                        0,
                    )
                ),
        )

    @Test
    fun `B-292 the kept file holds an MCP server's name and status but never its error text`() {
        val written = Json.encodeToString(KeptLibrary.serializer(), KeptLibrary.of(inventory))
        assertFalse(leaked in written)
        assertTrue("github" in written)
        val restored = Json.decodeFromString(KeptLibrary.serializer(), written).over(LibraryInventory())
        assertEquals("failed", restored.servers.single().status)
        assertNull(restored.servers.single().error)
    }

    @Test
    fun `B-292 the kept file never holds a sign-in method's hidden preset answer`() {
        val written = Json.encodeToString(KeptLibrary.serializer(), KeptLibrary.of(inventory))
        assertFalse(preset in written)
        val method = Json.decodeFromString(KeptLibrary.serializer(), written).over(LibraryInventory())
            .providers.single().methods.single()
        assertEquals("oauth", method.type)
        assertTrue(method.presetAnswers.isEmpty())
    }

    @Test
    fun `B-292 an older kept file that still has error text and presets is read without showing either`() {
        val old =
            """{"servers":[{"name":"github","status":"failed","error":"$leaked"}],
               "providers":[{"id":"gitlab","name":"GitLab","methods":[{"type":"oauth","id":"gitlab","label":"Sign in",
               "presetAnswers":{"clientSecret":"$preset"}}],"connections":[],"connectionCount":0}]}"""
        val restored = Json.decodeFromString(KeptLibrary.serializer(), old).over(LibraryInventory())
        assertNull(restored.servers.single().error)
        assertTrue(restored.providers.single().methods.single().presetAnswers.isEmpty())
    }
}
