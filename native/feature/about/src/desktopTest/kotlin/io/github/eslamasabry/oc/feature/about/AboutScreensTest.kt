package io.github.eslamasabry.oc.feature.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.about.presentation.NoticesActions
import io.github.eslamasabry.oc.feature.about.presentation.NoticesPhase
import io.github.eslamasabry.oc.feature.about.presentation.NoticesUi
import io.github.eslamasabry.oc.feature.about.presentation.TipsActions
import io.github.eslamasabry.oc.feature.about.presentation.TipsRegistry
import io.github.eslamasabry.oc.feature.about.presentation.tipsUi
import io.github.eslamasabry.oc.feature.about.ui.NoticesScreen
import io.github.eslamasabry.oc.feature.about.ui.TipsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Help › Tips and Open source notices on the desktop JVM, found by the words a person reads. */
@OptIn(ExperimentalTestApi::class)
class AboutScreensTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(previousLocale)

    private class Recording : TipsActions, NoticesActions {
        val calls = mutableListOf<String>()

        override fun onBack() { calls += "back" }

        override fun onShowMe(id: String) { calls += "showMe:$id" }

        override fun onReplayDismissed(id: String) { calls += "dismissed:$id" }

        override fun onShowTipsAgain() { calls += "showTipsAgain" }

        override fun onRetry() { calls += "retry" }
    }

    @Composable
    private fun Themed(tag: String, content: @Composable () -> Unit) {
        KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = tag, stillFrame = true) {
            Box(Modifier.size(412.dp, 1600.dp).background(LocalKitColors.current.ground)) { content() }
        }
    }

    private fun ComposeUiTest.shows(text: String): Boolean = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private val tips = tipsUi(TipsRegistry.entries, replaying = null, resetDone = false, resetting = false)

    @Test
    fun `B-NAV-074 Help Tips lists every tip and tutorial with Show me, and the Make it yours tour`() = runComposeUiTest {
        val actions = Recording()
        setContent { Themed("en") { TipsScreen(tips, actions) } }
        waitForIdle()
        assertEquals(2, onAllNodesWithText("Tips").fetchSemanticsNodes().size, "the page title and the Tips section")
        onNodeWithText("Each tip shows at its moment, at most twice. Replay any of them here.").assertExists()
        for (title in listOf("Stop after this step", "Send later or repeat", "Hold to talk", "Make it yours", "What's new in this version")) {
            onNodeWithText(title).performScrollTo().assertExists()
        }
        onNodeWithText("Mini tutorials").assertExists()
        assertEquals(5, onAllNodesWithText("Show me").fetchSemanticsNodes().size)
        onNodeWithText("Show tips again").performScrollTo().performClick()
        onNodeWithText("One-time tips will appear again at their moment").assertExists()
        assertTrue("showTipsAgain" in actions.calls)
    }

    @Test
    fun `B-NAV-074 a replayed tip shows its sentence on its row and Show tips again then says so`() = runComposeUiTest {
        val actions = Recording()
        setContent {
            Themed("en") { TipsScreen(tips.copy(replaying = TipsRegistry.STOP_AFTER_STEP, resetDone = true), actions) }
        }
        waitForIdle()
        assertTrue(shows("Hold to stop after this step"))
        onNodeWithText("Tips will show again.").performScrollTo().assertExists()
    }

    @Test
    fun `B-NAV-074 Help Tips reads in Arabic`() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        setContent { Themed("ar") { TipsScreen(tips, Recording()) } }
        waitForIdle()
        assertTrue(shows("نصائح"))
        assertTrue(shows("اجعله على ذوقك"))
        assertTrue(shows("أرني"))
    }

    @Test
    fun `B-NAV-001 the notices page shows provenance and desktop limits, then the notices without the intro`() = runComposeUiTest {
        val actions = Recording()
        setContent {
            Themed("en") { NoticesScreen(NoticesUi(phase = NoticesPhase.Ready, text = "## Bundled components\n\nMIT."), actions) }
        }
        waitForIdle()
        onNodeWithText("Open source notices").assertExists()
        onNodeWithText("About this build").assertExists()
        onNodeWithText("AI assistance", substring = true).assertExists()
        onNodeWithText("Desktop builds are experimental and have not been hardware-tested.", substring = true).assertExists()
        assertTrue(!shows("public alpha") && onAllNodesWithText("alpha", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        onNodeWithText("## Bundled components", substring = true).performScrollTo().assertExists()
        onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), actions.calls)
    }

    @Test
    fun `B-NAV-001 notices that cannot be loaded say so and offer Try again`() = runComposeUiTest {
        val actions = Recording()
        setContent { Themed("en") { NoticesScreen(NoticesUi(phase = NoticesPhase.Failed), actions) } }
        waitForIdle()
        onNodeWithText("The notices couldn't be loaded.").performScrollTo().assertExists()
        onNodeWithText("Try again").performScrollTo().performClick()
        assertEquals(listOf("retry"), actions.calls)
    }

    @Test
    fun `B-NAV-001 the build notice reads in Arabic`() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        setContent { Themed("ar") { NoticesScreen(NoticesUi(phase = NoticesPhase.Loading), Recording()) } }
        waitForIdle()
        assertTrue(shows("إشعارات المصادر المفتوحة"))
        onNodeWithText("أندرويد هو النظام الأساسي المدعوم.", substring = true).assertExists()
    }
}
