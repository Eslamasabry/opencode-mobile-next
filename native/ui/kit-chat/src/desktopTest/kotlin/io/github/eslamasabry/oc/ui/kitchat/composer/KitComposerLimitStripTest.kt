package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S24-LIMIT: the composer's "Approaching limit" strip (owner reference 2026-11-08). */
@OptIn(ExperimentalTestApi::class)
class KitComposerLimitStripTest {
    private val friday5 = KitLimitNotice(reached = false, reset = KitLimitReset.OnDay(isoWeekday = 5, hour = 5, minute = 0))
    private val saved = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(saved)

    @Composable
    private fun Strip(
        notice: KitLimitNotice?,
        twelveHour: Boolean = true,
        onView: () -> Unit = {},
        onDismiss: () -> Unit = {},
        level: MotionLevel = MotionLevel.Off,
        rtl: Boolean = false,
    ) {
        MotionTheme(
            level = level,
            platform = RecordingPlatform(KitSystemSignals(is24Hour = !twelveHour)),
            localeTag = if (rtl) "ar" else "en",
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.width(360.dp)) { KitComposerLimitStrip(notice, onView, onDismiss) }
            }
        }
    }

    @Test
    fun `S24-LIMIT-020 the strip reads Approaching limit and the weekday reset on the phone's clock`() = runComposeUiTest {
        Locale.setDefault(Locale.ENGLISH)
        var twelve by mutableStateOf(true)
        setContent { Strip(friday5, twelveHour = twelve) }
        onNodeWithText("Approaching limit").assertExists()
        onNodeWithText("Resets Friday at 5:00\u00A0AM").assertExists()
        onNodeWithText("View usage").assertExists()
        twelve = false
        waitForIdle()
        onNodeWithText("Resets Friday at 05:00").assertExists()
    }

    @Test
    fun `S24-LIMIT-021 at the limit it says Limit reached, and a countdown under a day`() = runComposeUiTest {
        Locale.setDefault(Locale.ENGLISH)
        var notice by mutableStateOf(KitLimitNotice(true, KitLimitReset.Within(2, 10)))
        setContent { Strip(notice) }
        onNodeWithText("Limit reached").assertExists()
        onNodeWithText("Resets in 2 h 10 min").assertExists()
        notice = KitLimitNotice(true, KitLimitReset.Within(3, 0))
        waitForIdle()
        onNodeWithText("Resets in 3 h").assertExists()
        notice = KitLimitNotice(true, KitLimitReset.Within(0, 5))
        waitForIdle()
        onNodeWithText("Resets in 5 min").assertExists()
        // No reset time known: the title alone, nothing made up.
        notice = KitLimitNotice(true, null)
        waitForIdle()
        onAllNodesWithTag(LIMIT_STRIP_TAG).assertCountEquals(1)
        onNodeWithText("Resets", substring = true).assertDoesNotExist()
    }

    @Test
    fun `S24-LIMIT-022 View usage and Dismiss each run their action once`() = runComposeUiTest {
        Locale.setDefault(Locale.ENGLISH)
        val ran = mutableListOf<String>()
        setContent { Strip(friday5, onView = { ran += "view" }, onDismiss = { ran += "dismiss" }) }
        onNodeWithTag(LIMIT_VIEW_TAG).performClick()
        onNodeWithContentDescription("Dismiss").performClick()
        assertEquals(listOf("view", "dismiss"), ran)
    }

    @Test
    fun `S24-LIMIT-023 no notice draws nothing, and the strip folds away`() = runComposeUiTest {
        Locale.setDefault(Locale.ENGLISH)
        var notice by mutableStateOf<KitLimitNotice?>(null)
        mainClock.autoAdvance = false
        setContent { Strip(notice, level = MotionLevel.Full) }
        mainClock.advanceTimeBy(50)
        onAllNodesWithTag(LIMIT_STRIP_TAG).assertCountEquals(0)
        notice = friday5
        mainClock.advanceTimeBy(400)
        onAllNodesWithTag(LIMIT_STRIP_TAG).assertCountEquals(1)
        notice = null
        mainClock.advanceTimeBy(64)
        // Still folding (QUICK, 150 ms) with its last words, then gone.
        onNodeWithText("Approaching limit").assertExists()
        mainClock.advanceTimeBy(400)
        onAllNodesWithTag(LIMIT_STRIP_TAG).assertCountEquals(0)
    }

    @Test
    fun `S24-LIMIT-024 reduced motion shows and hides it at once`() = runComposeUiTest {
        Locale.setDefault(Locale.ENGLISH)
        var notice by mutableStateOf<KitLimitNotice?>(friday5)
        mainClock.autoAdvance = false
        setContent { Strip(notice, level = MotionLevel.Off) }
        mainClock.advanceTimeByFrame()
        onAllNodesWithTag(LIMIT_STRIP_TAG).assertCountEquals(1)
        notice = null
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onAllNodesWithTag(LIMIT_STRIP_TAG).assertCountEquals(0)
    }

    @Test
    fun `S24-LIMIT-025 at 360 dp with large text it grows taller, never wider`() = runComposeUiTest {
        Locale.setDefault(Locale.ENGLISH)
        var scale by mutableStateOf(1f)
        var density = 1f
        setContent {
            val base = LocalDensity.current
            density = base.density
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) { Strip(friday5) }
        }
        val normal = onNodeWithTag(LIMIT_STRIP_TAG).getBoundsInRoot()
        scale = 2f
        waitForIdle()
        val large = onNodeWithTag(LIMIT_STRIP_TAG).getBoundsInRoot()
        val nw = normal.right - normal.left
        val lw = large.right - large.left
        assertTrue(nw <= 360.dp && lw <= 360.dp && lw == nw, "widths $nw / $lw")
        assertTrue(large.bottom - large.top > normal.bottom - normal.top, "large text grows the strip taller")
        val view = onNodeWithTag(LIMIT_VIEW_TAG).getBoundsInRoot()
        assertTrue(view.right <= large.right, "View usage stays inside the strip")
        assertTrue(density > 0f)
    }

    @Test
    fun `S24-LIMIT-026 in Arabic the words are Arabic and the actions sit at the start edge`() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        setContent { Strip(friday5, twelveHour = true, rtl = true) }
        val title = onNodeWithText("اقتربت من الحد").getBoundsInRoot()
        onNodeWithText("يُعاد يوم الجمعة الساعة 5:00\u00A0ص").assertExists()
        val view = onNodeWithText("عرض الاستخدام").getBoundsInRoot()
        assertTrue(view.right <= title.left, "RTL: the pill sits left of the title")
    }

    /**
     * Desktop render for the side-by-side with the owner's reference: the strip inside a real composer box, dark, at
     * 360 dp and on a wide phone, plus Arabic. Written to `looks-out/s24-limit/`.
     */
    @Test
    fun `S24-LIMIT-027 desktop render of the strip in the composer box`() {
        val out = File(System.getProperty("user.dir"), "../../../looks-out/s24-limit").also { it.mkdirs() }
        for ((name, tag, width) in listOf(Triple("strip-en-412", "en", 412), Triple("strip-en-360", "en", 360), Triple("strip-ar-360", "ar", 360))) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            val scale = 2.625f
            runDesktopComposeUiTest((width * scale).toInt(), (250 * scale).toInt()) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(scale)) {
                        KitTheme(
                            KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                            RecordingPlatform(KitSystemSignals(is24Hour = false)),
                            tag,
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(
                                LocalKitRedactor provides Redactor(),
                                LocalLayoutDirection provides if (tag == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
                            ) {
                                Column(Modifier.fillMaxSize().background(LocalKitColors.current.ground).padding(16.dp)) {
                                    KitComposerBox(
                                        running = false,
                                        field = {
                                            KitField(
                                                rememberTextFieldState(if (tag == "ar") "هل يمكننا بناء هذا؟" else "Can we build this?"),
                                                "Ask",
                                                kind = KitFieldKind.Multiline,
                                                multilineLines = 1..6,
                                                labelShown = false,
                                                framed = false,
                                            )
                                        },
                                        leading = { KitComposerAdd("Add", {}) },
                                        chips = { KitComposerChip("Opus 5.5", {}) },
                                        trailing = { KitComposerSend("Send", {}) },
                                        above = { KitComposerLimitStrip(friday5, {}, {}) },
                                    )
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
            }
        }
    }
}
