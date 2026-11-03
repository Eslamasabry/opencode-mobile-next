package io.github.eslamasabry.oc.feature.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.background.presentation.BackgroundActions
import io.github.eslamasabry.oc.feature.background.presentation.BackgroundLiveFacts
import io.github.eslamasabry.oc.feature.background.presentation.PauseFacts
import io.github.eslamasabry.oc.feature.background.presentation.PauseReason
import io.github.eslamasabry.oc.feature.background.presentation.SwitchFailure
import io.github.eslamasabry.oc.feature.background.presentation.backgroundUi
import io.github.eslamasabry.oc.feature.background.ui.BackgroundScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** The Background page on the desktop JVM, found by the words a person reads. */
@OptIn(ExperimentalTestApi::class)
class BackgroundScreenUiTest {
    private var previousLocale: Locale = Locale.getDefault()
    private val now = Instant.parse("2026-11-03T15:10:00Z")

    @BeforeTest
    fun english() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(previousLocale)

    private class Recording : BackgroundActions {
        val calls = mutableListOf<String>()

        override fun onBack() { calls += "back" }

        override fun onSwitch(on: Boolean) { calls += "switch:$on" }

        override fun onRestart() { calls += "restart" }

        override fun onResume() { calls += "resume" }

        override fun onDismissPause() { calls += "dismiss" }

        override fun onAllowBattery() { calls += "battery" }

        override fun onOpenKeepRunning() { calls += "keepRunning" }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            Box(Modifier.size(412.dp, 1400.dp).background(LocalKitColors.current.ground)) { content() }
        }
    }

    private fun ComposeUiTest.page(facts: BackgroundLiveFacts, failure: SwitchFailure? = null): Recording {
        val actions = Recording()
        setContent { Themed { BackgroundScreen(backgroundUi(facts, failure), actions, now, TimeZone.UTC) } }
        waitForIdle()
        return actions
    }

    private fun ComposeUiTest.shows(text: String): Boolean = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `B-ANDR-012 off reads Off with the switch and its cost, and turning it on asks the live connection`() =
        runComposeUiTest {
            val actions = page(BackgroundLiveFacts(supported = true))
            onNodeWithText("Stay connected in the background").assertExists()
            assertTrue(shows("Off"))
            assertTrue(!shows("Allow unrestricted battery access"), "no battery row while off")
            onNodeWithText("Stay connected in the background").performClick()
            waitForIdle()
            assertEquals(listOf("switch:true"), actions.calls)
        }

    @Test
    fun `B-ANDR-012 on and confirmed reads Running now with the battery row`() = runComposeUiTest {
        val actions = page(BackgroundLiveFacts(supported = true, enabled = true, active = true))
        onNodeWithText("Running now").assertExists()
        onNodeWithText("Allow unrestricted battery access").performClick()
        waitForIdle()
        assertEquals(listOf("battery"), actions.calls)
    }

    @Test
    fun `B-ANDR-012 a refused start says Android did not enable it, in plain words`() = runComposeUiTest {
        page(BackgroundLiveFacts(supported = true), SwitchFailure.NotEnabled)
        onNodeWithText("Android did not enable background mode.").assertExists()
    }

    @Test
    fun `B-ANDR-014 Android's time limit leads the page and Restart turns it back on`() = runComposeUiTest {
        val actions = page(BackgroundLiveFacts(supported = true, stoppedByAndroid = true))
        onNodeWithText("Android stopped the live connection").assertExists()
        onNodeWithText("Stopped by Android — tap to restart").assertExists()
        onNodeWithText("Restart the live connection").performClick()
        waitForIdle()
        assertEquals(listOf("restart"), actions.calls)
    }

    @Test
    fun `B-ANDR-018 the pause line says why and when, Resume resumes and Dismiss hides it`() = runComposeUiTest {
        val pause = PauseFacts(PauseReason.TimeLimit, now - 2.hours)
        val actions = page(BackgroundLiveFacts(supported = true, pause = pause))
        onNodeWithText("Background connection paused at 13:10 to save battery.").assertExists()
        onNodeWithText("Resume").performClick()
        onNodeWithText("Dismiss").performClick()
        waitForIdle()
        assertEquals(listOf("resume", "dismiss"), actions.calls)
    }

    @Test
    fun `B-ANDR-016 a resume Android did not confirm keeps the pause and offers Keep running settings`() =
        runComposeUiTest {
            val pause = PauseFacts(PauseReason.UserStopped, now - 30.hours, resumeFailed = true)
            val actions = page(BackgroundLiveFacts(supported = true, pause = pause))
            onNodeWithText("Background connection stopped when the app closed on 2/11 at 09:10.").assertExists()
            onNodeWithText("Couldn't resume. Open Keep running settings.").assertExists()
            onNodeWithText("Open Keep running settings").performClick()
            waitForIdle()
            assertEquals(listOf("keepRunning"), actions.calls)
        }

    @Test
    fun `B-ANDR-018 while a resume is confirmed the line only says it is resuming`() = runComposeUiTest {
        page(BackgroundLiveFacts(supported = true, pause = PauseFacts(PauseReason.Interrupted, now), resuming = true))
        onNodeWithText("Resuming background connection…").assertExists()
        assertTrue(!shows("Resume"))
    }
}
