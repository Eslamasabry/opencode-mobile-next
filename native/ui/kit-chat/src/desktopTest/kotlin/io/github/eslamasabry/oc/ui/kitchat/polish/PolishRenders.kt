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
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kitchat.ChatFakePlatform
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
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
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.FileImageOutputStream
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

    @Test
    fun `render 568 running node clip`() {
        val dir = File("build/polish-render").also { it.mkdirs() }
        val frames = mutableListOf<BufferedImage>()
        kitUiTest(360, 220) {
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Full),
                        ChatFakePlatform(),
                        "en",
                        stillFrame = false,
                    ) {
                        CompositionLocalProvider(
                            LocalKitRedactor provides Redactor(),
                            LocalKitWindow provides KitWindowInfo(360f, 780f),
                        ) {
                            Box(
                                Modifier.width(360.dp)
                                    .background(LocalKitColors.current.ground)
                                    .padding(16.dp),
                            ) {
                                KitStepList {
                                    KitToolRow(
                                        KitToolView(
                                            "c1", KitToolKind.Read, "Read", "app.kt", null,
                                            KitToolStatus.Done, "0.2s", null, null, "Done",
                                        ),
                                        opened = false,
                                        onToggle = {},
                                    )
                                    KitToolRow(
                                        KitToolView(
                                            "c2", KitToolKind.Edit, "Edit", "checkout.kt", null,
                                            KitToolStatus.Running, "1.2s", null, null, "Running",
                                        ),
                                        opened = false,
                                        onToggle = {},
                                    )
                                    KitToolRow(
                                        KitToolView(
                                            "c3", KitToolKind.Shell, "Run", "npm test", null,
                                            KitToolStatus.WaitingForYou, null, null, null, "Waiting for you",
                                        ),
                                        opened = false,
                                        onToggle = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
            repeat(20) { step ->
                mainClock.advanceTimeBy(if (step == 0) 60L else 100L)
                waitForIdle()
                frames += onRoot().captureToImage().toAwtImage()
            }
        }
        ImageIO.write(frames[9], "png", File(dir, "568-$PHASE-running-node-head-360-1.0-dark.png"))
        val strip = stitchVertically(listOf(frames[2], frames[6], frames[9], frames[14]))
        ImageIO.write(strip, "png", File(dir, "568-$PHASE-running-node-strip-360-1.0-dark.png"))
        writeAnimatedGif(frames, File(dir, "568-$PHASE-running-node-360-1.0-dark.gif"), delayCs = 10)
    }

    private fun stitchVertically(images: List<BufferedImage>): BufferedImage {
        val w = images.maxOf { it.width }
        val h = images.sumOf { it.height }
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        var y = 0
        for (img in images) {
            g.drawImage(img, 0, y, null)
            y += img.height
        }
        g.dispose()
        return out
    }

    private fun writeAnimatedGif(frames: List<BufferedImage>, file: File, delayCs: Int) {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        FileImageOutputStream(file).use { stream ->
            writer.output = stream
            val param = writer.defaultWriteParam
            val type = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB)
            val meta = writer.getDefaultImageMetadata(type, param)
            val format = meta.nativeMetadataFormatName
            val root = meta.getAsTree(format) as IIOMetadataNode
            val gce = IIOMetadataNode("GraphicControlExtension").apply {
                setAttribute("disposalMethod", "none")
                setAttribute("userInputFlag", "FALSE")
                setAttribute("transparentColorFlag", "FALSE")
                setAttribute("delayTime", delayCs.toString())
                setAttribute("transparentColorIndex", "0")
            }
            val appExts = IIOMetadataNode("ApplicationExtensions")
            val app = IIOMetadataNode("ApplicationExtension").apply {
                setAttribute("applicationID", "NETSCAPE")
                setAttribute("authenticationCode", "2.0")
                userObject = byteArrayOf(0x1, 0x0, 0x0)
            }
            appExts.appendChild(app)
            root.appendChild(gce)
            root.appendChild(appExts)
            meta.setFromTree(format, root)
            writer.prepareWriteSequence(null)
            for (frame in frames) {
                writer.writeToSequence(IIOImage(frame, null, meta), param)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
    }
}
