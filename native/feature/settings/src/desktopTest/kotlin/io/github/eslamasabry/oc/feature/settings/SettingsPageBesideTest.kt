package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.settings.presentation.GroupUi
import io.github.eslamasabry.oc.feature.settings.presentation.HubUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/**
 * Big screens, the shared frame: a Settings page of the module's own (Privacy, About, Phrase suggestions, ...) opened
 * from a group stands beside that group's rows from 840 dp (its row marked), fills the rest up to the column cap, and
 * is the phone's whole page below it; the page and its scroll survive the width threshold both ways.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsPageBesideTest {
    private val groups =
        persistentListOf(
            SettingsGroupId.Server,
            SettingsGroupId.Agent,
            SettingsGroupId.Conversations,
            SettingsGroupId.ThisApp,
            SettingsGroupId.Help,
        )

    private fun state(page: SettingsPage) =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Hub, SettingsPage.Group(SettingsGroupId.ThisApp), page),
            hub = HubUi(groups = groups),
            firstGroup = GroupUi(SettingsGroupId.Server, persistentListOf()),
            listGroup =
                GroupUi(
                    SettingsGroupId.ThisApp,
                    persistentListOf(
                        SettingsRowUi(SettingsRowId.Privacy, SettingsDoor.Page(SettingsPage.Privacy)),
                        SettingsRowUi(SettingsRowId.About, SettingsDoor.Page(SettingsPage.About)),
                        SettingsRowUi(SettingsRowId.PhraseChips, SettingsDoor.Page(SettingsPage.PhraseChips)),
                    ),
                ),
        )

    private fun ComposeUiTest.show(
        state: SettingsUi,
        window: androidx.compose.runtime.State<KitWindowInfo>,
        actions: SettingsActions = RecordingActions(),
    ) =
        setContent {
            Locale.setDefault(Locale.ENGLISH)
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    CompositionLocalProvider(LocalKitRedactor provides Redactor(), LocalKitWindow provides window.value) {
                        Box(Modifier.size(window.value.widthDp.dp, window.value.heightDp.dp)) {
                            SettingsScreen(state, actions)
                        }
                    }
                }
            }
        }

    @Test
    fun `B-NAV-250 a settings page fills beside its list at every width, two equal margins only past the cap`() {
        for (page in listOf(SettingsPage.Privacy, SettingsPage.About, SettingsPage.PhraseChips)) {
            for (w in 600..1700 step 40) {
                runDesktopComposeUiTest(w, 800) {
                    show(state(page), mutableStateOf(KitWindowInfo(w.toFloat(), 800f)))
                    waitForIdle()
                    val at = "$page at $w dp"
                    val list = onAllTag("settings-list-pane")
                    if (w < 840) {
                        assertEquals(0, list, "$at: the phone's single page, no list beside it")
                    } else {
                        assertEquals(1, list, "$at: the group's rows stand beside the page")
                        val l = onNodeWithTag("settings-list-pane").getBoundsInRoot()
                        val d = onNodeWithTag("settings-detail-pane").getBoundsInRoot()
                        assertTrue(w - l.right.value > PAGE_CAP || abs(d.left.value - l.right.value) <= 1f, "$at: the page starts at the list's edge ($l, $d)")
                        val left = d.left.value - l.right.value
                        val right = w - d.right.value
                        assertTrue(right <= 1f || abs(left - right) <= 1f, "$at: fills, or two equal margins ($left, $right)")
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.onAllTag(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    @Test
    fun `B-NAV-251 the open page and its scroll survive the 840 dp threshold both ways`() =
        runDesktopComposeUiTest(1280, 500) {
            val window = mutableStateOf(KitWindowInfo(1280f, 500f))
            show(state(SettingsPage.About), window)
            waitForIdle()
            fun offset(): Float =
                onAllNodes(hasScrollAction()).fetchSemanticsNodes().maxOf {
                    it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: 0f
                }
            onAllNodes(hasScrollAction()).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
            onNodeWithTag("settings-detail-pane").performTouchInput { swipeUp() }
            waitForIdle()
            val scrolled = offset()
            assertTrue(scrolled > 0f, "the About page scrolls ($scrolled)")
            window.value = KitWindowInfo(700f, 500f)
            waitForIdle()
            assertEquals(0, onAllTag("settings-list-pane"), "narrow: one page")
            assertEquals(scrolled, offset(), "the page keeps its scroll when it goes under 840 dp")
            window.value = KitWindowInfo(1280f, 500f)
            waitForIdle()
            assertEquals(1, onAllTag("settings-list-pane"), "wide again: beside the list")
            assertEquals(scrolled, offset(), "and when it comes back beside the list")
        }

    @Test
    fun `B-NAV-252 at phone width a page's switch and its storage door act as before, no list beside`() {
        val calls = mutableListOf<String>()
        val base = RecordingActions()
        val actions =
            object : SettingsActions by base {
                override fun onPhraseChips(on: Boolean) {
                    calls += "chips:$on"
                }
            }
        runDesktopComposeUiTest(412, 915) {
            show(state(SettingsPage.PhraseChips), mutableStateOf(KitWindowInfo(412f, 915f)), actions)
            waitForIdle()
            assertEquals(0, onAllTag("settings-list-pane"))
            onAllNodesWithText("Phrase suggestions").onLast().performClick()
            waitForIdle()
            assertEquals(listOf("chips:false"), calls)
        }
        runDesktopComposeUiTest(412, 915) {
            show(
                state(SettingsPage.Storage).copy(
                    localModels =
                        io.github.eslamasabry.oc.feature.settings.presentation.LocalModelStorageFacts(
                            loading = false,
                            present = true,
                            size = "1.2 GB",
                        ),
                ),
                mutableStateOf(KitWindowInfo(412f, 915f)),
                actions,
            )
            waitForIdle()
            assertEquals(0, onAllTag("settings-list-pane"))
            onNodeWithText("Clear this app's storage").performClick()
            waitForIdle()
            assertTrue("outside:AppStorage" in base.calls, "the confirm door is the phone's: ${base.calls}")
        }
    }

    private companion object {
        const val PAGE_CAP = 1000
    }
}
