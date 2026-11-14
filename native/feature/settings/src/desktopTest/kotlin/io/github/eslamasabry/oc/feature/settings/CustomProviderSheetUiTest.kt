package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.core.model.IntegrationMethodInfo
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderDraft
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderProblem
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderRefusal
import io.github.eslamasabry.oc.engine.integrations.catalog.LibraryInventory
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderStage
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderUi
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryActions
import io.github.eslamasabry.oc.feature.settings.presentation.LibrarySheet
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryTab
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryUi
import io.github.eslamasabry.oc.feature.settings.ui.LibraryScreen
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Pull request 142, ported: the Providers page offers Add a provider (above its rows and in its empty state), and the
 * sheet shows each state in plain words: why a remote server cannot, the untouched form, the problems Save found, a
 * refusal, saved and not loaded, a failed restart, a restart that did not load it, and loaded with its key next.
 */
@OptIn(ExperimentalTestApi::class)
class CustomProviderSheetUiTest {
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

    private val phone = ServerCapabilities(serverCatalog = true, customProviders = true)
    private val remote = ServerCapabilities(serverCatalog = true)
    private val keyMethod =
        IntegrationMethodInfo(
            "key",
            null,
            "Manually enter API Key",
            persistentListOf(),
            persistentListOf(),
            persistentMapOf(),
        )
    private val declared = IntegrationInfo("my-box", "My box", persistentListOf(keyMethod), persistentListOf(), 0)

    private fun page(
        capabilities: ServerCapabilities = phone,
        rows: List<IntegrationInfo> = emptyList(),
        sheet: LibrarySheet? = LibrarySheet.AddProvider,
        custom: CustomProviderUi = CustomProviderUi(),
    ) =
        LibraryUi(
            tab = LibraryTab.Providers,
            available = true,
            inventory =
                LibraryInventory(loading = false, capabilities = capabilities, providers = rows.toImmutableList()),
            sheet = sheet,
            customProvider = custom,
        )

    private fun saved(stage: CustomProviderStage) =
        CustomProviderUi(stage = stage, name = "My box", providerId = "my-box")

    private fun field(tag: String): SemanticsMatcher =
        hasSetTextAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)))

    private fun ComposeUiTest.shows(state: LibraryUi) =
        setContent {
            CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
                SettingsThemed { LibraryScreen(state, actions) }
            }
        }

    @Test
    fun `PR142-24 the Providers page offers Add a provider above its rows and in its empty state`() =
        runComposeUiTest {
            shows(page(rows = listOf(declared.copy(id = "openai", name = "OpenAI")), sheet = null))
            onAllNodesWithText("Add a provider").onFirst().performClick()
            assertEquals(listOf<Any?>(LibrarySheet.AddProvider), calls.single { it.first == "show" }.second)
        }

    @Test
    fun `PR142-25 an empty Providers page says so and offers Add a provider`() = runComposeUiTest {
        shows(page(sheet = null))
        onNodeWithText("No provider connections available").assertExists()
        onAllNodesWithText("Add a provider").onFirst().performClick()
        assertEquals(listOf<Any?>(LibrarySheet.AddProvider), calls.single { it.first == "show" }.second)
    }

    @Test
    fun `PR142-26 a server whose settings the app does not own explains why, with no form`() = runComposeUiTest {
        shows(page(capabilities = remote))
        onNodeWithText("keeps its own settings", substring = true).assertExists()
        onNodeWithText("Save provider").assertDoesNotExist()
        onNodeWithText("Provider ID").assertDoesNotExist()
    }

    @Test
    fun `PR142-27 an untouched form is not red, and Save sends exactly what was typed`() = runComposeUiTest {
        shows(page())
        onNodeWithText("Enter a name.").assertDoesNotExist()
        onNodeWithText("Add at least one model ID.").assertDoesNotExist()
        onNode(field("custom-provider-name")).performTextInput("My box")
        onNode(field("custom-provider-id")).performTextInput("my-box")
        onNode(field("custom-provider-address")).performTextInput("http://models.example.net:8080/v1")
        onNode(field("custom-provider-models")).performTextInput("llama-3")
        onNodeWithText("Save provider").performClick()
        assertEquals(
            CustomProviderDraft("My box", "my-box", "http://models.example.net:8080/v1", "llama-3"),
            calls.single { it.first == "addProvider" }.second.single(),
        )
    }

    @Test
    fun `PR142-28 the problems Save found sit under their fields in plain words`() = runComposeUiTest {
        val problems =
            listOf(
                CustomProviderProblem.NameMissing,
                CustomProviderProblem.IdTaken,
                CustomProviderProblem.AddressHasCredentials,
                CustomProviderProblem.ModelInvalid,
            )
        shows(page(custom = CustomProviderUi(problems = problems.toImmutableList())))
        onNodeWithText("Enter a name.").assertExists()
        onNodeWithText("This server already has a provider with this ID. Choose another one.").assertExists()
        onNodeWithText("Leave the user name and password out of the address", substring = true).assertExists()
        onNodeWithText("Model IDs can’t contain spaces or #. Put one ID on each line.").assertExists()
    }

    @Test
    fun `PR142-29 a refused save says nothing was saved, why in plain words, and keeps the form`() = runComposeUiTest {
        shows(page(custom = CustomProviderUi(refusal = CustomProviderRefusal.Busy, detail = "Exit 23")))
        onNodeWithText("Nothing was saved").assertExists()
        onNodeWithText("Another change to OpenCode’s settings is in progress. Try again in a moment.").assertExists()
        onNodeWithText("Details").assertExists()
        onNodeWithText("Save provider").assertExists()
    }

    @Test
    fun `PR142-30 saved but not loaded offers the restart by its target`() = runComposeUiTest {
        shows(page(custom = saved(CustomProviderStage.SavedNotLoaded)))
        onNodeWithText("My box is saved").assertExists()
        onNodeWithText("A reply that is running stops", substring = true).assertExists()
        onNodeWithText("Restart OpenCode on this phone").performClick()
        assertEquals(1, calls.count { it.first == "restartForProvider" })
    }

    @Test
    fun `PR142-31 a failed restart says so, keeps its label in Details and offers the restart again`() =
        runComposeUiTest {
            shows(page(custom = saved(CustomProviderStage.RestartFailed).copy(detail = "Runtime · Generic")))
            onNodeWithText("My box is saved, but OpenCode did not restart").assertExists()
            onNodeWithText("Details").assertExists()
            onNodeWithText("Restart OpenCode on this phone").assertExists()
        }

    @Test
    fun `PR142-32 a restart that came back without the provider says saved but not loaded`() = runComposeUiTest {
        shows(page(custom = saved(CustomProviderStage.NotLoadedAfterRestart)))
        onNodeWithText("My box is saved but not loaded").assertExists()
        onNodeWithText("Restart OpenCode on this phone").assertExists()
    }

    @Test
    fun `PR142-33 loaded says ready and opens the key step of its own row`() = runComposeUiTest {
        shows(page(rows = listOf(declared), custom = saved(CustomProviderStage.Loaded)))
        onNodeWithText("My box is ready").assertExists()
        onNodeWithText("Restart OpenCode on this phone").assertDoesNotExist()
        onNodeWithText("Add key for My box").performClick()
        assertEquals(
            listOf<Any?>(LibrarySheet.Method(declared, keyMethod)),
            calls.single { it.first == "show" }.second,
        )
    }
}
