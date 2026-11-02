package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerViewModel
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import kotlin.test.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ComposerDraftViewModelTest {
    private val base = FakeChatWorld()
    private var repeated = 0
    private val world =
        object : ChatWorld by base {
            override suspend fun sendRepeated(text: String, expectedRevision: Long): Outcome<Unit> {
                repeated++
                return Outcome.Ok(Unit)
            }
        }
    private val worlds =
        object : ChatWorlds {
            override fun open(
                ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef
            ): ChatWorld = world

            override fun release(world: ChatWorld) = Unit
        }

    private fun vm() = BasicComposerViewModel(ComposerTarget("p1", "s1"), worlds, emptySet())

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `B-STATE-101 restoration and failed storage block send without overwriting the draft`() =
        runTest {
            base.draft.value = DraftFacts("saved text", ready = false)
            val vm = vm()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
            assertFalse(vm.send("replacement"))
            assertEquals("saved text", base.draft.value.text)
            base.draft.value =
                DraftFacts("saved text", ready = true, failure = Failure.StorageRefused())
            assertFalse(vm.send("replacement"))
            assertTrue(base.calls.none { it.startsWith("send ") })
        }

    @Test
    fun `B-STATE-100 dispatch normalizes its text while exact draft whitespace is preserved`() =
        runTest {
            val vm = vm()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
            assertTrue(vm.send("  unfinished words\n "))
            assertEquals("  unfinished words\n ", base.draft.value.text)
            assertEquals(listOf("send unfinished words"), base.calls)
        }

    @Test
    fun `B-STATE-102 unknown receipts forbid both normal send and repeat confirmation`() = runTest {
        base.draft.value =
            DraftFacts(
                "possibly sent",
                "possibly sent",
                sendBlocked = true,
                editRevision = 5,
                repeatAllowed = true,
            )
        base.pending.value =
            persistentListOf(PendingSend(1, "possibly sent", PendingState.Unconfirmed))
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertFalse(vm.state.value.canRepeat)
        assertFalse(vm.send("possibly sent"))
        assertFalse(vm.sendAgain("possibly sent", 5))
        assertEquals(0, repeated)
    }

    @Test
    fun `B-STATE-102 retry save acknowledges the existing exact draft without dispatch`() =
        runTest {
            base.draft.value = DraftFacts(" keep me ", failure = Failure.StorageRefused())
            base.saveResult = Outcome.Ok(Unit)
            val vm = vm()
            vm.retrySave()
            assertEquals(" keep me ", base.draft.value.saved)
            assertEquals(listOf("saveDraft"), base.calls)
        }
}
