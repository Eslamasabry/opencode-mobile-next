package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitFeedAgent
import io.github.eslamasabry.oc.ui.kit.row.KitRecentRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowPanel
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Issue 147 (Galaxy S22, 360 dp): a row whose state word was a sentence ("Can't reopen old conversations") let the
 * word take the row, and the title read "Build …" and the project "ha…". The end of a row (time over state word) takes
 * at most its share of the width; a longer word is cut, never the title.
 */
@OptIn(ExperimentalTestApi::class)
class KitRecentRowEndTest {
    /** The ink of the row's start (mark and title), left of the end's share, at 360 dp. */
    private fun titleInk(state: KitRowState?): Int {
        var ink = 0
        runComposeUiTest {
            var fill = Color.Unspecified
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2f)) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        stillFrame = true,
                        localeTag = "en",
                    ) {
                        fill = LocalKitColors.current.surface1
                        Box(Modifier.width(360.dp).testTag("row")) {
                            KitRowPanel {
                                KitRecentRow(
                                    title = "Fix the tests",
                                    agent = KitFeedAgent("", "Antigravity"),
                                    time = "8 min ago",
                                    onClick = {},
                                    state = state,
                                )
                            }
                        }
                    }
                }
            }
            val map = onNodeWithTag("row").captureToImage().toPixelMap()
            // Left of 55 % of the width: the mark and the title, never the end's time or state word.
            val right = (map.width * 0.55f).toInt()
            for (x in 0 until right) for (y in 0 until map.height) {
                val c = map[x, y]
                if (abs(c.red - fill.red) > 0.08f || abs(c.green - fill.green) > 0.08f || abs(c.blue - fill.blue) > 0.08f)
                    ink++
            }
        }
        return ink
    }

    @Test
    fun `I147-ROW-001 a long state word at 360 dp never cuts a title that fits beside a short one`() {
        val plain = titleInk(KitRowState("Done", KitRowStateTone.Quiet))
        val long = titleInk(KitRowState("Can't reopen old conversations, says a long word", KitRowStateTone.Quiet))
        assertTrue(plain > 0, "the title draws")
        assertTrue(long >= plain * 0.95, "the title keeps its words beside a long state word ($long of $plain)")
    }
}
