package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import io.github.eslamasabry.oc.feature.chat.E20Goldens.picture
import io.github.eslamasabry.oc.feature.chat.presentation.CopiedUi
import io.github.eslamasabry.oc.feature.chat.ui.CopyKeyHiddenNotes
import io.github.eslamasabry.oc.ui.gate.flicker.FlickerFailure
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiTest
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

private const val PHASE = "after"
private const val GUTTER_DP = 16f
private const val EDGE_TOLERANCE = 1f

private data class CopiedSceneCase(
    val scaleTag: String,
    val scale: Float,
    val modeTag: String,
    val dark: Boolean,
)

private val CASES = listOf(
    CopiedSceneCase("1x", 1f, "dark", true),
    CopiedSceneCase("1x", 1f, "light", false),
    CopiedSceneCase("1_3x", 1.3f, "dark", true),
    CopiedSceneCase("1_3x", 1.3f, "light", false),
)

@OptIn(ExperimentalTestApi::class)
class PolishB581Test {
    private fun forEachScene(block: KitUiTest.(CopiedSceneCase) -> Unit) {
        var flicker: FlickerFailure? = null
        for (case in CASES) {
            try {
                kitUiTest(360, 640) {
                    setContent {
                        E20Scene(case.scale, dark = case.dark) {
                            CopyNoteScene { CopyKeyHiddenNotes(CopiedUi(1, masked = true), null) }
                        }
                    }
                    waitForIdle()
                    block(case)
                }
            } catch (e: FlickerFailure) {
                flicker = flicker ?: e
            }
        }
        flicker?.let { throw it }
    }

    @Test
    fun `B-581 the copied-note scene keeps its words on the page gutter`() {
        forEachScene { case ->
            val left =
                onNodeWithText("Hello there", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .left
            assertTrue(
                abs(left - GUTTER_DP) <= EDGE_TOLERANCE,
                "Hello there (scale=${case.scale} dark=${case.dark}) expected " +
                    "$GUTTER_DP±$EDGE_TOLERANCE, was $left",
            )
        }
    }

    @Test
    fun `B-581 writes the before and after pictures`() {
        forEachScene { case ->
            val file = File("build/polish-render/38-$PHASE-copied-${case.scaleTag}-${case.modeTag}.png")
            file.parentFile.mkdirs()
            ImageIO.write(picture(), "png", file)
        }
    }
}
