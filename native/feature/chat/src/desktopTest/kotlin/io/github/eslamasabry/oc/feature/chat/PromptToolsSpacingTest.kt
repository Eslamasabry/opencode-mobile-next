package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chat.composer.ComposerToolsChoices
import io.github.eslamasabry.oc.feature.chat.composer.ComposerToolsSheet
import io.github.eslamasabry.oc.feature.chat.composer.TrayItem
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KIT_CONTAINER_GAP_DP
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** The owner's merge41 complaint: the two groups of Prompt tools touched. They keep the kit's gap now. */
@OptIn(ExperimentalTestApi::class)
class PromptToolsSpacingTest {
    @Test
    fun `Prompt tools groups are one kit gap apart`() = runComposeUiTest {
        val choices =
            ComposerToolsChoices(
                state = BasicComposerUi(available = true, sending = false, steers = false, draft = DraftFacts(), targetKey = "p/s", filesAvailable = true, attachKinds = setOf(AttachKind.Photos), attachmentsSupported = true, imagesOnly = false),
                inTray = listOf(TrayItem.Photos),
                agentName = "OpenCode", attachCount = 0, blockedReason = null,
                attach = {}, photos = null, camera = null, commands = {}, promptEditor = {},
                reuse = null, saved = null, sendLater = null, clearText = null, conversation = null,
                modelWords = "Server default", model = {}, settings = null, projectFile = {},
            )
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                KitSheetHost { ComposerToolsSheet(true, choices) {} }
            }
        }
        waitForIdle()
        // The settings group carries its caller's tag in place of the kit's, so count both.
        val nodes =
            (onAllNodesWithTag("kit-row-group").fetchSemanticsNodes() + onAllNodesWithTag("composer-tools-settings").fetchSemanticsNodes())
                .sortedBy { it.boundsInRoot.top }
        System.getenv("OC_RENDER_DIR")?.let { dir ->
            File(dir).mkdirs()
            val png = Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!
            File(dir, System.getenv("OC_RENDER_NAME") ?: "prompt-tools.png").writeBytes(png.bytes)
        }
        assertEquals(2, nodes.size)
        assertEquals(KIT_CONTAINER_GAP_DP, (nodes[1].boundsInRoot.top - nodes[0].boundsInRoot.bottom).toInt())
    }
}
