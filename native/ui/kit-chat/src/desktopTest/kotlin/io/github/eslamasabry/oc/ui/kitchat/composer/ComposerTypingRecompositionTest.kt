package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.chip.KitComposerModelChip
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val runs = HashMap<String, Int>()

/** Counts how often this part's body runs: a recomposition of the part, not of something inside it. */
@Composable
private fun Counted(name: String, content: @Composable () -> Unit = {}) {
    runs[name] = (runs[name] ?: 0) + 1
    content()
}

/**
 * Typing in the composer (smoothness README, cause 6): every key used to recompose the whole composer, 4-10 ms of UI
 * thread per key. A key press must recompose the text field only; the chips, the attach row, the note above, the
 * round control, the face and the running glow wait for what they really depend on.
 */
@OptIn(ExperimentalTestApi::class)
class ComposerTypingRecompositionTest {
    @Test
    fun `typing 20 characters recomposes the field and nothing else of the composer`() {
        // One pass of each first, so the JVM is warm when the counted passes (whose times are printed) run.
        typeTwentyKeys(lateRead = false)
        typeTwentyKeys(lateRead = true)
        val late = typeTwentyKeys(lateRead = true)
        val early = typeTwentyKeys(lateRead = false)
        println("COMPOSER-TYPING-COMPARE late=$late early(old shape)=$early")
        assertEquals(0, late["parent"] ?: 0, "the composer's parent recomposed while typing: $late")
        assertTrue((early["parent"] ?: 0) >= 20, "control: reading the words in the parent recomposes it per key: $early")
    }

    private fun typeTwentyKeys(lateRead: Boolean): Map<String, Int> {
        var result: Map<String, Int> = emptyMap()
        runComposeUiTest {
        val state = TextFieldState()
        runs.clear()
        setContent {
            KitTheme(
                appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp)) {
                    Column {
                        runs["parent"] = (runs["parent"] ?: 0) + 1
                        val early = if (lateRead) "" else state.text.toString()
                        KitComposerStatusStrip(
                            hasChips = true,
                            model = { Counted("model chip") { KitComposerModelChip("Sonnet", onClick = {}) } },
                        ) {
                            Counted("approval chip")
                        }
                        KitComposerTextBox(
                            text = if (lateRead) ({ state.text.toString() }) else ({ early }),
                            running = true,
                            above = { Counted("note above") },
                            field = {
                                Counted("field slot") {
                                    KitField(
                                        state = state,
                                        label = "Message",
                                        kind = KitFieldKind.Multiline,
                                        placeholder = "Ask OpenCode",
                                        labelShown = false,
                                        framed = false,
                                        composer = true,
                                        multilineLines = 1..5,
                                    )
                                }
                            },
                            leading = { Counted("attach row") { Box(Modifier.size(48.dp)) } },
                            trailing = { Counted("round control") { Box(Modifier.size(48.dp)) } },
                            hasLeading = true,
                        )
                    }
                }
            }
        }
        waitForIdle()
        val before = HashMap(runs)
        val perKey = ArrayList<Long>()
        repeat(20) {
            val t0 = System.nanoTime()
            onNode(hasSetTextAction()).performTextInput("a")
            waitForIdle()
            perKey += System.nanoTime() - t0
        }
        val after = HashMap(runs)
        val delta = after.keys.associateWith { (after[it] ?: 0) - (before[it] ?: 0) }.toSortedMap()
        val sorted = perKey.sorted()
        result = delta
        println("COMPOSER-TYPING lateRead=$lateRead recompositions over 20 keys: $delta; per key ms median ${sorted[10] / 1e6} p90 ${sorted[18] / 1e6}")
        assertEquals(20, state.text.length)
        for (part in listOf("model chip", "approval chip", "note above", "attach row", "round control")) {
            assertEquals(0, delta[part] ?: 0, "$part recomposed while typing: $delta")
        }
        // Rows that stay one line: the field keeps its slot as well; only a flip to two rows may rebuild it.
        assertTrue((delta["field slot"] ?: 0) <= 1, "the field's slot rebuilt per key: $delta")
        }
        return result
    }
}
