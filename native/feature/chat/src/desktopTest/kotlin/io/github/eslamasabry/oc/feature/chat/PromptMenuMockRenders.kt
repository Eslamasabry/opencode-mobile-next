package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.tokens.Alphas
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuPanel
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatUiDeleteMessage
import io.github.eslamasabry.oc.ui.words.res.chatUiSendPromptAgain
import io.github.eslamasabry.oc.ui.words.res.chatUiCopyMessageText
import io.github.eslamasabry.oc.ui.words.res.chatUiForkFromThisPrompt
import io.github.eslamasabry.oc.ui.words.res.revertFromHere
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import org.jetbrains.compose.resources.stringResource

/**
 * MOCK, capture only (nothing asserted, nothing built in the kit): where the prompt's long-press menu could sit (F4,
 * build 10113). The real chat page with a conversation; "today" is the real menu opened by a real long-press, the
 * others draw the kit's own menu panel at a placement worked out here, with the bubble's own held face instead of the
 * full-width band. Dark, 360 dp, text 1.0, Solid and Glass. Writes PNGs to `PROMPT_MENU_MOCK_OUT` (or the picture
 * server's render-out folder); does nothing when neither exists, so CI never writes.
 */
@OptIn(ExperimentalTestApi::class)
class PromptMenuMockRenders {
    private val out =
        (
            System.getenv("PROMPT_MENU_MOCK_OUT")?.let(::File)
                ?: File(System.getProperty("user.home"), "render-out")
                    .takeIf { it.isDirectory }
                    ?.resolve("menu-mock")
        )?.also(File::mkdirs)

    private enum class Mock { Today, AboveClearOfFooter, BelowBubble, Focus }

    /** Where things are on the page, px in the root. */
    private class Geom(val bubble: Rect, val footerTop: Float, val safeTop: Float, val safeBottom: Float)

    private val composerActions =
        object : BasicComposerActions {
            override fun draftChanged(text: String) = Unit

            override suspend fun send(text: String) = true

            override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
        }

    private val host =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                BasicComposer(
                    BasicComposerUi(
                        available = true,
                        sending = false,
                        steers = true,
                        targetKey = "menu-mock",
                        models = ComposerModels(usesDefault = true),
                        filesAvailable = true,
                    ),
                    composerActions,
                    chrome,
                    modifier,
                )
            }
        }

    private fun world(nearTop: Boolean): FakeChatWorld {
        val world = FakeChatWorld()
        world.title.value = TITLE
        val first =
            listOf(
                prompt("u1", "Write two lines about rivers"),
                block("u1", "a1", "p1", 0, "Rivers carry the hills to the sea,\n$REPLY_LAST"),
                footer("u1"),
            )
        val answer = if (nearTop) LONG_ANSWER else "base-three\n\nThat is the word you asked for, on its own line.\nNothing else was added."
        val second =
            listOf(
                prompt("u2", PROMPT),
                thought("u2", "a2", "r2", "Thought"),
                block("u2", "a2", "p2", 0, answer),
                footer("u2"),
            )
        world.transcript.value = stateOf(*(first + second).toTypedArray())
        return world
    }

    private fun shoot(mock: Mock, style: Style, name: String, nearTop: Boolean = false) {
        val o = out ?: return
        Locale.setDefault(Locale.forLanguageTag("en"))
        val world = world(nearTop)
        val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val states = presenter.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
        val geom = mutableStateOf<Geom?>(null)
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent {
                val state by states.collectAsState()
                CompositionLocalProvider(LocalDensity provides Density(DENSITY, FONT_SCALE)) {
                    KitTheme(
                        appearance = KitAppearance(mode = AppearanceMode.Dark, style = style, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            KitSheetHost {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    ChatScreen(state.copy(title = TITLE), presenter, host)
                                    geom.value?.let { MockOverlay(mock, it) }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            if (mock == Mock.Today) {
                onNodeWithText(PROMPT, useUnmergedTree = true).performTouchInput { longClick(center) }
            } else {
                val text = onNodeWithText(PROMPT, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val px = DENSITY
                val bubble =
                    Rect(
                        text.left - BUBBLE_PAD_H * px,
                        text.top - BUBBLE_PAD_V * px,
                        text.right + BUBBLE_PAD_H * px,
                        text.bottom + BUBBLE_PAD_V * px,
                    )
                // The footer of the reply above: the small buttons between the reply's words and the bubble.
                val footer =
                    (onAllNodes(hasClickAction()).fetchSemanticsNodes() +
                            onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes())
                        .map { it.boundsInRoot }
                        .filter {
                            it.width < WIDTH * 0.5f && it.bottom <= bubble.top + 1f &&
                                it.top >= bubble.top - FOOTER_SEARCH_DP * px
                        }
                // Else: the footer starts just under the reply's last line.
                val replyBottom =
                    onNodeWithText(REPLY_LAST, substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.bottom
                val footerTop = footer.minOfOrNull { it.top } ?: (replyBottom + 4f * px)
                geom.value = Geom(bubble, footerTop, SAFE_TOP_DP * px, HEIGHT - SAFE_BOTTOM_DP * px)
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(o, "$name.png"))
        }
    }

    @Composable
    private fun MockOverlay(mock: Mock, g: Geom) {
        val colors = LocalKitColors.current
        val px = LocalDensity.current.density
        val gap = GAP_DP * px
        val gutter = GUTTER_DP * px
        val bubbleShape =
            RoundRect(
                g.bubble,
                topLeft = CornerRadius(RADIUS_DP * px),
                topRight = CornerRadius(RADIUS_DP * px),
                bottomRight = CornerRadius(TAIL_DP * px),
                bottomLeft = CornerRadius(RADIUS_DP * px),
            )
        Box(Modifier.fillMaxSize()) {
            if (mock == Mock.Focus)
                Canvas(Modifier.fillMaxSize()) {
                    val dim =
                        Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(Offset.Zero, Size(size.width, size.height)))
                            addRoundRect(bubbleShape)
                        }
                    drawPath(dim, Color.Black.copy(alpha = SCRIM))
                }
            // The bubble's own held face (the press fill kept, in the bubble's outline), never a full-width band.
            Canvas(Modifier.fillMaxSize()) {
                drawPath(Path().apply { addRoundRect(bubbleShape) }, colors.text1.copy(alpha = Alphas.PRESSED))
            }
            val items =
                listOf(
                    KitMenuItem(stringResource(Res.string.chatUiCopyMessageText), {}, KitIcons.copy),
                    KitMenuItem(stringResource(Res.string.chatUiSendPromptAgain), {}, KitIcons.send),
                    KitMenuItem(stringResource(Res.string.chatUiForkFromThisPrompt), {}, KitIcons.fork),
                    KitMenuItem(stringResource(Res.string.revertFromHere), {}, KitIcons.history),
                    KitMenuItem(stringResource(Res.string.chatUiDeleteMessage), {}, KitIcons.delete, destructive = true),
                )
            Layout(content = { KitMenuPanel(items, onChoose = {}) }, modifier = Modifier.fillMaxSize()) { ms, c ->
                val p = ms.first().measure(Constraints(maxWidth = c.maxWidth, maxHeight = c.maxHeight))
                val h = p.height.toFloat()
                // The panel's end edge on the bubble's end edge, held inside the gutters.
                val x = (g.bubble.right - p.width).coerceIn(gutter, c.maxWidth - gutter - p.width).roundToInt()
                val aboveBubble = g.bubble.top - gap - h
                val clearOfFooter = g.footerTop - gap - h
                val belowBubble = g.bubble.bottom + gap
                val fitsBelow = belowBubble + h <= g.safeBottom
                val y =
                    when (mock) {
                        // Above when there is room above the reply's footer; below when the bubble is near the top.
                        Mock.AboveClearOfFooter ->
                            if (clearOfFooter >= g.safeTop) clearOfFooter else belowBubble.coerceAtMost(g.safeBottom - h)
                        // Below the bubble when it fits; else above, clear of the footer.
                        Mock.BelowBubble -> if (fitsBelow) belowBubble else clearOfFooter.coerceAtLeast(g.safeTop)
                        // The page dims; the menu right above the bubble (below when near the top).
                        Mock.Focus -> if (aboveBubble >= g.safeTop) aboveBubble else belowBubble
                        Mock.Today -> belowBubble
                    }
                layout(c.maxWidth, c.maxHeight) { p.place(x, y.roundToInt()) }
            }
        }
    }

    @Test
    fun `prompt menu mock renders`() {
        if (out == null) return
        for (style in listOf(Style.Solid, Style.Glass)) {
            val s = style.name.lowercase()
            shoot(Mock.Today, style, "0-today-$s-dark-360")
            shoot(Mock.AboveClearOfFooter, style, "A-above-clear-of-footer-$s-dark-360")
            shoot(Mock.Focus, style, "C-focus-dim-$s-dark-360")
        }
        shoot(Mock.AboveClearOfFooter, Style.Solid, "A2-near-top-opens-below-solid-dark-360", nearTop = true)
        shoot(Mock.Focus, Style.Solid, "C2-near-top-opens-below-solid-dark-360", nearTop = true)
    }

    private companion object {
        const val DENSITY = 2.625f
        const val FONT_SCALE = 1.0f
        const val WIDTH = 945 // 360 dp
        const val HEIGHT = 2100 // 800 dp
        const val TITLE = "Reply with exactly the word"
        const val REPLY_LAST = "one grain at a time, without hurry."
        const val PROMPT = "Reply with exactly the word: base-three"
        const val LONG_ANSWER =
            "base-three\n\nOne\nTwo\nThree\nFour\nFive\nSix\nSeven\nEight\nNine\nTen\nEleven\nTwelve\nThirteen\nFourteen"
        const val BUBBLE_PAD_H = 12f
        const val BUBBLE_PAD_V = 10f
        const val RADIUS_DP = 20f
        const val TAIL_DP = 6f
        const val GAP_DP = 8f
        const val GUTTER_DP = 16f
        const val FOOTER_SEARCH_DP = 72f
        const val SAFE_TOP_DP = 64f
        const val SAFE_BOTTOM_DP = 150f
        const val SCRIM = 0.55f
    }
}
