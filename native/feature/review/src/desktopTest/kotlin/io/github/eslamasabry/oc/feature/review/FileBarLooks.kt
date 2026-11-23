package io.github.eslamasabry.oc.feature.review

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.review.presentation.FileDiffActions
import io.github.eslamasabry.oc.feature.review.presentation.FileDiffUi
import io.github.eslamasabry.oc.feature.review.presentation.ReviewLoad
import io.github.eslamasabry.oc.feature.review.ui.FileDiffScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * B-KIT-029 on the real review FILE page (`FileDiffScreen` with all four text actions present): PNG pictures of its
 * bar, dark, in `build/looks` for a person to look at — 360 dp wide at text 1.0 and 1.3, 600 dp and 840 dp wide,
 * each once with the overflow closed and once open. The layout is asserted too: on the phone widths the title keeps
 * at least 90 dp, at 600 and 840 dp it keeps its whole single line, and no action shown — beside the title or in the
 * open overflow — is cut, while every action is drawn somewhere.
 */
@OptIn(ExperimentalTestApi::class)
class FileBarLooks {
    private val out = File(System.getenv("FILE_BAR_LOOKS_DIR") ?: "build/looks").apply { mkdirs() }

    private val labels =
        listOf("Copy patch", "Copy updated file", "Show surrounding lines", "Add UploadViewModel.kt to the prompt")

    private val diff =
        FileDiff(
            "src/UploadViewModel.kt",
            before = "val x = 1\n",
            after = "val x = 10\n",
            patch = "@@ -1 +1 @@\n-val x = 1\n+val x = 10\n",
            additions = 1,
            deletions = 1,
        )

    private fun page() =
        FileDiffUi("src/UploadViewModel.kt", load = ReviewLoad.Ready, diff = diff, canAddFile = true)

    private class Idle : FileDiffActions {
        override fun back() = Unit

        override fun retry() = Unit
    }

    @Test
    fun `B-KIT-029 the file page's bar keeps title room and whole actions at 360 dp text 1_0`() =
        look(945, 1f, "file-bar-360-text1", phone = true)

    @Test
    fun `B-KIT-029 the file page's bar keeps title room and whole actions at 360 dp text 1_3`() =
        look(945, 1.3f, "file-bar-360-text13", phone = true)

    @Test
    fun `B-KIT-029 the file page's bar keeps its whole title and whole actions at 600 dp`() =
        look(1575, 1f, "file-bar-600", phone = false)

    @Test
    fun `B-KIT-029 the file page's bar keeps its whole title and whole actions at 840 dp`() =
        look(2205, 1f, "file-bar-840", phone = false)

    private fun look(widthPx: Int, fontScale: Float, name: String, phone: Boolean) {
        Locale.setDefault(Locale.ENGLISH)
        runDesktopComposeUiTest(widthPx, 2100) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.625f, fontScale)) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                    ) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            FileDiffScreen(page(), Idle())
                        }
                    }
                }
            }
            waitForIdle()
            if (phone) titleKeepsItsRoom() else titleKeepsItsLine()
            assertTrue(
                onAllNodesWithTag("kit-top-bar-more").fetchSemanticsNodes().isNotEmpty(),
                "some of the four actions do not fit beside the title at this width, so the bar owns an overflow",
            )
            labels.forEach { label -> ifShown(label) { layout -> wholeBesideTheTitle(label, layout) } }
            shot(name)
            onNodeWithTag("kit-top-bar-more").performClick()
            waitForIdle()
            labels.forEach { label ->
                assertTrue(
                    onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
                    "$label is drawn somewhere: beside the title or in the bar's overflow",
                )
                wholeWhereShown(label, textLayoutOf(label))
            }
            shot("$name-open")
        }
    }

    private fun ComposeUiTest.titleKeepsItsRoom() {
        val width = onNodeWithTag("kit-top-bar-title", useUnmergedTree = true).fetchSemanticsNode().size.width
        val dp = width / density.density
        assertTrue(dp >= 90f, "the title keeps at least its minimum width: $dp dp")
    }

    private fun ComposeUiTest.titleKeepsItsLine() {
        val title = tagLayout("kit-top-bar-title")
        val whole = !title.hasVisualOverflow && title.lineCount == 1
        assertTrue(whole, "the title keeps its whole single line at this width")
    }

    private fun ComposeUiTest.ifShown(label: String, check: (TextLayoutResult) -> Unit) {
        if (onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            check(textLayoutOf(label))
        }
    }

    private fun ComposeUiTest.tagLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.first()
    }

    private fun ComposeUiTest.textLayoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.first()
    }

    private fun wholeBesideTheTitle(label: String, layout: TextLayoutResult) {
        assertTrue(!layout.hasVisualOverflow && layout.lineCount == 1, "$label fits whole beside the title")
    }

    private fun wholeWhereShown(label: String, layout: TextLayoutResult) {
        assertTrue(!layout.hasVisualOverflow, "$label is whole where the bar shows it")
    }

    private fun ComposeUiTest.shot(name: String) {
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
    }
}
