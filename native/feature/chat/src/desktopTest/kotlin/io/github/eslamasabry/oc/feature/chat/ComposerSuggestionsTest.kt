package io.github.eslamasabry.oc.feature.chat

import app.cash.turbine.test
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerViewModel
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ComposerAttachSource
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ComposerQuery
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.feature.chat.world.ComposerCommand
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** The `/` and `@` strips and the Add sheet's extra sources, at the ViewModel (P12 composer). */
class ComposerSuggestionsTest {
    private class SuggestWorld : FakeChatWorld() {
        override val commands = MutableStateFlow<List<ComposerCommand>>(emptyList())
        val searched = mutableListOf<String>()
        val mentioned = mutableListOf<String>()
        var files =
            listOf("docs/readme.md", "src/main.kt", "src/domain/main_test.kt", "has space.txt")

        override suspend fun loadCommands(): Outcome<Unit> {
            calls += "loadCommands"
            commands.value =
                listOf(
                    ComposerCommand("init"),
                    ComposerCommand("review"),
                    ComposerCommand("prereview"),
                )
            return Outcome.Ok(Unit)
        }

        override suspend fun findFiles(query: String): Outcome<List<String>> {
            searched += query
            return Outcome.Ok(files)
        }

        override suspend fun mentionFile(path: String): Outcome<Unit> {
            mentioned += path
            return Outcome.Ok(Unit)
        }

        override suspend fun replaceAttachments(files: List<PromptAttachment>): Outcome<Unit> {
            draft.update { it.copy(attachments = files) }
            return Outcome.Ok(Unit)
        }
    }

    private class Worlds(private val world: ChatWorld) : ChatWorlds {
        override fun open(ref: ConversationRef): ChatWorld = world

        override fun release(world: ChatWorld) {}
    }

    private val picture = PromptAttachment("image/png", "pasted.png", "data:image/png;base64,iVBO")

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `B-NAV-049 the query of a field is a whole-field slash or the at-word being typed`() {
        assertEquals(ComposerQuery.Command("rev"), ComposerQuery.of("/rev"))
        assertEquals(ComposerQuery.Command(""), ComposerQuery.of("/"))
        assertNull(ComposerQuery.of("/review now"))
        assertEquals(ComposerQuery.File("src/ma", 4), ComposerQuery.of("see @src/ma"))
        assertEquals(ComposerQuery.File("x", 1), ComposerQuery.of("(@x"))
        assertNull(ComposerQuery.of("mail@example"))
        assertNull(ComposerQuery.of("@done "))
    }

    @Test
    fun `B-CHAT-087 files rank name prefix first, then contains, without folders or spaces`() {
        assertEquals(
            listOf("src/main.kt", "src/domain/main_test.kt", "docs/readme.md"),
            ComposerQuery.rankFiles(
                "main",
                listOf(
                    "docs/readme.md",
                    "src/main.kt",
                    "src/domain/main_test.kt",
                    "has space.txt",
                    "src/",
                ),
            ),
        )
    }

    @Test
    fun `B-NAV-049 typing a slash loads the server commands once and filters them`() = runTest {
        val world = SuggestWorld()
        val vm = BasicComposerViewModel(ComposerTarget("p1", "s1"), Worlds(world), emptySet())
        vm.state.test {
            awaitItem()
            vm.draftChanged("/rev")
            var item = awaitItem()
            while (item.commandMatches.isEmpty()) item = awaitItem()
            assertEquals(listOf("review", "prereview"), item.commandMatches.map { it.name })
            vm.draftChanged("/re")
            vm.draftChanged("hello")
            while (item.commandMatches.isNotEmpty()) item = awaitItem()
            assertEquals(1, world.calls.count { it == "loadCommands" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B-CHAT-087 an at-word searches after a short pause and a pick stages the file`() =
        runTest {
            val world = SuggestWorld()
            val vm = BasicComposerViewModel(ComposerTarget("p1", "s1"), Worlds(world), emptySet())
            vm.state.test {
                awaitItem()
                vm.draftChanged("see @m")
                vm.draftChanged("see @ma")
                vm.draftChanged("see @mai")
                advanceTimeBy(200)
                var item = awaitItem()
                while (item.fileMatches.isEmpty()) item = awaitItem()
                assertEquals(listOf("mai"), world.searched, "one search after the typing settles")
                assertEquals("src/main.kt", item.fileMatches.first())
                vm.mentionFile("src/main.kt")
                while (item.fileMatches.isNotEmpty()) item = awaitItem()
                assertEquals(listOf("src/main.kt"), world.mentioned)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `B-CHAT-009 a clipboard picture is attached and an empty clipboard says so`() = runTest {
        val world = SuggestWorld()
        var next: List<PromptAttachment> = listOf(picture)
        val source =
            object : ComposerAttachSource {
                override val kinds = setOf(AttachKind.Clipboard)

                override suspend fun attach(kind: AttachKind, current: List<PromptAttachment>) =
                    Outcome.Ok(next)
            }
        val vm =
            BasicComposerViewModel(
                ComposerTarget("p1", "s1"),
                Worlds(world),
                emptySet(),
                setOf(source),
            )
        vm.state.test {
            var item = awaitItem()
            while (AttachKind.Clipboard !in item.attachKinds) item = awaitItem()
            vm.attach(AttachKind.Clipboard)
            while (item.draft.attachments.isEmpty()) item = awaitItem()
            assertEquals(listOf("pasted.png"), item.draft.attachments.map { it.filename })
            next = emptyList()
            vm.attach(AttachKind.Clipboard)
            while (item.attachmentFailure == null) item = awaitItem()
            val failure = assertIs<Failure.Unavailable>(item.attachmentFailure)
            assertEquals("clipboardImage", failure.feature.name)
            assertTrue(
                world.draft.value.attachments.size == 1,
                "an empty clipboard changes nothing",
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
