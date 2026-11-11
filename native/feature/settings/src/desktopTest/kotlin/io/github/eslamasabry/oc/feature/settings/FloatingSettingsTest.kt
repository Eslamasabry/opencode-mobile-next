package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.analytics.health.ProblemReportContent
import io.github.eslamasabry.oc.engine.analytics.health.ReportConnection
import io.github.eslamasabry.oc.feature.settings.presentation.HubUi
import io.github.eslamasabry.oc.feature.settings.presentation.ReportUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.effects.LIQUID_BAR_INSET_Y
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/**
 * Glass step 4 on Settings (STEP4-PLAN.md §4): where the lens is drawn every page of the Settings frame scrolls on under
 * a floating liquid bar; at rest nothing moves, and a focused field never rests under the bar.
 */
@OptIn(ExperimentalTestApi::class)
class FloatingSettingsTest {
    private val savedLocale: Locale = Locale.getDefault()

    @BeforeTest fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest fun restore() = Locale.setDefault(savedLocale)

    /** Desktop with the lens, as Android 13+ has it (its panes the kit's stand-in tint). */
    private class LensPlatform : KitPlatform by DesktopKitPlatform() {
        override val liquidGlass: Boolean = true
    }

    private val hub = SettingsUi(stack = persistentListOf(SettingsPage.Hub), hub = HubUi(groups = SettingsGroupId.entries.toPersistentList()))

    private val report =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Hub, SettingsPage.ReportProblem),
            report =
                ReportUi(
                    content =
                        ProblemReportContent(
                            lastClose = null,
                            appVersion = "2.1.0",
                            appBuild = "57",
                            android = "15",
                            abi = "arm64",
                            ramRange = "8-12",
                            connection = ReportConnection("OpenCode", "2.1.1"),
                            problems = emptyList(),
                            madeMs = 1_800_000_000_000L,
                        ),
                    canSend = true,
                ),
        )

    private fun page(ui: SettingsUi, platform: KitPlatform, act: ComposeUiTest.() -> Unit = {}): Map<String, DpRect?> {
        lateinit var bounds: Map<String, DpRect?>
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D, 1f)) {
                    KitTheme(
                        appearance = KitAppearance(mode = AppearanceMode.Dark, style = Style.Glass, motion = MotionLevel.Full),
                        platform = platform,
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(
                            LocalKitRedactor provides Redactor(),
                            LocalKitWindow provides KitWindowInfo(W.toFloat(), H.toFloat()),
                            LocalKitScreenInsets provides WindowInsets(top = STATUS_DP.dp, bottom = NAV_DP.dp),
                        ) {
                            Box(Modifier.size(W.dp, H.dp)) { SettingsScreen(ui, RecordingActions()) }
                        }
                    }
                }
            }
            waitForIdle()
            act()
            waitForIdle()
            bounds =
                listOf("kit-liquid-bar", "settings-search", "report-description").associateWith {
                    runCatching { onNodeWithTag(it, useUnmergedTree = true).getBoundsInRoot() }.getOrNull()
                }
        }
        return bounds
    }

    /** A slow drag of [dyDp] (negative: up), held still before the finger lifts, so nothing flings. */
    private fun ComposeUiTest.drag(dyDp: Float) {
        onRoot().performTouchInput {
            val total = dyDp * D
            down(Offset(width / 2f, height * 0.8f))
            moveBy(Offset(0f, viewConfiguration.touchSlop * sign(total)))
            repeat(STEPS) {
                advanceEventTime(16)
                moveBy(Offset(0f, total / STEPS))
            }
            advanceEventTime(200)
            up()
        }
        waitForIdle()
    }

    /** The bottom of the bar the capsule floats in (the capsule sits LIQUID_BAR_INSET_Y inside it). */
    private fun barBottom(b: Map<String, DpRect?>): Float = b["kit-liquid-bar"]!!.bottom.value + LIQUID_BAR_INSET_Y

    @Test
    fun `B-SET-FLOAT-001 the hub floats its bar under the lens and its search rests where it did`() {
        val lens = page(hub, LensPlatform())
        val flat = page(hub, DesktopKitPlatform())
        assertNotNull(lens["kit-liquid-bar"], "the hub's bar is a liquid capsule")
        assertNull(flat["kit-liquid-bar"], "without the lens the bar is pinned as before")
        val a = lens["settings-search"]!!
        val b = flat["settings-search"]!!
        assertTrue(abs(a.top.value - b.top.value) <= .5f, "the search rests at ${a.top} under the lens, ${b.top} without")
        assertTrue(a.top.value >= barBottom(lens) - .5f, "the search $a rests below the bar")
    }

    @Test
    fun `B-SET-FLOAT-002 on Report a problem a focused field moved under the bar comes back below it`() {
        lateinit var under: DpRect
        val b =
            page(report, LensPlatform()) {
                drag(-200f)
                under = onNodeWithTag("report-description", useUnmergedTree = true).getBoundsInRoot()
                onNode(
                    hasSetTextAction() and (hasTestTag("report-description") or hasAnyAncestor(hasTestTag("report-description"))),
                    useUnmergedTree = true,
                ).requestFocus()
            }
        val bar = barBottom(b)
        // The drag took the field up under the bar (else the test proves nothing).
        assertTrue(under.top.value < bar, "the drag moved the field under the bar: $under, bar ends at $bar")
        val field = b["report-description"]!!
        assertTrue(field.top.value >= bar - .5f, "the focused field $field rests below the bar ending at $bar")
    }

    private companion object {
        const val W = 412
        const val H = 915
        const val D = 2f
        const val STATUS_DP = 32
        const val NAV_DP = 16
        const val STEPS = 20
    }
}
