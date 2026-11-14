package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.content.ContentFakePlatform
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Audit 2 (terminal): the Send button sat about 10 px lower than the field it belongs to. */
@OptIn(ExperimentalTestApi::class)
class TerminalSendAlignTest {
    @Test
    fun `W2-021 the Send button ends where the field's box ends`() = runComposeUiTest {
        setContent {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = ContentFakePlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp).height(300.dp)) {
                    KitTerminalAccessible(
                        transcript = "",
                        onSend = {},
                        words =
                            KitTerminalWords(
                                terminal = "Terminal",
                                accessibleMode = "Accessible mode",
                                transcript = "Terminal transcript",
                                noOutput = "No terminal output yet.",
                                sendToTerminal = "Send to terminal",
                                send = "Send",
                                inputUnavailable = "Input is unavailable.",
                            ),
                    )
                }
            }
        }
        val field = onNodeWithTag(KitTerminalTags.INPUT).getUnclippedBoundsInRoot()
        val send = onNodeWithTag(KitTerminalTags.SEND).getUnclippedBoundsInRoot()
        assertTrue(
            abs((field.bottom - send.bottom).value) < 0.5f,
            "field ends at ${field.bottom}, Send at ${send.bottom}",
        )
    }
}
