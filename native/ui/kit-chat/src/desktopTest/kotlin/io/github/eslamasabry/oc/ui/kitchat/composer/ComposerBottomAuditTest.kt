package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Audit 2026-11-14, the bottom of the conversation: Stop on Send's inset; the tray names its buttons in every style. */
@OptIn(ExperimentalTestApi::class)
class ComposerBottomAuditTest {
    @Test
    fun `B-CHAT-BOTTOM-01 Stop at the end of the words' row ends where Send's disc does`() = runComposeUiTest {
        setContent {
            MotionTheme(level = MotionLevel.Off) {
                Box(Modifier.width(400.dp)) {
                    KitComposerBox(
                        running = false,
                        stacked = true,
                        field = { KitText("Steer while it works…") },
                        fieldEnd = { KitComposerStop(label = "Stop", targetLabel = "Stop OpenCode", onStop = {}) },
                        trailing = { KitComposerSend(label = "Send", onClick = {}) },
                    )
                }
            }
        }
        val stop = onNodeWithTag(STOP_TAG, useUnmergedTree = true).getBoundsInRoot()
        val disc = onNodeWithTag("kit-composer-send-disc", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(abs((stop.right - disc.right).value) <= 1f, "Stop ends at ${stop.right}, Send's disc at ${disc.right}")
    }

    @Test
    fun `B-CHAT-BOTTOM-02 on the slab of glass the tray shows a word under each button`() = runComposeUiTest {
        setContent {
            KitTheme(KitAppearance(style = Style.Glass, motion = MotionLevel.Off), RecordingPlatform(), "en", true) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor(), LocalKitComposerSlab provides true) {
                    Box(Modifier.width(360.dp)) {
                        KitComposerTray(
                            open = true,
                            actions =
                                listOf(
                                    KitComposerTrayAction("Photos", KitIcons.images, {}),
                                    KitComposerTrayAction("Camera", KitIcons.camera, {}),
                                    KitComposerTrayAction("File", KitIcons.attach, {}),
                                    KitComposerTrayAction("More", KitIcons.more, {}),
                                ),
                        )
                    }
                }
            }
        }
        listOf("Photos", "Camera", "File", "More").forEach { onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
    }
}
