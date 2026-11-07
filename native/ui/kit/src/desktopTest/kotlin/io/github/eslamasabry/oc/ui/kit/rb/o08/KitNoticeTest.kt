package io.github.eslamasabry.oc.ui.kit.rb.o08

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.noticeGlyph
import io.github.eslamasabry.oc.ui.kit.state.noticeTint
import io.github.eslamasabry.oc.ui.kit.state.offerDrop
import io.github.eslamasabry.oc.ui.kit.state.offerStacks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Order 08: KitNotice's rules from kit_notice.dart (glyphs, ink, the offer's stacking and drop, the error's copy). */
@OptIn(ExperimentalTestApi::class, RedactorApi::class)
class KitNoticeTest {
    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(412, 915) { content() }
            }
        }
    }

    @Test
    fun `RB08-NOTICE-001 each tone has Flutter's glyph`() {
        assertEquals(KitIcons.checkCircle, noticeGlyph(StatusTone.Ok))
        assertEquals(KitIcons.error, noticeGlyph(StatusTone.Failure))
        assertEquals(KitIcons.warning, noticeGlyph(StatusTone.Attention))
        assertEquals(KitIcons.info, noticeGlyph(StatusTone.Neutral))
        assertEquals(KitIcons.info, noticeGlyph(StatusTone.Progress))
    }

    @Test
    fun `RB08-NOTICE-002 attention and failure ink in text1, never amber or red`() {
        assertEquals(TextTone.Secondary, noticeTint(StatusTone.Neutral))
        assertEquals(TextTone.Accent, noticeTint(StatusTone.Progress))
        assertEquals(TextTone.Success, noticeTint(StatusTone.Ok))
        assertEquals(TextTone.Primary, noticeTint(StatusTone.Attention))
        assertEquals(TextTone.Primary, noticeTint(StatusTone.Failure))
    }

    @Test
    fun `RB08-NOTICE-003 an offer stacks only when sentence, action, lead, gap and close overflow the line`() {
        // 28 lead + 12 gap + 48 close = 88 fixed.
        assertFalse(offerStacks(348.dp, message = 120.dp, action = 48.dp, lead = 28.dp, close = 48.dp))
        assertTrue(offerStacks(348.dp, message = 220.dp, action = 60.dp, lead = 28.dp, close = 48.dp))
        assertFalse(offerStacks(348.dp, message = 200.dp, action = 60.dp, lead = 28.dp, close = 48.dp))
    }

    @Test
    fun `RB08-NOTICE-004 a stacked offer drops its first line onto the close's centre, whole pixels`() {
        assertEquals(12.dp, offerDrop(48.dp, 24.dp, hasClose = true))
        assertEquals(10.dp, offerDrop(48.dp, 27.dp, hasClose = true))
        assertEquals(0.dp, offerDrop(48.dp, 24.dp, hasClose = false))
        assertEquals(0.dp, offerDrop(48.dp, 60.dp, hasClose = true))
    }

    @Test
    fun `RB08-NOTICE-005 an error with details offers Copy details at its end and never shows the details`() = runComposeUiTest {
        setContent {
            Themed {
                KitErrorNotice(
                    "Couldn’t reach the laptop", failure = Failure.TimedOut(), details = Redacted("GET /session timed out"),
                    copyLabel = "Copy details", retry = KitLineAction("Try again", {}), switchServer = KitLineAction("Switch server", {}),
                )
            }
        }
        onNodeWithTag("kit-notice-copy-details", useUnmergedTree = true).assertExists()
        onNodeWithText("Switch server", useUnmergedTree = true).assertExists()
        onNodeWithText("GET /session timed out", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `RB08-NOTICE-006 a notice shows at most two actions`() = runComposeUiTest {
        setContent {
            Themed {
                KitNotice("Saved", actions = listOf(KitLineAction("One", {}), KitLineAction("Two", {}), KitLineAction("Three", {})))
            }
        }
        onNodeWithText("Two", useUnmergedTree = true).assertExists()
        onNodeWithText("Three", useUnmergedTree = true).assertDoesNotExist()
    }
}
