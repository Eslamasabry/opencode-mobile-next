package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
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
import io.github.eslamasabry.oc.ui.routes.ServerSettingsPage
import java.lang.reflect.Proxy
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class B605ServerAutomationsBeforeRendersTest {
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
                    lastVersion = "2.4.1",
                    canCheck = true,
                    capabilities = ServerCapabilities(scheduledPrompts = true),
                    managedOnPhone = false,
                ),
            health = Health(healthy = true, version = "2.4.1"),
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
        )

    @Test
    fun `B-AUTO-002 server page lists Scheduled prompts when supported`() {
        Locale.setDefault(Locale.ENGLISH)
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    ServerSettingsScreen(laptop, actions)
                }
            }
            waitForIdle()
            onNodeWithText("Scheduled prompts").performScrollTo().assertIsDisplayed()
        }
    }
}
