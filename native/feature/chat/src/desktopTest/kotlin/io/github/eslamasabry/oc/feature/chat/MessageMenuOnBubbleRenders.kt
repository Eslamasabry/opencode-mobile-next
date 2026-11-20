package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
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
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted

/**
 * Capture only (no comparison, nothing asserted): F4, option C. The real chat page (ChatScreen with its presenter over
 * a fake world) with a reply and its footer, then a prompt, then a reply; the prompt is held with a real long-press and
 * its message menu opens on the bubble, a step clear of it, with the page dimmed and the bubble left bright. Solid and
 * Glass, dark and light, 360 dp, text 1.0 and 1.3; a prompt near the top (its menu opens below); Glass over the live
 * background. Writes PNGs to `MESSAGE_MENU_ON_BUBBLE_OUT` (or the picture server's render-out folder); does nothing
 * when neither exists, so CI never writes.
 */
@OptIn(ExperimentalTestApi::class)
class MessageMenuOnBubbleRenders {
    private val out =
        (
            System.getenv("MESSAGE_MENU_ON_BUBBLE_OUT")?.let(::File)
                ?: File(System.getProperty("user.home"), "render-out")
                    .takeIf { it.isDirectory }
                    ?.resolve("message-menu-on-bubble")
        )?.also(File::mkdirs)

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
                        targetKey = "menu",
                        models = ComposerModels(usesDefault = true),
                        filesAvailable = true,
                    ),
                    composerActions,
                    chrome,
                    modifier,
                )
            }
        }

    /** A reply with its footer, then the held prompt, then a short reply: the menu has room above the prompt. */
    private fun middle(): List<TranscriptRow> =
        turn(1, ask = "Write two lines about rivers", answer = "Rivers carry the hills to the sea,\none grain at a time, without hurry.") +
            turn(2, ask = PROMPT, answer = "base-three\n\nThat is the word you asked for, on its own line. Nothing else was added.")

    /** The held prompt near the top: a long reply of [paragraphs] under it fills the page, so its menu opens below it. */
    private fun nearTop(paragraphs: Int): List<TranscriptRow> =
        listOf(prompt("u1", PROMPT)) +
            (0 until paragraphs).map { block("u1", "a1", "p1", it * 100, LONG_LINE) } +
            footer("u1")

    private fun shoot(name: String, style: Style, dark: Boolean, scale: Float, rows: List<TranscriptRow>, live: Boolean = false) {
        val o = out ?: return
        Locale.setDefault(Locale.forLanguageTag("en"))
        val world = FakeChatWorld()
        world.transcript.value = stateOf(*rows.toTypedArray())
        world.title.value = TITLE
        val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val states = presenter.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
        // runSkikoComposeUiTest honours its timeout (runDesktopComposeUiTest keeps a minute, too short for Glass here).
        runSkikoComposeUiTest(androidx.compose.ui.geometry.Size(WIDTH.toFloat(), HEIGHT.toFloat()), testTimeout = kotlin.time.Duration.parse("6m")) {
            setContent {
                val state by states.collectAsState()
                CompositionLocalProvider(LocalDensity provides Density(DENSITY, scale)) {
                    KitTheme(
                        appearance =
                            KitAppearance(
                                mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                                style = style,
                                motion = MotionLevel.Off,
                                liveBackground = live,
                                liveStyle = "aurora",
                            ),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                ChatScreen(state, presenter, host)
                            }
                        }
                    }
                }
            }
            // The clock is stepped by hand (the live background's glass is drawn in software here; see
            // ChatEdgesBuildRenders): two frames to compose, a second to settle, then the hold and a few frames.
            mainClock.autoAdvance = false
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            waitForIdle()
            mainClock.advanceTimeBy(1_000, ignoreFrameDuration = true)
            waitForIdle()
            // A real long-press: the finger goes down on the prompt, stays past the long-press time (one jump of the
            // clock, one frame), and lifts.
            // On the root: once the menu is open the page's words leave the semantics tree, and the finger lifts there.
            val at = onNodeWithText(PROMPT, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
            onRoot().performTouchInput { down(at) }
            mainClock.advanceTimeBy(LONG_PRESS_MS, ignoreFrameDuration = true)
            waitForIdle()
            onRoot().performTouchInput { up() }
            repeat(3) { mainClock.advanceTimeByFrame() }
            waitForIdle()
            mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(o, "$name.png"))
        }
    }

    @Test
    fun `message menu on bubble renders, Solid`() {
        if (out == null) return
        for (dark in listOf(true, false)) for (scale in listOf(1.0f, 1.3f)) {
            shoot("solid-${mode(dark)}-360-${scale}", Style.Solid, dark, scale, middle())
        }
        shoot("near-top-solid-dark-360-1.0", Style.Solid, dark = true, scale = 1.0f, rows = nearTop(paragraphs = 4))
    }

    @Test
    fun `message menu on bubble renders, Glass`() {
        if (out == null) return
        for (dark in listOf(true, false)) for (scale in listOf(1.0f, 1.3f)) {
            shoot("glass-${mode(dark)}-360-${scale}", Style.Glass, dark, scale, middle())
        }
        shoot("near-top-glass-dark-360-1.3", Style.Glass, dark = true, scale = 1.3f, rows = nearTop(paragraphs = 3))
    }

    @Test
    fun `message menu on bubble renders, Glass over the live background`() {
        if (out == null) return
        shoot("glass-live-dark-360-1.0", Style.Glass, dark = true, scale = 1.0f, rows = middle(), live = true)
        shoot("glass-live-light-360-1.0", Style.Glass, dark = false, scale = 1.0f, rows = middle(), live = true)
    }

    private fun mode(dark: Boolean) = if (dark) "dark" else "light"

    private companion object {
        const val DENSITY = 2.625f
        const val LONG_PRESS_MS = 800L
        const val WIDTH = 945 // 360 dp
        const val HEIGHT = 2100 // 800 dp
        const val TITLE = "Rivers, then one word"
        const val PROMPT = "Reply with exactly the word: base-three"
        const val LONG_LINE =
            "A river is never the same water twice: what you see now left the hills days ago and will reach the sea " +
                "long after you have walked away from its bank."
    }
}
