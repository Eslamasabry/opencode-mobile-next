package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatListWords
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptList
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Pictures of the first send (F28): Sending, Thinking, the reply starting, at 360 dp, dark, text 1.0 and 1.3. It
 * asserts nothing; set ONE_SLOT_OUT to a folder to write the pictures.
 */
@OptIn(ExperimentalTestApi::class)
class OneStatusSlotRenders {
    private object Words : ChatTranscriptWords {
        override val jumpToLatest: String = "Jump to latest"

        override fun workLine(calls: Int, failed: Int, state: WorkState): String = "$calls steps"

        override fun notice(kind: NoticeKind): String = "Notice"

        override fun footer(failed: Boolean): String = "More"

        override fun thought(firstLine: String): String = firstLine
    }

    private val listWords =
        ChatListWords(
            messageActions = "Message actions",
            working = "Working",
            stopping = "Stopping",
            sending = "Sending",
            notSent = "Not sent",
            unconfirmed = "Not confirmed",
            tryAgain = "Try again",
        )

    private val out = System.getenv("ONE_SLOT_OUT")?.let { File(it).also(File::mkdirs) }
    private val ask = "Fix the flaky checkout test"

    @Test
    fun `one status slot renders`() {
        val dir = out ?: return
        Locale.setDefault(Locale.forLanguageTag("en"))
        for (style in listOf(Style.Solid, Style.Glass)) {
            for (scale in listOf(1.0f, 1.3f)) {
                runDesktopComposeUiTest(945, 1400) {
                    var pending: ImmutableList<PendingSend> by
                        mutableStateOf(persistentListOf(PendingSend(1L, ask, PendingState.Sending)))
                    var live by mutableStateOf<LiveTurnUi?>(null)
                    var rows by mutableStateOf<List<TranscriptRow>>(emptyList())
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(2.625f, scale)) {
                            KitTheme(
                                appearance = KitAppearance(mode = AppearanceMode.Dark, style = style, motion = MotionLevel.Full),
                                platform = DesktopKitPlatform(),
                                localeTag = "en",
                                stillFrame = true,
                            ) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    KitStandaloneRedaction {
                                        ChatTranscriptList(
                                            rows = stateOf(*rows.toTypedArray()).rows,
                                            live = live,
                                            pending = pending,
                                            words = Words,
                                            listWords = listWords,
                                            list = rememberTranscriptListState(),
                                            reveal = rememberRevealRegistry(),
                                            motion = KitMotionLevel.Full,
                                            onOpenMenu = null,
                                            onRetrySend = {},
                                        )
                                    }
                                }
                            }
                        }
                    }
                    fun shot(name: String) {
                        waitForIdle()
                        mainClock.advanceTimeBy(800)
                        ImageIO.write(
                            onRoot().captureToImage().toAwtImage(),
                            "png",
                            File(dir, "$name-${style.storageName}-dark-t${(scale * 10).toInt()}.png"),
                        )
                    }
                    shot("1-sending")
                    live = LiveTurnUi(stopping = false)
                    shot("2-thinking")
                    pending = persistentListOf()
                    rows = listOf(prompt("u1", ask), block("u1", "a1", "p1", 0, "The race is in CheckoutBloc.", live = true))
                    shot("3-reply-starting")
                }
            }
        }
    }
}
