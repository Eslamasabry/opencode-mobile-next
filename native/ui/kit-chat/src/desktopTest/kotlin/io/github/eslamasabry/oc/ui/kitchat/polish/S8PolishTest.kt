package io.github.eslamasabry.oc.ui.kitchat.polish

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeChunk
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.code.codeLineInsets
import io.github.eslamasabry.oc.ui.kitchat.code.codeOverflows
import io.github.eslamasabry.oc.ui.kitchat.code.codeTextRoom
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerTray
import io.github.eslamasabry.oc.ui.kitchat.composer.KitTrayAction
import io.github.eslamasabry.oc.ui.kitchat.composer.KitTrayKind
import io.github.eslamasabry.oc.ui.kitchat.composer.trayPerRow
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitBlockKind
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyBlock
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** S8 polish (auditor sweep of a2e7d3fa5): each test fails on the build the auditor measured. */
@OptIn(ExperimentalTestApi::class)
class S8PolishTest {
    private fun SemanticsNodeInteraction.box(): Rect =
        getUnclippedBoundsInRoot().let { Rect(it.left.value, it.top.value, it.right.value, it.bottom.value) }

    private fun near(expected: Float, actual: Float, slack: Float = 1f, what: String) =
        assertTrue(abs(expected - actual) <= slack, "$what: expected $expected, was $actual")

    // ---- P-001: code lines are no longer clipped at the Copy/Wrap column ----

    @Test
    fun `P-001 C-006 every line keeps S3 clear of the Copy column, as in Flutter`() =
        runComposeUiTest {
            val words = (1..120).joinToString(" ") { "ab" }
            setContent {
                ChatTestTheme {
                    KitCodeBlock(
                        "short\n$words", KitCodeKind.Output, Modifier.width(360.dp), wrap = true,
                        copyLabel = "Copy", onCopy = {},
                    )
                }
            }
            // C-006 supersedes P-001's full-width lines: Flutter lays the lines beside the control column
            // (`kit_code_block.dart:575-593`), so every line wraps S3 before it, not only the first.
            val second = onNodeWithText(words, useUnmergedTree = true).box()
            val copy = onNodeWithContentDescription("Copy").box()
            assertTrue(second.right <= copy.left - Space.S3 + 1f, "line ends at ${second.right}, Copy at ${copy.left}")
            assertTrue(second.width >= copy.left - Space.S3 - Space.S4 - 30f, "second line wraps at ${second.width} dp")
        }

    @Test
    fun `P-001 the first line wraps clear of the corner buttons`() =
        runComposeUiTest {
            val words = (1..120).joinToString(" ") { "ab" }
            setContent {
                ChatTestTheme {
                    KitCodeBlock(words, KitCodeKind.Output, Modifier.width(360.dp), wrap = true, copyLabel = "Copy", onCopy = {})
                }
            }
            val first = onNodeWithText(words, useUnmergedTree = true).box()
            val copy = onNodeWithContentDescription("Copy").box()
            assertTrue(first.right <= copy.left + 1f, "first line ends at ${first.right}, Copy starts at ${copy.left}")
        }

    @Test
    fun `P-001 Copy and Wrap are both 48 dp icon buttons and Copy sits flush with the block's end`() =
        runComposeUiTest {
            setContent {
                ChatTestTheme {
                    Column(Modifier.testTag("page")) {
                        KitCodeBlock(
                            "x = 1\n" + "y".repeat(400), KitCodeKind.Code, Modifier.width(360.dp).testTag("block"),
                            copyLabel = "Copy", onCopy = {},
                        )
                    }
                }
            }
            val target = FlutterKitTokens.MIN_TARGET.toFloat()
            val block = onNodeWithTag("block").box()
            val copy = onNodeWithContentDescription("Copy").box()
            val wrap = onNodeWithContentDescription("Wrap lines").box()
            near(target, copy.width, what = "Copy width")
            near(target, copy.height, what = "Copy height")
            near(target, wrap.width, what = "Wrap width")
            near(target, wrap.height, what = "Wrap height")
            // C-006: Wrap sits under Copy (Flutter `kit_code_block.dart:584-592`), Copy 1 dp from the end edge.
            near(block.right - 1f, copy.right, what = "Copy's end 1 dp inside the block's end")
            near(copy.left, wrap.left, what = "Wrap in Copy's column")
            near(copy.bottom, wrap.top, what = "Wrap right under Copy")
        }

    @Test
    fun `P-001 C-006 the text room, the overflow rule and the centring insets`() {
        // 360 less S4, S1, the 48 dp column and the S3 gap (`_plan`, kit_code_block.dart:704-712).
        assertEquals(280f, codeTextRoom(360f))
        assertFalse(codeOverflows(longest = 280.4f, room = 280f))
        assertTrue(codeOverflows(longest = 281f, room = 280f))
        // A 19 dp mono line centred on the 48 dp Copy target: 14 above, 15 below.
        assertEquals(14f to 15f, codeLineInsets(19f))
        assertEquals(0f to 0f, codeLineInsets(60f))
    }

    // ---- P-002: a fence's rows draw as one box in one style ----

    @Test
    fun `P-002 a revealing code row and a settled code chunk start their text on the same edge at the same size`() =
        runComposeUiTest {
            val streamed = "a = 1\nb = 2\nc = 3"
            val settled = "d = 4\ne = 5\nf = 6"
            setContent {
                ChatTestTheme {
                    Column(Modifier.width(360.dp)) {
                        KitReplyBlock(
                            streamed, KitBlockKind.Code, live = false, streamed = false, revealKey = "r",
                            revealGroup = "g", registry = RevealRegistry(), motion = KitMotionLevel.Off,
                            continued = false, continues = true,
                        )
                        KitCodeChunk(settled, continued = true, continues = false, Modifier.padding(horizontal = Space.S4.dp))
                    }
                }
            }
            val a = onNodeWithText(streamed, useUnmergedTree = true).box()
            val b = onNodeWithText(settled, useUnmergedTree = true).box()
            near(a.left, b.left, 0.5f, "text start")
            near(a.height, b.height, 0.5f, "line height")
        }

    @Test
    fun `P-002 a revealing code row never wraps - its lines scroll sideways like the settled rows`() =
        runComposeUiTest {
            val long = "total = " + (1..80).joinToString(" + ") { "len(x$it)" }
            setContent {
                ChatTestTheme {
                    Column(Modifier.width(360.dp)) {
                        KitReplyBlock(
                            long, KitBlockKind.Code, live = false, streamed = false, revealKey = "r", revealGroup = "g",
                            registry = RevealRegistry(), motion = KitMotionLevel.Off,
                        )
                    }
                }
            }
            val layout = textLayout(onNodeWithText(long, useUnmergedTree = true))
            assertEquals(1, layout.lineCount)
        }

    // ---- P-004: the + tray ----

    @Test
    fun `P-004 every tray choice draws its own glyph`() {
        val icons = KitTrayKind.entries.map { it.icon }
        assertEquals(icons.size, icons.toSet().size, "two tray choices share a glyph")
    }

    @Test
    fun `P-004 a wrapped tray label is centred line by line and keeps 4 dp from its cell`() =
        runComposeUiTest {
            val label = "Paste image from clipboard"
            setContent {
                ChatTestTheme {
                    KitComposerTray(
                        listOf(
                            KitTrayAction(label, KitTrayKind.Clipboard) {},
                            KitTrayAction("Reuse a prompt", KitTrayKind.History) {},
                            KitTrayAction("Commands", KitTrayKind.Commands) {},
                            KitTrayAction("Send later or repeat", KitTrayKind.Schedule) {},
                        ),
                        Modifier.width(360.dp),
                    )
                }
            }
            val node = onNodeWithText(label, useUnmergedTree = true)
            val box = node.box()
            val cell = 360f / 4
            assertTrue(box.left >= Space.S1 - 0.5f, "label starts ${box.left} dp into its cell")
            assertTrue(box.right <= cell - Space.S1 + 0.5f, "label ends at ${box.right} of a $cell dp cell")
            val layout = textLayout(node)
            assertTrue(layout.lineCount > 1, "the label wraps in a $cell dp cell")
            for (line in 0 until layout.lineCount) {
                val left = layout.getLineLeft(line)
                val right = layout.size.width - layout.getLineRight(line)
                assertTrue(abs(left - right) <= 1.5f, "line $line: $left px before, $right px after")
            }
        }

    @Test
    fun `P-004 the tray takes three choices to a row at large text so whole words fit`() {
        assertEquals(4, trayPerRow(1f))
        assertEquals(4, trayPerRow(1.15f))
        assertEquals(3, trayPerRow(1.3f))
    }

    // ---- P-011: the opened tool group has one start edge ----

    @Test
    fun `P-011 an opened work line's step names start on the summary's text edge`() =
        runComposeUiTest {
            setContent {
                ChatTestTheme {
                    Column(Modifier.width(412.dp)) {
                        KitWorkLine("Read 1 file", "Done", KitWorkState.Done, expanded = true, onToggle = {}) {
                            KitToolRow(
                                KitToolView("s1", KitToolKind.Read, "Opened", "lib/main.dart", null, KitToolStatus.Done, "0.4s", null, null, "Done"),
                                opened = false, onToggle = {},
                            )
                        }
                    }
                }
            }
            val summary = onNodeWithText("Read 1 file", useUnmergedTree = true).box()
            val step = onNodeWithText("Opened", useUnmergedTree = true).box()
            near(summary.left, step.left, 0.5f, "step name edge")
            // 16 dp page edge + the 16 dp mark + one 8 dp step (the auditor's 105 px at 2.625 px per dp).
            near(Space.S4 + 16f + Space.S2, step.left, 0.5f, "text edge")
        }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }

}
