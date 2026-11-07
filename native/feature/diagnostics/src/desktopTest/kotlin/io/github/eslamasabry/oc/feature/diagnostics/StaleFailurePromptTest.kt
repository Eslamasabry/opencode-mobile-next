package io.github.eslamasabry.oc.feature.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.diagnostics.presentation.ReportVersions
import io.github.eslamasabry.oc.feature.diagnostics.presentation.StaleFailureGate
import io.github.eslamasabry.oc.feature.diagnostics.ui.StaleFailurePrompt
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.runtime.collectAsState

/** E19-S03 on the desktop JVM: the stale-failure line, found by the words a person reads. */
@OptIn(ExperimentalTestApi::class)
class StaleFailurePromptTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(previousLocale)

    private class Versions(val running: String?, val latest: String?) : ReportVersions {
        var opened = 0

        override fun running(): String? = running

        override fun latestRelease(): String? = latest

        override fun openUpdate() {
            opened++
        }
    }

    @Composable
    private fun Host(gate: StaleFailureGate) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            Box(Modifier.size(412.dp, 915.dp).background(LocalKitColors.current.ground)) {
                KitSheetHost {
                    val state by gate.state.collectAsState()
                    StaleFailurePrompt(state, gate)
                }
            }
        }
    }

    @Test
    fun `E19-S03 an older failure says it may be fixed and Report anyway still reports`() = runComposeUiTest {
        val versions = Versions(running = "2.1.4", latest = "3.0.0")
        val gate = StaleFailureGate(versions)
        var reports = 0
        gate.request("2.1.1") { reports++ }
        setContent { Host(gate) }
        onNodeWithText("This happened on 2.1.1. Version 3.0.0 may already fix it.").assertExists()
        onNodeWithText("Update first").assertExists()
        onNodeWithText("Report anyway").performClick()
        waitForIdle()
        assertEquals(1, reports)
        assertEquals(0, versions.opened)
        onNodeWithText("Report anyway").assertDoesNotExist()
    }

    @Test
    fun `E19-S03 Update first opens the update instead of the report`() = runComposeUiTest {
        val versions = Versions(running = "2.1.4", latest = "3.0.0")
        val gate = StaleFailureGate(versions)
        var reports = 0
        gate.request("2.1.1") { reports++ }
        setContent { Host(gate) }
        onNodeWithText("Update first").performClick()
        waitForIdle()
        assertEquals(1, versions.opened)
        assertEquals(0, reports)
    }

    @Test
    fun `E19-S03 the same version shows no line`() = runComposeUiTest {
        val gate = StaleFailureGate(Versions(running = "2.1.4", latest = "2.1.4"))
        var reports = 0
        gate.request("2.1.4") { reports++ }
        setContent { Host(gate) }
        onNodeWithText("Update first").assertDoesNotExist()
        assertEquals(1, reports)
    }
}
