package io.github.eslamasabry.oc.ui.kit.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitPdfPngPage
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerImageBody
import kotlin.test.Test

/** P12-FILES byte parts: an undecodable picture falls back, a PDF page waits, fails and says so. */
@OptIn(ExperimentalTestApi::class)
class KitViewerBytesDesktopTest {
    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        KitTheme(
            appearance = KitAppearance(motion = MotionLevel.Off),
            platform = ContentFakePlatform(),
            localeTag = "en",
            stillFrame = true,
        ) {
            Box(Modifier.size(400.dp, 700.dp)) { content() }
        }
    }

    @Test
    fun `B-MEDIA-001 picture bytes that do not decode show the host's fallback, never an empty frame`() =
        runComposeUiTest {
            setContent { Themed { KitViewerImageBody(byteArrayOf(1, 2, 3), "logo.png") { KitText("not a picture") } } }
            onNodeWithText("not a picture").assertExists()
        }

    @Test
    fun `B-MEDIA-001 a PDF page captions its number while it renders and when it fails`() = runComposeUiTest {
        setContent { Themed { KitPdfPngPage(2, 3, png = null, failed = true, failedReason = "Couldn't show it") } }
        onNodeWithText("Page 2 of 3").assertExists()
        onNodeWithText("Couldn't show it", substring = true).assertExists()
    }
}
