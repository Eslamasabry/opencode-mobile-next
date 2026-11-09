package io.github.eslamasabry.oc.feature.chatcomposer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ApprovalMode
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerChip
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerUi
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.EffortWord
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldButton
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ModelLabel
import io.github.eslamasabry.oc.feature.chatcomposer.ui.ComposerScreen
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The real composer screen, typed into key by key (smoothness README, cause 6). The screen state does not change while
 * the words grow, so the round control's slot must not run again: a key press recomposes the field only.
 */
@OptIn(ExperimentalTestApi::class)
class ComposerTypingTest {
    @Test
    fun `typing 20 characters does not rebuild the composer's controls`() = runComposeUiTest {
        var micRuns = 0
        val field = TextFieldState()
        val ui =
            ComposerUi(
                button = FieldButton.Mic,
                chips =
                    persistentListOf(
                        ComposerChip.Model(ModelLabel.Named("Sonnet"), isNew = false, effort = EffortWord.Known.High),
                        ComposerChip.Approval(ApprovalMode.AsksFirst),
                    ),
                glow = true,
            )
        val actions = RecordingActions()
        setContent {
            ComposerThemed {
                ComposerScreen(
                    ui,
                    actions,
                    field,
                    mic = {
                        micRuns++
                        Box(Modifier.size(48.dp))
                    },
                )
            }
        }
        waitForIdle()
        val before = micRuns
        val perKey = ArrayList<Long>()
        repeat(20) {
            val t0 = System.nanoTime()
            onNode(hasSetTextAction()).performTextInput("a")
            waitForIdle()
            perKey += System.nanoTime() - t0
        }
        val sorted = perKey.sorted()
        println("COMPOSER-SCREEN-TYPING mic slot ran ${micRuns - before} times over 20 keys; per key ms median ${sorted[10] / 1e6} p90 ${sorted[18] / 1e6}")
        assertEquals(20, field.text.length)
        assertEquals(0, micRuns - before, "the round control's slot ran again while typing")
        assertEquals("a".repeat(20), actions.calls.last { it.startsWith("text:") }.removePrefix("text:"))
    }
}
