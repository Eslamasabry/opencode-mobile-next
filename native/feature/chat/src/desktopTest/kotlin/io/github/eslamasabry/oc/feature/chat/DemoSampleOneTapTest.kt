package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import io.github.eslamasabry.oc.ui.gate.flicker.FlickerFailure
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiTest
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
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
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * B-623 (demo-sample-one-tap): drives the real [DemoEntries] route in one scene per test so the collected
 * `vm.state`, `ownReplyKey`, and `stage` flows verify one-tap sample execution, direct phone setup exit,
 * "Connect a computer" beside it, and unchanged B-428 honesty.
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

    private fun runDemoScene(block: suspend KitUiTest.() -> Unit) {
        try {
            kitUiTest(PHONE_WIDTH, PHONE_HEIGHT, block = block)
        } catch (e: FlickerFailure) {
            val detail = e.findings.joinToString(" | ") { "${it.rule} ${it.detail}" }
            throw AssertionError("FLICKER(${e.findings.size}): $detail", e)
        }
    }

    @Test
    fun `B-623 tapping the suggested sample question runs it in one tap without retyping`() {
        val nav = RecordingNavigator()
        val vm = DemoViewModel(nav, dispatchers)
        val store = ViewModelStore()
        try {
            runDemoScene {
                setContent { DemoRouteHost(vm, store) }
                waitForIdle()
                assertTrue(runBlocking { vm.send(OWN_QUESTION) })
                waitForIdle()
                onAllNodesWithText(OWN_HONEST_LINE, substring = true).onFirst().assertIsDisplayed()
                assertEquals(DemoStage.Ready, vm.stage.value)
                assertNull(vm.requests.value.card)
                onNodeWithText(SAMPLE_PROMPT).assertIsDisplayed().performClick()
                waitForIdle()
                assertEquals(DemoStage.Review, vm.stage.value)
                assertNotNull(vm.requests.value.card)
                assertNull(vm.ownReplyKey.value)
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
            runDemoScene {
                setContent { DemoRouteHost(vm, store) }
                waitForIdle()
                assertTrue(runBlocking { vm.send(OWN_QUESTION) })
                waitForIdle()
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
            runDemoScene {
                setContent { DemoRouteHost(vm, store) }
                waitForIdle()
                assertTrue(runBlocking { vm.send(OWN_QUESTION) })
                waitForIdle()
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
            runDemoScene {
                setContent { DemoRouteHost(vm, store) }
                waitForIdle()
                assertTrue(runBlocking { vm.send(OWN_QUESTION) })
                waitForIdle()
                onAllNodesWithText(OWN_QUESTION, substring = true).onFirst().assertIsDisplayed()
                onAllNodesWithText(OWN_HONEST_LINE, substring = true).onFirst().assertIsDisplayed()
                assertEquals(DemoStage.Ready, vm.stage.value)
                assertNull(vm.requests.value.card)
            }
        } finally {
            store.clear()
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
        const val OWN_QUESTION = "Which Excel formula adds up column B?"
        const val OWN_HONEST_LINE = "This demo can’t answer your own questions."
        const val SAMPLE_PROMPT = "Make the welcome message friendlier."
        const val SAMPLE_REPLY_SNIPPET = "welcome.txt"
        const val SET_UP_PHONE = "Set up OpenCode on this phone"
        const val CONNECT_COMPUTER = "Connect a computer"
    }
}
