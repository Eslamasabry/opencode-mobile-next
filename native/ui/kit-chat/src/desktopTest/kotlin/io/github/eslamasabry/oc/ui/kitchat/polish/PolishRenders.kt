@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.polish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffPreview
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestChange
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestLook
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestTarget
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestTargets
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepList
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitPromptBubble
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

class PolishRenders {
    companion object {
        const val PHASE = "after"
    }

    private fun render(
        name: String,
        locale: String = "en",
        height: Int = 480,
        content: @Composable () -> Unit,
    ) {
        val dir = File("build/polish-render").also { it.mkdirs() }
        val file = File(dir, name)
        kitUiTest(360, height) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ChatTestTheme(dark = true, localeTag = locale, fontScale = 1f) {
                        val dirLayout = if (locale == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
                        CompositionLocalProvider(
                            LocalLayoutDirection provides dirLayout,
                            LocalKitWindow provides KitWindowInfo(360f, 780f),
                        ) {
                            Box(
                                Modifier.width(360.dp)
                                    .background(LocalKitColors.current.ground)
                                    .padding(16.dp),
                            ) {
                                content()
                            }
                        }
                    }
                }
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
        }
    }

    @Test
    fun `render 551 inplace card`() {
        val diff = KitDiffPreview.fromText("val a = 1\nval b = 2")
        render("551-$PHASE-inplace-card-360-1.0-dark.png", height = 780) {
            KitPermissionCard(
                title = "Edit src/app.kt?",
                summary = "The agent waits until you answer.",
                subject = null,
                targets = listOf(KitRequestTarget("src/app.kt", "+2 −0")),
                allowOnceLabel = "Allow once",
                alwaysLabel = null,
                denyLabel = "Deny",
                onAllowOnce = {},
                onAlways = null,
                onDeny = {},
                onDetails = {},
                look = KitRequestLook.InPlace,
                change = KitRequestChange(diff, "Change to src/app.kt", null, {}),
            )
        }
    }

    @Test
    fun `render 568 step rail`() {
        render("568-$PHASE-step-rail-360-1.0-dark.png") {
            KitStepList {
                KitToolRow(
                    KitToolView(
                        "s1", KitToolKind.Shell, "Ran 3 commands", null, null,
                        KitToolStatus.Done, "26.7s", null, null, "Done",
                    ),
                    opened = false,
                    onToggle = {},
                    group = true,
                )
                KitToolRow(
                    KitToolView(
                        "s2", KitToolKind.Read, "Read", "app.kt", null,
                        KitToolStatus.Running, "1.2s", null, null, "Running",
                    ),
                    opened = false,
                    onToggle = {},
                )
                KitToolRow(
                    KitToolView(
                        "s3", KitToolKind.Edit, "Edit", "app.kt", null,
                        KitToolStatus.WaitingForYou, null, null, null, "Waiting for you",
                    ),
                    opened = false,
                    onToggle = {},
                )
                KitToolRow(
                    KitToolView(
                        "s4", KitToolKind.Search, "Search", "TODO", null,
                        KitToolStatus.Failed, null, null, null, "Didn't finish",
                    ),
                    opened = false,
                    onToggle = {},
                )
            }
        }
    }

    @Test
    fun `render 584 prompt bubble`() {
        render("584-$PHASE-prompt-bubble-360-1.0-dark.png", height = 220) {
            Column(Modifier.width(328.dp)) {
                KitPromptBubble("Look into why the player closes on the TV")
            }
        }
    }

    @Test
    fun `render 588 rtl targets`() {
        render("588-$PHASE-rtl-targets-360-1.0-dark.png", locale = "ar", height = 220) {
            KitRequestTargets(
                targets = listOf(KitRequestTarget("npm test")),
                copyLabel = "Copy",
                copiedLabel = "Copied",
            )
        }
    }
}
