package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.E20Goldens.compareGolden
import io.github.eslamasabry.oc.feature.chat.E20Goldens.picture
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E20-S13 (owner's phone, 11-07): a scrolled user bubble showed through between the chat's title and its
 * "new · This phone" line, and the header was three rows (Back / title / line) with big gaps. Now it is one opaque
 * block of the page's ground: Back on the title's row, the line right under the title.
 */
@OptIn(ExperimentalTestApi::class)
class E20S13ChatHeaderTest {
    @Composable
    private fun Header() {
        KitTopBar(
            "Fix the login bug",
            scope = { KitTopBarScope("new", "This phone", "Files of new", onProject = {}) },
            exit = KitTopBarExit.Back,
            exitLabel = "Back",
            onExit = {},
            onTitleTap = {},
            titleTapLabel = "Rename",
            backBesideTitle = true,
        )
    }

    /** The chat page: the header over a transcript scrolled so a bright "bubble" runs under it. */
    @Composable
    private fun Page(bubble: Color, words: Boolean) {
        val frost = rememberKitFrostState()
        KitScreen(page = true, overlayChrome = true, frost = frost, topBar = { Header() },
            edges = KitScreenEdges.Bleed,
        ) {
            Box(Modifier.fillMaxSize().kitFrostSource(frost)) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState(160)),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(12) { i ->
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(72.dp).background(bubble)) {
                            if (words) KitText("Message ${i + 1}", Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `E20-S13 Back sits on the title's row and the line right under the title`() {
        for (scale in listOf(1f, 1.3f)) runDesktopComposeUiTest(360, 800) {
            setContent { E20Scene(scale) { Header() } }
            waitForIdle()
            val back = boundsOfTag("kit-top-bar-exit")
            val title = boundsOfTag("kit-top-bar-title")
            val line = boundsOfText("new · This phone")
            assertTrue(back.top < title.bottom && title.top < back.bottom, "${scale}x: Back $back beside the title $title")
            assertTrue(back.right <= title.left + 0.5f, "${scale}x: Back leads the title")
            val gap = line.top - title.bottom
            assertTrue(gap in 0f..4f, "${scale}x: the line starts the kit's tight step under the title, was $gap")
            // The three-row bar took a 48 dp Back row on top of the title and the line. Flutter's chat bar
            // (kit_top_bar.dart, chat_top_bar.dart) is one row: 4 dp, the title's first line centred on the 48 dp
            // controls, the scope line's 48 dp target, 4 dp (91 dp at 1x in chat_one_bar_two_servers). The tucked
            // native line may be shorter, never taller; the old three-row bar was taller still.
            val bar = boundsOfTag("kit-top-bar")
            val flutterBar = 4f + 24f + title.height / 2 + 48f + 4f
            assertTrue(bar.height <= flutterBar + 0.5f, "${scale}x: one compact block, was ${bar.height}, Flutter's $flutterBar")
        }
    }

    @Test
    fun `E20-S13 nothing in the transcript shows through any part of the header, Glass included`() {
        val red = Color(0xFFFF0000)
        for (style in listOf(Style.Solid, Style.Glass)) runDesktopComposeUiTest(360, 800) {
            setContent { E20Scene(style = style) { Page(red, words = false) } }
            waitForIdle()
            val header = boundsOfTag("kit-top-bar")
            val image = picture()
            var seen = 0
            for (y in header.top.toInt() until header.bottom.toInt()) for (x in 0 until image.width) {
                val p = image.getRGB(x, y)
                val r = p shr 16 and 0xFF
                val g = p shr 8 and 0xFF
                val b = p and 0xFF
                if (r > 150 && r - g > 60 && r - b > 60) seen++
            }
            assertEquals(0, seen, "$style: transcript pixels seen through the header ($header)")
        }
    }

    @Test
    fun `E20-S13 the header over a scrolled list, as a golden`() {
        for (dark in listOf(false, true)) runDesktopComposeUiTest(360, 640) {
            setContent { E20Scene(dark = dark) { Page(Color(0xFF8AB4F8), words = true) } }
            waitForIdle()
            compareGolden("e20-s13-header-scrolled-${if (dark) "dark" else "light"}")
        }
    }
}
