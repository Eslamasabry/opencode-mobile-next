package io.github.eslamasabry.oc.ui.kit.agent

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test

/**
 * B-KIT-410: an agent without its own picture is drawn with a one-letter mark beside its name. The mark is decoration:
 * the letter must stay visible while a screen reader skips it, so no node may speak it.
 */
@OptIn(ExperimentalTestApi::class)
class KitAgentMonogramSilentTest {
    @Test
    fun `B-KIT-410 an agent's letter mark is drawn but not spoken`() = runComposeUiTest {
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                stillFrame = true,
            ) {
                AgentMark(slug = "no-such-agent-art", tint = LocalKitColors.current.text1, box = 48.dp, monogram = "F")
            }
        }
        waitUntil { onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("F").assertCountEquals(0)
    }
}
