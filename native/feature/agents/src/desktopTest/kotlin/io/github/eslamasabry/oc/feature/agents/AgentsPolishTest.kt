package io.github.eslamasabry.oc.feature.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.COMPUTERS
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.EVERY_STATE
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.MARKS
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Recorder
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Agents page polish tests (B-546, B-574, B-577). */
@OptIn(ExperimentalTestApi::class)
class AgentsPolishTest {
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

    @Composable
    private fun Screen(state: AgentsUi, scale: Float = 1f) {
        CompositionLocalProvider(
            LocalDensity provides Density(3f, scale),
            LocalKitSecretSink provides KitSecretSink {},
            LocalKitLogoLoader provides MARKS,
        ) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                    AgentsScreen(state, Recorder())
                }
            }
        }
    }

    @Test
    fun `B-574 the phone line names no processor or Android version`() =
        runDesktopComposeUiTest(360 * 3, 780 * 3) {
            setContent { Screen(EVERY_STATE) }
            waitForIdle()
            onAllNodes(
                hasText("arm64", substring = true) or hasContentDescription("arm64", substring = true),
                useUnmergedTree = true,
            ).assertCountEquals(0)
            onNode(hasContentDescription("This phone is ready", substring = true)).assertExists()
        }

    @Test
    fun `B-577 a computer row shows its folder name, not its path`() =
        runDesktopComposeUiTest(360 * 3, 780 * 3) {
            setContent { Screen(COMPUTERS) }
            waitForIdle()
            onAllNodes(
                hasText("/Users/me", substring = true) or hasContentDescription("/Users/me", substring = true),
                useUnmergedTree = true,
            ).assertCountEquals(0)
            onAllNodes(
                hasText("/home/me", substring = true) or hasContentDescription("/home/me", substring = true),
                useUnmergedTree = true,
            ).assertCountEquals(0)
            val siteNodes = onAllNodes(hasText("site", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(siteNodes.isNotEmpty(), "expected a node containing 'site'")
        }

    @Test
    fun `B-577 Add a computer spans the page width`() =
        runDesktopComposeUiTest(360 * 3, 780 * 3) {
            setContent { Screen(COMPUTERS) }
            waitForIdle()
            val button = onNode(hasText("Add a computer") and hasClickAction()).performScrollTo().fetchSemanticsNode()
            val widthDp = button.boundsInRoot.width / 3f
            assertTrue(widthDp >= 300f, "expected Add a computer to be at least 300 dp wide, got $widthDp dp")
        }

    @Test
    fun `B-546 computer ready cards never cut New conversation`() {
        for (scale in listOf(1f, 1.3f)) {
            runDesktopComposeUiTest(360 * 3, 780 * 3) {
                setContent { Screen(COMPUTERS, scale) }
                waitForIdle()
                val chips = onAllNodesWithTag("kit-chip-label", useUnmergedTree = true)
                val count = chips.fetchSemanticsNodes().size
                assertTrue(count >= 3, "[scale=$scale] expected at least 3 tile chips, got $count")
                for (i in 0 until count) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    chips[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    val text = layout.layoutInput.text.text
                    assertFalse(layout.hasVisualOverflow, "[scale=$scale] visual overflow on: $text")
                    assertFalse(
                        (0 until layout.lineCount).any { layout.isLineEllipsized(it) },
                        "[scale=$scale] ellipsized: $text",
                    )
                }
            }
        }
    }
}
