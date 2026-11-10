@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The room the chrome takes from a page's body ([kitScreenChromePadding]) is read where it is used, not while the
 * page is composed (jank pass 2026-11-10). The message box growing a line used to hand the body a new padding object:
 * the whole frame and the body were composed again for every change of height, and again for every frame of a status
 * line folding in.
 *
 * Guard: the bottom block grows; the body keeps the same padding object, is not composed again, and the object's
 * value follows the new height.
 */
class KitChromeRoomLazyTest {
    @Test
    fun `the bottom block growing does not compose the body again`() = runComposeUiTest {
        var blockHeight by mutableStateOf(48)
        var bodyCompositions = 0
        var room: PaddingValues? = null
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                Box(Modifier.size(412.dp, 720.dp)) {
                    KitScreen(
                        page = true,
                        overlayChrome = true,
                        bottom = { Box(Modifier.fillMaxWidth().height(blockHeight.dp)) },
                    ) {
                        room = kitScreenChromePadding()
                        SideEffect { bodyCompositions++ }
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        waitForIdle()
        val first = checkNotNull(room)
        val before = first.calculateBottomPadding()
        val compositions = bodyCompositions

        blockHeight = 96
        waitForIdle()

        assertSame(first, room, "the body keeps one padding object")
        assertEquals(compositions, bodyCompositions, "the body is not composed again when the block grows")
        assertEquals(48.dp, first.calculateBottomPadding() - before, "the padding follows the block's new height")
    }
}
