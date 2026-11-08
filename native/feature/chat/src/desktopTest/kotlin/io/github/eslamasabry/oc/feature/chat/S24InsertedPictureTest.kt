package io.github.eslamasabry.oc.feature.chat

import app.cash.turbine.test
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerViewModel
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ComposerAttachSource
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * S24-CHAT story 5 (Flutter `_handleInsertedContent`): a picture the keyboard inserts joins the
 * draft through the platform's media intake; a text-only agent says so instead.
 */
class S24InsertedPictureTest {
    private val gif = PromptAttachment("image/gif", "pasted-image-1.gif", "data:image/gif;base64,R0lG")

    private class DraftWorld : FakeChatWorld() {
        override suspend fun replaceAttachments(files: List<PromptAttachment>): Outcome<Unit> {
            calls += "replace ${files.map { it.filename }}"
            draft.update { it.copy(attachments = files) }
            return Outcome.Ok(Unit)
        }
    }

    private class Media(private val gif: PromptAttachment) : ComposerAttachSource {
        val inserted = mutableListOf<String>()
        override val kinds: Set<AttachKind> = setOf(AttachKind.Photos)

        override suspend fun attach(kind: AttachKind, current: List<PromptAttachment>) =
            Outcome.Ok(emptyList<PromptAttachment>())

        override suspend fun attachInserted(
            mime: String?,
            bytes: ByteArray,
            current: List<PromptAttachment>,
        ): Outcome<List<PromptAttachment>> {
            inserted += "$mime ${bytes.size}"
            return Outcome.Ok(listOf(gif))
        }
    }

    private class Worlds(private val world: ChatWorld) : ChatWorlds {
        override fun open(ref: ConversationRef): ChatWorld = world

        override fun release(world: ChatWorld) {}
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `B-CHAT-S24-50 a keyboard GIF joins the draft through the media intake`() = runTest {
        val world = DraftWorld().apply { draft.value = DraftFacts(ready = true) }
        val media = Media(gif)
        val vm = BasicComposerViewModel(ComposerTarget("p1", "s1"), Worlds(world), attachSources = setOf(media))
        vm.attachInserted("image/gif", byteArrayOf(71, 73, 70))
        assertEquals(listOf("image/gif 3"), media.inserted)
        assertEquals(listOf("replace [pasted-image-1.gif]"), world.calls.filter { it.startsWith("replace") })
    }

    @Test
    fun `B-CHAT-S24-51 a text-only agent takes no picture and says why`() = runTest {
        val world = DraftWorld().apply {
            draft.value = DraftFacts(ready = true)
            capabilities.value = ServerCapabilities(promptAttachments = false)
        }
        val media = Media(gif)
        val vm = BasicComposerViewModel(ComposerTarget("p1", "s1"), Worlds(world), attachSources = setOf(media))
        vm.state.test {
            awaitItem()
            vm.attachInserted("image/gif", byteArrayOf(1))
            assertEquals(
                Failure.Unavailable(FeatureId("promptAttachments")),
                awaitUntil { it.attachmentFailure != null }.attachmentFailure,
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(media.inserted.isEmpty())
        assertTrue(world.calls.none { it.startsWith("replace") })
    }

    @Test
    fun `B-CHAT-S24-52 a picture the platform could not read is said on the draft's line`() = runTest {
        val world = DraftWorld().apply { draft.value = DraftFacts(ready = true) }
        val vm = BasicComposerViewModel(ComposerTarget("p1", "s1"), Worlds(world), attachSources = setOf(Media(gif)))
        val failure = Failure.Media(MediaProblem.ClipboardImageTooLarge)
        vm.state.test {
            awaitItem()
            vm.insertFailed(failure)
            assertEquals(failure, awaitUntil { it.attachmentFailure != null }.attachmentFailure)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(world.calls.none { it.startsWith("replace") })
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
