package io.github.eslamasabry.oc.feature.chat

import app.cash.turbine.test
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.platform.PromptFilePicker
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerViewModel
import io.github.eslamasabry.oc.feature.chat.composer.presentation.SendGate
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Behavior rows for the composer ViewModel's file picking and model choice (full-composer 2026-11-02). */
class ComposerFilesViewModelTest {
    private val mini = ComposerModel("prov", "mini", "Mini", supportsAttachments = true)

    private fun file(name: String) = PromptAttachment("text/plain", name, "data:text/plain;base64,eA==")

    /** The scripted world: only the frozen model/file members matter here. */
    private class FilesWorld : FakeChatWorld() {
        override val models = MutableStateFlow(ComposerModels())
        var selectResult: Outcome<Unit> = Outcome.Ok(Unit)
        var selectGate: CompletableDeferred<Unit>? = null
        var replaceResult: Outcome<Unit> = Outcome.Ok(Unit)
        var replaceGate: CompletableDeferred<Unit>? = null
        override var draftGeneration: Long = 0

        override suspend fun loadModels(): Outcome<Unit> {
            calls += "loadModels"
            return Outcome.Ok(Unit)
        }

        override suspend fun selectModel(choice: ComposerModel?, variant: String): Outcome<Unit> {
            calls += "select <${choice?.label}>"
            selectGate?.await()
            return selectResult
        }

        override suspend fun replaceAttachments(files: List<PromptAttachment>): Outcome<Unit> {
            calls += "replace ${files.map { it.filename }}"
            replaceGate?.await()
            if (replaceResult is Outcome.Ok) draft.update { it.copy(attachments = files) }
            return replaceResult
        }
    }

    private class Worlds(private val world: ChatWorld) : ChatWorlds {
        override fun open(ref: ConversationRef): ChatWorld = world

        override fun release(world: ChatWorld) {}
    }

    @BeforeTest
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `B-STATE-103 a cancelled pick changes nothing`() = runTest {
        val world = FilesWorld()
        val vm = BasicComposerViewModel(
            ComposerTarget("p1", "s1"),
            Worlds(world),
            setOf(PromptFilePicker { Outcome.Ok(null) }),
        )
        vm.state.test {
            awaitItem()
            vm.attachFile()
            assertNull((awaitItem()).attachmentFailure)
            assertTrue(world.calls.none { it.startsWith("replace") }, "a cancelled pick must not write")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B-STATE-103 a picked file appends to the latest draft files, not the picker snapshot`() =
        runTest {
            val world = FilesWorld()
            val vm = BasicComposerViewModel(
                ComposerTarget("p1", "s1"),
                Worlds(world),
                setOf(
                    PromptFilePicker { current ->
                        assertEquals(0, current.size, "the picker sees the files at pick time")
                        world.draft.update { it.copy(attachments = listOf(file("during.txt"))) }
                        Outcome.Ok(file("picked.txt"))
                    }
                ),
            )
            vm.state.test {
                awaitItem()
                vm.attachFile()
                val settled = awaitUntil { !it.pickingFile && world.calls.any { c -> c.startsWith("replace") } }
                assertNull(settled.attachmentFailure)
                assertFalse(settled.pickingFile)
                assertEquals(listOf("replace [during.txt, picked.txt]"), world.calls.filter { it.startsWith("replace") })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `B-STATE-103 a refused commit surfaces as an attachment failure the person can dismiss`() =
        runTest {
            val world =
                FilesWorld().apply { replaceResult = Outcome.Failed(Failure.StorageRefused()) }
            val vm = BasicComposerViewModel(
                ComposerTarget("p1", "s1"),
                Worlds(world),
                setOf(PromptFilePicker { Outcome.Ok(file("picked.txt")) }),
            )
            vm.state.test {
                awaitItem()
                vm.attachFile()
                val failed = awaitUntil { it.attachmentFailure != null }
                assertTrue(failed.attachmentFailure is Failure.StorageRefused)
                vm.dismissAttachmentFailure()
                assertNull(awaitUntil { it.attachmentFailure == null }.attachmentFailure)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `B-STATE-103 a picker failure surfaces without touching the draft`() = runTest {
        val world = FilesWorld()
        val vm = BasicComposerViewModel(
            ComposerTarget("p1", "s1"),
            Worlds(world),
            setOf(
                PromptFilePicker {
                    Outcome.Failed(Failure.Media(MediaProblem.AttachmentUnsupported))
                }
            ),
        )
        vm.state.test {
            awaitItem()
            vm.attachFile()
            val failed = awaitUntil { it.attachmentFailure != null }
            assertTrue(failed.attachmentFailure is Failure.Media)
            assertTrue(world.calls.none { it.startsWith("replace") })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B-STATE-103 an unsettled draft fails closed with the picked file`() = runTest {
        val world = FilesWorld()
        val vm = BasicComposerViewModel(
            ComposerTarget("p1", "s1"),
            Worlds(world),
            setOf(PromptFilePicker { Outcome.Ok(file("picked.txt")) }),
        )
        vm.state.test {
            awaitItem()
            world.draft.update { it.copy(failure = Failure.StorageRefused()) }
            awaitUntil { it.draft.failure != null }
            vm.attachFile()
            val failed = awaitUntil { it.attachmentFailure != null }
            assertTrue(failed.attachmentFailure != null, "the refusal reaches the person")
            assertTrue(world.calls.none { it.startsWith("replace") }, "no write through an unsafe draft")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B-STATE-103 removeAttachment commits the filtered list and guards the index`() = runTest {
        val world = FilesWorld().apply {
            draft.update { it.copy(attachments = listOf(file("a.txt"), file("b.txt"))) }
        }
        val vm = BasicComposerViewModel(ComposerTarget("p1", "s1"), Worlds(world), emptySet())
        vm.state.test {
            awaitItem()
            vm.removeAttachment(1)
            awaitItem()
            vm.removeAttachment(9)
            assertEquals(listOf("replace [a.txt]"), world.calls.filter { it.startsWith("replace") })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B-STATE-103 selectModel reports the world answer and refuses a second in-flight choice`() =
        runTest {
            val world = FilesWorld()
            val gate = CompletableDeferred<Unit>()
            world.selectGate = gate
            val vm = BasicComposerViewModel(
                ComposerTarget("p1", "s1"),
                Worlds(world),
                emptySet(),
            )
            vm.state.test {
                awaitItem()
                var first: Boolean? = null
                val job = launch(start = CoroutineStart.UNDISPATCHED) { first = vm.selectModel(mini) }
                assertFalse(vm.selectModel(null), "one choice at a time")
                world.selectGate = null
                gate.complete(Unit)
                job.join()
                assertEquals(true, first)
                world.selectResult = Outcome.Failed(Failure.Network())
                assertFalse(vm.selectModel(mini), "a failed selection reports false")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `B-STATE-103 send stays off while a pick runs and returns when it ends`() = runTest {
        val world = FilesWorld()
        val gate = CompletableDeferred<PromptAttachment?>()
        val vm = BasicComposerViewModel(
            ComposerTarget("p1", "s1"),
            Worlds(world),
            setOf(PromptFilePicker { Outcome.Ok(gate.await()) }),
        )
        vm.state.test {
            awaitItem()
            assertTrue(vm.state.value.available)
            vm.attachFile()
            assertFalse(vm.state.value.available, "Send waits while a pick runs")
            gate.complete(null)
            awaitItem()
            assertTrue(vm.state.value.available)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B-STATE-103 files availability mirrors the platform picker set`() = runTest {
        val vm = BasicComposerViewModel(
            ComposerTarget("p1", "s1"),
            Worlds(FilesWorld()),
            setOf(PromptFilePicker { Outcome.Ok(null) }),
        )
        vm.state.test {
            awaitItem()
            assertTrue(vm.state.value.filesAvailable)
            cancelAndIgnoreRemainingEvents()
        }
        val bare = BasicComposerViewModel(ComposerTarget("p1", "s1"), Worlds(FilesWorld()), emptySet())
        bare.state.test {
            awaitItem()
            assertFalse(bare.state.value.filesAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B-STATE-103 without a session every file and model action is a safe no-op`() = runTest {
        val vm = BasicComposerViewModel(ComposerTarget("p1", null), Worlds(FilesWorld()), emptySet())
        vm.state.test {
            awaitItem()
            assertFalse(vm.state.value.available)
            assertFalse(vm.state.value.filesAvailable)
            vm.attachFile()
            vm.removeAttachment(0)
            vm.loadModels()
            vm.dismissAttachmentFailure()
            assertFalse(vm.selectModel(mini))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B-STATE-103 the send gate needs words or files and a settled draft`() {
        val settled = BasicComposerUi(available = true, sending = false, steers = false)
        assertFalse(SendGate.accepts(settled, ""))
        assertTrue(SendGate.accepts(settled, "hi"))
        assertTrue(
            SendGate.accepts(settled.copy(draft = DraftFacts(attachments = listOf(file("only.txt")))), "")
        )
        assertFalse(SendGate.accepts(settled.copy(sending = true), "hi"))
        assertFalse(
            SendGate.accepts(settled.copy(draft = DraftFacts(ready = false)), "hi")
        )
        assertFalse(
            SendGate.accepts(
                settled.copy(draft = DraftFacts(sendBlocked = true, attachments = listOf(file("only.txt")))),
                ""
            )
        )
    }

    // ---- RUNBOOK §7 finding 3 (red against the GLM patch) ----

    @Test
    fun `B-STATE-101 finding 3 Send refuses at once while a pick starts, before any state publishes`() =
        runTest {
            val main = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(main)
            val world = FilesWorld()
            world.draft.update { it.copy(text = "hello") }
            val gate = CompletableDeferred<PromptAttachment?>()
            val vm = BasicComposerViewModel(
                ComposerTarget("p1", "s1"),
                Worlds(world),
                setOf(PromptFilePicker { Outcome.Ok(gate.await()) }),
            )
            val collector = backgroundScope.launch { vm.state.collect {} }
            testScheduler.runCurrent()
            assertTrue(vm.state.value.available)
            vm.attachFile()
            assertFalse(vm.send("hello"), "the pick owns the composer until it ends")
            assertTrue(world.calls.none { it.startsWith("send") })
            gate.complete(null)
            testScheduler.runCurrent()
            collector.cancel()
        }

    @Test
    fun `B-STATE-101 finding 3 Send refuses at once while a model change runs`() = runTest {
        val main = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val world = FilesWorld()
        world.draft.update { it.copy(text = "hello") }
        val gate = CompletableDeferred<Unit>()
        world.selectGate = gate
        val vm = BasicComposerViewModel(ComposerTarget("p1", "s1"), Worlds(world), emptySet())
        val collector = backgroundScope.launch { vm.state.collect {} }
        testScheduler.runCurrent()
        val selecting = launch(start = CoroutineStart.UNDISPATCHED) { vm.selectModel(mini) }
        assertFalse(vm.send("hello"), "the prompt would race the model mutation")
        gate.complete(Unit)
        selecting.join()
        collector.cancel()
    }

    @Test
    fun `B-STATE-101 finding 3 a second removal waits for the first commit, never resurrecting a file`() =
        runTest {
            val world = FilesWorld().apply {
                draft.update { it.copy(attachments = listOf(file("a.txt"), file("b.txt"), file("c.txt"))) }
            }
            val gate = CompletableDeferred<Unit>()
            world.replaceGate = gate
            val vm = BasicComposerViewModel(ComposerTarget("p1", "s1"), Worlds(world), emptySet())
            vm.state.test {
                awaitItem()
                vm.removeAttachment(0)
                vm.removeAttachment(0)
                world.replaceGate = null
                gate.complete(Unit)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(
                listOf("replace [b.txt, c.txt]"),
                world.calls.filter { it.startsWith("replace") },
                "the tap during the commit is refused, not applied to a stale list",
            )
            assertEquals(listOf("b.txt", "c.txt"), world.draft.value.attachments.map { it.filename })
        }

    @Test
    fun `B-STATE-101 finding 3 a pick that returns after the draft target changed writes nothing`() =
        runTest {
            val world = FilesWorld()
            val vm = BasicComposerViewModel(
                ComposerTarget("p1", "s1"),
                Worlds(world),
                setOf(
                    PromptFilePicker {
                        world.draftGeneration++ // the draft owner was replaced while the picker was up
                        Outcome.Ok(file("late.txt"))
                    }
                ),
            )
            vm.state.test {
                awaitItem()
                vm.attachFile()
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(world.calls.none { it.startsWith("replace") })
            assertTrue(vm.state.value.attachmentFailure != null || world.calls.isEmpty())
        }

    private suspend fun app.cash.turbine.ReceiveTurbine<BasicComposerUi>.awaitUntil(
        done: (BasicComposerUi) -> Boolean
    ): BasicComposerUi {
        while (true) {
            val item = awaitItem()
            if (done(item)) return item
        }
    }
}
