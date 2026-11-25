package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import io.github.eslamasabry.oc.feature.chat.E20Goldens.picture
import io.github.eslamasabry.oc.feature.chat.presentation.CopiedUi
import io.github.eslamasabry.oc.feature.chat.ui.CopyKeyHiddenNotes
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

private const val PHASE = "after"
private const val GUTTER_DP = 16f
private const val EDGE_TOLERANCE = 1f

@OptIn(ExperimentalTestApi::class)
class PolishB581Test {
    @Test
    fun `B-581 the copied-note scene keeps its words on the page gutter`() {
        for (dark in listOf(true, false)) {
            for (scale in listOf(1f, 1.3f)) {
                kitUiTest(360, 640) {
                    setContent {
                        E20Scene(scale, dark = dark) {
                            CopyNoteScene { CopyKeyHiddenNotes(CopiedUi(1, masked = true), null) }
                        }
                    }
                    waitForIdle()
                    val left =
                        onNodeWithText("Hello there", useUnmergedTree = true)
                            .fetchSemanticsNode()
                            .boundsInRoot
                            .left
                    assertTrue(
                        abs(left - GUTTER_DP) <= EDGE_TOLERANCE,
                        "Hello there (scale=$scale dark=$dark) expected $GUTTER_DP±$EDGE_TOLERANCE, was $left",
                    )
                }
            }
        }
    }

    @Test
    fun `B-581 writes the before and after pictures`() {
        for ((scaleTag, scale) in listOf("1x" to 1f, "1_3x" to 1.3f)) {
            for ((modeTag, dark) in listOf("dark" to true, "light" to false)) {
                kitUiTest(360, 640) {
                    setContent {
                        E20Scene(scale, dark = dark) {
                            CopyNoteScene { CopyKeyHiddenNotes(CopiedUi(1, masked = true), null) }
                        }
                    }
                    waitForIdle()
                    val file = File("build/polish-render/38-$PHASE-copied-$scaleTag-$modeTag.png")
                    file.parentFile.mkdirs()
                    ImageIO.write(picture(), "png", file)
                }
            }
        }
    }
}
