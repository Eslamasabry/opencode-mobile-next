package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.composer.drafts.DraftAutosave
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DraftAutosaveTest {
    private val prefs = MemoryComposerPrefs()
    private val gated = GatedPrefs(prefs)
    private val drafts =
        Drafts(gated, AttachmentVault(MemoryPayloadStore()), TestProfiles("alpha"), TestClock())

    @Test
    fun `B-STATE-100 compe-2 debounce acknowledges a suspending save without cancelling itself`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            gated.gate = gate
            val autosave = DraftAutosave(this, drafts, "alpha", "s1")
            autosave.changed("pending words")
            advanceTimeBy(600)
            runCurrent()
            assertFalse(autosave.settled.value)
            gate.complete(Unit)
            runCurrent()
            assertEquals("pending words", drafts.draft("alpha", "s1")?.text)
            assertTrue(autosave.settled.value)
        }

    @Test
    fun `B-STATE-102 compe-2 leaving waits for an active save and acknowledges its snapshot`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            gated.gate = gate
            val autosave = DraftAutosave(this, drafts, "alpha", "s1")
            autosave.changed("pending words")
            advanceTimeBy(600)
            runCurrent()
            val leaving = async(start = CoroutineStart.UNDISPATCHED) { autosave.flush() }
            assertFalse(
                leaving.isCompleted,
                "leaving must not acknowledge words before the write lands",
            )
            gate.complete(Unit)
            assertIs<Outcome.Ok<Unit>>(leaving.await())
            assertEquals("pending words", drafts.draft("alpha", "s1")?.text)
            assertTrue(autosave.settled.value)
        }

    @Test
    fun `B-STATE-102 compe-2 cancelled flush keeps its snapshot available for retry`() = runTest {
        val gate = CompletableDeferred<Unit>()
        gated.gate = gate
        val autosave = DraftAutosave(this, drafts, "alpha", "s1")
        autosave.changed("pending words")
        val saving = async(start = CoroutineStart.UNDISPATCHED) { autosave.flush() }
        saving.cancelAndJoin()
        assertFalse(autosave.settled.value)
        gate.complete(Unit)
        assertIs<Outcome.Ok<Unit>>(autosave.flush())
        assertEquals("pending words", drafts.draft("alpha", "s1")?.text)
        assertTrue(autosave.settled.value)
    }

    @Test
    fun `B-STATE-102 compe-2 a newer snapshot remains pending while its predecessor saves`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            gated.gate = gate
            val autosave = DraftAutosave(this, drafts, "alpha", "s1")
            autosave.changed("first words")
            val first = async(start = CoroutineStart.UNDISPATCHED) { autosave.flush() }
            autosave.changed("newer words")
            gate.complete(Unit)
            assertIs<Outcome.Ok<Unit>>(first.await())
            assertFalse(autosave.settled.value)
            assertIs<Outcome.Ok<Unit>>(autosave.flush())
            assertEquals("newer words", drafts.draft("alpha", "s1")?.text)
            assertTrue(autosave.settled.value)
        }
}
