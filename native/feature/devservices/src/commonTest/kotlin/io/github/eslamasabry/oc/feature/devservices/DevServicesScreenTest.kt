@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.devservices

import androidx.compose.foundation.Indication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.runtime.services.commands.DevelopmentServiceStatus
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesActions
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesProblem
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesUi
import io.github.eslamasabry.oc.feature.devservices.presentation.FoundRowUi
import io.github.eslamasabry.oc.feature.devservices.presentation.ServiceRowUi
import io.github.eslamasabry.oc.feature.devservices.ui.DevServicesScreen
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatformFamily
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Development services page says the Flutter words for its states and routes taps to its intents. */
class DevServicesScreenTest {
    private object TestPlatform : KitPlatform {
        override val name: String = "test"
        override val family: KitPlatformFamily = KitPlatformFamily.Desktop
        override val signals: StateFlow<KitSystemSignals> = MutableStateFlow(KitSystemSignals())

        override fun pressIndication(ink: Color): Indication = KitFillIndication(ink)

        override fun haptic(kind: KitHaptic) = Unit

        override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? = null
    }

    // Window roots provide the graph's redactor (0d38a8abc); mono command text never draws without one.
    @Composable
    private fun Themed(content: @Composable () -> Unit) =
        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
            KitTheme(platform = TestPlatform, localeTag = "en", stillFrame = true, content = content)
        }

    private class Calls : DevServicesActions {
        val seen = mutableListOf<String>()

        override fun shown(visible: Boolean) = Unit
        override fun refresh() { seen += "refresh" }
        override fun add() { seen += "add" }
        override fun saveFound(id: String) { seen += "saveFound $id" }
        override fun edit(name: String, command: String, url: String) = Unit
        override fun save() { seen += "save" }
        override fun closeEditor() = Unit
        override fun start(id: String) { seen += "start $id" }
        override fun askStop(id: String) { seen += "askStop $id" }
        override fun askRestart(id: String) { seen += "askRestart $id" }
        override fun askForget(id: String) { seen += "askForget $id" }
        override fun askRemove(id: String) { seen += "askRemove $id" }
        override fun logs(id: String) { seen += "logs $id" }
        override fun visit(id: String) { seen += "visit $id" }
        override fun askStopFound(id: String) { seen += "askStopFound $id" }
        override fun foundLogs(id: String) { seen += "foundLogs $id" }
        override fun confirm() { seen += "confirm" }
        override fun cancel() { seen += "cancel" }
        override fun dismiss() { seen += "dismiss" }
        override fun back() { seen += "back" }
    }

    private val web =
        ServiceRowUi("s1", "web", "npm run dev", "", DevelopmentServiceStatus.NotStarted, null, canStart = true, canStop = false)

    @Test
    fun `B-LINUX-060 a saved service shows its status and command and Start starts it`() =
        runComposeUiTest {
            val calls = Calls()
            setContent {
                Themed { DevServicesScreen(DevServicesUi(directory = "/work/shopfront", supported = true, loading = false, services = persistentListOf(web)), calls) }
            }
            onNodeWithText("Development services").assertExists()
            onNodeWithText("Not started · npm run dev", substring = true).assertExists()
            // Flutter: Start is the row's icon button, named for the service.
            onNodeWithContentDescription("Start web").performClick()
            onNodeWithText("Register service").performClick()
            assertEquals(listOf("start s1", "add"), calls.seen)
            // Flutter keeps the project folder in the folded Details, under "Project folder".
            onNodeWithText("Details").performClick()
            onNodeWithText("/work/shopfront", substring = true, useUnmergedTree = true).assertExists()
        }

    @Test
    fun `B-LINUX-061 a server that cannot track commands says so and still lists saved services`() =
        runComposeUiTest {
            setContent { Themed { DevServicesScreen(DevServicesUi(supported = false, loading = false, services = persistentListOf(web)), Calls()) } }
            onNodeWithText("This server cannot start and track development commands", substring = true).assertExists()
            onNodeWithText("web").assertExists()
            onNodeWithText("Start").assertDoesNotExist()
        }

    @Test
    fun `B-LINUX-058 a found dev server shows its port and Stop asks first`() =
        runComposeUiTest {
            val calls = Calls()
            val found = FoundRowUi("p:42", "vite", "node vite --port 5173", persistentListOf(5173), canStop = true, hasLogs = true, canSave = true)
            setContent {
                Themed { DevServicesScreen(DevServicesUi(supported = true, loading = false, scansPhone = true, found = persistentListOf(found)), calls) }
            }
            onNodeWithText("Also running").assertExists()
            onNodeWithText("Found by the ports they listen on", substring = true).assertExists()
            // Flutter's found row: "Running command · Port 5173" ("Preview port" names a menu entry when there are several).
            onNodeWithText("Running command · Port 5173", substring = true).assertExists()
            onNodeWithContentDescription("Stop vite").performClick()
            assertEquals(listOf("askStopFound p:42"), calls.seen)
        }

    @Test
    fun `B-LINUX-063 with no live server the page says the server is not answering`() =
        runComposeUiTest {
            // Offline is a problem on a loaded page (Flutter servicesOffline); available = false is the
            // scope-changed state. Registering stays possible, as in Flutter (_canRegister ignores the connection).
            setContent { Themed { DevServicesScreen(DevServicesUi(supported = true, loading = false, problem = DevServicesProblem.Offline), Calls()) } }
            onNodeWithText("The server is not answering", substring = true).assertExists()
            onNodeWithText("The server or project changed", substring = true).assertDoesNotExist()
        }
}
