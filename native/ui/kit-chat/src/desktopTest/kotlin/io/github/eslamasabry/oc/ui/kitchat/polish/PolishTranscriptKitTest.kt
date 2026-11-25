@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.polish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffPreview
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestChange
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestLook
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestTarget
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestTargets
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepLightLap
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepList
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import io.github.eslamasabry.oc.ui.kitchat.tool.RailMeasures
import io.github.eslamasabry.oc.ui.kitchat.tool.railGlyphHalf
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitPromptBubble
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class PolishTranscriptKitTest {
    @Test
    fun `B-551 InPlace ask sits in the amber card`() =
        kitUiTest(360, 480) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ChatTestTheme(dark = true) {
                        Box(Modifier.width(360.dp).background(LocalKitColors.current.ground)) {
                            KitPermissionCard(
                                title = "Run npm test?",
                                summary = "The agent waits until you answer.",
                                subject = null,
                                targets = listOf(KitRequestTarget("npm test")),
                                allowOnceLabel = "Allow once",
                                alwaysLabel = null,
                                denyLabel = "Deny",
                                onAllowOnce = {},
                                onAlways = null,
                                onDeny = {},
                                look = KitRequestLook.InPlace,
                            )
                        }
                    }
                }
            }
            waitForIdle()
            val face = onNodeWithTag("kit-request-face", useUnmergedTree = true).getBoundsInRoot()
            val title = onNodeWithText("Run npm test?", useUnmergedTree = true).getBoundsInRoot()
            val inset = (title.left - face.left).value
            assertTrue(inset >= 15.5f, "InPlace title sits >= 16 dp inside kit-request-face, was $inset dp")
        }

    @Test
    fun `B-551 diff preview sits inside targets`() =
        kitUiTest(360, 640) {
            val preview = KitDiffPreview.fromText("val a = 1\nval b = 2\nval c = 3")
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ChatTestTheme(dark = true) {
                        Box(Modifier.width(360.dp).background(LocalKitColors.current.ground)) {
                            KitPermissionCard(
                                title = "Edit src/app.kt?",
                                summary = "The agent waits until you answer.",
                                subject = null,
                                targets = listOf(KitRequestTarget("src/app.kt", "+3 −0")),
                                allowOnceLabel = "Allow once",
                                alwaysLabel = null,
                                denyLabel = "Deny",
                                onAllowOnce = {},
                                onAlways = null,
                                onDeny = {},
                                change = KitRequestChange(preview, "Change to src/app.kt", null, {}),
                            )
                        }
                    }
                }
            }
            waitForIdle()
            val node = onNodeWithTag("kit-diff-preview", useUnmergedTree = true).fetchSemanticsNode()
            val hasTargetsAncestor = ancestors(node).any {
                it.config.getOrNull(SemanticsProperties.TestTag) == "kit-request-targets"
            }
            assertTrue(hasTargetsAncestor, "kit-diff-preview must have an ancestor tagged kit-request-targets")
        }

    @Test
    fun `B-564 Details row is at least 48 dp tall`() =
        kitUiTest(360, 480) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ChatTestTheme(dark = true) {
                        Box(Modifier.width(360.dp).background(LocalKitColors.current.ground)) {
                            KitPermissionCard(
                                title = "Run npm test?",
                                summary = null,
                                subject = "npm test",
                                allowOnceLabel = "Allow once",
                                alwaysLabel = null,
                                denyLabel = "Deny",
                                onAllowOnce = {},
                                onAlways = null,
                                onDeny = {},
                                onDetails = {},
                            )
                        }
                    }
                }
            }
            waitForIdle()
            val face = onNodeWithTag("kit-request-face", useUnmergedTree = true).getBoundsInRoot()
            val row = onNodeWithTag("permission-card-details-row", useUnmergedTree = true).getBoundsInRoot()
            val height = (row.bottom - row.top).value
            assertTrue(height >= 47.5f, "Details row must be at least 48 dp tall, was $height dp")
            val bottomGap = (face.bottom - row.bottom).value
            assertTrue(bottomGap >= 15.5f, "Details row must not be trimmed into S4 padding, gap=$bottomGap dp")
            val detailsLayout = mutableListOf<TextLayoutResult>()
            onNodeWithText("Details", useUnmergedTree = true)
                .fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult]
                .action!!
                .invoke(detailsLayout)
            assertTrue(
                detailsLayout.first().layoutInput.style.textDecoration == TextDecoration.Underline,
                "Details link must be underlined",
            )
        }

    @Test
    fun `B-568 rail stops outside the node`() {
        val m = RailMeasures(glyph = 24.dp, first = 20.dp, stepLine = 32.dp, scale = 1f)
        val half = railGlyphHalf(m)
        assertTrue(half >= m.glyph / 2 + 2.dp, "railGlyphHalf must be >= glyph/2 + 2 dp, was $half")
        assertTrue(
            KitStepLightLap.FILL_STROKE <= 1.5f,
            "running outline stroke must be <= 1.5 dp, was ${KitStepLightLap.FILL_STROKE}",
        )
    }

    private fun stepView(
        id: String,
        kind: KitToolKind,
        title: String,
        status: KitToolStatus,
        duration: String?,
        statusLabel: String,
    ) = KitToolView(id, kind, title, null, null, status, duration, null, null, statusLabel)

    @Test
    fun `B-568 durations share one right edge`() =
        kitUiTest(260, 480) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ChatTestTheme(dark = true) {
                        Box(Modifier.width(260.dp).background(LocalKitColors.current.ground)) {
                            KitStepList {
                                val s1 = stepView("s1", KitToolKind.Shell, "Ran 3", KitToolStatus.Done, "26.7s", "Done")
                                KitToolRow(s1, opened = false, onToggle = {}, group = true)
                                KitToolRow(
                                    stepView("s2", KitToolKind.Read, "Read app.kt", KitToolStatus.Done, "0.2s", "Done"),
                                    opened = false,
                                    onToggle = {},
                                )
                                KitToolRow(
                                    stepView(
                                        "s3",
                                        KitToolKind.Shell,
                                        "Run integration tests for checkout",
                                        KitToolStatus.WaitingForYou,
                                        null,
                                        "Waiting for you",
                                    ),
                                    opened = false,
                                    onToggle = {},
                                )
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val ends = onAllNodesWithTag("kit-tool-end", useUnmergedTree = true)
            val bounds = ends.fetchSemanticsNodes().indices.map { ends[it].getUnclippedBoundsInRoot() }
            assertTrue(bounds.size == 3, "expected 3 kit-tool-end nodes, found ${bounds.size}")
            val ref = bounds.first().right.value
            for (b in bounds) {
                assertTrue(abs(b.right.value - ref) <= 1f, "right edges must align within 1 dp: $bounds")
            }
            val statusLayout = mutableListOf<TextLayoutResult>()
            onNodeWithText("Waiting for you", useUnmergedTree = true)
                .fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult]
                .action!!
                .invoke(statusLayout)
            val wrapped = statusLayout.first()
            assertTrue(wrapped.lineCount >= 2, "expected wrapped status, got ${wrapped.lineCount}")
            val r0 = wrapped.getLineRight(0)
            val rLast = wrapped.getLineRight(wrapped.lineCount - 1)
            assertTrue(abs(rLast - r0) <= 2f, "wrapped status lines must right-align: line0=$r0, last=$rLast")
        }

    @Test
    fun `B-584 bubble has no lone last word`() =
        kitUiTest(360, 320) {
            val prompt = "Look into why the player closes on the TV"
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ChatTestTheme(dark = true) {
                        Column(Modifier.width(360.dp).background(LocalKitColors.current.ground)) {
                            KitPromptBubble(prompt)
                        }
                    }
                }
            }
            waitForIdle()
            val results = mutableListOf<TextLayoutResult>()
            onNodeWithText(prompt, useUnmergedTree = true)
                .fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult]
                .action!!
                .invoke(results)
            val layout = results.first()
            assertTrue(layout.lineCount >= 2, "expected wrapped prompt, got ${layout.lineCount} lines")
            val widths = (0 until layout.lineCount).map { layout.getLineRight(it) - layout.getLineLeft(it) }
            val widest = widths.max()
            val last = widths.last()
            assertTrue(last >= 0.4f * widest, "last line width $last must be >= 0.4 * widest $widest ($widths)")
        }

    @Test
    fun `B-588 RTL copy mark pins to the far edge`() =
        kitUiTest(360, 320) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ChatTestTheme(dark = true, localeTag = "ar") {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Box(Modifier.width(360.dp).background(LocalKitColors.current.ground)) {
                                Box(Modifier.fillMaxWidth().testTag("targets-host")) {
                                    KitRequestTargets(
                                        targets = listOf(KitRequestTarget("npm test")),
                                        copyLabel = "Copy",
                                        copiedLabel = "Copied",
                                    )
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val box = onNodeWithTag("targets-host", useUnmergedTree = true).getBoundsInRoot()
            val copy = onNodeWithContentDescription("Copy", useUnmergedTree = true).getBoundsInRoot()
            val text = onNodeWithText("npm test", useUnmergedTree = true).getBoundsInRoot()
            assertTrue(
                copy.right >= box.right - 16.dp,
                "RTL copy right ${copy.right} must be >= box right ${box.right} - 16 dp",
            )
            val copyCentre = (copy.left + copy.right) / 2f
            val textCentre = (text.left + text.right) / 2f
            assertTrue(copyCentre > textCentre, "RTL copy centre $copyCentre must be > text centre $textCentre")
        }

    private fun ancestors(node: SemanticsNode): List<SemanticsNode> {
        val out = ArrayList<SemanticsNode>()
        var cur = node.parent
        while (cur != null) {
            out += cur
            cur = cur.parent
        }
        return out
    }
}
