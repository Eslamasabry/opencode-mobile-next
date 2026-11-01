package io.github.eslamasabry.oc.ui.kit.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.content.image.KitZoomControls
import io.github.eslamasabry.oc.ui.kit.content.image.KitZoomState
import io.github.eslamasabry.oc.ui.kit.content.image.KitZoomTags
import io.github.eslamasabry.oc.ui.kit.content.qr.KIT_QR_TAG
import io.github.eslamasabry.oc.ui.kit.content.qr.KitQr
import io.github.eslamasabry.oc.ui.kit.content.viewer.KIT_PDF_PAGE_TAG
import io.github.eslamasabry.oc.ui.kit.content.viewer.KIT_VIEWER_LINE_TAG
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitPdfPage
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitPdfPageState
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerAction
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerBinaryBody
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerCodeBody
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerFrame
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerTags
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerTextBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Content parts headless on desktop (`runComposeUiTest`, Skia): behaviour and semantics, not looks. */
@OptIn(ExperimentalTestApi::class)
class ContentPartsDesktopTest {
    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = ContentFakePlatform(),
                localeTag = "en",
                stillFrame = true,
                content = content,
            )
        }
    }

    private val link = "https://handoff.example.invalid/s/42?t=abc"

    @Test
    fun `MV-8 the drawn code scans, and only the label reaches semantics`() = runComposeUiTest {
        setContent { Themed { Box(Modifier.size(300.dp)) { KitQr(link, "QR code for this chat") } } }
        val node = onNodeWithTag(KIT_QR_TAG)
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("QR code for this chat")))
        onAllNodesWithText(link, substring = true).assertCountEquals(0)
        val image: ImageBitmap = node.captureToImage()
        val map = image.toPixelMap()
        val pixels = IntArray(image.width * image.height) { i -> argb(map[i % image.width, i / image.width]) }
        assertEquals(link, QrDecode.argb(pixels, image.width, image.height))
        // Square, whole pixels per module: version 3 (29 modules) + quiet zone = 37; 240 dp × 2 = 480 px → 12 px each.
        assertEquals(image.width, image.height)
        assertEquals(0, image.width % 37)
    }

    @Test
    fun `B-KIT-026 an empty payload is refused in debug builds`() {
        assertFailsWith<IllegalArgumentException> {
            runComposeUiTest { setContent { Themed { KitQr("", "code") } } }
        }
    }

    @Test
    fun `B-KIT-026 a payload too long shows words instead of a code`() = runComposeUiTest {
        setContent { Themed { KitQr("a".repeat(3000), "code") } }
        onNodeWithText("This is too long for a QR code. Copy the link instead.").assertExists()
        onNodeWithTag(KIT_QR_TAG).assertDoesNotExist()
    }

    @Test
    fun `B-NAV-028 a root file shows its name once and a nested one its folder`() = runComposeUiTest {
        var closed = 0
        var asked = 0
        setContent {
            Themed {
                KitViewerFrame(
                    "README.md", path = "README.md", onClose = { closed++ },
                    primary = KitViewerAction("Ask about this file", { asked++ }),
                ) { KitViewerTextBody("hello") }
            }
        }
        onNodeWithTag(KitViewerTags.FOLDER).assertDoesNotExist()
        onNodeWithText("Ask about this file").performClick()
        onNodeWithText("Close").performClick()
        assertEquals(1, asked)
        assertEquals(1, closed)
    }

    @Test
    fun `B-NAV-028 the folder line is the parent folder, kept left to right`() = runComposeUiTest {
        setContent { Themed { KitViewerFrame("a.kt", path = "src/main/a.kt") { KitViewerTextBody("x") } } }
        onNodeWithTag(KitViewerTags.FOLDER).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(androidx.compose.ui.text.AnnotatedString("⁦src/main/⁩"))),
        )
        onNodeWithText("Close").assertDoesNotExist() // no onClose: an embedded pane has no Close
    }

    @Test
    fun `MEDIA-VOICE-37 a binary file says it can't be shown, with its type and size in words`() = runComposeUiTest {
        var saved = 0
        setContent {
            Themed {
                KitViewerBinaryBody("application/zip", (2.4 * 1024 * 1024).toLong(), action = KitViewerAction("Save", { saved++ }))
            }
        }
        onNodeWithText("Can't show this file").assertExists()
        onNodeWithText("⁦application/zip⁩ · 2.4 MB").assertExists()
        onNodeWithText("Save").performClick()
        assertEquals(1, saved)
    }

    @Test
    fun `MEDIA-VOICE-37 unknown type and size say so`() = runComposeUiTest {
        setContent { Themed { KitViewerBinaryBody(null, null) } }
        onNodeWithText("⁦Unknown type⁩ · size unknown").assertExists()
    }

    @Test
    fun `MEDIA-VOICE-37 a code file opened at a line marks that line`() = runComposeUiTest {
        val code = (1..400).joinToString("\n") { "val line$it = $it" }
        setContent { Themed { Box(Modifier.size(400.dp, 600.dp)) { KitViewerCodeBody(code, initialLine = 200) } } }
        onNodeWithTag("${KIT_VIEWER_LINE_TAG}200").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        onNodeWithTag("${KIT_VIEWER_LINE_TAG}1").assertDoesNotExist() // lazy: far lines are not laid out
    }

    @Test
    fun `MEDIA-VOICE-37 an empty file says so`() = runComposeUiTest {
        setContent { Themed { KitViewerTextBody("") } }
        onNodeWithText("This file is empty").assertExists()
    }

    @Test
    fun `MEDIA-VOICE-42 zoom has a visible path that states its limits`() = runComposeUiTest {
        val state = KitZoomState()
        setContent { Themed { KitZoomControls(state) } }
        onNodeWithTag(KitZoomTags.OUT).assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Already at full view"))
        onNodeWithTag(KitZoomTags.IN).assertIsEnabled().performClick()
        assertEquals(1.5f, state.scale)
        onNodeWithTag(KitZoomTags.FIT).assertIsEnabled().performClick()
        assertEquals(1f, state.scale)
    }

    @Test
    fun `MEDIA-VOICE-40 a failed page offers Try again and a ready page has its caption`() = runComposeUiTest {
        var retries = 0
        setContent {
            Themed {
                androidx.compose.foundation.layout.Column {
                    KitPdfPage(2, 3, KitPdfPageState.Failed(), onRetry = { retries++ })
                    KitPdfPage(1, 3, KitPdfPageState.Ready(ImageBitmap(10, 14)))
                }
            }
        }
        onNodeWithText("Page 1 of 3").assertExists()
        onNodeWithText("Couldn't show page 2").assertExists()
        onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
        onNodeWithTag("${KIT_PDF_PAGE_TAG}1").assertExists()
    }

    /** Composited over white, as the card sits on a light screen (transparent corners must not read as black). */
    private fun argb(c: androidx.compose.ui.graphics.Color): Int {
        fun ch(v: Float) = ((v * c.alpha + (1f - c.alpha)) * 255).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(c.red) shl 16) or (ch(c.green) shl 8) or ch(c.blue)
    }
}
