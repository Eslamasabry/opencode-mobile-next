package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChipUi
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class UnharnessedApprovalScreenTest {
    @Test
    fun `B-AGENT-UH-033 the composer says never asks with no click action`() =
        runDesktopComposeUiTest(412, 915) {
            val scene = ChatScene()
            val state = scene.live()
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    appearance = KitAppearance(motion = MotionLevel.Off),
                ) {
                    scene.Screen(state, approval = ApprovalChipUi(neverAsks = true))
                }
            }
            onNodeWithText("Never asks").assertExists().assertHasNoClickAction()
            onNodeWithText("Ask first").assertDoesNotExist()
            val file =
                File("build/reports/unharnessed/composer-never-asks.png").apply {
                    parentFile.mkdirs()
                }
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
        }
}
