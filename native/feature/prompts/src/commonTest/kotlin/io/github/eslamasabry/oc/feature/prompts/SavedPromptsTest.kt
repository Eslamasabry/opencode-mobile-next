package io.github.eslamasabry.oc.feature.prompts

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.stash.PromptShelf
import io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs
import io.github.eslamasabry.oc.engine.composer.store.ComposerProfiles
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import io.github.eslamasabry.oc.engine.composer.store.PrefWrite
import io.github.eslamasabry.oc.engine.composer.store.PrefsEdit
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsIssue
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsNote
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsRules
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsViewModel
import io.github.eslamasabry.oc.feature.prompts.presentation.ShelfSavedPrompts
import io.github.eslamasabry.oc.ui.kitchat.composer.ComposerInbox
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.PromptsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Throws (process death) on the first commit that writes a saved prompt record. */
internal class DyingPrefs(private val inner: ComposerPrefs) : ComposerPrefs by inner {
    override suspend fun commit(edit: PrefsEdit): Boolean {
        if (edit.entries.any { (key, write) -> key.startsWith("oc.promptStash.") && write is PrefWrite.Str }) {
            throw CancellationException("process died")
        }
        return inner.commit(edit)
    }
}

internal class StepClock(var millis: Long = 1_760_000_000_000) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis).also { millis += 1_000 }
}

internal class Profiles(vararg ids: String) : ComposerProfiles {
    val ids: MutableSet<String> = ids.toMutableSet()

    override fun exists(profileId: String): Boolean = profileId in ids

    override fun count(): Int = ids.size
}

/** A back stack: Chat under the prompts pages. */
internal class StackNavigator(vararg initial: AppRoute) : AppNavigator {
    val stack = initial.toMutableList()
    override val top = MutableStateFlow(stack.lastOrNull())

    override fun open(route: AppRoute) {
        stack += route
        top.value = route
    }

    override fun openChat(ref: ConversationRef, focusComposer: Boolean) = open(ChatRoute(ref, focusComposer))

    override fun land() = Unit

    override fun replaceRoot(root: AppRoute) {
        stack.clear()
        open(root)
    }

    override fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        top.value = stack.lastOrNull()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SavedPromptsTest {
    private val ref = ConversationRef(ProfileId("p1"), SessionId("s1"))
    private val target = ComposerInbox.target("p1", "s1")
    private val prefs = MemoryComposerPrefs()
    private val payloads = MemoryPayloadStore()
    private val profiles = Profiles("p1", "p2")
    private val clock = StepClock()

    /** A fresh shelf over the same stored data = an app restart. */
    private fun store() =
        ShelfSavedPrompts(PromptShelf(prefs, AttachmentVault(payloads), profiles, Redactor()), { Unit.ok() }, clock)

    private fun vm(
        store: ShelfSavedPrompts = store(),
        inbox: ComposerInbox = ComposerInbox(),
        nav: StackNavigator = StackNavigator(ChatRoute(ref), PromptsRoute.Saved(ref)),
    ) = SavedPromptsViewModel(PromptsRoute.Saved(ref), store, inbox, nav)

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `B-STATE-140 a saved prompt survives a restart and profile deletion removes it`() = runTest {
        val first = vm()
        first.newPrompt()
        first.submitEdit("Review the open pull request")
        assertEquals(SavedPromptsNote.Saved, first.state.value.note)
        // Restart: a new shelf and ViewModel over the stored data.
        val again = vm()
        assertEquals(listOf("Review the open pull request"), again.state.value.prompts.map { it.text })
        // Another server never sees it.
        val other = store().list("p2")
        assertEquals(emptyList(), (other as Outcome.Ok).value)
        // Profile deletion: the shelf clears every record; a reopened page reads empty.
        assertTrue(PromptShelf(prefs, AttachmentVault(payloads), profiles, Redactor()).clearForProfile("p1"))
        assertTrue(prefs.keys().none { it.startsWith("oc.promptStash.p1.") })
        assertTrue(vm().state.value.prompts.isEmpty())
    }

    @Test
    fun `B-STATE-085 delete is durable at once and Undo restores the exact prompt`() = runTest {
        val seed = store()
        seed.save("p1", "first")
        seed.save("p1", "second")
        val vm = vm()
        val before = vm.state.value.prompts.first { it.text == "first" }
        vm.delete(before.id)
        assertTrue(vm.state.value.prompts.none { it.text == "first" })
        assertTrue(vm.state.value.undo != null)
        // Durable: a restart does not bring it back.
        assertTrue((store().list("p1") as Outcome.Ok).value.none { it.text == "first" })
        vm.undo()
        assertNull(vm.state.value.undo)
        val restored = vm.state.value.prompts.first { it.text == "first" }
        assertEquals(before.createdAt, restored.createdAt)
        assertEquals(before.id, restored.id)
    }

    @Test
    fun `B-STATE-085 restoring into the composer keeps the saved prompt and targets this conversation only`() =
        runTest {
            store().save("p1", "Explain the failing test")
            val inbox = ComposerInbox()
            val nav = StackNavigator(ChatRoute(ref), PromptsRoute.Saved(ref))
            val vm = vm(inbox = inbox, nav = nav)
            vm.insert(vm.state.value.prompts.single().id)
            assertEquals("Explain the failing test", inbox.pendingFor(target)?.text)
            assertNull(inbox.pendingFor(ComposerInbox.target("p1", "other")))
            assertIs<ChatRoute>(nav.top.value)
            assertEquals(1, (store().list("p1") as Outcome.Ok).value.size)
        }

    @Test
    fun `B-CHAT-088 fifty saved prompts never evict one and a new one is refused plainly`() = runTest {
        val seed = store()
        repeat(50) { seed.save("p1", "prompt $it") }
        val vm = vm()
        assertEquals(50, vm.state.value.prompts.size)
        vm.newPrompt()
        assertEquals(SavedPromptsIssue.Full, vm.state.value.issue)
        assertNull(vm.state.value.editor)
        assertTrue(seed.save("p1", "one more") is Outcome.Failed)
        assertEquals(50, (store().list("p1") as Outcome.Ok).value.size)
    }

    @Test
    fun `B-STATE-135 a failed edit never removes the original prompt`() = runTest {
        store().save("p1", "original words")
        val vm = vm()
        val id = vm.state.value.prompts.single().id
        vm.editPrompt(id)
        assertEquals("original words", vm.state.value.editor?.initial)
        prefs.refuse = true
        vm.submitEdit("new words")
        prefs.refuse = false
        assertEquals(SavedPromptsIssue.SaveFailed, vm.state.value.issue)
        assertEquals(listOf("original words"), (store().list("p1") as Outcome.Ok).value.map { it.text })
    }

    @Test
    fun `B-STATE-135 an edit interrupted before the new prompt is saved keeps the original`() = runTest {
        store().save("p1", "original words")
        val id = ((store().list("p1") as Outcome.Ok).value.single()).id
        // The process dies on the write that saves the replacement.
        val dying = DyingPrefs(prefs)
        val shelf = ShelfSavedPrompts(PromptShelf(dying, AttachmentVault(payloads), profiles, Redactor()), { Unit.ok() }, clock)
        assertFailsWith<CancellationException> { shelf.edit("p1", id, "new words") }
        assertEquals(listOf("original words"), (store().list("p1") as Outcome.Ok).value.map { it.text })
    }

    @Test
    fun `B-STATE-085 an edit keeps the prompt's date and place in the list`() = runTest {
        val seed = store()
        seed.save("p1", "older")
        seed.save("p1", "newer")
        val vm = vm()
        val older = vm.state.value.prompts.first { it.text == "older" }
        vm.editPrompt(older.id)
        vm.submitEdit("older, edited")
        assertEquals(SavedPromptsNote.Edited, vm.state.value.note)
        val after = vm.state.value.prompts
        assertEquals(listOf("newer", "older, edited"), after.map { it.text })
        assertEquals(older.createdAt, after.last().createdAt)
    }

    @Test
    fun `B-STATE-141 a removed server's shelf refuses new prompts`() = runTest {
        val vm = vm()
        profiles.ids -= "p1"
        vm.newPrompt()
        vm.submitEdit("late")
        assertEquals(SavedPromptsIssue.SaveFailed, vm.state.value.issue)
        assertTrue(prefs.keys().none { it.startsWith("oc.promptStash.p1.") })
    }

    @Test
    fun `B-STATE-140 an unreadable shelf is named and never shown as empty`() = runTest {
        prefs.commit(io.github.eslamasabry.oc.engine.composer.store.PrefsEdit().putString("oc.promptStash.p1.x", "{not json"))
        val vm = vm()
        assertEquals(SavedPromptsIssue.ReadFailed, vm.state.value.issue)
    }

    @Test
    fun `search keeps matching prompts newest first and insertion appends after typed words`() {
        val items =
            listOf(
                io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptItem("a", "Fix the build", 1),
                io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptItem("b", "fix lint", 2),
                io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptItem("c", "Write docs", 3),
            )
        assertEquals(listOf("b", "a"), SavedPromptsRules.filter(items, " FIX ").map { it.id })
        assertEquals("saved", SavedPromptsRules.merged("  ", "saved"))
        assertEquals("typed\nsaved", SavedPromptsRules.merged("typed  ", "saved"))
    }
}
