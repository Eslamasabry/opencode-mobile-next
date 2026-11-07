package io.github.eslamasabry.oc.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.review.presentation.ChangeRowUi
import io.github.eslamasabry.oc.feature.review.presentation.ChangesActions
import io.github.eslamasabry.oc.feature.review.presentation.ChangesUi
import io.github.eslamasabry.oc.feature.review.presentation.CommentAnchor
import io.github.eslamasabry.oc.feature.review.presentation.CommentSide
import io.github.eslamasabry.oc.feature.review.presentation.CommentStatus
import io.github.eslamasabry.oc.feature.review.presentation.FileDiffActions
import io.github.eslamasabry.oc.feature.review.presentation.FileDiffUi
import io.github.eslamasabry.oc.feature.review.presentation.GuideSection
import io.github.eslamasabry.oc.feature.review.presentation.GuideUi
import io.github.eslamasabry.oc.feature.review.presentation.LineComment
import io.github.eslamasabry.oc.feature.review.presentation.ReviewGuide
import io.github.eslamasabry.oc.feature.review.presentation.ReviewLoad
import io.github.eslamasabry.oc.feature.review.presentation.UndoAllUi
import io.github.eslamasabry.oc.feature.review.ui.ChangesScreen
import io.github.eslamasabry.oc.feature.review.ui.FileDiffScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.collections.immutable.persistentListOf

/**
 * B-156 / B-155 on the real screens: a commented line as a draft, waiting for the agent and fixed (goldens), "Send N
 * comments", and the Guided view. Goldens at density 1: record with
 * `./gradlew :feature:review:desktopTest -Poc.recordGoldens=true --tests '*ReviewCommentGoldensTest*'`; a missing
 * golden is written and the test fails once, so every new picture is looked at before it counts.
 */
@OptIn(ExperimentalTestApi::class)
class ReviewCommentGoldensTest {
    private var previous: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(previous)

    @Composable
    private fun Scene(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "en", stillFrame = true) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                    Box(Modifier.size(412.dp, 900.dp).background(LocalKitColors.current.ground)) { content() }
                }
            }
        }
    }

    private val diff = FileDiff("src/a.kt", before = "val x = 1\nval y = 2\n", after = "val x = 10\nval y = 2\n", additions = 1, deletions = 1)

    private fun comment(status: CommentStatus) =
        LineComment("c1", CommentAnchor("src/a.kt", CommentSide.New, 1, 1, persistentListOf("val x = 10")), "Call it count", status)

    private fun page(status: CommentStatus) =
        FileDiffUi(
            "src/a.kt",
            load = ReviewLoad.Ready,
            diff = diff,
            canComment = true,
            comments = persistentListOf(comment(status)),
            drafts = if (status == CommentStatus.Draft) 1 else 0,
        )

    private class Calls : FileDiffActions, ChangesActions {
        val calls = mutableListOf<String>()

        override fun back() { calls += "back" }

        override fun retry() { calls += "retry" }

        override fun removeComment(id: String) { calls += "remove $id" }

        override fun sendComments() { calls += "send" }

        override fun openFile(path: String) { calls += "file $path" }

        override fun keepAll() {}

        override fun undoAll() {}

        override fun confirmUndoAll() {}

        override fun dismissConfirm() {}

        override fun openStaged() {}

        override fun sendBack() {}

        override fun sendBackNote(note: String?) {}

        override fun guided(on: Boolean) { calls += "guided $on" }
    }

    @Test
    fun `B-156 a commented line as a draft offers Send 1 comment and Remove`() = runComposeUiTest {
        val calls = Calls()
        setContent { Scene { FileDiffScreen(page(CommentStatus.Draft), calls) } }
        onNodeWithText("Your comments").assertIsDisplayed()
        onNodeWithText("Line 1").assertIsDisplayed()
        onNodeWithText("Call it count").assertIsDisplayed()
        onNodeWithText("Not sent", substring = true).assertIsDisplayed()
        compareGolden("review-comment-draft")
        onNodeWithText("Send 1 comment").performClick()
        assertEquals(listOf("send"), calls.calls)
    }

    @Test
    fun `B-156 a sent comment waits for the agent`() = runComposeUiTest {
        setContent { Scene { FileDiffScreen(page(CommentStatus.Pending), Calls()) } }
        onNodeWithText("Waiting for the agent", substring = true).assertIsDisplayed()
        assertEquals(0, onAllNodes(androidx.compose.ui.test.hasText("Send 1 comment")).fetchSemanticsNodes().size)
        compareGolden("review-comment-pending")
    }

    @Test
    fun `B-156 a comment is fixed after the agent's edit`() = runComposeUiTest {
        setContent { Scene { FileDiffScreen(page(CommentStatus.Resolved), Calls()) } }
        onNodeWithText("Fixed", substring = true).assertIsDisplayed()
        compareGolden("review-comment-resolved")
    }

    @Test
    fun `B-156 with no comments the page says how to comment`() = runComposeUiTest {
        setContent { Scene { FileDiffScreen(page(CommentStatus.Draft).copy(comments = persistentListOf(), drafts = 0), Calls()) } }
        onNodeWithText("Tap a line number, then Comment", substring = true).assertIsDisplayed()
    }

    private val files =
        persistentListOf(
            ChangeRowUi("src/upload.kt", "upload.kt", "src", 3, 1, hasDiff = true),
            ChangeRowUi("test/upload_test.kt", "upload_test.kt", "test", 9, 0, hasDiff = true),
        )

    @Test
    fun `B-155 Guided shows the agent's sections with glue under Also changed`() = runComposeUiTest {
        val calls = Calls()
        val guide =
            ReviewGuide(
                persistentListOf(GuideSection("Retry the upload", "Uploads failed on flaky networks, so they now retry.", persistentListOf("src/upload.kt"))),
                persistentListOf("test/upload_test.kt"),
            )
        setContent {
            Scene {
                ChangesScreen(
                    ChangesUi(turn = false, load = ReviewLoad.Ready, files = files, undoAll = UndoAllUi.Unavailable, canGuide = true, guide = GuideUi.Ready(guide), drafts = 3),
                    calls,
                )
            }
        }
        onNodeWithText("Retry the upload").assertIsDisplayed()
        onNodeWithText("Uploads failed on flaky networks, so they now retry.").assertIsDisplayed()
        onNodeWithText("Also changed").assertIsDisplayed()
        compareGolden("review-guided")
        onNodeWithText("Guided").performClick()
        onNodeWithText("Send 3 comments").performClick()
        assertTrue("guided false" in calls.calls)
        assertTrue("send" in calls.calls)
    }

    private fun ComposeUiTest.compareGolden(name: String) {
        waitForIdle()
        val dir = File(System.getProperty("oc.goldens.dir") ?: "src/desktopTest/goldens")
        val record = System.getProperty("oc.goldens.record") == "true"
        val image = onRoot().captureToImage().toAwtImage()
        val file = File(dir, "$name.png")
        if (record || !file.isFile) {
            file.parentFile.mkdirs()
            ImageIO.write(image, "png", file)
            if (!record) fail("$name: no golden yet; recorded ${file.name}. Look at it, then run again.")
            return
        }
        val golden = ImageIO.read(file)
        assertTrue(golden.width == image.width && golden.height == image.height, "$name: size ${image.width}x${image.height} vs ${golden.width}x${golden.height}")
        var differing = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val p = golden.getRGB(x, y)
            val q = image.getRGB(x, y)
            if (p != q && (0..24 step 8).any { abs((p shr it and 0xFF) - (q shr it and 0xFF)) > 8 }) differing++
        }
        val share = differing.toDouble() / (image.width * image.height)
        if (share > 0.002) {
            val actual = File("build/golden-actual/$name.png").also { it.parentFile.mkdirs() }
            ImageIO.write(image, "png", actual)
            fail("$name: ${"%.3f".format(share * 100)} % of pixels differ; actual: $actual")
        }
    }
}
