package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.ui.ChatListWords
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptList
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
import io.github.eslamasabry.oc.feature.chat.world.PendingFile
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
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
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/**
 * B-500 before/after 360 dp renders (light & dark, text 1.0 & 1.3) for the offline outbox bubble,
 * plus visible copy & Cancel interaction checks. Writes PNGs to
 * `native/feature/chat/build/renders/offline-outbox-keep-message/`.
 */
@OptIn(ExperimentalTestApi::class)
class OfflineOutboxRenders {
    private val out: File = File("build/renders/offline-outbox-keep-message").also(File::mkdirs)

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
            sendFailure = { "Not connected to the server right now." },
            waitingForConnection = "Waiting for connection — sends when back online",
            cancelWaiting = "Cancel",
        )

    private val beforeBubble =
        PendingSend(
            localId = 1L,
            text = "Please run the checkout suite and post the failing trace.",
            state = PendingState.Failed,
            failure = Failure.Network(NetworkWhy.Offline),
            files = listOf(PendingFile("trace-log.txt", "text/plain", null)),
        )

    private val afterBubble =
        PendingSend(
            localId = 2L,
            text = "Please run the checkout suite and post the failing trace.",
            state = PendingState.WaitingForConnection,
            files = listOf(PendingFile("trace-log.txt", "text/plain", null)),
        )

    private fun renderScene(
        bubble: PendingSend,
        mode: AppearanceMode,
        scale: Float,
        fileName: String,
        checkCancel: Boolean = false,
    ) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        var cancelledId: Long? = null
        kitUiTest(945, 900) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.625f, scale)) {
                    KitTheme(
                        appearance =
                            KitAppearance(
                                mode = mode,
                                style = Style.Solid,
                                motion = MotionLevel.Off,
                            ),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                            KitStandaloneRedaction {
                                ChatTranscriptList(
                                    rows = stateOf().rows,
                                    live = null,
                                    pending = persistentListOf(bubble),
                                    words = Words,
                                    listWords = listWords,
                                    list = rememberTranscriptListState(),
                                    reveal = rememberRevealRegistry(),
                                    motion = KitMotionLevel.Off,
                                    onOpenMenu = null,
                                    onRetrySend = {},
                                    onCancelWaiting = { cancelledId = it },
                                )
                            }
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(500)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, fileName))
            if (checkCancel) {
                onNodeWithText("Waiting for connection — sends when back online").assertIsDisplayed()
                onNodeWithText("Cancel").assertIsDisplayed().performClick()
                waitForIdle()
                assertEquals(bubble.localId, cancelledId)
            }
        }
    }

    @Test
    fun `B-500 offline outbox before and after renders at 360dp`() {
        for (mode in listOf(AppearanceMode.Light, AppearanceMode.Dark)) {
            val theme = mode.name.lowercase()
            for (scale in listOf(1.0f, 1.3f)) {
                val t = "t${(scale * 10).toInt()}"
                renderScene(beforeBubble, mode, scale, "before-360-$theme-$t.png")
                renderScene(
                    afterBubble,
                    mode,
                    scale,
                    "after-360-$theme-$t.png",
                    checkCancel = mode == AppearanceMode.Light && scale == 1.0f,
                )
            }
        }
    }
}
