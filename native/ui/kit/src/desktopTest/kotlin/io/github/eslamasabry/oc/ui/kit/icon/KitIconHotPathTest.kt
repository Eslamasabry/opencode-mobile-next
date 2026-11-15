package io.github.eslamasabry.oc.ui.kit.icon

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private class CountingSignalsPlatform : KitPlatform by FakeKitPlatform() {
    val backing = MutableStateFlow(KitSystemSignals())
    var collectCalls = 0
        private set

    override val signals: StateFlow<KitSystemSignals> =
        object : StateFlow<KitSystemSignals> by backing {
            override suspend fun collect(collector: FlowCollector<KitSystemSignals>): Nothing {
                collectCalls++
                backing.collect(collector)
            }
        }
}

@OptIn(ExperimentalTestApi::class)
class KitIconHotPathTest {
    @Test
    fun `B-KIT-ICON-HOT icons share theme font families and subscribe to signals only through KitTheme`() =
        runComposeUiTest {
            val platform = CountingSignalsPlatform()
            var tone by mutableStateOf(TextTone.Primary)
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), platform, "en", stillFrame = true) {
                    Column {
                        KitIcon(KitIcons.star, Modifier.testTag("icon-0"), tone = tone)
                        KitIcon(KitIcons.check, Modifier.testTag("icon-1"))
                        KitIcon(KitIcons.add, Modifier.testTag("icon-2"))
                        KitIcon(KitIcons.close, Modifier.testTag("icon-3"))
                        KitIcon(KitIcons.workspaceSelected, Modifier.testTag("icon-4"))
                    }
                }
            }
            waitForIdle()

            assertEquals(1, platform.collectCalls, "only KitTheme should collect platform.signals, not each KitIcon")

            fun fontFamilyOf(tag: String): FontFamily? {
                val glyphNode = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().children.last()
                val results = mutableListOf<TextLayoutResult>()
                glyphNode.config[SemanticsActions.GetTextLayoutResult].action!!(results)
                return results.single().layoutInput.style.fontFamily
            }

            val family0 = fontFamilyOf("icon-0")
            val family1 = fontFamilyOf("icon-1")
            assertSame(family0, family1, "separate icons must share the same FontFamily instance")

            tone = TextTone.Accent
            waitForIdle()
            val family0After = fontFamilyOf("icon-0")
            assertSame(family0, family0After, "recomposing an icon must reuse the same FontFamily instance")

            assertEquals(2, onNodeWithTag("icon-4", useUnmergedTree = true).fetchSemanticsNode().children.size)
            platform.backing.value = KitSystemSignals(highContrast = true)
            waitForIdle()
            assertEquals(1, onNodeWithTag("icon-4", useUnmergedTree = true).fetchSemanticsNode().children.size)
            assertEquals(1, platform.collectCalls, "signal updates must not create additional collectors")
        }
}
