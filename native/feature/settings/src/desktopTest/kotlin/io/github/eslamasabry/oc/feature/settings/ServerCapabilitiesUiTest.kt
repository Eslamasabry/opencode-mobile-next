package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.feature.settings.presentation.CapabilitiesUi
import io.github.eslamasabry.oc.feature.settings.presentation.FeatureRowUi
import io.github.eslamasabry.oc.feature.settings.presentation.ServerFeature
import io.github.eslamasabry.oc.feature.settings.presentation.ServerLine
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.feature.settings.ui.featureDetail
import io.github.eslamasabry.oc.feature.settings.ui.featureTitle
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.capabilityAddServer
import io.github.eslamasabry.oc.ui.words.res.capabilityAddServerDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityAllAvailable
import io.github.eslamasabry.oc.ui.words.res.capabilityAvailableCount
import io.github.eslamasabry.oc.ui.words.res.capabilityAvailableCountDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityScreenIntro
import io.github.eslamasabry.oc.ui.words.res.capabilityScreenTitle
import io.github.eslamasabry.oc.ui.words.res.capabilityStateHere
import io.github.eslamasabry.oc.ui.words.res.capabilityStateNotDevice
import io.github.eslamasabry.oc.ui.words.res.capabilityStateNotServer
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Available on this server on screen (B-L10N-018): words in both languages, no backend name, 320 dp at 2.5x text. */
@OptIn(ExperimentalTestApi::class)
class ServerCapabilitiesUiTest {
    private val saved: Locale = Locale.getDefault()
    private lateinit var presenter: SettingsPresenter

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    /** No catalogue, files or terminal: the Flutter Codex shape, where most rows say what is missing. */
    private val sparse =
        ServerCapabilities(
            serverCatalog = false, toolInventory = false, terminal = false, fileBrowsing = false, sessionDiff = false,
            projectManagement = false, sessionShare = false, sessionFork = false, shellSettings = false,
        )

    @Composable
    private fun Themed(locale: String, width: Int, height: Int, fontScale: Float = 1f, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
            KitTheme(
                appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
                platform = DesktopKitPlatform(),
                localeTag = locale,
                stillFrame = true,
            ) {
                Box(Modifier.size(width.dp, height.dp).background(LocalKitColors.current.ground)) { content() }
            }
        }
    }

    /** Every word the page can show, in [locale]. */
    private fun ComposeUiTest.words(locale: String): List<String> {
        Locale.setDefault(Locale.forLanguageTag(locale))
        val seen = mutableListOf<String>()
        setContent {
            Themed(locale, 320, 200) {
                Column {
                    seen.clear()
                    seen += stringResource(Res.string.capabilityScreenTitle)
                    seen += stringResource(Res.string.capabilityScreenIntro, "Workstation")
                    seen += stringResource(Res.string.capabilityAllAvailable)
                    seen += stringResource(Res.string.capabilityStateHere)
                    seen += stringResource(Res.string.capabilityStateNotServer)
                    seen += stringResource(Res.string.capabilityStateNotDevice)
                    seen += pluralStringResource(Res.plurals.capabilityAvailableCount, 17, 17)
                    seen += stringResource(Res.string.capabilityAvailableCountDetail)
                    seen += stringResource(Res.string.capabilityAddServer)
                    seen += stringResource(Res.string.capabilityAddServerDetail)
                    ServerFeature.entries.forEach {
                        val title = featureTitle(it)
                        val detail = featureDetail(it)
                        assertTrue(title.isNotBlank() && detail.isNotBlank(), "${it.id} has no words in $locale")
                        seen += title
                        seen += detail
                    }
                }
            }
        }
        waitForIdle()
        return seen.toList()
    }

    @Test
    fun `B-L10N-018 every feature has words in English and no backend name appears on the page`() =
        runComposeUiTest {
            val words = words("en").joinToString("\n").lowercase()
            for (name in listOf("opencode", "codex", "paseo", "claude", "termux")) assertFalse(name in words, name)
        }

    @Test
    fun `B-L10N-018 every feature has words in Arabic, its own`() =
        runComposeUiTest {
            val ar = words("ar")
            assertTrue(ar.isNotEmpty())
            assertTrue(ar.any { it.any { c -> c in '؀'..'ۿ' } }, "the Arabic words are Arabic")
            val joined = ar.joinToString("\n").lowercase()
            for (name in listOf("opencode", "codex", "paseo", "claude", "termux")) assertFalse(name in joined, name)
        }

    private fun ComposeUiTest.show(locale: String, facts: SettingsFacts, width: Int = 320, height: Int = 915, fontScale: Float = 1f) {
        Locale.setDefault(Locale.forLanguageTag(locale))
        setContent {
            Themed(locale, width, height, fontScale) {
                val scope = rememberCoroutineScope()
                val p = remember { SettingsPresenter(scope, MutableStateFlow(facts), FakeChoices(), RecordingOpeners()).also { presenter = it } }
                val state by p.state.collectAsState()
                SettingsScreen(state, p)
            }
        }
        waitForIdle()
    }

    private fun sparseFacts() =
        phoneFacts(ServerLine("Workstation", onPhone = false)).copy(capabilities = sparse, opens = setOf(SettingsOutside.Servers))

    @Test
    fun `B-L10N-018 Help opens Available on this server, which names the server and folds what works`() =
        runComposeUiTest {
            show("en", sparseFacts(), width = 412, height = 915)
            presenter.onOpen(SettingsPage.Group(SettingsGroupId.Help)); waitForIdle()
            onNodeWithText("Available on this server").performScrollTo().performClick(); waitForIdle()
            onNodeWithTag("server-capabilities-intro").assertExists()
            onNodeWithText("Workstation", substring = true).assertExists()
            onNodeWithTag("capability-unavailable-files").performScrollTo().assertExists()
            onNodeWithTag("capability-available-voice").assertDoesNotExist()
            onNodeWithTag("capabilities-add-server").performScrollTo().assertExists()
            onNodeWithTag("capabilities-available").performScrollTo().performClick(); waitForIdle()
            onNodeWithTag("capability-available-voice").performScrollTo().assertExists()
        }

    @Test
    fun `B-L10N-018 a server with everything says so once`() =
        runComposeUiTest {
            show("en", phoneFacts().copy(capabilities = ServerCapabilities.Unknown.copy(
                developmentServices = true, managedWorkspaces = true, webSearch = true, inbox = true, pluginInventory = true,
            ), usageStatistics = true), width = 412, height = 915)
            presenter.onOpen(SettingsPage.ServerCapabilities()); waitForIdle()
            onNodeWithText("This server supports everything the app offers.").assertExists()
            onNodeWithTag("capabilities-add-server").assertDoesNotExist()
        }

    private fun ComposeUiTest.holds(locale: String) {
        show(locale, sparseFacts(), height = 915, fontScale = 2.5f)
        presenter.onOpen(SettingsPage.ServerCapabilities()); waitForIdle()
        onNodeWithTag("capabilities-available").performScrollTo().performClick(); waitForIdle()
        val ui: CapabilitiesUi = presenter.state.value.capabilities!!
        val rows: List<FeatureRowUi> = ui.focused + ui.missingOnServer + ui.missingOnDevice + ui.available
        assertEquals(ServerFeature.entries.size, rows.size)
        for (row in rows) {
            val bounds = onNodeWithTag("capability-${row.slug}-${row.feature.id}").performScrollTo().assertExists().getBoundsInRoot()
            assertTrue(bounds.right - bounds.left <= 320.dp, "${row.feature.id} is wider than 320 dp: $bounds")
            assertTrue(bounds.left >= 0.dp, "${row.feature.id} starts off screen: $bounds")
        }
    }

    @Test
    fun `B-L10N-018 the page holds at 320 dp and 2_5x text in English`() = runComposeUiTest { holds("en") }

    @Test
    fun `B-L10N-018 the page holds at 320 dp and 2_5x text in Arabic`() = runComposeUiTest { holds("ar") }
}
