package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-485: composer icon buttons and + tray controls expose spoken labels with no inner glyph semantics. */
@OptIn(ExperimentalTestApi::class)
class ComposerSpokenLabelsTest {
    private fun String.hasPrivateUseGlyph(): Boolean = any { it in '\uE000'..'\uF8FF' }

    private fun activeDescendants(node: SemanticsNode): List<SemanticsNode> =
        if (node.config.isClearingSemantics) emptyList()
        else node.children.flatMap { child -> listOf(child) + activeDescendants(child) }

    private fun assertNoInnerIconSemantics(unmergedRoot: SemanticsNode, controlName: String) {
        for (child in activeDescendants(unmergedRoot)) {
            val cfg = child.config
            val text = cfg.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            assertFalse(
                text.any { it.hasPrivateUseGlyph() },
                "$controlName leaked icon glyph text into unmerged semantics: $text",
            )
            assertTrue(
                cfg.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty(),
                "$controlName exposed duplicate contentDescription on child: $cfg",
            )
            assertFalse(
                SemanticsActions.OnClick in cfg,
                "$controlName exposed nested clickable child: $cfg",
            )
        }
    }

    private fun assertIconOnlyControl(
        merged: SemanticsNode,
        unmerged: SemanticsNode,
        expectedLabel: String,
    ) {
        val cfg = merged.config
        assertTrue(SemanticsActions.OnClick in cfg, "$expectedLabel must be clickable")
        assertEquals(
            listOf(expectedLabel),
            cfg.getOrNull(SemanticsProperties.ContentDescription),
            "$expectedLabel must expose contentDescription on clickable node",
        )
        val mergedText = cfg.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        assertTrue(
            mergedText.isEmpty(),
            "$expectedLabel icon-only button must not merge icon glyph text: $mergedText",
        )
        assertNoInnerIconSemantics(unmerged, expectedLabel)
    }

    @Test
    fun `B-485 plus button exposes spoken label without icon glyph text`() = runComposeUiTest {
        var open by mutableStateOf(false)
        setContent {
            MotionTheme(level = MotionLevel.Off) {
                KitComposerAdd(
                    label = if (open) "Close" else "Attach and more",
                    onClick = { open = !open },
                    modifier = Modifier.testTag("chat-attach-button"),
                    open = open,
                )
            }
        }
        assertIconOnlyControl(
            merged = onNodeWithTag("chat-attach-button").fetchSemanticsNode(),
            unmerged = onNodeWithTag("chat-attach-button", useUnmergedTree = true).fetchSemanticsNode(),
            expectedLabel = "Attach and more",
        )
        onNodeWithTag("kit-composer-add", useUnmergedTree = true).assertExists()
        open = true
        waitForIdle()
        assertIconOnlyControl(
            merged = onNodeWithTag("chat-attach-button").fetchSemanticsNode(),
            unmerged = onNodeWithTag("chat-attach-button", useUnmergedTree = true).fetchSemanticsNode(),
            expectedLabel = "Close",
        )
        onNodeWithTag("kit-composer-add-open", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `B-485 shield chip exposes spoken label without icon glyph text in collapsed and full modes`() =
        runComposeUiTest {
            setContent {
                MotionTheme(level = MotionLevel.Off) {
                    Column {
                        KitComposerChip(
                            label = "Ask first",
                            onClick = {},
                            modifier = Modifier.testTag("shield-collapsed"),
                            icon = KitIcons.shield,
                            opensPicker = false,
                            semanticsLabel = "Ask first",
                            wordless = true,
                        )
                        KitComposerChip(
                            label = "Auto-approve on",
                            onClick = {},
                            modifier = Modifier.testTag("shield-full"),
                            icon = KitIcons.shieldFilled,
                            opensPicker = false,
                            semanticsLabel = "Auto-approve on",
                            active = true,
                            wordless = false,
                        )
                    }
                }
            }
            assertIconOnlyControl(
                merged = onNodeWithTag("shield-collapsed").fetchSemanticsNode(),
                unmerged = onNodeWithTag("shield-collapsed", useUnmergedTree = true).fetchSemanticsNode(),
                expectedLabel = "Ask first",
            )
            val fullMerged = onNodeWithTag("shield-full").fetchSemanticsNode()
            val fullUnmerged = onNodeWithTag("shield-full", useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(SemanticsActions.OnClick in fullMerged.config, "full shield must be clickable")
            assertEquals(
                listOf("Auto-approve on"),
                fullMerged.config.getOrNull(SemanticsProperties.ContentDescription),
            )
            val fullText = fullMerged.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            assertEquals(listOf("Auto-approve on"), fullText, "full shield must not merge PUA glyph")
            assertNoInnerIconSemantics(fullUnmerged, "Auto-approve on")
        }

    @Test
    fun `B-485 open plus tray controls expose spoken labels on tappables without icon glyph text`() =
        runComposeUiTest {
            val trayActions =
                listOf(
                    KitComposerTrayAction("Photos", KitIcons.images, {}, testTag = "tray-photos"),
                    KitComposerTrayAction("Camera", KitIcons.camera, {}, testTag = "tray-camera"),
                    KitComposerTrayAction("File", KitIcons.attach, {}, testTag = "tray-file"),
                    KitComposerTrayAction("More", KitIcons.more, {}, testTag = "tray-more"),
                )
            setContent {
                MotionTheme(level = MotionLevel.Off) {
                    Box(Modifier.width(360.dp)) { KitComposerTray(open = true, actions = trayActions) }
                }
            }
            for ((tag, expected) in listOf(
                "tray-photos" to "Photos",
                "tray-camera" to "Camera",
                "tray-file" to "File",
                "tray-more" to "More",
            )) {
                val merged = onNodeWithTag(tag).fetchSemanticsNode()
                val unmerged = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                assertTrue(SemanticsActions.OnClick in merged.config, "$expected tray button clickable")
                assertEquals(
                    listOf(expected),
                    merged.config.getOrNull(SemanticsProperties.ContentDescription),
                    "$expected tray button must expose spoken contentDescription on tappable node",
                )
                val mergedText = merged.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                assertEquals(
                    listOf(expected),
                    mergedText,
                    "$expected tray button must not merge icon glyph into text",
                )
                assertNoInnerIconSemantics(unmerged, expected)
            }
        }

    @Test
    fun `B-485 Send Stop and Mic icon controls expose spoken labels with no inner icon semantics`() =
        runComposeUiTest {
            setContent {
                MotionTheme(level = MotionLevel.Off) {
                    Column {
                        KitComposerSend(label = "Send", onClick = {})
                        KitComposerStop(
                            label = "Stop",
                            targetLabel = "Stop Claude Code",
                            onStop = {},
                            afterStepButton = false,
                        )
                        KitComposerStopRing(label = "Stop reply", onClick = {})
                        KitComposerMic(label = "Dictate", onClick = {})
                        KitMicButton(
                            label = "Hold to dictate",
                            onTap = {},
                            onHoldStart = {},
                            onHoldEnd = {},
                            onArmedChanged = {},
                            prominent = true,
                        )
                    }
                }
            }
            val clickable =
                onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()
            val expected = setOf("Send", "Stop Claude Code", "Stop reply", "Dictate", "Hold to dictate")
            val labels =
                clickable.map { node ->
                    val desc = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                    assertTrue(desc.isNotEmpty() && desc.all { it.isNotBlank() }, "missing label on $node")
                    val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                    assertFalse(text.any { it.hasPrivateUseGlyph() }, "glyph leaked into $desc: $text")
                    desc.single()
                }.toSet()
            assertEquals(expected, labels)
            val unmergedClickable =
                onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
            for (node in unmergedClickable) {
                val label = node.config.getOrNull(SemanticsProperties.ContentDescription).toString()
                assertNoInnerIconSemantics(node, label)
            }
        }
}
