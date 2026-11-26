package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoStage
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoViewModel
import io.github.eslamasabry.oc.feature.chat.demo.ui.LocalDemoOwnReply
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptList
import io.github.eslamasabry.oc.feature.chat.ui.rememberChatWords
import io.github.eslamasabry.oc.ui.gate.flicker.FlickerMode
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiOptions
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiTest
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.DemoRoute
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * B-623 (demo-sample-one-tap): drives the real [DemoEntries] route in one scene per test with real composer
 * typing/send and real motion so one-tap sample execution, direct phone setup exit, "Connect a computer" beside it,
 * and unchanged B-428 honesty are proven end-to-end with zero flicker findings.
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

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun KitUiTest.sendOwnQuestion() {
        onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.SetText) {
            it(AnnotatedString(OWN_QUESTION))
        }
        // Allow realistic typing duration so replacing the prefilled sample draft before sending is not an 80 ms ABA.
        mainClock.advanceTimeBy(TYPING_MS)
        waitForIdle()
        onNodeWithTag("chat-send-button").performClick()
        waitForIdle()
    }

    @Test
    fun `B-623 tapping the suggested sample question runs it in one tap without retyping`() {
        val nav = RecordingNavigator()
        val vm = DemoViewModel(nav, dispatchers)
        val store = ViewModelStore()
        try {
            // Window 1: verify 0 flicker on initial mount, off-script question, and one-tap button arrival.
            kitUiTest(PHONE_WIDTH, PHONE_HEIGHT) {
                setContent { DemoRouteHost(vm, store) }
                waitForIdle()
                sendOwnQuestion()
                onAllNodesWithText(OWN_HONEST_LINE, substring = true).onFirst().assertIsDisplayed()
                assertEquals(DemoStage.Ready, vm.ui.value.stage)
                assertNull(vm.ui.value.requests.card)
                onNodeWithText(SAMPLE_PROMPT).assertIsDisplayed()
            }
            // Ruling #843: narrow observation around the intentional 1-frame tap-append under MotionLevel.Off.
            kitUiTest(PHONE_WIDTH, PHONE_HEIGHT, options = KitUiOptions(mode = FlickerMode.Off)) {
                setContent { DemoRouteHost(vm, store) }
                waitForIdle()
                onNodeWithTag("demo-run-sample").assertIsDisplayed().performClick()
                waitForIdle()
                assertEquals(DemoStage.Review, vm.ui.value.stage)
                assertNotNull(vm.ui.value.requests.card)
                assertNull(vm.ui.value.ownReplyKey)
                onAllNodesWithText(SAMPLE_REPLY_SNIPPET, substring = true).onFirst().assertIsDisplayed()
                onNodeWithTag("demo-run-sample").assertDoesNotExist()
            }
            // Window 2: verify 0 flicker after the sample reply settles.
            kitUiTest(PHONE_WIDTH, PHONE_HEIGHT) {
                setContent { SettledDemoTranscript(vm) }
                waitForIdle()
                onAllNodesWithText(SAMPLE_REPLY_SNIPPET, substring = true).onFirst().assertIsDisplayed()
                onNodeWithTag("demo-run-sample").assertDoesNotExist()
            }
        } finally {
            store.clear()
        }
    }

    @Test
    fun `B-623 primary exit names Set up OpenCode on this phone and routes directly to phone setup`() {
        val nav = RecordingNavigator()
        val vm = DemoViewModel(nav, dispatchers)
        val store = ViewModelStore()
        try {
            kitUiTest(PHONE_WIDTH, PHONE_HEIGHT) {
                setContent { DemoRouteHost(vm, store) }
                waitForIdle()
                sendOwnQuestion()
                onNodeWithText(SET_UP_PHONE).assertIsDisplayed().performClick()
                waitForIdle()
                assertEquals(listOf("back", "open"), nav.calls)
                assertEquals(listOf<AppRoute>(PhoneSetupRoute.Setup), nav.opened)
            }
        } finally {
            store.clear()
        }
    }

    @Test
    fun `B-623 Connect a computer sits beside phone setup and routes to computer connection`() {
        val nav = RecordingNavigator()
        val vm = DemoViewModel(nav, dispatchers)
        val store = ViewModelStore()
        try {
            kitUiTest(PHONE_WIDTH, PHONE_HEIGHT) {
                setContent { DemoRouteHost(vm, store) }
                waitForIdle()
                sendOwnQuestion()
                onNodeWithText(SET_UP_PHONE).assertIsDisplayed()
                onNodeWithText(CONNECT_COMPUTER).assertIsDisplayed().performClick()
                waitForIdle()
                assertEquals(listOf("back", "open"), nav.calls)
                assertEquals(listOf<AppRoute>(ServersRoute.Edit()), nav.opened)
            }
        } finally {
            store.clear()
        }
    }

    @Test
    fun `B-623 B-428 honest reply stays intact when asking an off-script question`() {
        val nav = RecordingNavigator()
        val vm = DemoViewModel(nav, dispatchers)
        val store = ViewModelStore()
        try {
            kitUiTest(PHONE_WIDTH, PHONE_HEIGHT) {
                setContent { DemoRouteHost(vm, store) }
                waitForIdle()
                sendOwnQuestion()
                onAllNodesWithText(OWN_QUESTION, substring = true).onFirst().assertIsDisplayed()
                onAllNodesWithText(OWN_HONEST_LINE, substring = true).onFirst().assertIsDisplayed()
                assertEquals(DemoStage.Ready, vm.ui.value.stage)
                assertNull(vm.ui.value.requests.card)
            }
        } finally {
            store.clear()
        }
    }

    @Composable
    private fun SettledDemoTranscript(vm: DemoViewModel) {
        val environment = remember { KitChatEnvironment(Redactor()) }
        val lifecycle = remember { ResumedLifecycleOwner() }
        E20Scene {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {
                val words = demoWords()
                val ui by vm.ui.collectAsStateWithLifecycle()
                val ownReply =
                    remember(ui.ownReplyKey, ui.stage, words.prompt, vm) {
                        demoOwnReplyOf(ui.ownReplyKey, ui.stage, words.prompt, vm)
                    }
                environment.Provide {
                    CompositionLocalProvider(LocalDemoOwnReply provides ownReply) {
                        ChatTranscriptList(
                            rows = ui.chat.rows,
                            live = ui.chat.live,
                            pending = ui.chat.pending,
                            words = rememberChatWords(),
                            listWords = null,
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

    @Composable
    private fun DemoRouteHost(vm: DemoViewModel, store: ViewModelStore) {
        val environment = remember { KitChatEnvironment(Redactor()) }
        val owner = remember(store) { FixedViewModelStoreOwner(store) }
        val lifecycle = remember { ResumedLifecycleOwner() }
        val factory = remember(vm) { SingleDemoVmFactory(vm) }
        val provider =
            remember(environment) {
                entryProvider<NavKey> { with(DemoEntries(environment)) { install() } }
            }
        E20Scene {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides owner,
                LocalLifecycleOwner provides lifecycle,
                LocalMetroViewModelFactory provides factory,
            ) {
                provider(DemoRoute).Content()
            }
        }
    }

    private class FixedViewModelStoreOwner(override val viewModelStore: ViewModelStore) :
        ViewModelStoreOwner

    private class ResumedLifecycleOwner : LifecycleOwner {
        private val registry =
            LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }

        override val lifecycle: Lifecycle
            get() = registry
    }

    private class SingleDemoVmFactory(private val vm: DemoViewModel) : MetroViewModelFactory() {
        override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel> =
            mapOf(DemoViewModel::class to { vm })
        override val assistedFactoryProviders:
            Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory> = emptyMap()
        override val manualAssistedFactoryProviders:
            Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory> =
            emptyMap()
    }

    private companion object {
        const val PHONE_WIDTH = 360
        const val PHONE_HEIGHT = 780
        const val TYPING_MS = 1_100L
        const val OWN_QUESTION = "Which Excel formula adds up column B?"
        const val OWN_HONEST_LINE = "This demo can’t answer your own questions."
        const val SAMPLE_PROMPT = "Make the welcome message friendlier."
        const val SAMPLE_REPLY_SNIPPET = "welcome.txt"
        const val SET_UP_PHONE = "Set up OpenCode on this phone"
        const val CONNECT_COMPUTER = "Connect a computer"
    }
}
