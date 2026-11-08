@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.setup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.IncompatibleKind
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.setup.presentation.ComputerServer
import io.github.eslamasabry.oc.feature.setup.presentation.ComputerUpdateCommands
import io.github.eslamasabry.oc.feature.setup.presentation.ComputerUpdateSource
import io.github.eslamasabry.oc.feature.setup.presentation.ComputerUpdateViewModel
import io.github.eslamasabry.oc.feature.setup.presentation.RecheckAnswer
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.SetupRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** "Update this server" for a computer on OpenCode 1 (connection_failure.dart's OpenCode 1 case) and its Check again. */
class ComputerUpdateTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private val laptop = ProfileId("laptop")

    private class Nav : AppNavigator {
        val calls = mutableListOf<String>()
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {
            calls += "open $route"
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            calls += "chat"
        }

        override fun land() {
            calls += "land"
        }

        override fun replaceRoot(root: AppRoute) {
            calls += "root $root"
        }

        override fun back() {
            calls += "back"
        }
    }

    private class Source(
        var saved: ComputerServer? = ComputerServer("Laptop", 4096),
        val answers: ArrayDeque<Outcome<Unit>> = ArrayDeque(),
    ) : ComputerUpdateSource {
        var rechecks = 0
        var gate: CompletableDeferred<Unit>? = null
        var guides = 0

        override suspend fun server(profile: ProfileId): ComputerServer? = saved

        override suspend fun recheck(profile: ProfileId): Outcome<Unit> {
            rechecks++
            gate?.await()
            return answers.removeFirstOrNull() ?: Outcome.Ok(Unit)
        }

        override fun openGuide() {
            guides++
        }
    }

    private fun model(source: Source, nav: Nav = Nav()) =
        ComputerUpdateViewModel(SetupRoute.UpdateComputer(laptop), source, nav)

    @Test
    fun `B-PROTO-095 the guide names the server and gives the pinned update command for its port`() = runTest {
        val vm = model(Source(saved = ComputerServer("Laptop", 4097)))
        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals("Laptop", state.server)
        assertEquals(ComputerUpdateCommands.upgrade(4097), state.command)
        assertTrue(state.command.startsWith("npm uninstall -g opencode-ai &&\n"))
        assertTrue("--port 4097" in state.command)
    }

    @Test
    fun `B-PROTO-095 Check again on a server still on OpenCode 1 says so in plain words and stays`() = runTest {
        val nav = Nav()
        val source =
            Source(answers = ArrayDeque(listOf(Outcome.Failed(Failure.Incompatible(IncompatibleKind.LegacyOpenCode1)))))
        val vm = model(source, nav)
        vm.recheck()
        assertEquals(RecheckAnswer.StillOpenCode1, vm.state.value.answer)
        assertFalse(vm.state.value.checking)
        assertTrue(nav.calls.isEmpty())
    }

    @Test
    fun `B-PROTO-095 Check again after the update connects and lands on the conversations`() = runTest {
        val nav = Nav()
        val vm = model(Source(), nav)
        vm.recheck()
        assertEquals(listOf("land"), nav.calls)
        assertNull(vm.state.value.answer)
    }

    @Test
    fun `B-PROTO-095 a new password or no answer is named plainly, never as the raw error`() = runTest {
        val source =
            Source(
                answers =
                    ArrayDeque(
                        listOf(
                            Outcome.Failed(Failure.SignIn(SignInWhy.PasswordRejected)),
                            Outcome.Failed(Failure.Network(NetworkWhy.Refused)),
                            Outcome.Failed(Failure.Server(500)),
                        )
                    )
            )
        val vm = model(source)
        vm.recheck()
        assertEquals(RecheckAnswer.PasswordRejected, vm.state.value.answer)
        vm.recheck()
        assertEquals(RecheckAnswer.NoAnswer, vm.state.value.answer)
        vm.recheck()
        assertEquals(RecheckAnswer.Other, vm.state.value.answer)
    }

    @Test
    fun `B-PROTO-095 Check again runs once however often it is tapped`() = runTest {
        val source = Source().apply { gate = CompletableDeferred() }
        val vm = model(source)
        vm.recheck()
        vm.recheck()
        assertTrue(vm.state.value.checking)
        assertEquals(1, source.rechecks)
        source.gate!!.complete(Unit)
        assertFalse(vm.state.value.checking)
    }

    @Test
    fun `B-PROTO-095 a removed server shows the missing state and Change server opens the list`() = runTest {
        val nav = Nav()
        val vm = model(Source(saved = null), nav)
        assertTrue(vm.state.value.missing)
        vm.recheck()
        vm.changeServer()
        assertEquals(listOf("open ${ServersRoute.Home}"), nav.calls)
    }
}
