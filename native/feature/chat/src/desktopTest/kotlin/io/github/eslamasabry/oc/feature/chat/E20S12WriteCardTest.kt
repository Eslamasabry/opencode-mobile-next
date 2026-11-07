package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnStep
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolOutputView
import io.github.eslamasabry.oc.feature.chat.E20Goldens.compareGolden
import io.github.eslamasabry.oc.feature.chat.ui.ToolStepBody
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * E20-S12 (owner's phone, 11-07): an opened Write drew its path and "Show all output" over code lines 1–2, and the
 * code started left of the card's text.
 */
@OptIn(ExperimentalTestApi::class)
class E20S12WriteCardTest {
    private val path =
        "/home/owner/projects/opencode-mobile-next/native/feature/chat/src/commonMain/kotlin/io/github/" +
            "eslamasabry/oc/feature/chat/ui/WriteTargetWithAVeryLongName.kt"

    private fun writeStep(lines: Int = 24): TurnStep {
        val content = (1..lines).joinToString("\n") { "val line$it = \"content of line $it\"" }
        val state =
            ToolState(
                ToolStatus.Completed,
                input = JsonObject(mapOf("filePath" to JsonPrimitive(path), "content" to JsonPrimitive(content))),
                output = "Wrote file successfully.",
                startedAtMs = 1_000,
                completedAtMs = 1_040,
            )
        return TurnStep("w1", toolFacts("write", state), StepState.Done, 40, 1_000, toolOutputView(state, "write"))
    }

    @Composable
    private fun WriteRow(step: TurnStep, opened: Boolean) {
        KitToolRow(
            KitToolView(
                id = step.partId,
                kind = KitToolKind.Write,
                title = "Write",
                subject = step.facts.subject,
                detail = null,
                status = KitToolStatus.Done,
                durationLabel = null,
                added = 24,
                removed = null,
                statusLabel = "Done",
            ),
            opened = opened,
            onToggle = {},
            body = { ToolStepBody(step, null) },
        )
    }

    private data class Frame(val width: Int, val height: Int, val name: String)

    private val frames = listOf(Frame(360, 1200, "360"), Frame(600, 1200, "600"), Frame(800, 1200, "landscape"))

    @Test
    fun `E20-S12 an opened Write stacks its path, code and Show all output on the card's text edge`() {
        val step = writeStep()
        for (frame in frames) for (scale in listOf(1f, 1.3f)) {
            runDesktopComposeUiTest(frame.width, frame.height) {
                setContent { E20Scene(scale) { WriteRow(step, opened = true) } }
                waitForIdle()
                val where = "${frame.name} dp at ${scale}x"
                val title = boundsOfText("Write")
                val body = boundsOfTag("kit-tool-body")
                val pathLine = boundsOfText(path)
                val first = boundsOfText("val line1 =", substring = true)
                val twelfth = boundsOfText("val line12 =", substring = true)
                val more = boundsOfText("Show all output")
                assertTrue(abs(body.left - title.left) < 1f, "$where: the body starts on the title's edge, $body vs $title")
                assertTrue(abs(pathLine.left - title.left) < 1f, "$where: the path starts on the title's edge, $pathLine")
                assertTrue(first.left >= body.left - 0.5f, "$where: the code starts at or inside the text edge, $first")
                assertTrue(pathLine.bottom <= first.top + 0.5f, "$where: the path $pathLine is above line 1 $first")
                assertTrue(twelfth.bottom <= more.top + 0.5f, "$where: Show all output $more is under line 12 $twelfth")
            }
        }
    }

    @Test
    fun `E20-S12 Write with a long path and more than 20 lines, collapsed and expanded, as goldens`() {
        val step = writeStep()
        for ((name, opened, scale) in
            listOf(
                Triple("e20-s12-write-collapsed-360", false, 1f),
                Triple("e20-s12-write-expanded-360", true, 1f),
                Triple("e20-s12-write-expanded-360-1.3x", true, 1.3f),
            )) {
            runDesktopComposeUiTest(360, 760) {
                setContent { E20Scene(scale) { WriteRow(step, opened) } }
                waitForIdle()
                compareGolden(name)
            }
        }
        runDesktopComposeUiTest(600, 760) {
            setContent { E20Scene { WriteRow(step, opened = true) } }
            waitForIdle()
            compareGolden("e20-s12-write-expanded-600")
        }
    }
}
