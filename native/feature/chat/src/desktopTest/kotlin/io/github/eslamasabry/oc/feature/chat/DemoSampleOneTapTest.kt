package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoStage
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoViewModel
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoWords
import io.github.eslamasabry.oc.feature.chat.demo.ui.DemoOwnReply
import io.github.eslamasabry.oc.feature.chat.demo.ui.LocalDemoOwnReply
import io.github.eslamasabry.oc.feature.chat.ui.ChatListWords
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptList
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * B-623 (demo-sample-one-tap): when a person asks their own question in the offline demo, B-428's honest reply stays
 * intact, the suggested sample sentence runs in one tap without retyping, the primary exit names "Set up OpenCode on
 * this phone" and routes directly to phone setup, and "Connect a computer" sits clearly beside it and routes to
 * computer connection.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class DemoSampleOneTapTest {
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

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun preparedDemo(): Pair<RecordingNavigator, DemoViewModel> {
        val nav = RecordingNavigator()
        val vm = DemoViewModel(nav, dispatchers)
        vm.configure(words)
        assertTrue(runBlocking { vm.send(OWN_QUESTION) })
        return nav to vm
    }

    @Test
    fun `B-623 tapping the suggested sample question runs it in one tap without retyping`() {
        val (_, vm) = preparedDemo()
        val initialRows = vm.state.value.rows
        val ownReply = demoOwnReplyOf(vm.ownReplyKey.value, vm.stage.value, words.prompt, vm)
        kitUiTest(PHONE_WIDTH, PHONE_HEIGHT) {
            setContent { DemoTranscriptScene(initialRows, ownReply) }
            waitForIdle()
            onAllNodesWithText(OWN_HONEST_LINE, substring = true).onFirst().assertIsDisplayed()
            assertEquals(DemoStage.Ready, vm.stage.value)
            assertNull(vm.requests.value.card)
            onNodeWithText(SAMPLE_PROMPT).assertIsDisplayed().performClick()
            waitForIdle()
            assertEquals(DemoStage.Review, vm.stage.value)
            assertNotNull(vm.requests.value.card)
            assertNull(vm.ownReplyKey.value)
            val rows = vm.state.value.rows
            assertTrue(rows.any { it is PromptRow && it.text == SAMPLE_PROMPT })
            assertTrue(rows.any { it is BlockRow && SAMPLE_REPLY_SNIPPET in it.text })
        }
        val afterRows = vm.state.value.rows
        val afterReply = demoOwnReplyOf(vm.ownReplyKey.value, vm.stage.value, words.prompt, vm)
        kitUiTest(PHONE_WIDTH, PHONE_HEIGHT) {
            setContent { DemoTranscriptScene(afterRows, afterReply) }
            waitForIdle()
            onAllNodesWithText(SAMPLE_REPLY_SNIPPET, substring = true).onFirst().assertIsDisplayed()
            onNodeWithTag("demo-run-sample").assertDoesNotExist()
        }
    }

    @Test
    fun `B-623 primary exit names Set up OpenCode on this phone and routes directly to phone setup`() {
        val (nav, vm) = preparedDemo()
        val rows = vm.state.value.rows
        val ownReply = demoOwnReplyOf(vm.ownReplyKey.value, vm.stage.value, words.prompt, vm)
        kitUiTest(PHONE_WIDTH, PHONE_HEIGHT) {
            setContent { DemoTranscriptScene(rows, ownReply) }
            waitForIdle()
            onNodeWithText(SET_UP_PHONE).assertIsDisplayed().performClick()
            waitForIdle()
            assertEquals(listOf("back", "open"), nav.calls)
            assertEquals(listOf<AppRoute>(PhoneSetupRoute.Setup), nav.opened)
        }
    }

    @Test
    fun `B-623 Connect a computer sits beside phone setup and routes to computer connection`() {
        val (nav, vm) = preparedDemo()
        val rows = vm.state.value.rows
        val ownReply = demoOwnReplyOf(vm.ownReplyKey.value, vm.stage.value, words.prompt, vm)
        kitUiTest(PHONE_WIDTH, PHONE_HEIGHT) {
            setContent { DemoTranscriptScene(rows, ownReply) }
            waitForIdle()
            onNodeWithText(SET_UP_PHONE).assertIsDisplayed()
            onNodeWithText(CONNECT_COMPUTER).assertIsDisplayed().performClick()
            waitForIdle()
            assertEquals(listOf("back", "open"), nav.calls)
            assertEquals(listOf<AppRoute>(ServersRoute.Edit()), nav.opened)
        }
    }

    @Test
    fun `B-623 B-428 honest reply stays intact and nothing clips at 360 dp at text 1_0 and 1_3`() {
        val (_, vm) = preparedDemo()
        val rows = vm.state.value.rows
        val ownReply = demoOwnReplyOf(vm.ownReplyKey.value, vm.stage.value, words.prompt, vm)
        for (scale in listOf(SCALE_DEFAULT, SCALE_LARGE)) {
            kitUiTest(PHONE_WIDTH, PHONE_HEIGHT) {
                setContent { DemoTranscriptScene(rows, ownReply, scale = scale) }
                waitForIdle()
                onAllNodesWithText(OWN_QUESTION, substring = true).onFirst().assertIsDisplayed()
                onAllNodesWithText(OWN_HONEST_LINE, substring = true).onFirst().assertIsDisplayed()
                assertTrue(boundsOfText(SAMPLE_PROMPT).right <= PHONE_WIDTH.toFloat())
                assertTrue(boundsOfText(SET_UP_PHONE).right <= PHONE_WIDTH.toFloat())
                assertTrue(boundsOfText(CONNECT_COMPUTER).right <= PHONE_WIDTH.toFloat())
                assertEquals(DemoStage.Ready, vm.stage.value)
                assertNull(vm.requests.value.card)
            }
        }
    }

    @Composable
    private fun DemoTranscriptScene(
        rows: ImmutableList<TranscriptRow>,
        ownReply: DemoOwnReply?,
        scale: Float = SCALE_DEFAULT,
    ) {
        val environment = remember { KitChatEnvironment(Redactor()) }
        E20Scene(scale = scale) {
            environment.Provide {
                CompositionLocalProvider(LocalDemoOwnReply provides ownReply) {
                    Box(Modifier.size(PHONE_WIDTH.dp, PHONE_HEIGHT.dp)) {
                        ChatTranscriptList(
                            rows = rows,
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

    private companion object {
        const val PHONE_WIDTH = 360
        const val PHONE_HEIGHT = 780
        const val SCALE_DEFAULT = 1.0f
        const val SCALE_LARGE = 1.3f
        const val OWN_QUESTION = "Which Excel formula adds up column B?"
        const val OWN_HONEST_LINE = "This demo can’t answer your own questions."
        const val SAMPLE_PROMPT = "Make the welcome message friendlier."
        const val SAMPLE_REPLY_SNIPPET = "welcome.txt"
        const val SET_UP_PHONE = "Set up OpenCode on this phone"
        const val CONNECT_COMPUTER = "Connect a computer"
    }
}
