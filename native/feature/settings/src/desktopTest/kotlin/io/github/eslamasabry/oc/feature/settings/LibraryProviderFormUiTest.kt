package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.model.IntegrationConnectionInfo
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.core.model.IntegrationMethodInfo
import io.github.eslamasabry.oc.engine.integrations.catalog.LibraryInventory
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryActions
import io.github.eslamasabry.oc.feature.settings.presentation.LibrarySheet
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryTab
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryUi
import io.github.eslamasabry.oc.feature.settings.ui.LibraryScreen
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * w4-provforms gate: the Library's provider row and method sheet with forms in the shape the OC2 reader hands over
 * (Oc2ProviderSignIn `prompt`): GitHub Copilot's choice and conditional address, and GitLab's prefilled address.
 */
@OptIn(ExperimentalTestApi::class)
class LibraryProviderFormUiTest {
    private val calls = mutableListOf<Pair<String, List<Any?>>>()

    private val actions: LibraryActions =
        Proxy.newProxyInstance(LibraryActions::class.java.classLoader, arrayOf(LibraryActions::class.java)) {
            proxy,
            method,
            args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "TestActions"
                else -> {
                    calls += method.name to args?.toList().orEmpty()
                    null
                }
            }
        } as LibraryActions

    private val copilotMethod =
        IntegrationMethodInfo(
            "oauth",
            "device",
            "Login with GitHub",
            persistentListOf(
                buildJsonObject {
                    put("key", "deploymentType")
                    put("message", "Deployment type")
                    put("type", "select")
                    put(
                        "options",
                        JsonArray(
                            listOf("github.com" to "GitHub.com", "enterprise" to "GitHub Enterprise").map { (v, l) ->
                                buildJsonObject {
                                    put("value", v)
                                    put("label", l)
                                }
                            }
                        ),
                    )
                    put("required", true)
                    put("default", "github.com")
                },
                buildJsonObject {
                    put("key", "enterpriseUrl")
                    put("message", "Enterprise URL")
                    put("type", "text")
                    put("required", true)
                    put("format", "uri")
                    put(
                        "when",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("key", "deploymentType")
                                    put("op", "eq")
                                    put("value", "enterprise")
                                }
                            )
                        ),
                    )
                },
            ),
            persistentListOf(),
            persistentMapOf(),
        )

    private val copilot =
        IntegrationInfo("github-copilot", "GitHub Copilot", persistentListOf(copilotMethod), persistentListOf(), 0)

    private fun method(sheet: IntegrationInfo, method: IntegrationMethodInfo) =
        LibraryUi(
            tab = LibraryTab.Providers,
            available = true,
            inventory = LibraryInventory(loading = false, providers = persistentListOf(sheet)),
            sheet = LibrarySheet.Method(sheet, method),
        )

    @Suppress("UNCHECKED_CAST")
    private fun started(): List<Map<String, String>> =
        calls.filter { it.first == "start" }.map { it.second[2] as Map<String, String> }

    @Test
    fun `Copilot shows its choices with the default picked, the address only for Enterprise, and never sends a hidden answer`() =
        runComposeUiTest {
            setContent { SettingsThemed { LibraryScreen(method(copilot, copilotMethod), actions) } }
            onNodeWithText("GitHub.com").assertExists()
            onNodeWithText("GitHub Enterprise").assertExists()
            onNodeWithText("Enterprise URL").assertDoesNotExist()

            onNodeWithText("GitHub Enterprise").performClick()
            onNodeWithText("Enterprise URL").assertExists()
            // A required visible answer is missing: nothing starts and the sheet says why.
            onNodeWithText("Start sign-in on the server").performClick()
            assertEquals(emptyList(), started())
            onNodeWithText("Enter a value for every required field.").assertExists()

            // Back to github.com: the address goes away and is not sent.
            onNodeWithText("GitHub.com").performClick()
            onNodeWithText("Enterprise URL").assertDoesNotExist()
            onNodeWithText("Start sign-in on the server").performClick()
            assertEquals(listOf(mapOf("deploymentType" to "github.com")), started())
        }

    @Test
    fun `GitLab's address is prefilled with the server default and sent as is`() = runComposeUiTest {
        val gitlabMethod =
            IntegrationMethodInfo(
                "oauth",
                "gitlab-oauth",
                "Sign in with GitLab",
                persistentListOf(
                    buildJsonObject {
                        put("key", "instanceUrl")
                        put("message", "GitLab instance")
                        put("type", "text")
                        put("required", false)
                        put("format", "uri")
                        put("default", "https://gitlab.com")
                    }
                ),
                persistentListOf(),
                persistentMapOf("client" to "preset"),
            )
        val gitlab = IntegrationInfo("gitlab", "GitLab", persistentListOf(gitlabMethod), persistentListOf(), 0)
        setContent { SettingsThemed { LibraryScreen(method(gitlab, gitlabMethod), actions) } }
        onNodeWithText("GitLab instance").assertExists()
        onNodeWithText("Start sign-in on the server").performClick()
        assertEquals(listOf(mapOf("client" to "preset", "instanceUrl" to "https://gitlab.com")), started())
    }

    private fun providers(vararg rows: IntegrationInfo) =
        LibraryUi(
            tab = LibraryTab.Providers,
            available = true,
            inventory = LibraryInventory(loading = false, providers = rows.toList().toImmutableList()),
        )

    private val keyMethod =
        IntegrationMethodInfo("key", null, "API key", persistentListOf<JsonObject>(), persistentListOf(), persistentMapOf())
    private val claude =
        IntegrationMethodInfo("oauth", "claude", "Claude Pro/Max", persistentListOf<JsonObject>(), persistentListOf(), persistentMapOf())

    @Test
    fun `a provider with nothing to manage and one way in opens that way`() =
        runComposeUiTest {
            val fresh = IntegrationInfo("anthropic", "Anthropic", persistentListOf(keyMethod, claude), persistentListOf(), 0)
            setContent { SettingsThemed { LibraryScreen(providers(fresh), actions) } }
            onAllNodesWithText("Anthropic").onFirst().performClick()
            assertEquals(listOf("show" to listOf<Any?>(LibrarySheet.Method(fresh, keyMethod))), calls.filter { it.first == "show" })
        }

    @Test
    fun `a connected provider opens its sheet`() = runComposeUiTest {
        val connected =
            IntegrationInfo(
                "anthropic",
                "Anthropic",
                persistentListOf(keyMethod),
                persistentListOf(IntegrationConnectionInfo("credential", "c1", "Work", "connected")),
                1,
            )
        setContent { SettingsThemed { LibraryScreen(providers(connected), actions) } }
        onAllNodesWithText("Anthropic").onFirst().performClick()
        assertEquals(listOf("show" to listOf<Any?>(LibrarySheet.Provider(connected))), calls.filter { it.first == "show" })
    }

    private val back =
        object : NavigationEventInput() {
            fun press() {
                dispatchOnBackStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f))
                dispatchOnBackCompleted()
            }
        }
    private var fellThrough = 0
    private val owner =
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher =
                NavigationEventDispatcher(onBackCompletedFallback = { fellThrough++ }).also { it.addInput(back) }
        }

    @Test
    fun `B19 OpenCode Go opens on the key field and one Back returns to the list`() = runComposeUiTest {
        val env = IntegrationMethodInfo("env", null, "env", persistentListOf<JsonObject>(), persistentListOf(), persistentMapOf())
        val go = IntegrationInfo("opencode-go", "OpenCode Go", persistentListOf(keyMethod, env), persistentListOf(), 0)
        var sheet by mutableStateOf<LibrarySheet?>(null)
        var leaves = 0
        val live =
            Proxy.newProxyInstance(LibraryActions::class.java.classLoader, arrayOf(LibraryActions::class.java)) {
                proxy,
                method,
                args ->
                when (method.name) {
                    "equals" -> proxy === args?.getOrNull(0)
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "LiveActions"
                    "show" -> {
                        sheet = args?.getOrNull(0) as LibrarySheet?
                        null
                    }
                    "back" -> {
                        leaves++
                        null
                    }
                    else -> null
                }
            } as LibraryActions
        setContent {
            // The key step's field reports keystrokes to the app's redactor; nothing is typed here.
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides owner,
                LocalKitSecretSink provides KitSecretSink {},
            ) {
                SettingsThemed { LibraryScreen(providers(go).copy(sheet = sheet), live) }
            }
        }
        onAllNodesWithText("OpenCode Go").onFirst().performClick()
        waitForIdle()
        assertEquals(LibrarySheet.Method(go, keyMethod), sheet, "the row opens the key step, not a one-row list")
        onAllNodesWithText("Save API key").onFirst().assertExists()

        runOnIdle { back.press() }
        waitForIdle()
        assertEquals(null, sheet, "one Back closes the key step")
        assertEquals(0, fellThrough + leaves, "the same Back must not also leave the page")
        onAllNodesWithText("OpenCode Go").onFirst().assertExists()

        runOnIdle { back.press() }
        waitForIdle()
        assertEquals(1, fellThrough + leaves, "the next Back leaves the Providers page")
    }
}
