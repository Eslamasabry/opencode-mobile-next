package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.lamp.K2
import io.github.eslamasabry.oc.ui.kit.lamp.k2Host
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reordering by the handle (LAMP-CRAFT §5 "Reorder"): the row lifts at once with LongPress, one Select tick per new
 * place, no haptic on the drop, the order lands; rows that may not move have no handle; in every style and direction.
 */
@OptIn(ExperimentalTestApi::class)
class KitReorderRowsTest {
    private class Scene {
        var items by mutableStateOf(listOf("Write the release notes", "Bank API migration", "Deploy preview to staging"))
        val moves = mutableListOf<Pair<Int, Int>>()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.host(scene: Scene, style: Style = Style.Solid, rtl: Boolean = false, platform: FakeKitPlatform = FakeKitPlatform(), fixed: Set<String> = emptySet()) =
        k2Host(style, rtl = rtl, platform = platform) {
            KitReorderRows(
                scene.items,
                key = { it },
                title = { it },
                onMove = { a, b ->
                    scene.moves += a to b
                    scene.items = scene.items.toMutableList().apply { add(b, removeAt(a)) }
                },
                words = K2.reorder,
                enabled = { it !in fixed },
            ) { item, handle ->
                Row(Modifier.fillMaxWidth().height(ROW.dp)) {
                    handle()
                    Box(Modifier.weight(1f)) { KitText(item, role = TypeRole.RowTitle) }
                }
            }
        }

    @Test
    fun `B-LAMP-K2-030 drag the handle - lift with LongPress, a Select tick per new place, the order lands, every style and direction`() {
        for (style in K2.styles) for (rtl in listOf(false, true)) {
            runDesktopComposeUiTest(412, 400) {
                val platform = FakeKitPlatform()
                val scene = Scene()
                host(scene, style, rtl, platform)
                waitForIdle()
                val handles = onAllNodesWithTag(REORDER_HANDLE_TAG, useUnmergedTree = true)
                handles[0].performTouchInput {
                    down(center)
                    // 42, 84, 126 dp down: the centre passes the second row's middle, then the third's.
                    moveBy(Offset(0f, ROW * 0.75f))
                    moveBy(Offset(0f, ROW * 0.75f))
                    moveBy(Offset(0f, ROW * 0.75f))
                    up()
                }
                waitForIdle()
                val what = "$style ${if (rtl) "rtl" else "ltr"}"
                assertEquals(listOf(0 to 2), scene.moves, "$what: dropped two places down")
                assertEquals(KitHaptic.LongPress, platform.haptics.first(), "$what: the row lifts at once with LongPress")
                assertEquals(2, platform.haptics.count { it == KitHaptic.Select }, "$what: one Select per new place")
                assertEquals(3, platform.haptics.size, "$what: no haptic on the drop")
                val top = onNodeWithText("Bank API migration").getUnclippedBoundsInRoot().top
                val last = onNodeWithText("Write the release notes").getUnclippedBoundsInRoot().top
                assertTrue(last > top, "$what: the moved row settled below")
            }
        }
    }

    @Test
    fun `B-LAMP-K2-031 a row that may not move has no handle`() =
        runDesktopComposeUiTest(412, 400) {
            val scene = Scene()
            host(scene, fixed = setOf("Deploy preview to staging"))
            waitForIdle()
            assertEquals(2, onAllNodesWithTag(REORDER_HANDLE_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
        }

    private companion object {
        const val ROW = 56
    }
}
