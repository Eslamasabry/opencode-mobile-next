package io.github.eslamasabry.oc.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.kitBreakableFileName
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
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
import kotlinx.collections.immutable.persistentListOf

private const val PHASE = "after"
private const val GUTTER_DP = 16f
private const val EDGE_TOLERANCE = 1f

@OptIn(ExperimentalTestApi::class)
class PolishB563Test {
    private var previous: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(previous)

    @Composable
    private fun Scene(
        scale: Float = 1f,
        dark: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        val mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light
        CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(mode = mode, motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                CompositionLocalProvider(
                    LocalKitWindow provides KitWindowInfo(360f, 800f),
                    LocalKitRedactor provides Redactor(),
                ) {
                    Box(Modifier.size(360.dp, 800.dp).background(LocalKitColors.current.ground)) {
                        content()
                    }
                }
            }
        }
    }

    private val diff =
        FileDiff(
            "src/a.kt",
            before = "val x = 1\nval y = 2\n",
            after = "val x = 10\nval y = 2\n",
            additions = 1,
            deletions = 1,
        )

    private fun comment(status: CommentStatus) =
        LineComment(
            "c1",
            CommentAnchor("src/a.kt", CommentSide.New, 1, 1, persistentListOf("val x = 10")),
            "Call it count",
            status,
        )

    private fun page(status: CommentStatus) =
        FileDiffUi(
            "src/a.kt",
            load = ReviewLoad.Ready,
            diff = diff,
            canComment = true,
            comments = persistentListOf(comment(status)),
            drafts = if (status == CommentStatus.Draft) 1 else 0,
        )

    private val files =
        persistentListOf(
            ChangeRowUi("src/upload.kt", "upload.kt", "src", 3, 1, hasDiff = true),
            ChangeRowUi("test/upload_test.kt", "upload_test.kt", "test", 9, 0, hasDiff = true),
        )

    private val section =
        GuideSection(
            "Retry the upload",
            "Uploads failed on flaky networks, so they now retry.",
            persistentListOf("src/upload.kt"),
        )

    private fun guidedChanges(drafts: Int = 3): ChangesUi =
        ChangesUi(
            turn = false,
            load = ReviewLoad.Ready,
            files = files,
            undoAll = UndoAllUi.Unavailable,
            canGuide = true,
            guide = GuideUi.Ready(ReviewGuide(persistentListOf(section), persistentListOf("test/upload_test.kt"))),
            drafts = drafts,
        )

    private class Calls : FileDiffActions, ChangesActions {
        val log = mutableListOf<String>()

        override fun back() { log += "back" }

        override fun retry() { log += "retry" }

        override fun removeComment(id: String) { log += "remove $id" }

        override fun sendComments() { log += "send" }

        override fun openFile(path: String) { log += "file $path" }

        override fun keepAll() { log += "done" }

        override fun undoAll() = Unit

        override fun confirmUndoAll() = Unit

        override fun dismissConfirm() = Unit

        override fun openStaged() = Unit

        override fun sendBack() = Unit

        override fun sendBackNote(note: String?) = Unit

        override fun guided(on: Boolean) { log += "guided $on" }
    }

    private fun ComposeUiTest.leftOf(text: String): Float =
        onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left

    private fun assertOnGutter(label: String, actual: Float, context: String) {
        assertTrue(
            abs(actual - GUTTER_DP) <= EDGE_TOLERANCE,
            "$label ($context) expected $GUTTER_DP±$EDGE_TOLERANCE, was $actual",
        )
    }

    @Test
    fun `B-563 every block on Changes starts on the 16 dp edge`() {
        val labels =
            listOf(
                "Guided" to "Guided",
                "Retry the upload" to "Retry the upload",
                "upload.kt" to kitBreakableFileName("upload.kt"),
                "Also changed" to "Also changed",
            )
        for (dark in listOf(true, false)) {
            for (scale in listOf(1f, 1.3f)) {
                runComposeUiTest {
                    setContent { Scene(scale = scale, dark = dark) { ChangesScreen(guidedChanges(), Calls()) } }
                    waitForIdle()
                    for ((name, text) in labels) {
                        assertOnGutter(name, leftOf(text), "scale=$scale dark=$dark")
                    }
                }
            }
        }
    }

    @Test
    fun `B-563 Also changed counts its files with a noun`() {
        runComposeUiTest {
            setContent { Scene { ChangesScreen(guidedChanges(), Calls()) } }
            waitForIdle()
            onNodeWithText("1 file", useUnmergedTree = true).assertExists()
        }
        val threeFiles =
            files.add(ChangeRowUi("test/retry_test.kt", "retry_test.kt", "test", 4, 0, hasDiff = true))
        val twoGlue =
            ReviewGuide(
                persistentListOf(section),
                persistentListOf("test/upload_test.kt", "test/retry_test.kt"),
            )
        val ui = guidedChanges().copy(files = threeFiles, guide = GuideUi.Ready(twoGlue))
        runComposeUiTest {
            setContent { Scene { ChangesScreen(ui, Calls()) } }
            waitForIdle()
            onNodeWithText("2 files", useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun `B-563 with comments written Send N comments leads and Done follows`() {
        runComposeUiTest {
            val calls = Calls()
            setContent { Scene { ChangesScreen(guidedChanges(drafts = 3), calls) } }
            waitForIdle()
            val sendTop =
                onNodeWithText("Send 3 comments", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            val doneTop =
                onNodeWithText("Done", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            assertTrue(sendTop < doneTop, "expected Send 3 comments ($sendTop) above Done ($doneTop)")
            onNodeWithText("Send 3 comments").performClick()
            assertTrue("send" in calls.log, "expected send in ${calls.log}")
        }
        runComposeUiTest {
            setContent { Scene { ChangesScreen(guidedChanges(drafts = 0), Calls()) } }
            waitForIdle()
            onNodeWithText("Done").assertExists()
            val sends =
                onAllNodes(hasText("Send", substring = true), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .filter { node ->
                        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Send") } == true
                    }
            assertEquals(0, sends.size, "expected no node starting with Send when drafts=0")
        }
    }

    @Test
    fun `B-563 the file review names its file once`() = runComposeUiTest {
        setContent { Scene { FileDiffScreen(page(CommentStatus.Draft), Calls()) } }
        waitForIdle()
        assertEquals(0, onAllNodes(hasText("src/a.kt"), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(
            0,
            onAllNodes(hasText("a.kt") and hasAnyAncestor(hasTestTag("diff-file-header")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
        )
        assertEquals(
            1,
            onAllNodes(hasText("src/") and hasAnyAncestor(hasTestTag("diff-file-header")), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun `B-563 comments start on the 16 dp edge with no empty mark gap`() {
        for (status in listOf(CommentStatus.Draft, CommentStatus.Pending)) {
            for (dark in listOf(true, false)) {
                for (scale in listOf(1f, 1.3f)) {
                    runComposeUiTest {
                        setContent { Scene(scale = scale, dark = dark) { FileDiffScreen(page(status), Calls()) } }
                        waitForIdle()
                        val ctx = "status=$status scale=$scale dark=$dark"
                        assertOnGutter("Your comments", leftOf("Your comments"), ctx)
                        assertOnGutter("Line 1", leftOf("Line 1"), ctx)
                    }
                }
            }
        }
    }

    @Test
    fun `B-563 writes the before and after pictures`() {
        val scenes: List<Pair<String, @Composable () -> Unit>> =
            listOf(
                "changes" to { ChangesScreen(guidedChanges(), Calls()) },
                "file-draft" to { FileDiffScreen(page(CommentStatus.Draft), Calls()) },
                "file-pending" to { FileDiffScreen(page(CommentStatus.Pending), Calls()) },
            )
        for ((name, body) in scenes) {
            for ((scaleTag, scale) in listOf("1x" to 1f, "1_3x" to 1.3f)) {
                for ((modeTag, dark) in listOf("dark" to true, "light" to false)) {
                    runComposeUiTest {
                        setContent { Scene(scale = scale, dark = dark) { body() } }
                        waitForIdle()
                        val file = File("build/polish-render/20-$PHASE-$name-$scaleTag-$modeTag.png")
                        file.parentFile.mkdirs()
                        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
                    }
                }
            }
        }
    }
}
