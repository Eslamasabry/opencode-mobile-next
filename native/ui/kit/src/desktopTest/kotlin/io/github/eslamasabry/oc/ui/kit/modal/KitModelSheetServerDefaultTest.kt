@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test

/** B-KIT-MODEL-301: the "Server default" row names the model the server resolves it to, on that provider's page only. */
class KitModelSheetServerDefaultTest {
    private val zen = listOf(KitModelOption("zen", "glm-5.3", "GLM-5.3", "Zen"), KitModelOption("zen", "glm-5.3-flash", "GLM-5.3-Flash", "Zen"))
    private val openai = KitModelOption("openai", "gpt-5.5", "GPT-5.5", "OpenAI")

    private fun row(serverDefault: KitModelOption?, check: ComposeUiTest.() -> Unit) =
        runComposeUiTest {
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    InWindow(412, 915) {
                        KitSheetHost(Modifier.fillMaxSize()) {
                            KitModelSheet(
                                visible = true,
                                onDismissRequest = {},
                                options = zen,
                                current = null,
                                onChoose = {},
                                serverDefault = serverDefault,
                            )
                        }
                    }
                }
            }
            waitForIdle()
            check()
        }

    @Test
    fun `B-KIT-MODEL-301 the default row names the resolved model when it is on this provider's page`() =
        row(zen[0]) { onNodeWithTag("chat-model-default").assert(hasText("GLM-5.3", substring = true)) }

    @Test
    fun `B-KIT-MODEL-302 the default row is unchanged when the default is another provider's`() =
        row(openai) { onNodeWithTag("chat-model-default").assert(!hasText("GPT-5.5", substring = true)) }

    @Test
    fun `B-KIT-MODEL-303 the default row is unchanged when the server does not say`() =
        row(null) { onNodeWithTag("chat-model-default").assert(!hasText("GLM", substring = true)) }
}
