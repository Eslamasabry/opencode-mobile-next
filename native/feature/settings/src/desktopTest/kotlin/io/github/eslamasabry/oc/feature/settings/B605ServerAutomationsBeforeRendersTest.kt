package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.Health
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.settings.presentation.ServerLocalFacts
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.ServerSettingsScreen
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.routes.ServerSettingsPage
import java.io.File
import java.lang.reflect.Proxy
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class B605ServerAutomationsBeforeRendersTest {
    private val out = File("build/b605-renders").also { it.mkdirs() }
    private val savedLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private val actions =
        Proxy.newProxyInstance(
            ServerSettingsActions::class.java.classLoader,
            arrayOf(ServerSettingsActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "B605NoopActions"
                else -> null
            }
        } as ServerSettingsActions

    private val laptop =
        ServerSettingsUi(
            page = ServerSettingsPage.Home,
            loading = false,
            facts =
                ServerSettingsFacts(
                    name = "Laptop",
                    address = "https://laptop.example:4096",
                    connected = true,
                    hasSecret = true,
                    lastVersion = "2.3.2",
                    canCheck = true,
                    capabilities =
                        ServerCapabilities(scheduledPrompts = true, serverAutomations = false),
                    managedOnPhone = false,
                ),
            health = Health(healthy = true, version = "2.3.2"),
            local =
                ServerLocalFacts(
                    model = ModelRef("opencode", "claude-sonnet-4"),
                    watching = true,
                    notifications = true,
                    monitorSupported = true,
                    sharingRead = false,
                    sharingDefault = false,
                    sharingSaving = false,
                ),
            timeZoneId = "Europe/Berlin",
        )

    @Test
    fun `B-AUTO-002 server page without serverAutomations stays clean and captures before pictures`() {
        Locale.setDefault(Locale.ENGLISH)
        val variants =
            listOf(
                Triple("light-10", false, 1.0f),
                Triple("dark-10", true, 1.0f),
                Triple("light-13", false, 1.3f),
                Triple("dark-13", true, 1.3f),
            )
        for ((name, dark, scale) in variants) {
            runDesktopComposeUiTest(360, 800) {
                setContent {
                    SettingsThemed(dark = dark, locale = "en", fontScale = scale, width = 360f) {
                        KitSheetHost(Modifier.fillMaxSize()) {
                            ServerSettingsScreen(laptop, actions)
                        }
                    }
                }
                waitForIdle()
                onNodeWithText("Always allowed actions").performScrollTo().assertIsDisplayed()
                onNodeWithText("Automations").assertDoesNotExist()
                onNodeWithText("Scheduled prompts").assertDoesNotExist()
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(),
                    "png",
                    File(out, "before-server-$name.png"),
                )
            }
        }
    }
}
