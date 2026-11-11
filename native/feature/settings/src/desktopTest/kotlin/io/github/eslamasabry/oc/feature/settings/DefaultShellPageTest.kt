package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.TerminalShellOption
import io.github.eslamasabry.oc.core.model.TerminalShellSettings
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.ServerSettingsScreen
import io.github.eslamasabry.oc.ui.routes.ServerSettingsPage
import java.lang.reflect.Proxy
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/** Sweep B C2: Settings > Default shell lists the server's shells, and a failed read offers one Try again, not two. */
@OptIn(ExperimentalTestApi::class)
class DefaultShellPageTest {
    private var savedLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(savedLocale)

    private val actions =
        Proxy.newProxyInstance(
            ServerSettingsActions::class.java.classLoader,
            arrayOf(ServerSettingsActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "DefaultShellActions"
                else -> null
            }
        } as ServerSettingsActions

    private fun page(shells: TerminalShellSettings? = null, failure: Failure? = null) =
        ServerSettingsUi(
            page = ServerSettingsPage.Shell,
            loading = false,
            facts =
                ServerSettingsFacts(
                    name = "This phone",
                    address = "http://127.0.0.1:4096",
                    connected = true,
                    hasSecret = true,
                    lastVersion = "2.2.6",
                    canCheck = true,
                    capabilities = ServerCapabilities(shellSettings = true),
                    managedOnPhone = true,
                ),
            shells = shells,
            failure = failure,
        )

    @Test
    fun `a failed shell read shows one Try again`() = runComposeUiTest {
        setContent { SettingsThemed { ServerSettingsScreen(page(failure = Failure.UnreadableReply()), actions) } }
        waitForIdle()
        onAllNodesWithText("Try again").assertCountEquals(1)
    }

    @Test
    fun `the shells a 2_2_6 server lists are offered`() = runComposeUiTest {
        val shells =
            TerminalShellSettings(
                "",
                persistentListOf(
                    TerminalShellOption("/bin/sh", "sh", true),
                    TerminalShellOption("/usr/bin/bash", "bash", true),
                    TerminalShellOption("/usr/bin/fish", "fish", false),
                ),
            )
        setContent { SettingsThemed { ServerSettingsScreen(page(shells = shells), actions) } }
        waitForIdle()
        onNodeWithText("Automatic (server default)").assertExists()
        onNodeWithText("bash").assertExists()
        onNodeWithText("fish").assertExists()
        onAllNodesWithText("Try again").assertCountEquals(0)
    }
}
