package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoViewModel
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoWords
import io.github.eslamasabry.oc.feature.chat.demo.ui.LocalDemoOwnReply
import io.github.eslamasabry.oc.feature.chat.ui.ChatListWords
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptList
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * B-623 before/after 360 dp pictures (dark & light, text 1.0 & 1.3) written into `build/renders/`:
 * BEFORE shows plain B-428 stateDemoOwn prose with no buttons; AFTER shows the same honest reply
 * with the one-tap sample button, "Set up OpenCode on this phone", and "Connect a computer".
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class DemoOwnReplyPicturesTest {
    private val out: File = File("build/renders").also(File::mkdirs)

    private val dispatchers =
        object : AppDispatchers {
            override val main = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined

            override fun serial(name: String) = Dispatchers.Unconfined
        }

    private val words =
        DemoWords(
            title = "Offline demo",
            prompt = SAMPLE_PROMPT,
            reply = "I’ll change the greeting in welcome.txt from “Hello” to “Welcome aboard!”.",
            allowed = "Done. welcome.txt now says “Welcome aboard!”.",
            denied = "Okay, I left welcome.txt as it is.",
            before = "Hello",
            after = "Welcome aboard!",
            more = "That’s the end of the demo. Connect a server to work on your own projects.",
            model = "Sample model",
            own =
                "This demo can’t answer your own questions. It plays one sample: " +
                    "send “$SAMPLE_PROMPT” to see it. To ask your own, leave the demo and set it up for real.",
        )

    private object TranscriptWords : ChatTranscriptWords {
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

    private data class Shot(val before: Boolean, val dark: Boolean, val scale: Float, val name: String)

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `B-623 captures before and after 360 dp dark and light pictures at text 1_0 and 1_3`() {
        val shots =
            listOf(
                Shot(before = true, dark = true, scale = SCALE_DEFAULT, name = "before-dark-t10"),
                Shot(before = false, dark = true, scale = SCALE_DEFAULT, name = "after-dark-t10"),
                Shot(before = true, dark = true, scale = SCALE_LARGE, name = "before-dark-t13"),
                Shot(before = false, dark = true, scale = SCALE_LARGE, name = "after-dark-t13"),
                Shot(before = true, dark = false, scale = SCALE_DEFAULT, name = "before-light-t10"),
                Shot(before = false, dark = false, scale = SCALE_DEFAULT, name = "after-light-t10"),
                Shot(before = true, dark = false, scale = SCALE_LARGE, name = "before-light-t13"),
                Shot(before = false, dark = false, scale = SCALE_LARGE, name = "after-light-t13"),
            )
        shots.forEach(::capture)
    }

    private fun capture(shot: Shot) {
        val vm = DemoViewModel(RecordingNavigator(), dispatchers)
        vm.configure(words)
        assertTrue(runBlocking { vm.send(OWN_QUESTION) })
        kitUiTest(WIDTH_PX, HEIGHT_PX) {
            setContent { PictureScene(vm, shot.before, shot.dark, shot.scale) }
            waitForIdle()
            onAllNodesWithText(OWN_QUESTION, substring = true).onFirst().assertIsDisplayed()
            onAllNodesWithText(OWN_HONEST_LINE, substring = true).onFirst().assertIsDisplayed()
            if (shot.before) {
                onNodeWithTag("demo-own-reply-actions").assertDoesNotExist()
            } else {
                onNodeWithText(SAMPLE_PROMPT).assertIsDisplayed()
                onNodeWithText(SET_UP_PHONE).assertIsDisplayed()
                onNodeWithText(CONNECT_COMPUTER).assertIsDisplayed()
            }
            val image = onRoot().captureToImage().toAwtImage()
            val file = File(out, "${shot.name}.png")
            ImageIO.write(image, "png", file)
            assertEquals(WIDTH_PX, image.width)
            assertTrue(file.length() > 0L, "expected non-empty render ${file.name}")
        }
    }

    @Composable
    private fun PictureScene(
        vm: DemoViewModel,
        before: Boolean,
        dark: Boolean,
        scale: Float,
    ) {
        val state by vm.state.collectAsState()
        val stage by vm.stage.collectAsState()
        val ownReplyKey by vm.ownReplyKey.collectAsState()
        val ownReply = if (before) null else demoOwnReplyOf(ownReplyKey, stage, words.prompt, vm)
        val environment = remember { KitChatEnvironment(Redactor()) }
        val mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light
        CompositionLocalProvider(LocalDensity provides Density(DENSITY, scale)) {
            KitTheme(
                appearance = KitAppearance(mode = mode, style = Style.Solid, motion = MotionLevel.Off),
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                CompositionLocalProvider(
                    LocalKitRedactor provides Redactor(),
                    LocalDemoOwnReply provides ownReply,
                ) {
                    environment.Provide {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                            ChatTranscriptList(
                                rows = state.rows,
                                live = null,
                                pending = persistentListOf(),
                                words = TranscriptWords,
                                listWords = listWords,
                                list = rememberTranscriptListState(),
                                reveal = rememberRevealRegistry(),
                                motion = KitMotionLevel.Off,
                                onOpenMenu = null,
                                onRetrySend = {},
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val DENSITY = 2.625f
        const val WIDTH_PX = 945
        const val HEIGHT_PX = 2100
        const val SCALE_DEFAULT = 1.0f
        const val SCALE_LARGE = 1.3f
        const val OWN_QUESTION = "Which Excel formula adds up column B?"
        const val OWN_HONEST_LINE = "This demo can’t answer your own questions."
        const val SAMPLE_PROMPT = "Make the welcome message friendlier."
        const val SET_UP_PHONE = "Set up OpenCode on this phone"
        const val CONNECT_COMPUTER = "Connect a computer"
    }
}
