package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyAskStage
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyAskUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlinx.collections.immutable.toPersistentList
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Capture only (no comparison): the first-reply ask states (ask, yes, no, skip) at 945x2100 px (360 dp),
 * density 2.625, font scale 1.0 and 1.3. Writes PNGs to `STAR_PROMPT_OUT`; does nothing when unset.
 */
@OptIn(ExperimentalTestApi::class)
class FirstReplyAskRenders {
    private val out = System.getenv("STAR_PROMPT_OUT")?.let { File(it).also(File::mkdirs) }

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
                        targetKey = "ask-renders",
                        models = ComposerModels(usesDefault = true),
                        filesAvailable = true,
                    ),
                    composerActions,
                    chrome,
                    modifier,
                )
            }
        }

    @Composable
    private fun Screen(ask: ReplyAskUi?, fontScale: Float) {
        CompositionLocalProvider(LocalDensity provides Density(DENSITY, fontScale)) {
            KitTheme(
                appearance =
                    KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = MotionLevel.Off),
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                        val rows: List<TranscriptRow> = turn(1, ask = FIRST, answer = ANSWER).asReversed()
                        val state =
                            ChatUi.loading(Ref).copy(
                                title = "Fix the flaky checkout test",
                                phase = ChatPhase.Live,
                                rows = rows.toPersistentList(),
                                replyAsk = ask,
                            )
                        ChatScreen(
                            state,
                            ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate),
                            host,
                            facts = completedReplyFacts,
                        )
                    }
                }
            }
        }
    }

    private fun shoot(name: String, ask: ReplyAskUi?, fontScale: Float) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        kitUiTest(WIDTH, HEIGHT) {
            setContent { Screen(ask, fontScale) }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            val image = onRoot().captureToImage().toAwtImage()
            ImageIO.write(image, "png", File(out, "$name.png"))
        }
    }

    @Test
    fun `first reply ask renders`() {
        if (out == null) return
        val states =
            listOf(
                "ask" to ReplyAskUi("u1", ReplyAskStage.Ask, menu = false),
                "yes" to ReplyAskUi("u1", ReplyAskStage.Thanks, menu = true),
                "no" to ReplyAskUi("u1", ReplyAskStage.Sorry, menu = true),
                "skip" to null,
            )
        for (scale in listOf(1.0f, 1.3f)) {
            val tag = if (scale == 1.0f) "1_0" else "1_3"
            for ((label, ask) in states) {
                shoot("first-reply-ask-$label-$tag", ask, scale)
            }
        }
    }

    private companion object {
        const val WIDTH = 945
        const val HEIGHT = 2100
        const val DENSITY = 2.625f
        const val FIRST = "Why does the checkout test fail one run in five?"
        const val ANSWER =
            "The coupon total is read before the network call settles. Waiting on the settled state fixes it."
    }
}
