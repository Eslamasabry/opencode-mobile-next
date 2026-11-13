package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideRecord
import io.github.eslamasabry.oc.feature.settings.RecordingActions
import io.github.eslamasabry.oc.feature.settings.SettingsThemed
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.color.PackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Appearance on a 412 x 915 phone: its tab row is pinned, its sample is compact and labelled, a new tab opens at its top. */
@OptIn(ExperimentalTestApi::class)
class AppearancePinnedTabsTest {
    private val state =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Appearance),
            appearance =
                AppearanceUi(
                    packs = PackId.entries.take(8).toImmutableList(),
                    guide = AppearanceGuideRecord(done = false),
                ),
        )

    private fun run(block: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(412, 915) {
            setContent { SettingsThemed { SettingsScreen(state, RecordingActions()) } }
            waitForIdle()
            block()
        }

    @Test
    fun `B-KIT-017 the tab row keeps its place when the page is scrolled`() = run {
        val rest = onNodeWithTag("appearance-tabs").getBoundsInRoot()
        onNodeWithTag("appearance-guide-skip").performScrollTo()
        waitForIdle()
        assertEquals(rest, onNodeWithTag("appearance-tabs").getBoundsInRoot())
    }

    @Test
    fun `B-KIT-017 the footer never sits on the last control: the end of the page is whole`() = run {
        onNodeWithTag("appearance-guide-skip").performScrollTo()
        waitForIdle()
        val last = onNodeWithTag("appearance-guide-skip").getBoundsInRoot()
        val foot = onNodeWithText("1 of 4").getBoundsInRoot()
        assertTrue(last.bottom <= foot.top, "the last control ends above the footer (${last.bottom} vs ${foot.top})")
    }

    @Test
    fun `B-KIT-017 the preview says it is a preview, is compact, and its sample button is not a button`() = run {
        val preview = onNodeWithTag("appearance-preview").getBoundsInRoot()
        assertTrue(preview.height.value <= 150f, "the preview is compact (${preview.height})")
        onNodeWithText("Preview", useUnmergedTree = true).assertExists()
        val clickable = androidx.compose.ui.test.onAllNodes(hasClickAction() and androidx.compose.ui.test.hasText("Review changes", substring = true), useUnmergedTree = true)
        assertEquals(0, clickable.fetchSemanticsNodes().size)
    }

    @Test
    fun `B-KIT-017 the first group is whole above the footer without scrolling`() = run {
        val foot = onNodeWithText("1 of 4").getBoundsInRoot()
        val mode = onNodeWithTag("appearance-mode").getBoundsInRoot()
        val pack = onNodeWithTag("appearance-pack-${PackId.entries.first().name}").getBoundsInRoot()
        assertTrue(mode.bottom <= foot.top, "mode control above the footer")
        assertTrue(pack.bottom <= foot.top, "the first theme card above the footer (${pack.bottom} vs ${foot.top})")
    }

    @Test
    fun `B-KIT-017 Next opens the next tab at its top`() = run {
        onNodeWithTag("appearance-guide-skip").performScrollTo()
        waitForIdle()
        onNodeWithText("Next: Style").performClick()
        waitForIdle()
        onNodeWithTag("appearance-style-Solid").assertExists()
        val preview = onNodeWithTag("appearance-preview").getBoundsInRoot()
        val tabs = onNodeWithTag("appearance-tabs").getBoundsInRoot()
        assertTrue(preview.top >= tabs.bottom - 1f, "the page is back at its top: the preview lies under the tabs")
    }
}

