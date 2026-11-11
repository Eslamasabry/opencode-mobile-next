package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.model.IntegrationMethodInfo
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The shapes are the ones a real OpenCode 2.2.6 server offers (types and labels only). */
class ConnectMethodsTest {
    private fun m(type: String, id: String? = null, label: String = type, required: Boolean = false) =
        IntegrationMethodInfo(
            type,
            id,
            label,
            if (required) persistentListOf(buildJsonObject { put("key", "q"); put("required", true) })
            else persistentListOf(),
            persistentListOf(),
            persistentMapOf(),
        )

    private fun ids(list: List<IntegrationMethodInfo>) = list.map { it.id ?: it.type }

    @Test
    fun `W4-1 OpenCode Go offers only the key, the server has no sign-in for it`() {
        val shown = connectMethods("opencode-go", listOf(m("key"), m("env")), commandSupported = true)
        assertEquals(listOf("key"), ids(shown))
    }

    @Test
    fun `W4-2 OpenCode Console puts the account sign-in before the service-account key`() {
        val shown =
            connectMethods(
                "opencode",
                listOf(m("key", label = "API key (service account)"), m("env"), m("oauth", "device", "OpenCode Console account")),
                commandSupported = false,
            )
        assertEquals(listOf("device", "key"), ids(shown))
    }

    @Test
    fun `W4-3 OpenAI puts ChatGPT and device code before the key and the browser sign-in last`() {
        val shown =
            connectMethods(
                "openai",
                listOf(
                    m("key"),
                    m("env"),
                    m("oauth", "chatgpt", "Sign in with ChatGPT"),
                    m("oauth", "codex", "Codex browser (legacy)"),
                    m("oauth", "headless", "Codex device code (legacy)"),
                ),
                commandSupported = false,
            )
        assertEquals(listOf("chatgpt", "headless", "key", "codex"), ids(shown))
        assertTrue(connectMethodNeedsServerBrowser(shown.last()))
    }

    @Test
    fun `W4-4 GitHub Copilot keeps its sign-in even though it asks required questions`() {
        val shown =
            connectMethods(
                "github-copilot",
                listOf(m("env"), m("oauth", "device", "Login with GitHub Copilot", required = true)),
                commandSupported = false,
            )
        assertEquals(listOf("device"), ids(shown))
    }

    @Test
    fun `W4-5 Anthropic keeps only the key and its key page`() {
        val shown =
            connectMethods("anthropic", listOf(m("key"), m("env"), m("oauth", "claude", "Claude account")), commandSupported = true)
        assertEquals(listOf("key"), ids(shown))
        // Nothing is invented: a key-led provider without a key keeps what the server offers.
        assertEquals(
            listOf("claude"),
            ids(connectMethods("anthropic", listOf(m("oauth", "claude", "Claude account")), commandSupported = true)),
        )
    }

    @Test
    fun `W4-6 command sign-ins show only when the server runs them and they have an id`() {
        val methods = listOf(m("command", "login"), m("command"), m("key"))
        assertEquals(listOf("key"), ids(connectMethods("x", methods, commandSupported = false)))
        assertEquals(listOf("key", "login"), ids(connectMethods("x", methods, commandSupported = true)))
    }

    @Test
    fun `W4-7 hints say how each choice finishes`() {
        assertEquals(ConnectHint.Key, connectMethodHint(m("key")))
        assertEquals(ConnectHint.Command, connectMethodHint(m("command", "c")))
        assertEquals(ConnectHint.Browser, connectMethodHint(m("oauth", "a", "Codex browser")))
        assertEquals(ConnectHint.Device, connectMethodHint(m("oauth", "a", "Codex headless")))
        assertEquals(ConnectHint.Account, connectMethodHint(m("oauth", "a", "Console account")))
    }

    @Test
    fun `W4-8 a method with no name of its own is recognised so the page never shows the word key`() {
        assertFalse(connectMethodHasOwnLabel(m("key")))
        assertFalse(connectMethodHasOwnLabel(m("key", label = "  ")))
        assertTrue(connectMethodHasOwnLabel(m("key", label = "API key (service account)")))
        // An unnamed sign-in is never titled "API key".
        assertTrue(connectMethodHasOwnLabel(m("oauth", "a")))
        assertTrue(connectMethodHasOwnLabel(m("command", "c")))
    }
}
