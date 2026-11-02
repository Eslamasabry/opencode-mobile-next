package io.github.eslamasabry.oc.feature.onboarding

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.onboarding.presentation.FirstRun
import io.github.eslamasabry.oc.feature.onboarding.presentation.FirstRunFlow
import io.github.eslamasabry.oc.feature.onboarding.presentation.FirstRunPreferences
import io.github.eslamasabry.oc.feature.onboarding.presentation.RootChoice
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeViewModel
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.OnboardingRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A back stack with the navigator's rules (Root replaces, land clears to Conversations). */
private class StackNavigator(start: AppRoute = ChatsRoute.Home) : AppNavigator {
    val stack = mutableListOf(start)
    private val mutableTop = MutableStateFlow<AppRoute?>(start)
    override val top: StateFlow<AppRoute?> = mutableTop

    private fun publish() {
        mutableTop.value = stack.lastOrNull()
    }

    override fun open(route: AppRoute) {
        if (stack.lastOrNull() != route) stack += route
        publish()
    }

    override fun openChat(ref: ConversationRef, focusComposer: Boolean) = open(ChatRoute(ref, focusComposer))

    override fun land() = replaceRoot(ChatsRoute.Home)

    override fun replaceRoot(root: AppRoute) {
        stack.clear()
        stack += root
        publish()
    }

    override fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        publish()
    }
}

private class Prefs : FirstRunPreferences {
    val values = mutableMapOf<String, String>()

    override suspend fun read(key: String): String? = values[key]

    override suspend fun write(key: String, value: String) {
        values[key] = value
    }
}

class FirstRunFlowTest {
    private val p1 = ProfileId("p1")

    @Test
    fun `B-NAV-017 a device with nothing saved starts on the welcome and is a new device`() = runTest {
        val nav = StackNavigator()
        val prefs = Prefs()
        val choice = FirstRunFlow(FirstRun(prefs), nav).start(active = null, saved = emptySet())
        assertEquals(RootChoice.Servers(welcome = true), choice)
        assertEquals(listOf<AppRoute>(OnboardingRoute.Welcome), nav.stack)
        assertTrue(FirstRun(prefs).landingPending())
    }

    @Test
    fun `B-NAV-017 a device that already had servers never sees the welcome`() = runTest {
        val nav = StackNavigator()
        val prefs = Prefs()
        FirstRunFlow(FirstRun(prefs), nav).start(active = p1, saved = setOf(p1))
        assertEquals(listOf<AppRoute>(ChatsRoute.Home), nav.stack)
        assertFalse(FirstRun(prefs).landingPending())
        assertEquals("done", prefs.values[FirstRun.STATE_KEY])
    }

    @Test
    fun `A03 welcome, add a server, the first answer lands on Conversations and first run ends`() = runTest {
        val nav = StackNavigator()
        val prefs = Prefs()
        val flow = FirstRunFlow(FirstRun(prefs), nav)
        flow.start(active = null, saved = emptySet())
        WelcomeViewModel(nav).connect()
        assertEquals(ServersRoute.Home, nav.top.value)

        flow.onConnected(nav.top.value)
        assertEquals(listOf<AppRoute>(ChatsRoute.Home), nav.stack)
        assertFalse(FirstRun(prefs).landingPending())
        assertTrue(FirstRun(prefs).notifyAskPending(), "the one notification question is due after landing")

        // Later connections never move anyone again.
        nav.open(SettingsRoute.Home)
        flow.onConnected(nav.top.value)
        assertEquals(SettingsRoute.Home, nav.top.value)
    }

    @Test
    fun `A03 a connection while the person is elsewhere (a new chat already opening) does not yank them`() = runTest {
        val nav = StackNavigator()
        val flow = FirstRunFlow(FirstRun(Prefs()), nav)
        flow.start(active = null, saved = emptySet())
        nav.open(SettingsRoute.Home)
        flow.onConnected(nav.top.value)
        assertEquals(SettingsRoute.Home, nav.top.value)
    }

    @Test
    fun `A03 the welcome's second way opens Settings for language and look`() {
        val nav = StackNavigator(OnboardingRoute.Welcome)
        WelcomeViewModel(nav).openSettings()
        assertEquals(SettingsRoute.Home, nav.top.value)
    }
}
