package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E-4: with the app in Arabic, the chat header showed an English title as "…Fix the flaky checko": the words were cut
 * at their end but the "…" stood at their start. A title reads in its own direction and its "…" closes the words.
 */
@OptIn(ExperimentalTestApi::class)
class KitTopBarTitleDirectionTest {
    private fun ComposeUiTest.capsuleTitle(locale: String, title: String): TextLayoutResult {
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), locale, stillFrame = true) {
                Box(Modifier.width(360.dp)) {
                    KitHeaderCapsule {
                        KitTopBar(
                            title,
                            exit = KitTopBarExit.Back,
                            exitLabel = "Back",
                            onExit = {},
                            onTitleTap = {},
                            titleTapLabel = "Conversations",
                            backBesideTitle = true,
                        )
                    }
                }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithTag("kit-top-bar-title", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }

    @Test
    fun `E-4 B-KIT-2014 an English title in the Arabic capsule reads left to right and ends in its own ellipsis`() =
        runComposeUiTest {
            val line = capsuleTitle("ar", ENGLISH)
            assertTrue(line.isLineEllipsized(0), "the long title is cut to one line")
            assertEquals(ResolvedTextDirection.Ltr, line.getParagraphDirection(0), "the English title's direction")
            // The first letter stands at the left edge, so the "…" closes the words at the right.
            val first = line.getBoundingBox(0)
            assertTrue(first.left < 1f, "the first letter starts at ${first.left}, not at the left edge")
        }

    @Test
    fun `E-4 B-KIT-2015 an Arabic title in the English capsule reads right to left and ends in its own ellipsis`() =
        runComposeUiTest {
            val line = capsuleTitle("en", ARABIC)
            assertTrue(line.isLineEllipsized(0), "the long title is cut to one line")
            assertEquals(ResolvedTextDirection.Rtl, line.getParagraphDirection(0), "the Arabic title's direction")
            // The first letter stands at the right edge, so the "…" closes the words at the left.
            val first = line.getBoundingBox(0)
            val width = line.size.width.toFloat()
            assertTrue(first.right > width - 1f, "the first letter ends at ${first.right} of $width, not at the right")
        }

    private companion object {
        const val ENGLISH = "Fix the flaky checkout test on the cart page and in the coupon flow"
        const val ARABIC = "أصلح اختبار الدفع المتقطع في صفحة السلة وفي مسار القسيمة أيضا"
    }
}
