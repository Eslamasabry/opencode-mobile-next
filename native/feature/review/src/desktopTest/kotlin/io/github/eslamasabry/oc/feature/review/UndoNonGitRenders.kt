package io.github.eslamasabry.oc.feature.review

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.feature.review.presentation.StagedActions
import io.github.eslamasabry.oc.feature.review.presentation.StagedConfirm
import io.github.eslamasabry.oc.feature.review.presentation.StagedPhase
import io.github.eslamasabry.oc.feature.review.presentation.StagedUi
import io.github.eslamasabry.oc.feature.review.ui.StagedScreen
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
 * Pictures only, asserts nothing: the staged undo's "Put everything back" question and its done page, in a folder
 * that is not a Git project (messages only) and in one that is, dark, 360 dp wide, text 1.3. Writes PNGs to
 * `UNDO_NONGIT_OUT` (or the picture server's render-out folder); does nothing when neither exists.
 */
@OptIn(ExperimentalTestApi::class)
class UndoNonGitRenders {
    private val out =
        (System.getenv("UNDO_NONGIT_OUT")?.let(::File)
                ?: File(System.getProperty("user.home"), "render-out")
                    .takeIf { it.isDirectory }
                    ?.resolve("undo-nongit-words"))
            ?.also(File::mkdirs)

    private class Idle : StagedActions {
        override fun back() = Unit

        override fun retry() = Unit

        override fun openFile(path: String) = Unit

        override fun reviewLatest() = Unit

        override fun askKeep() = Unit

        override fun askPutBack() = Unit

        override fun confirm() = Unit

        override fun dismissConfirm() = Unit
    }

    private fun shoot(name: String, state: StagedUi) {
        Locale.setDefault(Locale.ENGLISH)
        runDesktopComposeUiTest(945, 2100) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.625f, 1.3f)) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                    ) {
                        StagedScreen(state, Idle())
                    }
                }
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
    }

    @Test
    fun `undo words render`() {
        if (out == null) return
        val git = persistentListOf(FileDiff("src/a.kt", additions = 2, deletions = 1))
        val review =
            StagedUi(phase = StagedPhase.Review, title = "Blue app", prompt = "make it blue", messagesAfter = 3)
        val ask = StagedConfirm.PutBack
        shoot("nongit-confirm", review.copy(files = persistentListOf(), noFileCopies = true, confirm = ask))
        shoot("git-confirm", review.copy(files = git, confirm = ask))
        shoot("nongit-done", StagedUi(phase = StagedPhase.Restored, noFileCopies = true))
        shoot("git-done", StagedUi(phase = StagedPhase.Restored))
    }
}
