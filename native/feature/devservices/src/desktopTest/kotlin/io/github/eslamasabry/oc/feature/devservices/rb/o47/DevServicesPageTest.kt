package io.github.eslamasabry.oc.feature.devservices.rb.o47

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.runtime.services.commands.DevelopmentServiceStatus
import io.github.eslamasabry.oc.feature.devservices.presentation.*
import io.github.eslamasabry.oc.feature.devservices.ui.DevServicesScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class DevServicesPageTest {
    private val calls = mutableListOf<Pair<String, List<Any?>>>()
    private val actions =
        Proxy.newProxyInstance(
            DevServicesActions::class.java.classLoader,
            arrayOf(DevServicesActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "DevServiceActions"
                "shown" -> null
                else -> {
                    calls += method.name to args?.toList().orEmpty()
                    null
                }
            }
        } as DevServicesActions
    private val running =
        ServiceRowUi(
            "preview",
            "Shopfront preview",
            "npm run dev",
            "http://localhost:5173",
            DevelopmentServiceStatus.Running,
            null,
            canStart = false,
            canStop = true,
            ports = persistentListOf(5173),
        )
    private val state =
        DevServicesUi(
            directory = "/work/shop",
            supported = true,
            loading = false,
            services = persistentListOf(running),
        )
    private val environment = KitChatEnvironment(Redactor())

    @Composable
    private fun Page(ui: DevServicesUi) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            environment.Provide { DevServicesScreen(ui, actions) }
        }
    }

    @Test
    fun `B-LINUX-061 running service has one row with named stop and logs actions`() =
        runComposeUiTest {
            setContent { Page(state) }
            onNodeWithText("/work/shop", substring = true).assertDoesNotExist()
            onNodeWithText("Running command · Port 5173 · npm run dev").assertExists()
            onNodeWithContentDescription("Stop Shopfront preview").performClick()
            onNodeWithTag("development-service-preview").performClick()
            assertEquals(listOf<Pair<String, List<Any?>>>("askStop" to listOf("preview"), "logs" to listOf("preview")), calls)
        }

    @Test
    fun `B-LINUX-061 visible service menu keeps the named remove target`() = runComposeUiTest {
        setContent { Page(state) }
        onNodeWithContentDescription("More · Shopfront preview").performClick()
        onNodeWithText("Restart").assertExists()
        onNodeWithText("Copy command").assertExists()
        onNodeWithText("Remove configuration").performClick() // Flutter servicesRemove
        assertEquals(listOf<Pair<String, List<Any?>>>("askRemove" to listOf("preview")), calls)
    }

    @Test
    fun `B-LINUX-061 empty list offers register service and keeps the folder in Details`() =
        runComposeUiTest {
            setContent { Page(state.copy(services = persistentListOf())) }
            onNodeWithText("No dev commands yet").assertExists()
            onNodeWithText("Register service").performClick()
            assertEquals(listOf("add" to emptyList()), calls)
            onNodeWithText("Details").performClick()
            onNodeWithText("/work/shop", useUnmergedTree = true).assertExists()
        }

    @Test
    fun `B-LINUX-061 log sheet closes before the named stop confirmation opens`() =
        runComposeUiTest {
            setContent {
                Page(
                    state.copy(log = DevServiceLog("preview", false, "Shopfront preview", "ready"))
                )
            }
            onNodeWithText("Stop").performClick()
            assertEquals(listOf("closeLogs" to emptyList(), "askStop" to listOf("preview")), calls)
        }
}
