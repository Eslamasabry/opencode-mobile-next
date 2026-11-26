package io.github.eslamasabry.oc.feature.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.feature.review.presentation.ChangeRowUi
import io.github.eslamasabry.oc.feature.review.presentation.ChangesActions
import io.github.eslamasabry.oc.feature.review.presentation.ChangesUi
import io.github.eslamasabry.oc.feature.review.presentation.ReviewLoad
import io.github.eslamasabry.oc.feature.review.presentation.StagedActions
import io.github.eslamasabry.oc.feature.review.presentation.StagedPhase
import io.github.eslamasabry.oc.feature.review.presentation.StagedUi
import io.github.eslamasabry.oc.feature.review.presentation.UndoAllUi
import io.github.eslamasabry.oc.feature.review.presentation.UndoFromHereActions
import io.github.eslamasabry.oc.feature.review.presentation.UndoFromHereUi
import io.github.eslamasabry.oc.feature.review.ui.ChangesScreen
import io.github.eslamasabry.oc.feature.review.ui.StagedScreen
import io.github.eslamasabry.oc.feature.review.ui.UndoFromHereScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/**
 * 360 dp renders (light & dark, text 1.0 & 1.3) for B-473/B-475 undo truth and one-tap Git init.
 * Writes PNGs into `native/feature/review/build/renders/undo-revert-direct/`.
 */
@OptIn(ExperimentalTestApi::class)
class UndoDirectRenders {
    private val out =
        (System.getenv("UNDO_DIRECT_OUT")?.let(::File) ?: File("build/renders/undo-revert-direct"))
            .also(File::mkdirs)

    private val idleChanges =
        object : ChangesActions {
            override fun back(): Unit = Unit

            override fun retry(): Unit = Unit

            override fun openFile(path: String): Unit = Unit

            override fun keepAll(): Unit = Unit

            override fun undoAll(): Unit = Unit

            override fun confirmUndoAll(): Unit = Unit

            override fun dismissConfirm(): Unit = Unit

            override fun openStaged(): Unit = Unit

            override fun sendBack(): Unit = Unit

            override fun sendBackNote(note: String?): Unit = Unit
        }

    private val idleUndo =
        object : UndoFromHereActions {
            override fun back(): Unit = Unit

            override fun putFilesBack(apply: Boolean): Unit = Unit

            override fun confirm(): Unit = Unit
        }

    private val idleStaged =
        object : StagedActions {
            override fun back(): Unit = Unit

            override fun retry(): Unit = Unit

            override fun openFile(path: String): Unit = Unit

            override fun reviewLatest(): Unit = Unit

            override fun askKeep(): Unit = Unit

            override fun askPutBack(): Unit = Unit

            override fun confirm(): Unit = Unit

            override fun dismissConfirm(): Unit = Unit
        }

    private fun shootAll(slug: String, content: @Composable () -> Unit) {
        val modes = listOf("dark" to AppearanceMode.Dark, "light" to AppearanceMode.Light)
        val scales = listOf("t10" to 1.0f, "t13" to 1.3f)
        for ((modeName, mode) in modes) {
            for ((scaleName, scale) in scales) {
                shoot("$PHASE-$slug-$modeName-$scaleName", mode, scale, content)
            }
        }
    }

    private fun shoot(
        name: String,
        mode: AppearanceMode,
        fontScale: Float,
        content: @Composable () -> Unit,
    ) {
        Locale.setDefault(Locale.ENGLISH)
        runDesktopComposeUiTest(WIDTH_PX, HEIGHT_PX) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY, fontScale)) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        appearance = KitAppearance(mode = mode, motion = MotionLevel.Off),
                        stillFrame = true,
                    ) {
                        content()
                    }
                }
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
    }

    @Test
    fun `undo direct renders`() {
        if (System.getenv("GITHUB_ACTIONS") == "true") return
        val files =
            persistentListOf(
                ChangeRowUi("src/a.kt", "a.kt", "src", 2, 1, hasDiff = true),
                ChangeRowUi("src/b.kt", "b.kt", "src", 4, 0, hasDiff = true),
            )
        shootAll("changes-nongit-confirm") {
            ChangesScreen(
                ChangesUi(
                    turn = false,
                    load = ReviewLoad.Ready,
                    files = files,
                    undoAll = UndoAllUi.Offered,
                    confirming = true,
                    noFileCopies = true,
                ),
                idleChanges,
            )
        }
        shootAll("undo-git") {
            UndoFromHereScreen(
                UndoFromHereUi(
                    load = ReviewLoad.Ready,
                    staged = true,
                    prompt = "make it blue",
                    applyFiles = true,
                ),
                idleUndo,
            )
        }
        shootAll("undo-nongit") {
            UndoFromHereScreen(
                UndoFromHereUi(
                    load = ReviewLoad.Ready,
                    staged = true,
                    prompt = "make it blue",
                    applyFiles = false,
                    noFileCopies = true,
                    canInitGit = true,
                ),
                idleUndo,
            )
        }
        shootAll("staged-nongit") {
            StagedScreen(
                StagedUi(
                    phase = StagedPhase.Review,
                    title = "Blue app",
                    prompt = "make it blue",
                    messagesAfter = 3,
                    files = persistentListOf(),
                    noFileCopies = true,
                    canInitGit = true,
                ),
                idleStaged,
            )
        }
    }

    private companion object {
        const val PHASE: String = "after"
        const val WIDTH_PX: Int = 945
        const val HEIGHT_PX: Int = 2100
        const val DENSITY: Float = 2.625f
    }
}
