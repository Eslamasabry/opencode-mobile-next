package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The model sheet while its list is on the way: one quiet loading row, never the caller's "no models" notice. */
@OptIn(ExperimentalTestApi::class)
class KitModelSheetLoadingTest {
    private val models = (1..3).map { KitModelOption("openai", "gpt-$it", "GPT $it", providerName = "OpenAI") }
    private val notice: @Composable ColumnScope.() -> Unit = { KitText("No models here") }
    private val belowText: @Composable ColumnScope.() -> Unit = { KitText("Thinking choices") }

    @Composable
    private fun Sheet(options: List<KitModelOption>, loading: Boolean, chosen: MutableList<KitModelOption?> = mutableListOf()) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(412, 915) {
                    KitSheetHost {
                        KitModelSheet(
                            visible = true,
                            onDismissRequest = {},
                            options = options,
                            loading = loading,
                            current = null,
                            onChoose = { chosen += it },
                            above = notice,
                            below = belowText,
                        )
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.settle() {
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    @Test
    fun `loading with no models shows Loading models and not the callers no-models notice`() = runComposeUiTest {
        setContent { Sheet(emptyList(), loading = true) }
        settle()
        onNodeWithText("Loading models…").assertExists()
        onNodeWithText("No models here").assertDoesNotExist()
        onNodeWithText("Thinking choices").assertExists()
    }

    @Test
    fun `not loading with no models shows the callers notice and no loading row`() = runComposeUiTest {
        setContent { Sheet(emptyList(), loading = false) }
        settle()
        onNodeWithText("No models here").assertExists()
        onNodeWithText("Loading models…").assertDoesNotExist()
    }

    @Test
    fun `loading over a last known list keeps the rows usable and marks the update`() = runComposeUiTest {
        val chosen = mutableListOf<KitModelOption?>()
        setContent { Sheet(models, loading = true, chosen) }
        settle()
        onNodeWithTag("chat-model-updating").assertExists()
        onNodeWithText("Loading models…").assertDoesNotExist()
        onNode(hasTestTag("chat-model-choice") and hasText("GPT 2")).performClick()
        assertEquals(listOf<KitModelOption?>(models[1]), chosen)
    }

    @Test
    fun `not loading shows no update mark`() = runComposeUiTest {
        setContent { Sheet(models, loading = false) }
        settle()
        assertTrue(onAllNodesWithTag("chat-model-updating").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `the first row sits at the same height with loading on and off`() = runComposeUiTest {
        var loading by mutableStateOf(false)
        setContent { Sheet(models, loading) }
        settle()
        fun top() = onNode(hasTestTag("chat-model-choice") and hasText("GPT 1")).fetchSemanticsNode().positionInRoot.y
        val off = top()
        loading = true
        settle()
        assertEquals(off, top(), 0.5f)
    }
}
