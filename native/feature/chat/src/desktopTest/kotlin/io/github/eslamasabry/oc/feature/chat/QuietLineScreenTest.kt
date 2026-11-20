package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.turnfacts.TurnStallNotice
import io.github.eslamasabry.oc.feature.chat.presentation.LiveNoticeUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.turn.SWEEP_TAG
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-435: real conversation and real composer. Labels must be whole; Stop must stay anchored as the clock changes. */
@OptIn(ExperimentalTestApi::class)
class QuietLineScreenTest {
    @Composable private fun Root(locale: String, scale: Float, motion: MotionLevel, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            KitTheme(appearance = KitAppearance(motion = motion), platform = DesktopKitPlatform(), localeTag = locale, stillFrame = true) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { content() }
                }
            }
        }
    }
    private fun check(locale: String, scale: Float, motion: MotionLevel) {
        val beforeLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag(locale))
            render(locale, scale, motion)
        } finally { Locale.setDefault(beforeLocale) }
    }
    private fun render(locale: String, scale: Float, motion: MotionLevel): Unit = kitUiTest(360, 800) {
        val scene = ChatScene()
        val state = scene.live().copy(status = null, rows = persistentListOf(prompt("t", "Explain this carefully")), live = LiveTurnUi(false))
        var facts by mutableStateOf(TurnFactsUi(nowMs = 72_000, runStartedAtMs = 0, live = LiveNoticeUi.Stall(TurnStallNotice.ModelSlow)))
        setContent { Root(locale, scale, motion) { scene.Screen(state, facts = facts, projectName = null) } }
        val prefix = if (locale == "ar") "ما زال يفكر" else "Still thinking"
        fun words(): String = onNodeWithText(prefix, substring = true, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
        fun whole() {
            val query = onNodeWithText(prefix, substring = true, useUnmergedTree = true)
            query.assertIsDisplayed()
            val node = query.fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            assertTrue(layouts.isNotEmpty(), "real text layout is inspectable")
            for (layout in layouts) {
                assertFalse(layout.hasVisualOverflow, "whole label at $locale $scale")
                assertTrue((0 until layout.lineCount).none(layout::isLineEllipsized))
                assertTrue(layout.size.width <= node.boundsInRoot.width + 1, "text cannot extend beyond its visible bounds")
            }
            val bounds = query.getUnclippedBoundsInRoot()
            assertTrue(bounds.left.value >= 0 && bounds.right.value <= 360, "inside the actual 360dp screen")
        }
        whole()
        val before = words()
        assertTrue(Regex("\\p{N}").containsMatchIn(before), "the live label includes elapsed time")
        val stop = boundsOfTag("chat-stop-button")
        runOnIdle { facts = facts.copy(nowMs = 73_000) }
        whole()
        assertTrue(before != words(), "elapsed time advances even with Motion off")
        assertEquals(stop, boundsOfTag("chat-stop-button"), "Stop stays where it was")
        if (motion == MotionLevel.Off) onNodeWithTag(SWEEP_TAG, useUnmergedTree = true).assertDoesNotExist()
        else onNodeWithTag(SWEEP_TAG, useUnmergedTree = true).assertExists()
        runOnIdle { facts = facts.copy(nowMs = 312_000) }
        whole()
        assertEquals(stop, boundsOfTag("chat-stop-button"), "the calm long-think hint adds no composer movement")
        val picture = File("build/reports/quiet-line/$locale-$scale-${motion.name}.png")
        picture.parentFile.mkdirs()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", picture)
        runOnIdle { facts = facts.copy(live = LiveNoticeUi.Stall(TurnStallNotice.ConnectionLost)) }
        onNodeWithText(prefix, substring = true, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(SWEEP_TAG, useUnmergedTree = true).assertDoesNotExist()
    }
    /** Pictures only (density 3, 360 dp wide, dark): the live line at 1 min and at 5 min with the calm hint. */
    private fun pictures(locale: String, motion: MotionLevel) {
        val beforeLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag(locale))
            kitUiTest(1080, 2400) {
                val scene = ChatScene()
                val state = scene.live().copy(status = null, rows = persistentListOf(prompt("t", "Explain this carefully")), live = LiveTurnUi(false))
                var facts by mutableStateOf(TurnFactsUi(nowMs = 72_000, runStartedAtMs = 0, live = LiveNoticeUi.Stall(TurnStallNotice.ModelSlow)))
                setContent { Root(locale, 1.3f, motion) { scene.Screen(state, facts = facts, projectName = null) } }
                for ((name, now) in listOf("1min" to 72_000L, "5min" to 312_000L)) {
                    runOnIdle { facts = facts.copy(nowMs = now) }
                    waitForIdle()
                    val picture = File("build/reports/quiet-line/shot-$locale-${motion.name}-$name.png")
                    picture.parentFile.mkdirs()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", picture)
                }
            }
        } finally { Locale.setDefault(beforeLocale) }
    }
    @Test fun `B-435 pictures at density 3`() {
        for (l in listOf("en", "ar")) for (m in listOf(MotionLevel.Full, MotionLevel.Off)) pictures(l, m)
    }
    @Test fun `B-435 en 360dp text 1 live clock and real loss`() { check("en", 1f, MotionLevel.Full) }
    @Test fun `B-435 en 360dp text 1 point 3 live clock and real loss`() { check("en", 1.3f, MotionLevel.Full) }
    @Test fun `B-435 ar 360dp text 1 live clock and real loss`() { check("ar", 1f, MotionLevel.Full) }
    @Test fun `B-435 ar 360dp text 1 point 3 live clock and real loss`() { check("ar", 1.3f, MotionLevel.Full) }
    @Test fun `B-435 en Motion off counts time without a sweep`() { check("en", 1.3f, MotionLevel.Off) }
    @Test fun `B-435 ar Motion off counts time without a sweep`() { check("ar", 1.3f, MotionLevel.Off) }
}
