package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitPaneColumn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Big screens: the conversation's header beside the list has no Back; its second line closes up under the title as with Back. */
@OptIn(ExperimentalTestApi::class)
class KitTopBarBesideListTest {
    @Composable
    private fun Bar(beside: Boolean) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                CompositionLocalProvider(LocalKitPaneColumn provides (if (beside) 1000.dp else null)) {
                    Box(Modifier.width(800.dp)) {
                        KitTopBar(
                            "Hi",
                            subtitle = "projects · This phone",
                            exit = KitTopBarExit.Back,
                            exitLabel = "Back",
                            onExit = {},
                            backBesideTitle = true,
                        )
                    }
                }
            }
        }
    }

    private fun gap(beside: Boolean): Pair<Float, Float> {
        var out = 0f to 0f
        runComposeUiTest {
            setContent { Bar(beside) }
            val title = onNodeWithTag("kit-top-bar-title", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val line = onNodeWithText("projects · This phone", useUnmergedTree = true).getUnclippedBoundsInRoot()
            out = (line.top - title.bottom).value to title.top.value
        }
        return out
    }

    @Test
    fun `B-NAV-234 beside the list the header's second line sits as close under the title as with Back`() {
        val (withBack, titleTopBack) = gap(beside = false)
        val (beside, titleTopBeside) = gap(beside = true)
        assertEquals(withBack, beside, 0.5f, "the gap under the title: with Back $withBack, beside the list $beside")
        assertEquals(titleTopBack, titleTopBeside, 0.5f, "the title keeps its height")
        assertTrue(beside < 12f, "the line is close to the title ($beside)")
    }
}
