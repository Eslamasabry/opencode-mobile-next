package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.catalog.LibraryInventory
import io.github.eslamasabry.oc.engine.settings.consent.ConsentChoice
import io.github.eslamasabry.oc.engine.settings.consent.InFlowConsentKind
import io.github.eslamasabry.oc.engine.settings.consent.InFlowConsentRow
import io.github.eslamasabry.oc.feature.settings.presentation.ConfigurationSources
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryActions
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryTab
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryUi
import io.github.eslamasabry.oc.feature.settings.presentation.NeedsYouAlerts
import io.github.eslamasabry.oc.feature.settings.presentation.PermissionSettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.PermissionSettingsUi
import io.github.eslamasabry.oc.feature.settings.presentation.ReportUi
import io.github.eslamasabry.oc.feature.settings.presentation.ServerCapabilitiesUi
import io.github.eslamasabry.oc.feature.settings.presentation.ServerConfigurationActions
import io.github.eslamasabry.oc.feature.settings.presentation.ServerFeatureId
import io.github.eslamasabry.oc.feature.settings.presentation.ServerFeatureUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.LibraryScreen
import io.github.eslamasabry.oc.feature.settings.ui.PermissionSettingsContent
import io.github.eslamasabry.oc.feature.settings.ui.ServerConfigurationScreen
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.lang.reflect.Proxy
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** w4-settings-truth (sweep-b 2026-11-11): what each fixed Settings page now shows. */
@OptIn(ExperimentalTestApi::class)
class SettingsTruthUiTest {
    private var savedLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest fun restore() = Locale.setDefault(savedLocale)

    @Test
    fun `N1 the needs-you row says why it is off in the words of the switch that is off`() = runComposeUiTest {
        val facts =
            PermissionSettingsFacts(
                profile = ProfileId("phone"),
                name = "This phone",
                ticket = 1,
                answers = persistentListOf(InFlowConsentRow(InFlowConsentKind.NeedsYouNotifications, ConsentChoice.Denied)),
                writable = true,
                declined = 0,
                needsYou = NeedsYouAlerts.OnlyWhileOpen,
            )
        setContent {
            SettingsThemed {
                PermissionSettingsContent(PermissionSettingsUi(facts), {}, {}, {}, {}, {}, {}, {})
            }
        }
        onNodeWithText("You're notified only while the app is open", substring = true).assertExists()
        onAllNodesWithText("You won't get a notification", substring = true).assertCountEquals(0)
    }

    @Test
    fun `S2 a connected server's missing features offer another server, never "no server yet"`() = runComposeUiTest {
        val actions = RecordingActions()
        val state =
            SettingsUi(
                stack = persistentListOf(SettingsPage.Hub, SettingsPage.ServerCapabilities),
                capabilities =
                    ServerCapabilitiesUi(
                        "This phone",
                        listOf(
                            ServerFeatureUi(ServerFeatureId.Share, available = false, device = false, capability = null),
                            ServerFeatureUi(ServerFeatureId.Files, available = true, device = false, capability = null),
                        ),
                    ),
            )
        setContent { SettingsThemed { SettingsScreen(state, actions) } }
        onAllNodesWithText("There's no server yet", substring = true).assertCountEquals(0)
        onNodeWithTag("capabilities-add-server").performClick()
        assertTrue("outside:Servers" in actions.calls, actions.calls.toString())
    }

    @Test
    fun `S4 a server with no configuration file says so in words, with no source as the page`() = runComposeUiTest {
        val ready = ConfigurationSources.ready("""{"sources":[{"type":"directory","path":"/srv/a/.config/opencode"}]}""")
        setContent { SettingsThemed { ServerConfigurationScreen(ready, ServerConfigurationActions {}) {} } }
        onNodeWithText("No configuration file yet").assertExists()
        onNodeWithText("/srv/a/.config/opencode", substring = true).assertExists()
        onAllNodesWithText("\"sources\"", substring = true).assertCountEquals(0)
    }

    @Test
    fun `S4 a configuration file is listed by name and a key in it is never shown`() = runComposeUiTest {
        val secret = "sk-ant-api03-SECRETVALUE0123456789"
        val ready =
            ConfigurationSources.ready(
                """{"sources":[{"type":"document","path":"/srv/a/.config/opencode/opencode.json",""" +
                    """"info":{"provider":{"anthropic":{"options":{"apiKey":"$secret"}}}}}]}"""
            )
        setContent { SettingsThemed { ServerConfigurationScreen(ready, ServerConfigurationActions {}) {} } }
        onNodeWithText("opencode.json", substring = true).assertExists()
        onAllNodesWithText("No configuration file yet").assertCountEquals(0)
        onAllNodesWithText("SECRETVALUE", substring = true).assertCountEquals(0)
    }

    @Test
    fun `A2 the providers page composes only the rows on screen`() = runComposeUiTest {
        val providers =
            (0 until 300)
                .map { IntegrationInfo("p%03d".format(it), "Provider %03d".format(it), persistentListOf(), persistentListOf(), 0) }
                .toImmutableList()
        val state =
            LibraryUi(
                tab = LibraryTab.Providers,
                available = true,
                inventory = LibraryInventory(loading = false, providers = providers),
            )
        setContent {
            SettingsThemed {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) { LibraryScreen(state, libraryActions()) }
            }
        }
        onNodeWithText("Provider 000").assertExists()
        onAllNodesWithText("Provider 299").assertCountEquals(0)
    }

    @Test
    fun `H4 the kept errors say what they are and that they are not part of the report`() = runComposeUiTest {
        val state =
            SettingsUi(
                stack = persistentListOf(SettingsPage.Hub, SettingsPage.ReportProblem),
                report = ReportUi(canSend = false, errorsKept = 17, errorLog = "Error"),
            )
        setContent { SettingsThemed { SettingsScreen(state, RecordingActions()) } }
        onNodeWithText("17 recent errors").assertExists()
        onNodeWithText("Errors the app noticed on its own", substring = true).assertExists()
    }

    private fun libraryActions(): LibraryActions =
        Proxy.newProxyInstance(LibraryActions::class.java.classLoader, arrayOf(LibraryActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "TruthLibraryActions"
                else -> null
            }
        } as LibraryActions
}
