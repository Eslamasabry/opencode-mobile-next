package io.github.eslamasabry.oc.feature.voice.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingIds
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingKind
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingPage
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingRegistry
import io.github.eslamasabry.oc.engine.settings.teaching.TipController
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Help > Tips: a mini tutorial's Show me opens the page it teaches and runs there, never on the Tips page. */
@OptIn(ExperimentalCoroutinesApi::class)
class TeachingViewModelTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private class Recorder : AppNavigator {
        val opened = mutableListOf<AppRoute>()
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {
            opened += route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private val hub =
        object : VoiceTeachingHub {
            override suspend fun controller(): TipController? = null
        }

    @Test
    fun `Make it yours opens Appearance with its guide running, nothing plays on the Tips page`() {
        val nav = Recorder()
        val vm = TeachingViewModel(hub, nav)
        vm.replay(TeachingIds.MakeItYours)
        assertEquals(listOf<AppRoute>(SettingsRoute.Target("settings-appearance", guide = true)), nav.opened)
        assertNull(vm.state.value.replay, "the Tips page shows no tutorial card of its own")
    }

    @Test
    fun `What's new opens its own page`() {
        val nav = Recorder()
        TeachingViewModel(hub, nav).replay(TeachingIds.WhatsNew)
        assertEquals(listOf<AppRoute>(SettingsRoute.WhatsNew), nav.opened)
    }

    @Test
    fun `a tutorial with no page opens nothing`() {
        val nav = Recorder()
        TeachingViewModel(hub, nav).replay(TeachingIds.VoiceConversation)
        assertTrue(nav.opened.isEmpty())
    }

    @Test
    fun `a tip replays in its row and opens no page`() {
        val nav = Recorder()
        TeachingViewModel(hub, nav).replay(TeachingIds.MessageDetails)
        assertTrue(nav.opened.isEmpty())
    }

    @Test
    fun `every mini tutorial either knows its page or is listed without Show me`() {
        val tours = TeachingRegistry.defaults.tips.filter { it.kind == TeachingKind.Tour }
        // Each page a tutorial names has a route; a tutorial with none is the in-conversation one.
        tours.mapNotNull { it.page }.forEach { teachingRoute(it) }
        assertEquals(setOf(TeachingIds.VoiceConversation), tours.filter { it.page == null }.map { it.id }.toSet())
        assertEquals(setOf(TeachingPage.Appearance, TeachingPage.WhatsNew), tours.mapNotNull { it.page }.toSet())
    }
}
