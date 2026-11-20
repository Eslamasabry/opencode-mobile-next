package io.github.eslamasabry.oc.feature.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.review.presentation.ReviewLoad
import io.github.eslamasabry.oc.feature.review.presentation.StagedActions
import io.github.eslamasabry.oc.feature.review.presentation.StagedConfirm
import io.github.eslamasabry.oc.feature.review.presentation.StagedPhase
import io.github.eslamasabry.oc.feature.review.presentation.StagedUi
import io.github.eslamasabry.oc.feature.review.presentation.UndoFromHereActions
import io.github.eslamasabry.oc.feature.review.presentation.UndoFromHereUi
import io.github.eslamasabry.oc.feature.review.ui.StagedScreen
import io.github.eslamasabry.oc.feature.review.ui.UndoFromHereScreen
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Capture-only phone-width (360 dp) renders for the OpenCode 2 Go back redesign review screens:
 * Preview ("Go back to this prompt"), Review ("Review what went back"), Keep confirmation,
 * zero-files preview, and 12-file preview with disclosure.
 */
@OptIn(ExperimentalTestApi::class)
class UndoRedesignReviewRenders {
    private val out = File("build/undo-redesign-renders").also(File::mkdirs)

    private val undoActions = object : UndoFromHereActions {
        override fun back() {}
        override fun putFilesBack(apply: Boolean) {}
        override fun confirm() {}
    }

    private val stagedActions = object : StagedActions {
        override fun back() {}
        override fun retry() {}
        override fun openFile(path: String) {}
        override fun reviewLatest() {}
        override fun askKeep() {}
        override fun askPutBack() {}
        override fun confirm() {}
        override fun dismissConfirm() {}
    }

    private data class Variant(val tag: String, val style: Style, val dark: Boolean, val fontScale: Float)

    private val solidVariants = listOf(
        Variant("solid-dark-1.0", Style.Solid, dark = true, fontScale = 1.0f),
        Variant("solid-light-1.0", Style.Solid, dark = false, fontScale = 1.0f),
        Variant("solid-dark-1.3", Style.Solid, dark = true, fontScale = 1.3f),
        Variant("solid-light-1.3", Style.Solid, dark = false, fontScale = 1.3f),
    )

    private val glassDark = listOf(Variant("glass-dark-1.0", Style.Glass, dark = true, fontScale = 1.0f))
    private val glassLight = listOf(Variant("glass-light-1.0", Style.Glass, dark = false, fontScale = 1.0f))

    private fun shoot(name: String, variants: List<Variant>, content: @Composable () -> Unit) {
        Locale.setDefault(Locale.ENGLISH)
        for (v in variants) {
            runDesktopComposeUiTest(360 * 2, 780 * 2) {
                setContent {
                    val appearance =
                        if (v.style == Style.Glass) {
                            KitAppearance(
                                mode = if (v.dark) AppearanceMode.Dark else AppearanceMode.Light,
                                style = Style.Glass,
                                motion = MotionLevel.Off,
                                pack = PackId.Midnight,
                                liveBackground = true,
                                liveStyle = "aurora",
                            )
                        } else {
                            KitAppearance(
                                mode = if (v.dark) AppearanceMode.Dark else AppearanceMode.Light,
                                style = Style.Solid,
                                motion = MotionLevel.Off,
                            )
                        }
                    CompositionLocalProvider(
                        LocalDensity provides Density(2f, v.fontScale),
                        LocalKitScreenInsets provides WindowInsets(top = 28.dp, bottom = 20.dp),
                    ) {
                        KitTheme(
                            appearance = appearance,
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                                Box(Modifier.fillMaxSize()) { content() }
                            }
                        }
                    }
                }
                mainClock.autoAdvance = false
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                waitForIdle()
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(),
                    "png",
                    File(out, "$name-${v.tag}.png"),
                )
            }
        }
    }

    @Composable
    private fun PreviewThreeFiles() {
        UndoFromHereScreen(
            state = UndoFromHereUi(
                load = ReviewLoad.Ready,
                staged = true,
                available = true,
                prompt = LONG_PROMPT,
                messagesAfter = 3,
                previewFiles = THREE_FILES,
                applyFiles = true,
            ),
            actions = undoActions,
        )
    }

    @Composable
    private fun ReviewStaged() {
        StagedScreen(
            state = StagedUi(
                phase = StagedPhase.Review,
                title = "Fix flaky checkout test",
                prompt = LONG_PROMPT,
                messagesAfter = 3,
                files = THREE_FILES,
            ),
            actions = stagedActions,
        )
    }

    @Test fun `render 02 preview solid`() = shoot("02-preview", solidVariants) { PreviewThreeFiles() }
    @Test fun `render 02 preview glass dark`() = shoot("02-preview", glassDark) { PreviewThreeFiles() }
    @Test fun `render 02 preview glass light`() = shoot("02-preview", glassLight) { PreviewThreeFiles() }

    @Test fun `render 02b preview files off`() = shoot(
        "02b-preview-files-off",
        listOf(
            Variant("solid-dark-1.0", Style.Solid, dark = true, fontScale = 1.0f),
            Variant("solid-light-1.0", Style.Solid, dark = false, fontScale = 1.0f),
        ),
    ) {
        UndoFromHereScreen(
            state = UndoFromHereUi(
                load = ReviewLoad.Ready,
                staged = true,
                available = true,
                prompt = LONG_PROMPT,
                messagesAfter = 3,
                previewFiles = THREE_FILES,
                applyFiles = false,
            ),
            actions = undoActions,
        )
    }

    @Test fun `render 04 review solid`() = shoot("04-review", solidVariants) { ReviewStaged() }
    @Test fun `render 04 review glass dark`() = shoot("04-review", glassDark) { ReviewStaged() }
    @Test fun `render 04 review glass light`() = shoot("04-review", glassLight) { ReviewStaged() }

    @Test
    fun `render 05 confirm keep`() = shoot("05-confirm-keep", solidVariants) {
        StagedScreen(
            state = StagedUi(
                phase = StagedPhase.Review,
                title = "Fix flaky checkout test",
                prompt = LONG_PROMPT,
                messagesAfter = 3,
                files = THREE_FILES,
                confirm = StagedConfirm.Keep,
            ),
            actions = stagedActions,
        )
    }

    @Test
    fun `render 07 preview empty`() = shoot("07-preview-empty", solidVariants) {
        UndoFromHereScreen(
            state = UndoFromHereUi(
                load = ReviewLoad.Ready,
                staged = true,
                available = true,
                prompt = "Explain why the coupon refresh runs before the cart total settles, without changing any files yet.",
                messagesAfter = 2,
                previewFiles = persistentListOf(),
                applyFiles = true,
            ),
            actions = undoActions,
        )
    }

    @Test
    fun `render 08 preview many files`() = shoot("08-preview-many-files", solidVariants) {
        UndoFromHereScreen(
            state = UndoFromHereUi(
                load = ReviewLoad.Ready,
                staged = true,
                available = true,
                prompt = LONG_PROMPT,
                messagesAfter = 5,
                previewFiles = TWELVE_FILES,
                applyFiles = true,
            ),
            actions = undoActions,
        )
    }

    @Test
    fun `render 09 preview no git`() = shoot("09-preview-no-git", solidVariants) {
        UndoFromHereScreen(
            state = UndoFromHereUi(
                load = ReviewLoad.Ready,
                staged = true,
                available = true,
                prompt = LONG_PROMPT,
                messagesAfter = 3,
                previewFiles = persistentListOf(),
                notGitRepository = true,
                applyFiles = false,
            ),
            actions = undoActions,
        )
    }

    private companion object {
        const val LONG_PROMPT =
            "Wait for the settled cart state in CheckoutBloc instead of sleeping 200 ms, then update the coupon retry test and verify all checkout assertions pass."

        val THREE_FILES = persistentListOf(
            FileDiff("lib/checkout/checkout_bloc.dart", additions = 14, deletions = 3, patch = "@@ -1,3 +1,14 @@"),
            FileDiff("lib/checkout/coupon_service.dart", additions = 22, deletions = 8, patch = "@@ -1,8 +1,22 @@"),
            FileDiff("test/checkout/checkout_test.dart", additions = 31, deletions = 4, patch = "@@ -1,4 +1,31 @@"),
        )

        val TWELVE_FILES = listOf(
            FileDiff("lib/checkout/checkout_bloc.dart", additions = 14, deletions = 3),
            FileDiff("lib/checkout/coupon_service.dart", additions = 22, deletions = 8),
            FileDiff("test/checkout/checkout_test.dart", additions = 31, deletions = 4),
            FileDiff("lib/checkout/cart_totals.dart", additions = 9, deletions = 2),
            FileDiff("lib/checkout/price_refresh.dart", additions = 18, deletions = 11),
            FileDiff("lib/checkout/discount_rules.dart", additions = 6, deletions = 1),
            FileDiff("lib/checkout/checkout_state.dart", additions = 12, deletions = 5),
            FileDiff("lib/checkout/checkout_events.dart", additions = 4, deletions = 0),
            FileDiff("test/checkout/coupon_service_test.dart", additions = 42, deletions = 9),
            FileDiff("test/checkout/cart_totals_test.dart", additions = 17, deletions = 3),
            FileDiff("lib/api/pricing_client.dart", additions = 8, deletions = 4),
            FileDiff("docs/checkout-flow.md", additions = 15, deletions = 2),
        ).toImmutableList()
    }
}
