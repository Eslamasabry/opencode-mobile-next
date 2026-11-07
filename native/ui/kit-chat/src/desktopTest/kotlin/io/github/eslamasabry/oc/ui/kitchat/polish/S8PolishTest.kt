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
import io.github.eslamasabry.oc.ui.kitchat.code.codeCornerReserve
import io.github.eslamasabry.oc.ui.kitchat.code.codeOverflows
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerTray
import io.github.eslamasabry.oc.ui.kitchat.composer.KitTrayAction
import io.github.eslamasabry.oc.ui.kitchat.composer.KitTrayKind
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
    fun `P-001 only the first line keeps clear of Copy - the other lines use the block's full width`() =
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
            // The block's text box: 360 dp less S3 on each side. A wrapped line of two-letter words leaves at most
            // one word's width (about 3 characters) at its end.
            val inner = 360f - 2 * Space.S3
            val second = onNodeWithText(words, useUnmergedTree = true).box()
            assertTrue(second.width >= inner - 30f, "second line wraps at ${second.width} dp of $inner")
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
            near(block.right, wrap.right, what = "Wrap's end on the block's end")
            near(wrap.left, copy.right, what = "Copy right before Wrap")
        }

    @Test
    fun `P-001 the corner reserve and the overflow rule`() {
        assertEquals(0, codeCornerReserve(0))
        assertEquals(FlutterKitTokens.MIN_TARGET - Space.S3, codeCornerReserve(1))
        assertEquals(2 * FlutterKitTokens.MIN_TARGET - Space.S3, codeCornerReserve(2))
        // A short block fits; a long line or a first line running into Copy overflows.
        assertFalse(codeOverflows(longest = 100, first = 100, room = 300, copyReserve = 36))
        assertTrue(codeOverflows(longest = 301, first = 10, room = 300, copyReserve = 36))
        assertTrue(codeOverflows(longest = 280, first = 280, room = 300, copyReserve = 36))
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
            near(a.height, b.height, 0.5f, "three lines' height")
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
