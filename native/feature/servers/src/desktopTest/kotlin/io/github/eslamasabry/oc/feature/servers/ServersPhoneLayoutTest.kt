package io.github.eslamasabry.oc.feature.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRowFacts
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRowsState
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.feature.servers.ui.ServersScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

private const val PHONE_WIDTH_DP: Int = 360
private const val PHONE_HEIGHT_DP: Int = 800

/** Phone-width layout proof for ServersScreen (B-SRV-301): rows, Add server, and nothing cut at 360 dp. */
@OptIn(ExperimentalTestApi::class)
class ServersPhoneLayoutTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private class RecordingActions : ServersActions {
        var openedSettings: ProfileId? = null
        var addedNew: Boolean = false

        override fun back() = Unit
        override fun allowCleartext(allowed: Boolean, address: String) = Unit
        override fun save(name: String, address: String, username: String, password: String) = Unit
        override fun connect(id: ProfileId) = Unit
        override fun create(id: ProfileId) = Unit
        override fun remove(id: ProfileId) = Unit
        override fun settings(id: ProfileId) {
            openedSettings = id
        }
        override fun edit(id: ProfileId?) {
            if (id == null) addedNew = true
        }
    }

    private val home =
        SavedServer(ProfileId("home"), "Home laptop", "https://laptop.example.net:4096", connected = true)
    private val desk =
        SavedServer(ProfileId("desk"), "Office desktop", "https://desk.example.net:4096", connected = false)
    private val vps =
        SavedServer(ProfileId("vps"), "Build server", "https://build.example.net:4096", connected = false)

    private fun phoneUi(): ServersUi =
        ServersUi(
            servers = persistentListOf(home, desk, vps),
            rows =
                ServerRowsState(
                    persistentMapOf(
                        home.id to ServerRowFacts(BackendKind.OpenCode2, "2.1.1", "/home/dev/app", false, 0, 0, 0),
                        desk.id to ServerRowFacts(BackendKind.OpenCode2, "2.1.0", "/home/office", false, 0, 0, 0),
                        vps.id to ServerRowFacts(BackendKind.OpenCode2, "2.1.1", "/srv/build", false, 0, 0, 0),
                    ),
                ),
        )

    private fun ComposeUiTest.assertTextWholeInsidePhone(label: String) {
        val nodes =
            onAllNodesWithText(label, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .filter { SemanticsActions.GetTextLayoutResult in it.config }
        assertTrue(nodes.isNotEmpty(), "Expected text node for '$label'")
        for (node in nodes) {
            val bounds = node.boundsInRoot
            assertTrue(
                bounds.left >= 0f && bounds.right <= PHONE_WIDTH_DP &&
                    bounds.top >= 0f && bounds.bottom <= PHONE_HEIGHT_DP,
                "Text '$label' clipped outside ${PHONE_WIDTH_DP}x${PHONE_HEIGHT_DP}: $bounds",
            )
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            assertFalse(layout.hasVisualOverflow, "Text '$label' visually overflowed at ${PHONE_WIDTH_DP}dp")
            val ellipsized = (0 until layout.lineCount).any(layout::isLineEllipsized)
            assertFalse(ellipsized, "Text '$label' ellipsized at ${PHONE_WIDTH_DP}dp")
        }
    }

    @Test
    fun `B-SRV-301 Servers screen keeps phone rows and Add server unclipped at 360dp`() {
        val actions = RecordingActions()
        runDesktopComposeUiTest(PHONE_WIDTH_DP, PHONE_HEIGHT_DP) {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f),
                    LocalKitSecretSink provides KitSecretSink {},
                ) {
                    KitTheme(
                        appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                            ServersScreen(phoneUi(), actions, onThisPhone = {})
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("servers-wide-start").assertDoesNotExist()
            onNodeWithTag("servers-wide-end").assertDoesNotExist()
            onNodeWithText("Open Home laptop").assertDoesNotExist()
            val homeBounds = onNodeWithText("Home laptop").getBoundsInRoot()
            val deskBounds = onNodeWithText("Office desktop").getBoundsInRoot()
            val vpsBounds = onNodeWithText("Build server").getBoundsInRoot()
            val addBounds = onNodeWithText("Add server").getBoundsInRoot()
            assertTrue(homeBounds.bottom.value <= deskBounds.top.value, "Rows must stack: $homeBounds vs $deskBounds")
            assertTrue(deskBounds.bottom.value <= vpsBounds.top.value, "Rows must stack: $deskBounds vs $vpsBounds")
            assertTrue(addBounds.top.value > vpsBounds.bottom.value, "Add server must sit below rows: $addBounds")
            onNodeWithContentDescription("Home laptop").assertExists()
            onNodeWithContentDescription("Office desktop").assertExists()
            onNodeWithContentDescription("Build server").assertExists()
            val expected =
                listOf("Servers", "Home laptop", "Office desktop", "Build server", "Connected", "Add server")
            for (text in expected) {
                assertTextWholeInsidePhone(text)
            }
            onNodeWithText("Office desktop").performClick()
            assertEquals(desk.id, actions.openedSettings, "Tapping a row on phone must open its settings")
            onNodeWithText("Add server").performClick()
            assertTrue(actions.addedNew, "Tapping Add server must open the add-server flow")
        }
    }
}
