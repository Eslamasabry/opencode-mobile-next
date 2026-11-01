package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllViewModel
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatsViewModelTest {
    private class RecordingNavigator : AppNavigator {
        val calls = mutableListOf<String>()
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)
        override fun open(route: AppRoute) { calls += "open $route" }
        override fun openChat(ref: ConversationRef, focusComposer: Boolean) { calls += "openChat" }
        override fun land() { calls += "land" }
        override fun replaceRoot(root: AppRoute) { calls += "replaceRoot" }
        override fun back() { calls += "back" }
    }

    @Test
    fun `route argument reaches the assisted ViewModel and back goes through the navigator`() {
        val nav = RecordingNavigator()
        val vm = ChatsAllViewModel(ChatsRoute.All("running"), nav)
        assertEquals("running", vm.state.value.filter)
        vm.back()
        assertEquals(listOf("back"), nav.calls)
    }
}
