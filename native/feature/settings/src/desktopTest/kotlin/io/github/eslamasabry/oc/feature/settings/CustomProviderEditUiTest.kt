package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.IntegrationConnectionInfo
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.core.model.IntegrationMethodInfo
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderProblem
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderRefusal
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderRestartPhase
import io.github.eslamasabry.oc.engine.integrations.catalog.DeclaredProvider
import io.github.eslamasabry.oc.engine.integrations.catalog.LibraryInventory
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderMode
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderStage
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderUi
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryActions
import io.github.eslamasabry.oc.feature.settings.presentation.LibrarySheet
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryTab
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryUi
import io.github.eslamasabry.oc.feature.settings.ui.LibraryScreen
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Edit and Remove of a provider added from this app (DESIGN §2 and §3, options 1A 2A 3A 4A 5A): the row and the
 * provider sheet for an entry the app wrote, the edit form with its fixed id, every state's plain words, the
 * restart's named step while it waits, and the two questions (Remove, Replace) with what goes.
 */
@OptIn(ExperimentalTestApi::class)
class CustomProviderEditUiTest {
    private val calls = mutableListOf<Pair<String, List<Any?>>>()

    private val actions: LibraryActions =
        Proxy.newProxyInstance(LibraryActions::class.java.classLoader, arrayOf(LibraryActions::class.java)) {
            proxy,
            method,
            args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "EditActions"
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
    private val declared =
        DeclaredProvider(
            "my-box",
            appShaped = true,
            "My box",
            "http://models.example.net:8080/v1",
            listOf("llama-3", "qwen"),
        )
    private val row = IntegrationInfo("my-box", "My box", persistentListOf(keyMethod), persistentListOf(), 0)
    private val connected =
        row.copy(
            connections = persistentListOf(IntegrationConnectionInfo("credential", "c-1", "", null)),
            connectionCount = 1,
        )

    private fun page(
        onThisPhone: Boolean = true,
        rows: List<IntegrationInfo> = listOf(row),
        sheet: LibrarySheet? = null,
        custom: CustomProviderUi = CustomProviderUi(),
        restartPhase: CustomProviderRestartPhase? = null,
    ) = LibraryUi(
        tab = LibraryTab.Providers,
        available = true,
        inventory =
            LibraryInventory(
                loading = false,
                capabilities = if (onThisPhone) phone else remote,
                providers = rows.toImmutableList(),
                declared = (if (onThisPhone) listOf(declared) else emptyList()).toImmutableList(),
                customRestart = restartPhase,
            ),
        sheet = sheet,
        customProvider = custom,
    )

    private fun ui(stage: CustomProviderStage, mode: CustomProviderMode = CustomProviderMode.Add) =
        CustomProviderUi(stage = stage, mode = mode, name = "My box", providerId = "my-box")

    private fun editSheet(stage: CustomProviderStage, mode: CustomProviderMode = CustomProviderMode.Edit) =
        page(sheet = LibrarySheet.EditProvider("my-box"), custom = ui(stage, mode))

    private fun ComposeUiTest.shows(state: LibraryUi) =
        setContent { SettingsThemed { LibraryScreen(state, actions) } }

    @Test
    fun `B-LIB-336 an entry in the file the server does not list gets a row with Restart, Edit and Remove`() =
        runComposeUiTest {
            val stub = IntegrationInfo("my-box", "My box", persistentListOf(), persistentListOf(), 0)
            shows(page(rows = emptyList(), sheet = null))
            onNodeWithText("My box").assertExists()
            onNodeWithText("Added by you · Not loaded").assertExists()
            shows(page(rows = emptyList(), sheet = LibrarySheet.Provider(stub)))
            onNodeWithText("My box is saved").assertExists()
            onNodeWithText("Restart OpenCode on this phone").assertExists()
            onNodeWithText("Edit provider").assertExists()
            onNodeWithText("Remove provider").assertExists()
        }

    @Test
    fun `B-LIB-336b the not-loaded row opens its sheet through show`() = runComposeUiTest {
        val stub = IntegrationInfo("my-box", "My box", persistentListOf(), persistentListOf(), 0)
        shows(page(rows = emptyList(), sheet = null))
        onNodeWithText("My box").performClick()
        assertEquals(listOf<Any?>(LibrarySheet.Provider(stub)), calls.single { it.first == "show" }.second)
    }

    @Test
    fun `B-LIB-337 the phone offers Edit and Remove for its own entry, a remote server offers neither`() =
        runComposeUiTest {
            shows(page(sheet = LibrarySheet.Provider(row)))
            onNodeWithText("Added by you on this phone").assertExists()
            onNodeWithText("Edit provider").assertExists()
            onNodeWithText("Remove provider").assertExists()
            shows(
                page(
                    onThisPhone = false,
                    rows = listOf(connected),
                    sheet = LibrarySheet.Provider(connected),
                ),
            )
            onNodeWithText("Added by you on this phone").assertDoesNotExist()
            onNodeWithText("Edit provider").assertDoesNotExist()
            onNodeWithText("Remove provider").assertDoesNotExist()
        }

    @Test
    fun `B-LIB-338 the row of an app-written provider opens its provider sheet, never the key-form shortcut`() =
        runComposeUiTest {
            shows(page(sheet = null))
            onAllNodesWithText("My box").onFirst().performClick()
            assertEquals(listOf<Any?>(LibrarySheet.Provider(row)), calls.single { it.first == "show" }.second)
        }

    @Test
    fun `B-LIB-340 the edit form fixes the id, fills the values and asks to save changes`() = runComposeUiTest {
        shows(editSheet(CustomProviderStage.Editing))
        onNodeWithText("Edit provider").assertExists()
        onNodeWithText("my-box").assertExists()
        onNodeWithText(
            "Fixed: conversations and defaults use it. To use another ID, remove this provider and add it again.",
        ).assertExists()
        onNodeWithText("My box").assertExists()
        onNodeWithText("http://models.example.net:8080/v1").assertExists()
        onNodeWithText("Save changes").assertExists()
    }

    @Test
    fun `B-LIB-341 the edit form's problems sit under their fields in the same plain words`() = runComposeUiTest {
        shows(
            page(
                sheet = LibrarySheet.EditProvider("my-box"),
                custom =
                    ui(CustomProviderStage.Editing, CustomProviderMode.Edit)
                        .copy(problems = listOf(CustomProviderProblem.NameMissing).toImmutableList()),
            ),
        )
        onNodeWithText("Enter a name.").assertExists()
    }

    @Test
    fun `B-LIB-342 a saved edit waits for the restart it asks for, with Not now`() = runComposeUiTest {
        shows(editSheet(CustomProviderStage.SavedNotLoaded))
        onNodeWithText("My box is saved").assertExists()
        onNodeWithText("OpenCode on this phone loads the change when it restarts. A reply that is running stops.")
            .assertExists()
        onNodeWithText("Restart OpenCode on this phone").assertExists()
        onNodeWithText("Not now").assertExists()
    }

    @Test
    fun `B-LIB-343 while OpenCode restarts the sheet names the step it is on, never an error`() = runComposeUiTest {
        shows(
            page(
                sheet = LibrarySheet.EditProvider("my-box"),
                custom = ui(CustomProviderStage.Restarting, CustomProviderMode.Edit),
                restartPhase = CustomProviderRestartPhase.Restarting,
            ),
        )
        onNodeWithText("Restarting OpenCode on this phone").assertExists()
        onNodeWithText("Waiting is normal: it loads the change as it starts.").assertExists()
        onNodeWithText("Restarting takes three steps").assertExists()
    }

    @Test
    fun `B-LIB-344 the check step says it lists the provider after an edit`() = runComposeUiTest {
        shows(
            page(
                sheet = LibrarySheet.EditProvider("my-box"),
                custom = ui(CustomProviderStage.Restarting, CustomProviderMode.Edit),
                restartPhase = CustomProviderRestartPhase.Checking,
            ),
        )
        onNodeWithText("Check that it lists My box").assertExists()
    }

    @Test
    fun `B-LIB-344b the gone check names the remove`() = runComposeUiTest {
        shows(
            page(
                sheet = LibrarySheet.Provider(row),
                custom = ui(CustomProviderStage.Restarting, CustomProviderMode.Remove),
                restartPhase = CustomProviderRestartPhase.Checking,
            ),
        )
        onNodeWithText("Check that it no longer lists My box").assertExists()
    }

    @Test
    fun `B-LIB-345 an edit the server lists again is updated, and Done closes it`() = runComposeUiTest {
        shows(editSheet(CustomProviderStage.Loaded))
        onNodeWithText("My box is updated").assertExists()
        onNodeWithText("Done").performClick()
        assertTrue(calls.any { it.first == "show" })
    }

    @Test
    fun `B-LIB-346 a failed restart says so, keeps its label in Details and offers the restart again`() =
        runComposeUiTest {
            shows(
                editSheet(CustomProviderStage.RestartFailed)
                    .let { it.copy(customProvider = it.customProvider.copy(detail = "Runtime · Generic")) },
            )
            onNodeWithText("My box is saved, but OpenCode did not restart").assertExists()
            onNodeWithText("Details").assertExists()
            onNodeWithText("Restart OpenCode on this phone").assertExists()
        }

    @Test
    fun `B-LIB-347 a restart that came back still listing it says saved but not loaded`() = runComposeUiTest {
        shows(
            page(
                sheet = LibrarySheet.EditProvider("my-box"),
                custom = ui(CustomProviderStage.NotLoadedAfterRestart, CustomProviderMode.Edit),
            ),
        )
        onNodeWithText("My box is saved but not loaded").assertExists()
        onNodeWithText("Restart OpenCode on this phone").assertExists()
    }

    @Test
    fun `B-LIB-348 an edit refused because the entry changed by hand keeps the typed values`() = runComposeUiTest {
        shows(
            page(
                sheet = LibrarySheet.EditProvider("my-box"),
                custom =
                    ui(CustomProviderStage.Editing, CustomProviderMode.Edit)
                        .copy(refusal = CustomProviderRefusal.ChangedByHand),
            ),
        )
        onNodeWithText("Nothing was saved").assertExists()
        onNodeWithText(
            "This provider was changed by hand in OpenCode’s settings since you opened it. " +
                "The app leaves hand-written settings alone.",
        ).assertExists()
        onNodeWithText("Save changes").assertExists()
    }

    @Test
    fun `B-LIB-349 an edit refused for the file's own reasons uses the existing refusal words`() = runComposeUiTest {
        shows(
            page(
                sheet = LibrarySheet.EditProvider("my-box"),
                custom =
                    ui(CustomProviderStage.Editing, CustomProviderMode.Edit)
                        .copy(refusal = CustomProviderRefusal.Busy, detail = "Exit 23"),
            ),
        )
        onNodeWithText("Another change to OpenCode’s settings is in progress. Try again in a moment.").assertExists()
        onNodeWithText("Details").assertExists()
    }

    @Test
    fun `B-LIB-350 Remove asks one question naming what goes`() = runComposeUiTest {
        shows(page(sheet = LibrarySheet.Provider(row)))
        onNodeWithText("Remove provider").performClick()
        onNodeWithText("Remove My box?").assertExists()
        onNodeWithText("OpenCode on this phone forgets this provider. You can add it again later.").assertExists()
        onNodeWithText("Its models leave the model list.").assertExists()
        onNodeWithText("OpenCode on this phone restarts to finish. A reply that is running stops.").assertExists()
        onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun `B-LIB-350b the Remove question names the saved key only when one is saved`() = runComposeUiTest {
        shows(page(rows = listOf(connected), sheet = LibrarySheet.Provider(connected)))
        onNodeWithText("Remove provider").performClick()
        onNodeWithText("Its saved key is removed.").assertExists()
    }

    @Test
    fun `B-LIB-351 a remove whose server is not answering removed nothing and says how to go on`() =
        runComposeUiTest {
            shows(
                page(
                    sheet = LibrarySheet.Provider(row),
                    custom =
                        ui(CustomProviderStage.Removing, CustomProviderMode.Remove)
                            .copy(refusal = CustomProviderRefusal.CouldNotCheck, detail = "Network · Refused"),
                ),
            )
            onNodeWithText("Nothing was removed").assertExists()
            onNodeWithText(
                "OpenCode on this phone is not answering, so its key could not be removed. " +
                    "Start OpenCode from This phone, then try again.",
            ).assertExists()
            onNodeWithText("Try again").assertExists()
            onNodeWithText("Open This phone").performClick()
            assertTrue(calls.any { it.first == "phoneTools" })
        }

    @Test
    fun `B-LIB-352 a remove whose key went but whose entry stayed says so and offers Try again`() =
        runComposeUiTest {
            shows(
                page(
                    sheet = LibrarySheet.Provider(row),
                    custom = ui(CustomProviderStage.RemovePartial, CustomProviderMode.Remove).copy(detail = "Exit 26"),
                ),
            )
            onNodeWithText("My box is not removed yet").assertExists()
            onNodeWithText("Its key is removed, but OpenCode’s settings file could not be written. Try again.")
                .assertExists()
            onNodeWithText("Try again").assertExists()
        }

    @Test
    fun `B-LIB-353 a remove refused because the entry changed by hand says nothing was removed`() =
        runComposeUiTest {
            shows(
                page(
                    sheet = LibrarySheet.Provider(row),
                    custom =
                        ui(CustomProviderStage.Removing, CustomProviderMode.Remove)
                            .copy(refusal = CustomProviderRefusal.ChangedByHand),
                ),
            )
            onNodeWithText("Nothing was removed").assertExists()
            onNodeWithText(
                "This provider was changed by hand in OpenCode’s settings since you opened it. " +
                    "The app leaves hand-written settings alone.",
            ).assertExists()
        }

    @Test
    fun `B-LIB-354 a removed entry waits for the restart, with Not now`() = runComposeUiTest {
        shows(
            page(
                sheet = LibrarySheet.Provider(row),
                custom = ui(CustomProviderStage.RemovedNotRestarted, CustomProviderMode.Remove),
            ),
        )
        onNodeWithText("My box is removed from the settings").assertExists()
        onNodeWithText("Restart OpenCode on this phone").assertExists()
        onNodeWithText("Not now").assertExists()
    }

    @Test
    fun `B-LIB-355 a removed provider is done, and Done closes it`() = runComposeUiTest {
        shows(
            page(
                sheet = LibrarySheet.Provider(row),
                custom = ui(CustomProviderStage.Removed, CustomProviderMode.Remove),
            ),
        )
        onNodeWithText("My box is removed").assertExists()
        onNodeWithText(
            "Its key and its entry in OpenCode’s settings are gone. OpenCode on this phone restarted.",
        ).assertExists()
        onNodeWithText("Done").performClick()
        assertTrue(calls.any { it.first == "show" })
    }

    @Test
    fun `B-LIB-356 adding the same id again asks Replace, and Keep editing writes nothing`() = runComposeUiTest {
        shows(page(sheet = LibrarySheet.AddProvider, custom = ui(CustomProviderStage.AskReplace)))
        onNodeWithText("Replace My box?").assertExists()
        onNodeWithText("OpenCode on this phone already has a provider with the ID my-box, added from this app.")
            .assertExists()
        onNodeWithText("Its address and models become the ones you typed.").assertExists()
        onNodeWithText("OpenCode on this phone restarts to load it. A reply that is running stops.").assertExists()
        onNodeWithText("Replace provider").assertExists()
        onNodeWithText("Keep editing").performClick()
        assertTrue(calls.any { it.first == "keepEditingProvider" })
    }

    @Test
    fun `B-LIB-359 a refused remove does not stick to the provider sheet`() {
        assertEquals(
            CustomProviderUi(),
            ui(CustomProviderStage.Removing, CustomProviderMode.Remove)
                .copy(refusal = CustomProviderRefusal.ChangedByHand)
                .reopened(),
        )
        val waiting = ui(CustomProviderStage.RemovedNotRestarted, CustomProviderMode.Remove)
        assertEquals(waiting, waiting.reopened())
    }

    @Test
    fun `B-LIB-360 a failed restart from a stranded entry's sheet says it failed`() = runComposeUiTest {
        val stub = IntegrationInfo("my-box", "My box", persistentListOf(), persistentListOf(), 0)
        shows(
            page(
                rows = emptyList(),
                sheet = LibrarySheet.Provider(stub),
                custom = ui(CustomProviderStage.RestartFailed).copy(detail = "Runtime · Generic"),
            ),
        )
        onNodeWithText("My box is saved, but OpenCode did not restart").assertExists()
        onNodeWithText("Try again, or start OpenCode from This phone. The provider loads when OpenCode starts.")
            .assertExists()
        onNodeWithText("Details").assertExists()
        onNodeWithText("My box is saved").assertDoesNotExist()
        onNodeWithText("Restart OpenCode on this phone").performClick()
        assertEquals(listOf<Any?>("my-box"), calls.single { it.first == "restartForProvider" }.second)
        shows(
            page(
                rows = emptyList(),
                sheet = LibrarySheet.Provider(stub),
                custom = ui(CustomProviderStage.NotLoadedAfterRestart),
            ),
        )
        onNodeWithText("My box is saved but not loaded").assertExists()
        onNodeWithText("OpenCode restarted but does not list it yet. Try restarting it again.").assertExists()
        onNodeWithText("My box is saved").assertDoesNotExist()
        onNodeWithText("Restart OpenCode on this phone").assertExists()
    }
}
