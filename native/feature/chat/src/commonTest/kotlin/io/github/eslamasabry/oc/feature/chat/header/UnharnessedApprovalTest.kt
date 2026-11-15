package io.github.eslamasabry.oc.feature.chat.header

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.feature.chat.Immediate
import io.github.eslamasabry.oc.feature.chat.Ref
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChoice
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalPresenter
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class UnharnessedApprovalTest {
    @Test
    fun `B-AGENT-UH-020 never asks shows its warning and offers no ineffective approval control`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = MemoryApprovalStore()
            val port = FakeApprovalPort()
            val never = MutableStateFlow(true)
            val presenter =
                ApprovalPresenter(
                    Ref,
                    store,
                    MutableStateFlow(ServerCapabilities(permissionRequests = true)),
                    port,
                    Immediate,
                    TestClock(),
                    never,
                )
            presenter.states(backgroundScope, SharingStarted.Eagerly)
            assertTrue(presenter.current().neverAsks)
            assertFalse(presenter.current().offered)
            presenter.openApprovalMenu()
            presenter.chooseApproval(ApprovalChoice.Everything)
            assertFalse(presenter.current().menuOpen)
            assertFalse(presenter.current().confirmEverything)
            assertTrue(store.raw.isEmpty())
            port.waiting.value = listOf(ask("fixture-request"))
            assertTrue(port.allowed.isEmpty())
            never.value = false
            assertTrue(presenter.current().offered)
            assertFalse(presenter.current().neverAsks)
        }
}
