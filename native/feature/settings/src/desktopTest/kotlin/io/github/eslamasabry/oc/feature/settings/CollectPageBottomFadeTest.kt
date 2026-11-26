package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.analytics.ConsentState
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollectSource
import io.github.eslamasabry.oc.feature.settings.presentation.PrivacyUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.feature.settings.ui.WhatWeCollectBody
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.frame.KitFloatingBar
import io.github.eslamasabry.oc.ui.kit.frame.KitPinnedFootBody
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.collectNever
import io.github.eslamasabry.oc.ui.words.res.collectTitle
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
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

    private fun collectPrivacy() =
        PrivacyUi(WhatWeCollectSource.build(ConsentState(), RemoteSink), crashReports = false, usage = false)

    private fun settingsUi(page: SettingsPage) =
        SettingsUi(stack = persistentListOf(SettingsPage.Hub, SettingsPage.Privacy, page), privacy = collectPrivacy())

    @Composable
    private fun PreFixWhatWeCollect() {
        val actions = RecordingActions()
        val scroll = rememberScrollState()
        KitScreen(
            topBar = {
                KitTopBar(
                    title = stringResource(Res.string.collectTitle),
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::onBack,
                    backBesideTitle = true,
                )
            },
            width = KitScreenWidth.Reading,
            page = true,
            edges = KitScreenEdges.Bleed,
            floatingBar = KitFloatingBar(below = null, belowPinned = true),
        ) {
            KitPinnedFootBody(
                scroll = scroll,
                contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                WhatWeCollectBody(collectPrivacy(), actions)
            }
        }
    }

    private fun ComposeUiTest.scrollPageToEnd(tag: String) {
        val scroller = onNode(hasScrollAction() and !hasTestTag("kit-screen-status"))
        val before = scroller.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(before.maxValue() > 0f, "$tag must be scrollable at ${PHONE_W}dp")
        scroller.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, before.maxValue() + EXTRA_SCROLL) }
        waitForIdle()
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
        val after = scroller.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(
            after.value() >= after.maxValue() - 1f,
            "$tag did not reach end of scroll: ${after.value()} / ${after.maxValue()}",
        )
    }

    private fun ComposeUiTest.measureLastLineBottom(lastText: String, density: Float, tag: String): Pair<Float, Float> {
        val viewportBottom = onNodeWithTag("kit-screen").getBoundsInRoot().bottom.value
        val lastNode = onNodeWithText(lastText, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        lastNode.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val layout = layouts.single()
        assertFalse(layout.hasVisualOverflow, "$tag last text has visual overflow")
        assertTrue((0 until layout.lineCount).none(layout::isLineEllipsized), "$tag last text is ellipsized")
        val lastLineBottom = (lastNode.positionInRoot.y + layout.getLineBottom(layout.lineCount - 1)) / density
        return lastLineBottom to viewportBottom
    }

    private data class SceneSpec(
        val lastTextRes: StringResource,
        val dark: Boolean,
        val fontScale: Float,
        val renderName: String?,
        val tag: String,
    )

    private fun runEndScene(spec: SceneSpec, content: @Composable () -> Unit): Pair<Float, Float> {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        var result = 0f to 0f
        try {
            runDesktopComposeUiTest(PHONE_W, PHONE_H) {
                var lastText = ""
                var density = 1f
                setContent {
                    SettingsThemed(
                        dark = spec.dark,
                        locale = "en",
                        fontScale = spec.fontScale,
                        width = PHONE_W.toFloat(),
                    ) {
                        density = LocalDensity.current.density
                        lastText = stringResource(spec.lastTextRes)
                        content()
                    }
                }
                waitForIdle()
                scrollPageToEnd(spec.tag)
                if (spec.renderName != null) {
                    val img = onRoot().captureToImage().toAwtImage()
                    ImageIO.write(img, "png", File(rendersDir, "${spec.renderName}.png"))
                }
                result = measureLastLineBottom(lastText, density, spec.tag)
            }
        } finally {
            Locale.setDefault(previous)
        }
        return result
    }

    private fun assertClearsBottomFade(lastLineBottom: Float, viewportBottom: Float, tag: String) {
        val fadeTop = viewportBottom - BOTTOM_FADE_DP
        assertTrue(
            lastLineBottom <= fadeTop - MIN_CLEARANCE_DP,
            "$tag last line bottom ${lastLineBottom}dp does not clear bottom fade top ${fadeTop}dp " +
                "(viewport bottom ${viewportBottom}dp, need >= ${MIN_CLEARANCE_DP}dp clearance)",
        )
    }

    @Test
    fun `B-624 What we collect last line clears bottom fade at 360dp 1-0x and 1-3x in light and dark`() {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            for (scale in listOf(1.0f, 1.3f)) {
                val tag = "WhatWeCollect ($mode, ${scale}x)"
                val (beforeBottom, _) =
                    runEndScene(
                        SceneSpec(
                            lastTextRes = Res.string.collectNever,
                            dark = dark,
                            fontScale = scale,
                            renderName = "what-we-collect-before-360dp-$mode-$scale",
                            tag = "$tag before",
                        )
                    ) { PreFixWhatWeCollect() }
                val (afterBottom, viewportBottom) =
                    runEndScene(
                        SceneSpec(
                            lastTextRes = Res.string.collectNever,
                            dark = dark,
                            fontScale = scale,
                            renderName = "what-we-collect-after-360dp-$mode-$scale",
                            tag = "$tag after",
                        )
                    ) { SettingsScreen(settingsUi(SettingsPage.WhatWeCollect), RecordingActions()) }
                assertClearsBottomFade(afterBottom, viewportBottom, tag)
                assertTrue(
                    afterBottom <= beforeBottom - MIN_CLEARANCE_DP,
                    "$tag after ($afterBottom dp) did not move up from before ($beforeBottom dp)",
                )
            }
        }
    }

    @Test
    fun `B-624 sibling scrollable settings page clears bottom fade at 360dp 1-3x in light and dark`() {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            val tag = "PrivacyPolicy ($mode, 1.3x)"
            val (lastLineBottom, viewportBottom) =
                runEndScene(
                    SceneSpec(
                        lastTextRes = Res.string.nativeBundledPrivacy,
                        dark = dark,
                        fontScale = 1.3f,
                        renderName = null,
                        tag = tag,
                    )
                ) { SettingsScreen(settingsUi(SettingsPage.PrivacyPolicy), RecordingActions()) }
            assertClearsBottomFade(lastLineBottom, viewportBottom, tag)
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
