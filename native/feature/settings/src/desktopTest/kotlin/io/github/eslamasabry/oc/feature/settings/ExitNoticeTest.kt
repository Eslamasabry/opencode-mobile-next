package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind
import io.github.eslamasabry.oc.feature.settings.presentation.ExitNotice
import io.github.eslamasabry.oc.feature.settings.presentation.ExitNoticeRules
import io.github.eslamasabry.oc.feature.settings.presentation.ExitRestartLine
import io.github.eslamasabry.oc.feature.settings.ui.AppExitNoticeLine
import io.github.eslamasabry.oc.feature.settings.ui.exitNoticeMessage
import io.github.eslamasabry.oc.feature.settings.ui.exitRestartWords
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Flutter `test/app_exit_recovery_test.dart` (B-ANDR-008): the exit notice and the restart line say what is true. */
@OptIn(ExperimentalTestApi::class)
class ExitNoticeTest {
    private val saved: Locale = Locale.getDefault()

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    /** Every message for the given notices, in English. */
    private fun messages(vararg asked: Triple<ExitNotice, String, Boolean>): List<String> {
        Locale.setDefault(Locale.ENGLISH)
        val out = mutableListOf<String>()
        runComposeUiTest {
            setContent {
                SettingsThemed {
                    out.clear()
                    asked.forEach { (notice, at, back) -> out += exitNoticeMessage(notice, at, back) }
                }
            }
            waitForIdle()
        }
        return out.toList()
    }

    @Test
    fun `B-ANDR-008 never says starting again when automatic restart is off`() {
        for (team in listOf(false, true)) {
            val (off, on) =
                messages(
                    Triple(ExitNotice(ExitKind.ForceStop, teamStopped = team, recoveryAllowed = false), "at 21:21", false),
                    Triple(ExitNotice(ExitKind.ForceStop, teamStopped = team), "at 21:21", false),
                )
            assertFalse("starting again" in off, off)
            assertTrue("Start " in off, off)
            assertTrue("starting again" in on, on)
        }
        val (manual) = messages(Triple(ExitNotice(ExitKind.ForceStop, teamStopped = true, recoveryAllowed = false), "at 00:06", false))
        assertEquals(
            "OpenCode Mobile was closed at 00:06. Your phone's OpenCode and the AI Team stopped with it. Start them again when you're ready.",
            manual,
        )
    }

    @Test
    fun `B-ANDR-008 a force stop is not blamed on Android, and once the server is back it says so`() {
        for (team in listOf(false, true)) {
            val notice = ExitNotice(ExitKind.ForceStop, teamStopped = team)
            val (starting, back) = messages(Triple(notice, "at 9:21 PM", false), Triple(notice, "at 9:21 PM", true))
            assertTrue(starting.startsWith("OpenCode Mobile was closed at 9:21 PM"), starting)
            assertFalse("Android" in starting, starting)
            assertTrue("is running again" in back, back)
            assertFalse("starting again" in back, back)
        }
        val (crash, memory) =
            messages(Triple(ExitNotice(ExitKind.Crash), "at 00:06", false), Triple(ExitNotice(ExitKind.MemoryLimit), "at 00:06", false))
        assertTrue(crash.startsWith("OpenCode Mobile stopped unexpectedly at 00:06"), crash)
        assertTrue(memory.startsWith("Android closed OpenCode Mobile at 00:06 to free memory"), memory)
        // Nothing after a normal exit or an update.
        assertFalse(ExitNoticeRules.shows(ExitKind.Normal))
        assertFalse(ExitNoticeRules.shows(ExitKind.Update))
        assertTrue(ExitNoticeRules.shows(ExitKind.ForceStop))
    }

    @Test
    fun `B-ANDR-008 the restart line reflects the real state once the server is back`() {
        val on = ExitNotice(ExitKind.ForceStop)
        assertEquals(ExitRestartLine.Restarting, ExitNoticeRules.restartLine(on, serverBack = false))
        assertEquals(ExitRestartLine.Restarted, ExitNoticeRules.restartLine(on, serverBack = true))
        assertNull(ExitNoticeRules.restartLine(on.copy(recoveryAllowed = false), serverBack = true))
        Locale.setDefault(Locale.ENGLISH)
        val words = mutableListOf<String?>()
        runComposeUiTest {
            setContent {
                SettingsThemed {
                    words.clear()
                    words += exitRestartWords(on, serverBack = false)
                    words += exitRestartWords(on, serverBack = true)
                    words += exitRestartWords(on.copy(recoveryAllowed = false), serverBack = false)
                }
            }
            waitForIdle()
        }
        assertEquals(listOf("Restarting after the app closed", "Restarted after the app closed", null), words)
    }

    @Test
    fun `B-ANDR-008 the notice shows once and folds away when dismissed`() =
        runComposeUiTest {
            Locale.setDefault(Locale.ENGLISH)
            var shown by mutableStateOf(true)
            setContent {
                SettingsThemed {
                    if (shown) {
                        AppExitNoticeLine(ExitNotice(ExitKind.ForceStop, recoveryAllowed = false), "at 00:06", onDismiss = { shown = false })
                    }
                }
            }
            onNodeWithText("OpenCode Mobile was closed at 00:06. Your phone's OpenCode stopped with it. Start it again when you're ready.", substring = true)
                .assertExists()
            onNodeWithTag("kit-notice-dismiss", useUnmergedTree = true).performClick()
            waitForIdle()
            onNodeWithTag("app-exit-notice").assertDoesNotExist()
        }
}
