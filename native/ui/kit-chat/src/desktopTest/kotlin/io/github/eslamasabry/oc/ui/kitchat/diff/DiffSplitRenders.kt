@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.diff

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Pictures only (asserts nothing): the split diff at 840, 1280, 1280 Arabic and a 360 phone,
 * into /dev/shm/diffsplit.
 */
class DiffSplitRenders {
    private val longLine = "val route = " + "segment-".repeat(18) + "end!"

    private fun fixture(): DiffFile {
        val old = (1..34).map { "ctx $it" }
        val new = old.toMutableList()
        new[2] = "changed alpha"
        new[13] = longLine
        new[25] = "added beta"
        new.add(26, "added gamma")
        new.removeAt(24)
        return DiffModel.fromBeforeAfter("route.kt", old.joinToString("\n"), new.joinToString("\n"))
    }

    @Test
    fun `diff split renders`() {
        val out = File("/dev/shm/diffsplit").also { it.mkdirs() }
        for (shot in listOf(
            Shot("w840", 840, 800, "en"),
            Shot("w1280", 1280, 800, "en"),
            Shot("w1280-ar", 1280, 800, "ar"),
            Shot("phone", 360, 780, "en"),
        )) {
            val name = shot.name
            val w = shot.w
            val h = shot.h
            val tag = shot.tag
            runDesktopComposeUiTest(w, h) {
                setContent {
                    ChatTestTheme(localeTag = tag) {
                        CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(w.toFloat(), h.toFloat())) {
                            Box(Modifier.size(w.dp, h.dp)) {
                                KitDiffView(
                                    fixture(),
                                    maxLines = 5000,
                                    hunkLabel = { _, _, _, _ -> "hunk" },
                                    gapLabel = { "$it hidden" },
                                )
                            }
                        }
                    }
                }
                waitForIdle()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
            }
        }
    }

    private data class Shot(val name: String, val w: Int, val h: Int, val tag: String)
}
