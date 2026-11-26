package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import io.github.eslamasabry.oc.engine.analytics.ConsentState
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollectSource
import io.github.eslamasabry.oc.feature.settings.presentation.PrivacyUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.collectNever
import io.github.eslamasabry.oc.ui.words.res.nativeBundledPrivacy
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * B-624: scrolling "What we collect" (and sibling scrollable settings pages using SettingsFrame) to the end at
 * 360 dp places the full last line cleanly above the 24 dp bottom fade region at 1.0x and 1.3x in Light and Dark.
 */
@OptIn(ExperimentalTestApi::class)
class CollectPageBottomFadeTest {
    private val rendersDir = File("build/renders").also { it.mkdirs() }

    private fun ComposeUiTest.scrollPageToEnd(page: SettingsPage, fontScale: Float) {
        val scroller = onNode(hasScrollAction() and !hasTestTag("kit-screen-status"))
        val before = scroller.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(before.maxValue() > 0f, "$page must be scrollable at ${PHONE_W}dp scale=$fontScale")
        scroller.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, before.maxValue() + EXTRA_SCROLL) }
        waitForIdle()
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
        val after = scroller.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(
            after.value() >= after.maxValue() - 1f,
            "$page did not reach end of scroll: ${after.value()} / ${after.maxValue()}",
        )
    }

    private fun ComposeUiTest.assertLastLineAboveFade(lastText: String, density: Float, tag: String) {
        val viewportBottom = onNodeWithTag("kit-screen").getBoundsInRoot().bottom.value
        val fadeTop = viewportBottom - BOTTOM_FADE_DP
        val lastNode = onNodeWithText(lastText, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        lastNode.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val layout = layouts.single()
        assertFalse(layout.hasVisualOverflow, "$tag last text has visual overflow")
        assertTrue((0 until layout.lineCount).none(layout::isLineEllipsized), "$tag last text is ellipsized")
        val lastLineBottom = (lastNode.positionInRoot.y + layout.getLineBottom(layout.lineCount - 1)) / density
        assertTrue(
            lastLineBottom <= fadeTop - MIN_CLEARANCE_DP,
            "$tag last line bottom ${lastLineBottom}dp does not clear bottom fade top ${fadeTop}dp " +
                "(viewport bottom ${viewportBottom}dp, need >= ${MIN_CLEARANCE_DP}dp clearance)",
        )
    }

    private fun assertLastLineClearsBottomFade(
        page: SettingsPage,
        lastTextRes: StringResource,
        dark: Boolean,
        fontScale: Float,
        renderName: String? = null,
    ) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            val ui =
                SettingsUi(
                    stack = persistentListOf(SettingsPage.Hub, SettingsPage.Privacy, page),
                    privacy =
                        PrivacyUi(
                            WhatWeCollectSource.build(ConsentState(), RemoteSink),
                            crashReports = false,
                            usage = false,
                        ),
                )
            runDesktopComposeUiTest(PHONE_W, PHONE_H) {
                var lastText = ""
                var density = 1f
                setContent {
                    SettingsThemed(dark = dark, locale = "en", fontScale = fontScale, width = PHONE_W.toFloat()) {
                        density = LocalDensity.current.density
                        lastText = stringResource(lastTextRes)
                        SettingsScreen(ui, RecordingActions())
                    }
                }
                waitForIdle()
                scrollPageToEnd(page, fontScale)
                if (renderName != null) {
                    val img = onRoot().captureToImage().toAwtImage()
                    ImageIO.write(img, "png", File(rendersDir, "$renderName.png"))
                }
                val mode = if (dark) "dark" else "light"
                assertLastLineAboveFade(lastText, density, "$page ($mode, ${fontScale}x)")
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `B-624 What we collect last line clears bottom fade at 360dp 1-0x and 1-3x in light and dark`() {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            for (scale in listOf(1.0f, 1.3f)) {
                assertLastLineClearsBottomFade(
                    page = SettingsPage.WhatWeCollect,
                    lastTextRes = Res.string.collectNever,
                    dark = dark,
                    fontScale = scale,
                    renderName = "collect-end-360dp-$mode-$scale",
                )
            }
        }
    }

    @Test
    fun `B-624 sibling scrollable settings page clears bottom fade at 360dp 1-3x in light and dark`() {
        for (dark in listOf(false, true)) {
            assertLastLineClearsBottomFade(
                page = SettingsPage.PrivacyPolicy,
                lastTextRes = Res.string.nativeBundledPrivacy,
                dark = dark,
                fontScale = 1.3f,
            )
        }
    }

    private companion object {
        const val PHONE_W = 360
        const val PHONE_H = 915
        const val BOTTOM_FADE_DP = 24f
        const val MIN_CLEARANCE_DP = 16f
        const val EXTRA_SCROLL = 1_000f
        const val SETTLE_MS = 1_000L
    }
}
